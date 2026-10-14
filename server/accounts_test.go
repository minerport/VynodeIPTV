package main

import (
	"context"
	"encoding/json"
	"net/http"
	"net/http/httptest"
	"strings"
	"sync/atomic"
	"testing"
	"time"
)

func TestLegacyAccountIdentityAndFailedRefreshRetainData(t *testing.T) {
	var unavailable atomic.Bool
	provider := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		if unavailable.Load() {
			http.Error(w, "offline", 503)
			return
		}
		switch r.URL.Query().Get("action") {
		case "get_live_streams":
			w.Write([]byte(`[{"stream_id":7,"name":"Seven","category_id":"1","epg_channel_id":"seven"}]`))
		case "get_live_categories":
			w.Write([]byte(`[{"category_id":"1","category_name":"News"}]`))
		default:
			w.Write([]byte(`{"user_info":{"auth":1,"status":"Active"}}`))
		}
	}))
	defer provider.Close()
	a := testApp(t)
	p := Provider{ID: "legacy-random-id", Name: "Legacy", URL: provider.URL, Username: "owner", Password: "fixture", Format: "ts", AllowHTTP: true, MaxConnections: 1}
	if err := a.store.update(func(s *State) {
		s.Providers = []Provider{p}
		s.Preferences = map[string]GuidePreferences{"admin": {Favorites: []string{p.ID + ":7"}}, "tv": {HiddenGroups: []string{"provider:" + p.ID + ":1"}}}
	}); err != nil {
		t.Fatal(err)
	}
	if !refreshSucceeded(a.refreshChannels(context.Background())) {
		t.Fatal("fixture import failed")
	}
	a.guide = []Programme{{ChannelID: p.ID + ":7", Title: "Retain this programme", Start: time.Now(), End: time.Now().Add(time.Hour)}}
	unavailable.Store(true)
	if refreshSucceeded(a.refreshChannels(context.Background())) || refreshSucceeded(a.syncGuide(context.Background())) {
		t.Fatal("failed provider accepted")
	}
	if len(a.channels) != 2 || len(a.guide) != 1 {
		t.Fatal("failed refresh erased existing catalog or guide")
	}
	unavailable.Store(false)
	a.sessions["busy"] = &Session{provider: p.ID}
	if w := call(a, "DELETE", "/api/v1/providers/"+p.ID, a.admin, ""); w.Code != 409 {
		t.Fatal("deleted playing account")
	}
	delete(a.sessions, "busy")
	if w := call(a, "DELETE", "/api/v1/providers/"+p.ID, a.admin, ""); w.Code != 200 {
		t.Fatal(w.Body.String())
	}
	reloaded, err := openStore(a.store.dir)
	if err != nil {
		t.Fatal(err)
	}
	a.store = reloaded
	body, _ := json.Marshal(p)
	if w := call(a, "POST", "/api/v1/providers", a.admin, string(body)); w.Code != 201 {
		t.Fatal(w.Body.String())
	}
	state := a.store.snapshot()
	if state.Providers[0].ID != p.ID || state.Preferences["admin"].Favorites[0] != p.ID+":7" || len(state.Preferences["tv"].HiddenGroups) != 1 || len(state.Preferences["tv"].Favorites) != 0 {
		t.Fatal("legacy identity or separate owner preferences were lost")
	}
}

func TestSavedAccountEditDeleteReaddRetainsPreferences(t *testing.T) {
	provider := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		switch r.URL.Query().Get("action") {
		case "get_live_streams":
			w.Write([]byte(`[{"stream_id":7,"name":"Seven","category_id":"1","epg_channel_id":"seven"}]`))
		case "get_live_categories":
			w.Write([]byte(`[{"category_id":"1","category_name":"News"}]`))
		default:
			w.Write([]byte(`{"user_info":{"auth":1,"status":"Active"}}`))
		}
	}))
	defer provider.Close()
	a := testApp(t)
	p := Provider{Name: "Test", URL: provider.URL, Username: "owner", Password: "fixture-password", Format: "ts", MaxConnections: 1, AllowHTTP: true}
	body, _ := json.Marshal(p)
	added := call(a, "POST", "/api/v1/providers", a.admin, string(body))
	if added.Code != 201 {
		t.Fatal(added.Body.String())
	}
	id := a.store.snapshot().Providers[0].ID
	pref := `{"favorites":["` + id + `:7"],"hiddenGroups":["provider:` + id + `:1"],"groupOrder":["provider:` + id + `:1"]}`
	if w := call(a, "PUT", "/api/v1/guide/preferences", a.admin, pref); w.Code != 200 {
		t.Fatal(w.Body.String())
	}
	p.Password = ""
	p.Name = "Edited"
	edit, _ := json.Marshal(p)
	if w := call(a, "PUT", "/api/v1/providers/"+id, a.admin, string(edit)); w.Code != 200 {
		t.Fatal(w.Body.String())
	}
	if a.store.snapshot().Providers[0].Password != "fixture-password" {
		t.Fatal("blank edit lost password")
	}
	listed := call(a, "GET", "/api/v1/providers", a.admin, "")
	if strings.Contains(listed.Body.String(), "fixture-password") {
		t.Fatal("password exposed")
	}
	if w := call(a, "DELETE", "/api/v1/providers/"+id, a.admin, ""); w.Code != 200 {
		t.Fatal(w.Body.String())
	}
	if len(a.store.snapshot().Providers) != 0 {
		t.Fatal("credentials retained after delete")
	}
	if w := call(a, "POST", "/api/v1/providers", a.admin, string(body)); w.Code != 201 {
		t.Fatal(w.Body.String())
	}
	if a.store.snapshot().Providers[0].ID != id {
		t.Fatal("provider identity changed on re-add")
	}
	if got := a.store.snapshot().Preferences["admin"]; len(got.Favorites) != 1 || got.Favorites[0] != id+":7" || len(got.HiddenGroups) != 1 || len(got.GroupOrder) != 1 {
		t.Fatal("preferences lost")
	}
	p.Username = "another-user"
	p.Password = "other"
	other, _ := json.Marshal(p)
	call(a, "POST", "/api/v1/providers", a.admin, string(other))
	if len(a.store.snapshot().Providers) != 2 || a.store.snapshot().Providers[1].ID == id {
		t.Fatal("users merged")
	}
}
func TestRefreshSettingsPersistAndRemainAdminOnly(t *testing.T) {
	a := testApp(t)
	a.store.update(func(s *State) { s.Devices = append(s.Devices, Device{ID: "tv", TokenHash: tokenHash("tv-token")}) })
	if w := call(a, "PUT", "/api/v1/refresh/settings", "tv-token", `{"channelsDaily":true}`); w.Code != 403 {
		t.Fatal("device can change global schedule")
	}
	if w := call(a, "PUT", "/api/v1/refresh/settings", a.admin, `{"channelsDaily":true,"epgDaily":true}`); w.Code != 200 {
		t.Fatal(w.Body.String())
	}
	loaded, err := openStore(a.store.dir)
	if err != nil || !loaded.snapshot().Refresh.ChannelsDaily || !loaded.snapshot().Refresh.EPGDaily {
		t.Fatal("settings not persisted", err)
	}
	now := time.Now()
	if dailyDue(false, 0, now) || dailyDue(true, now.Unix(), now) || !dailyDue(true, now.Add(-25*time.Hour).Unix(), now) {
		t.Fatal("daily cadence incorrect")
	}
	if refreshSucceeded(map[string]string{"p": "Provider failed"}) {
		t.Fatal("failure recorded as successful")
	}
}
