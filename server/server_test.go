package main

import (
	"context"
	"encoding/json"
	"net/http"
	"net/http/httptest"
	"os"
	"path/filepath"
	"strings"
	"testing"
	"time"
)

func testApp(t *testing.T) *App {
	t.Helper()
	s, e := openStore(t.TempDir())
	if e != nil {
		t.Fatal(e)
	}
	return newApp(s, strings.Repeat("a", 64), t.TempDir(), "nonexistent-ffmpeg-test", true)
}
func call(a *App, method, path, token, body string) *httptest.ResponseRecorder {
	r := httptest.NewRequest(method, path, strings.NewReader(body))
	if body != "" {
		r.Header.Set("Content-Type", "application/json")
	}
	if token != "" {
		r.Header.Set("Authorization", "Bearer "+token)
	}
	w := httptest.NewRecorder()
	a.routes().ServeHTTP(w, r)
	return w
}
func TestEncryptedStateRoundTrip(t *testing.T) {
	dir := t.TempDir()
	s, e := openStore(dir)
	if e != nil {
		t.Fatal(e)
	}
	e = s.update(func(st *State) { st.Providers = append(st.Providers, Provider{Password: "very-private-password"}) })
	if e != nil {
		t.Fatal(e)
	}
	raw, _ := os.ReadFile(filepath.Join(dir, "state.enc"))
	if strings.Contains(string(raw), "very-private-password") {
		t.Fatal("secret written in plaintext")
	}
	loaded, e := openStore(dir)
	if e != nil || loaded.snapshot().Providers[0].Password != "very-private-password" {
		t.Fatal("round trip failed", e)
	}
	os.Remove(filepath.Join(dir, "encryption.key"))
	if _, e = openStore(dir); e == nil {
		t.Fatal("must not silently replace missing encryption key")
	}
}
func TestPairingAndRevocation(t *testing.T) {
	a := testApp(t)
	admin := a.admin
	if w := call(a, "GET", "/api/v1/channels", "", ""); w.Code != 401 {
		t.Fatal(w.Code)
	}
	w := call(a, "POST", "/api/v1/pairing/requests", "", `{"name":"Shield"}`)
	var p map[string]string
	_ = json.Unmarshal(w.Body.Bytes(), &p)
	if w.Code != 201 || p["deviceSecret"] == "" {
		t.Fatal(w.Body.String())
	}
	if w = call(a, "POST", "/api/v1/pairing/poll", "", `{"id":"`+p["id"]+`","deviceSecret":"wrong"}`); w.Code != 404 {
		t.Fatal("wrong secret accepted")
	}
	if w = call(a, "POST", "/api/v1/pairing/approve", admin, `{"id":"`+p["id"]+`","code":"`+p["code"]+`"}`); w.Code != 200 {
		t.Fatal(w.Body.String())
	}
	w = call(a, "POST", "/api/v1/pairing/poll", "", `{"id":"`+p["id"]+`","deviceSecret":"`+p["deviceSecret"]+`"}`)
	var result map[string]string
	_ = json.Unmarshal(w.Body.Bytes(), &result)
	token := result["token"]
	if w = call(a, "GET", "/api/v1/channels", token, ""); w.Code != 200 {
		t.Fatal(w.Body.String())
	}
	if w = call(a, "GET", "/api/v1/providers", token, ""); w.Code != 403 {
		t.Fatal("device has admin access")
	}
	if w = call(a, "DELETE", "/api/v1/devices/"+p["id"], admin, ""); w.Code != 200 {
		t.Fatal(w.Body.String())
	}
	if w = call(a, "GET", "/api/v1/channels", token, ""); w.Code != 401 {
		t.Fatal("revoked device accepted")
	}
}
func TestMediaOwnershipAndTraversal(t *testing.T) {
	a := testApp(t)
	a.sessions["s"] = &Session{ID: "s", owner: "other"}
	a.store.update(func(s *State) { s.Devices = append(s.Devices, Device{"device", "TV", tokenHash("device-token")}) })
	if w := call(a, "GET", "/media/s/1/live.m3u8", "device-token", ""); w.Code != 404 {
		t.Fatal("other device media accessible")
	}
	for _, name := range []string{"../admin.token", "file.tmp", "segment-hello.ts", "segment-123456.ts/.."} {
		if validMediaName(name) {
			t.Fatal("unsafe media name", name)
		}
	}
}
func TestProviderValidationAndImport(t *testing.T) {
	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		if r.URL.Query().Get("password") != "p&word" {
			t.Error("password not encoded correctly")
		}
		if r.URL.Query().Get("action") == "get_live_streams" {
			w.Write([]byte(`[{"stream_id":12,"name":"News","category_id":4,"epg_channel_id":"news"},{"stream_id":"12","name":"duplicate"}]`))
		} else if r.URL.Query().Get("action") == "get_live_categories" {
			w.Write([]byte(`[{"category_id":"4","category_name":"News channels"}]`))
		} else {
			w.Write([]byte(`{"user_info":{"auth":"1","status":"Active"}}`))
		}
	}))
	defer srv.Close()
	p := Provider{ID: "p", Name: "Test", URL: srv.URL, Username: "name", Password: "p&word", Format: "ts", MaxConnections: 1}
	if validateProvider(p) == nil {
		t.Fatal("HTTP opt-in missing")
	}
	p.AllowHTTP = true
	if e := validateProvider(p); e != nil {
		t.Fatal(e)
	}
	ch, e := loadChannels(context.Background(), p)
	if e != nil || len(ch) != 1 || ch[0].ID != "p:12" || ch[0].CategoryName != "News channels" {
		t.Fatal(ch, e)
	}
}
func TestEPGTimezoneAndMalformedInput(t *testing.T) {
	now := time.Date(2026, 10, 12, 16, 0, 0, 0, time.UTC)
	xml := `<tv><programme start="20261012120000 -0400" stop="20261012130000 -0400" channel="news"><title>News &amp; weather</title></programme></tv>`
	p, e := parseEPG(strings.NewReader(xml), []Channel{{ID: "p:1", EPGID: "news"}}, now)
	if e != nil || len(p) != 1 || !p[0].Start.Equal(now) {
		t.Fatal(p, e)
	}
	if _, e = parseEPG(strings.NewReader(xml[:len(xml)-4]), []Channel{{ID: "p:1", EPGID: "news"}}, now); e == nil {
		t.Fatal("truncated document accepted")
	}
}
func TestProviderSlotsReservedAtomically(t *testing.T) {
	a := testApp(t)
	c := Channel{ID: "p:1", ProviderID: "p"}
	p := Provider{ID: "p", MaxConnections: 1}
	s, e := a.startSession("admin", c, p)
	if e != nil {
		t.Fatal(e)
	}
	defer s.cancel()
	if _, e = a.startSession("admin", c, p); e == nil {
		t.Fatal("provider slot overbooked")
	}
	<-s.done
}
func TestGuideFailurePreservesData(t *testing.T) {
	a := testApp(t)
	before := len(a.guide)
	p := Provider{ID: "p", URL: "http://127.0.0.1:1", AllowHTTP: true}
	a.store.update(func(s *State) { s.Providers = append(s.Providers, p) })
	w := call(a, "POST", "/api/v1/guide/refresh", a.admin, "{}")
	if w.Code != 200 || len(a.guide) != before {
		t.Fatal("failed guide import removed previous data")
	}
}

