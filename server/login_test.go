package main

import (
	"encoding/json"
	"net/http/httptest"
	"os"
	"path/filepath"
	"strings"
	"testing"
	"time"
)

const testCredentials = `{"username":"owner","password":"a-test-password-123"}`

func accountToken(t *testing.T, a *App, action, key, body string) string {
	t.Helper()
	r := call(a, "POST", "/api/v1/auth/"+action, key, body)
	if r.Code != 200 {
		t.Fatalf("%s: %d %s", action, r.Code, r.Body.String())
	}
	var result struct {
		Token string `json:"token"`
	}
	if err := json.Unmarshal(r.Body.Bytes(), &result); err != nil || len(result.Token) != 64 {
		t.Fatal("missing session")
	}
	return result.Token
}

func TestAccountLifecycle(t *testing.T) {
	a := testApp(t)
	token := accountToken(t, a, "setup", "", testCredentials)
	if call(a, "GET", "/api/v1/providers", token, "").Code != 200 {
		t.Fatal("session rejected")
	}
	if call(a, "GET", "/api/v1/providers", a.admin, "").Code != 401 {
		t.Fatal("legacy key still accepted")
	}
	if call(a, "POST", "/api/v1/auth/setup", token, testCredentials).Code != 409 {
		t.Fatal("account overwritten")
	}
	if call(a, "POST", "/api/v1/auth/login", "", `{"username":"owner","password":"incorrect"}`).Code != 401 {
		t.Fatal("bad password accepted")
	}
	if call(a, "POST", "/api/v1/auth/login", "", `{"username":"other","password":"a-test-password-123"}`).Code != 401 {
		t.Fatal("bad username accepted")
	}
	raw, err := os.ReadFile(filepath.Join(a.store.dir, "admin-login.json"))
	if err != nil || strings.Contains(string(raw), "a-test-password") {
		t.Fatal("password persistence unsafe")
	}
	call(a, "POST", "/api/v1/auth/logout", token, `{}`)
	if call(a, "GET", "/api/v1/providers", token, "").Code != 401 {
		t.Fatal("logout did not revoke")
	}
	token = accountToken(t, a, "login", "", testCredentials)
	a.login.mu.Lock()
	a.login.sessions[tokenHash(token)] = time.Now().Add(-time.Second)
	a.login.mu.Unlock()
	if call(a, "GET", "/api/v1/providers", token, "").Code != 401 {
		t.Fatal("expired session accepted")
	}
	b := newApp(a.store, a.admin, t.TempDir(), "nonexistent", false)
	accountToken(t, b, "login", "", testCredentials)
	if call(b, "GET", "/api/v1/providers", a.admin, "").Code != 401 {
		t.Fatal("restart restored legacy access")
	}
}

func TestAccountMigrationPreservesDevice(t *testing.T) {
	a := testApp(t)
	device := "private-device-test-key"
	if err := a.store.update(func(s *State) {
		s.Devices = append(s.Devices, Device{ID: "tv", Name: "TV", TokenHash: tokenHash(device)})
	}); err != nil {
		t.Fatal(err)
	}
	if call(a, "POST", "/api/v1/auth/setup", "", testCredentials).Code != 401 {
		t.Fatal("unprotected migration")
	}
	if call(a, "POST", "/api/v1/auth/setup", device, testCredentials).Code != 401 {
		t.Fatal("device upgraded itself")
	}
	accountToken(t, a, "setup", a.admin, testCredentials)
	if call(a, "GET", "/api/v1/channels", device, "").Code != 200 {
		t.Fatal("pairing lost")
	}
	if call(a, "GET", "/api/v1/providers", device, "").Code != 403 {
		t.Fatal("device became admin")
	}
}

func TestLoginFailsClosedAndRateLimits(t *testing.T) {
	a := testApp(t)
	if call(a, "POST", "/api/v1/auth/setup", "", `{"username":"owner","password":"short"}`).Code != 400 {
		t.Fatal("short password accepted")
	}
	accountToken(t, a, "setup", "", testCredentials)
	for i := 0; i < 8; i++ {
		call(a, "POST", "/api/v1/auth/login", "", `{"username":"owner","password":"wrong"}`)
	}
	if call(a, "POST", "/api/v1/auth/login", "", testCredentials).Code != 429 {
		t.Fatal("missing throttle")
	}
	if err := os.WriteFile(filepath.Join(a.store.dir, "admin-login.json"), []byte("corrupt"), 0600); err != nil {
		t.Fatal(err)
	}
	b := newApp(a.store, a.admin, t.TempDir(), "nonexistent", false)
	if call(b, "GET", "/api/v1/auth/status", "", "").Code != 503 {
		t.Fatal("corrupt account did not fail closed")
	}
	if call(b, "GET", "/api/v1/providers", a.admin, "").Code != 401 {
		t.Fatal("corruption restored old access")
	}
}

func TestAccountSetupRejectsCrossSiteForm(t *testing.T) {
	a := testApp(t)
	r := httptest.NewRequest("POST", "/api/v1/auth/setup", strings.NewReader(testCredentials))
	r.Header.Set("Content-Type", "text/plain")
	w := httptest.NewRecorder()
	a.routes().ServeHTTP(w, r)
	if w.Code != 415 || a.login.account != nil {
		t.Fatal("cross-site form could configure account")
	}
}
