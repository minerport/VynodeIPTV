package main

import (
	"encoding/json"
	"net/http"
	"net/http/httptest"
	"strings"
	"testing"
	"time"
)

func TestCustomGroupsOwnershipAndPersistence(t *testing.T) {
	a := testApp(t)
	if err := a.store.update(func(s *State) {
		s.Devices = append(s.Devices, Device{ID: "device", Name: "TV", TokenHash: tokenHash("test-device")})
	}); err != nil {
		t.Fatal(err)
	}
	body := `{"name":"My news","channelIds":["demo:1","demo:1"]}`
	w := call(a, "POST", "/api/v1/groups", a.admin, body)
	if w.Code != 201 {
		t.Fatal(w.Body.String())
	}
	var g CustomGroup
	json.Unmarshal(w.Body.Bytes(), &g)
	if len(g.ChannelIDs) != 1 || g.Owner != "" {
		t.Fatal("invalid group response")
	}
	if w = call(a, "GET", "/api/v1/groups", "test-device", ""); strings.TrimSpace(w.Body.String()) != "[]" {
		t.Fatal("cross-owner read")
	}
	for _, method := range []string{"PUT", "DELETE"} {
		if w = call(a, method, "/api/v1/groups/"+g.ID, "test-device", body); w.Code != 404 {
			t.Fatal("cross-owner mutation", w.Code)
		}
	}
	loaded, err := openStore(a.store.dir)
	if err != nil || len(loaded.snapshot().Groups) != 1 {
		t.Fatal("groups did not persist", err)
	}
	copy := loaded.snapshot()
	copy.Groups[0].ChannelIDs[0] = "mutated"
	if loaded.snapshot().Groups[0].ChannelIDs[0] != "demo:1" {
		t.Fatal("snapshot alias")
	}
	if w = call(a, "POST", "/api/v1/groups", a.admin, `{"name":"Bad","channelIds":["unknown"]}`); w.Code != 400 {
		t.Fatal("unknown channel accepted")
	}
	if w = call(a, "DELETE", "/api/v1/groups/"+g.ID, a.admin, ""); w.Code != 200 {
		t.Fatal(w.Body.String())
	}
	if len(a.store.snapshot().Groups) != 0 {
		t.Fatal("group not removed")
	}
}
func TestGuideWindowAndChannels(t *testing.T) {
	a := testApp(t)
	now := time.Now().UTC().Truncate(time.Hour)
	a.mu.Lock()
	a.guide = []Programme{{ChannelID: "one", Title: "Included", Start: now, End: now.Add(time.Hour)}, {ChannelID: "two", Title: "Wrong channel", Start: now, End: now.Add(time.Hour)}, {ChannelID: "one", Title: "Too late", Start: now.Add(4 * time.Hour), End: now.Add(5 * time.Hour)}}
	a.mu.Unlock()
	r := httptest.NewRequest(http.MethodGet, "/api/v1/guide", nil)
	q := r.URL.Query()
	q.Set("channelIds", "one")
	q.Set("from", now.Format(time.RFC3339))
	q.Set("to", now.Add(2*time.Hour).Format(time.RFC3339))
	r.URL.RawQuery = q.Encode()
	r.Header.Set("Authorization", "Bearer "+a.admin)
	w := httptest.NewRecorder()
	a.routes().ServeHTTP(w, r)
	var result struct {
		Programmes []Programme `json:"programmes"`
	}
	json.Unmarshal(w.Body.Bytes(), &result)
	if len(result.Programmes) != 1 || result.Programmes[0].Title != "Included" {
		t.Fatal(w.Body.String())
	}
}
func TestWorkerDiagnosticRedaction(t *testing.T) {
	for _, raw := range []string{"https://host/live/SECRET/PASSWORD/401.ts: Server returned 403 Forbidden", "[http] Protocol 'http' not on whitelist 'https,tcp,tls,crypto'", "https://host/SECRET/PASSWORD: invalid data"} {
		d := &workerDiagnostics{}
		d.Write([]byte(raw))
		reason := d.reason()
		if strings.Contains(reason, "SECRET") || strings.Contains(reason, "PASSWORD") || strings.Contains(reason, "https://") {
			t.Fatal("worker output leaked")
		}
		if reason == "Upstream stream ended" {
			t.Fatal("missing diagnostic category")
		}
	}
}