func TestPairingCreationRateLimited(t *testing.T) {
	a := testApp(t)
	if w := call(a, "POST", "/api/v1/pairing/requests", "", `{"name":"TV"}`); w.Code != 201 {
		t.Fatal(w.Code)
	}
	if w := call(a, "POST", "/api/v1/pairing/requests", "", `{"name":"TV"}`); w.Code != 429 {
		t.Fatal("pairing was not throttled", w.Code)
	}
}

func TestGuideProviderNamespace(t *testing.T) {
	a := testApp(t)
	now := time.Now().UTC()
	xml := `<tv><programme start="` + now.Format("20060102150405 -0700") + `" stop="` + now.Add(time.Hour).Format("20060102150405 -0700") + `" channel="same-id"><title>Provider One</title></programme></tv>`
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) { w.Write([]byte(xml)) }))
	defer server.Close()
	a.store.update(func(s *State) {
		s.Providers = append(s.Providers, Provider{ID: "one", URL: server.URL, AllowHTTP: true})
	})
	a.mu.Lock()
	a.channels = append(a.channels, Channel{ID: "one:1", ProviderID: "one", EPGID: "same-id"}, Channel{ID: "two:1", ProviderID: "two", EPGID: "same-id"})
	a.mu.Unlock()
	call(a, "POST", "/api/v1/guide/refresh", a.admin, "{}")
	a.mu.Lock()
	defer a.mu.Unlock()
	found := false
	for _, p := range a.guide {
		if p.ChannelID == "two:1" {
			t.Fatal("guide crossed provider namespace")
		}
		if p.ChannelID == "one:1" {
			found = true
		}
	}
	if !found {
		t.Fatal("guide missing")
	}
}
