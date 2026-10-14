package main

import (
	"context"
	"crypto/tls"
	"crypto/x509"
	"encoding/json"
	"io"
	"net/http"
	"net/http/httptest"
	"os"
	"strings"
	"sync/atomic"
	"testing"
	"time"
)

type fallbackTransport func(*http.Request) (*http.Response, error)

func (f fallbackTransport) RoundTrip(r *http.Request) (*http.Response, error) { return f(r) }
func TestHTTPFallbackIsExplicitAndPreservesQuery(t *testing.T) {
	for _, enabled := range []bool{false, true} {
		calls := 0
		client := &http.Client{Transport: fallbackTransport(func(r *http.Request) (*http.Response, error) {
			calls++
			if r.URL.Scheme == "https" {
				return nil, &tls.CertificateVerificationError{Err: x509.CertificateInvalidError{Reason: x509.Expired}}
			}
			if r.URL.RawQuery != "password=a%26b" || r.URL.Host != "provider.example" || r.Host != "provider.example" {
				t.Fatal("fallback changed credentials or destination")
			}
			return &http.Response{StatusCode: 200, Body: io.NopCloser(strings.NewReader("ok")), Header: http.Header{}}, nil
		})}
		req, _ := http.NewRequest("GET", "https://provider.example:443/player_api.php?password=a%26b", nil)
		resp, err := doWithHTTPFallback(client, req, enabled)
		if enabled {
			if err != nil || calls != 2 {
				t.Fatal("opted-in fallback failed")
			}
			resp.Body.Close()
		} else if err == nil || calls != 1 {
			t.Fatal("fallback enabled by default")
		}
	}
	calls := 0
	client := &http.Client{Transport: fallbackTransport(func(r *http.Request) (*http.Response, error) {
		calls++
		return &http.Response{StatusCode: 401, Body: io.NopCloser(strings.NewReader("")), Header: http.Header{}}, nil
	})}
	req, _ := http.NewRequest("GET", "https://provider.example", nil)
	resp, err := doWithHTTPFallback(client, req, true)
	if err != nil || calls != 1 || resp.StatusCode != 401 {
		t.Fatal("auth failure triggered downgrade")
	}
	resp.Body.Close()
}
func TestCachedCatalogRestoresAndFailureRetriesWithoutDailySetting(t *testing.T) {
	var failed atomic.Bool
	failed.Store(true)
	provider := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		if failed.Load() {
			http.Error(w, "offline", 503)
			return
		}
		switch r.URL.Query().Get("action") {
		case "get_live_streams":
			io.WriteString(w, `[{"stream_id":7,"name":"Recovered","category_id":"1"}]`)
		case "get_live_categories":
			io.WriteString(w, `[]`)
		default:
			io.WriteString(w, `{"user_info":{"auth":1}}`)
		}
	}))
	defer provider.Close()
	a := testApp(t)
	p := Provider{ID: "saved", URL: provider.URL, Username: "test", Password: "fixture", AllowHTTP: true, Name: "Test", Format: "ts", MaxConnections: 1}
	a.store.update(func(s *State) { s.Providers = []Provider{p} })
	channels := []Channel{{ID: "saved:7", ProviderID: "saved", Name: "Remembered"}}
	guide := []Programme{{ChannelID: "saved:7", Title: "Cached show", Start: time.Now(), End: time.Now().Add(time.Hour)}}
	if err := a.store.saveCatalog(p.ID, channels, guide); err != nil {
		t.Fatal(err)
	}
	raw, _ := os.ReadFile(a.store.catalogPath(p.ID))
	if strings.Contains(string(raw), "Remembered") {
		t.Fatal("unencrypted cache")
	}
	store, err := openStore(a.store.dir)
	if err != nil {
		t.Fatal(err)
	}
	restored := &App{store: store}
	restored.restoreCachedCatalog(store.snapshot().Providers)
	if len(restored.channels) != 1 || len(restored.guide) != 1 {
		t.Fatal("restart lost catalog")
	}
	restored.refreshChannels(context.Background())
	if restored.channels[0].Name != "Remembered" {
		t.Fatal("failure erased cache")
	}
	state := restored.providerImportStatus(p.ID)["channels"]
	if state.Failures != 1 {
		t.Fatal("no automatic retry queued")
	}
	failed.Store(false)
	restored.retryImports(context.Background(), state.NextRetry.Add(time.Second))
	if restored.channels[0].Name != "Recovered" || len(restored.providerImportStatus(p.ID)) != 0 {
		t.Fatal("automatic reconnect failed")
	}
	restored.store.update(func(s *State) { s.Providers = nil })
	empty := &App{store: store}
	empty.restoreCachedCatalog(store.snapshot().Providers)
	if len(empty.channels) != 0 {
		t.Fatal("removed provider resurrected")
	}
}
func TestEditingProviderAddressAndUsernameRetainsIdentity(t *testing.T) {
	handler := http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		if r.URL.Query().Get("username") == "invalid" {
			io.WriteString(w, `{"user_info":{"auth":0}}`)
			return
		}
		switch r.URL.Query().Get("action") {
		case "get_live_streams":
			io.WriteString(w, `[{"stream_id":7,"name":"Seven"}]`)
		case "get_live_categories":
			io.WriteString(w, `[]`)
		default:
			io.WriteString(w, `{"user_info":{"auth":1}}`)
		}
	})
	first := httptest.NewServer(handler)
	defer first.Close()
	second := httptest.NewServer(handler)
	defer second.Close()
	a := testApp(t)
	p := Provider{URL: first.URL, Username: "before", Password: "fixture", Name: "Test", AllowHTTP: true, Format: "ts", MaxConnections: 1}
	body, _ := json.Marshal(p)
	if w := call(a, "POST", "/api/v1/providers", a.admin, string(body)); w.Code != 201 {
		t.Fatal(w.Body.String())
	}
	id := a.store.snapshot().Providers[0].ID
	call(a, "PUT", "/api/v1/guide/preferences", a.admin, `{"favorites":["`+id+`:7"]}`)
	edit := p
	edit.URL = second.URL
	edit.Username = "invalid"
	bad, _ := json.Marshal(edit)
	if w := call(a, "PUT", "/api/v1/providers/"+id, a.admin, string(bad)); w.Code != 422 {
		t.Fatal("invalid edit accepted")
	}
	if a.store.snapshot().Providers[0].URL != first.URL {
		t.Fatal("failed edit replaced account")
	}
	edit.Username = "after"
	edit.Password = ""
	changed, _ := json.Marshal(edit)
	if w := call(a, "PUT", "/api/v1/providers/"+id, a.admin, string(changed)); w.Code != 200 {
		t.Fatal(w.Body.String())
	}
	state := a.store.snapshot()
	if state.Providers[0].ID != id || state.Preferences["admin"].Favorites[0] != id+":7" {
		t.Fatal("edit lost preferences")
	}
	if w := call(a, "POST", "/api/v1/providers", a.admin, string(body)); w.Code != 201 {
		t.Fatal(w.Body.String())
	}
	state = a.store.snapshot()
	if len(state.Providers) != 2 || state.Providers[1].ID == id {
		t.Fatal("re-adding previous address overwrote edited account")
	}
}
