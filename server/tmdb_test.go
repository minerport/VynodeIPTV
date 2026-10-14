package main

import (
	"encoding/json"
	"net/http"
	"net/http/httptest"
	"os"
	"path/filepath"
	"strings"
	"sync/atomic"
	"testing"
	"time"
)

func TestTMDBMatching(t *testing.T) {
	movie := tmdbCandidate{ID: 1, Kind: "movie", Title: "The Example", Release: "2020-01-01"}
	remake := movie
	remake.ID = 2
	remake.Release = "1990-01-01"
	if chooseTMDB(MetadataQuery{Title: "The Example"}, []tmdbCandidate{movie, remake}, 1) != nil {
		t.Fatal("ambiguous remake matched")
	}
	if got := chooseTMDB(cleanMetadataQuery(MetadataQuery{Title: "The Example (2020)"}), []tmdbCandidate{movie, remake}, 1); got == nil || got.ID != 1 {
		t.Fatal("year disambiguation failed")
	}
	for _, title := range []string{"News", "Live NFL Football", "Untitled programme", "No EPG data", "The Examples"} {
		if chooseTMDB(MetadataQuery{Title: title}, []tmdbCandidate{movie}, 1) != nil {
			t.Fatal("unsafe match", title)
		}
	}
	if chooseTMDB(MetadataQuery{Title: movie.Title}, []tmdbCandidate{movie}, 2) != nil {
		t.Fatal("incomplete result set matched")
	}
	if tmdbImage("//evil.example/image.jpg", "w342") != "" || tmdbImage("/../secret.jpg", "w342") != "" {
		t.Fatal("unsafe image accepted")
	}
}
func TestTMDBSettingsCacheAndOwnership(t *testing.T) {
	a := testApp(t)
	t.Cleanup(a.close)
	var count atomic.Int32
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		if r.Header.Get("Authorization") != "Bearer fixture-token" || r.URL.Query().Has("api_key") {
			t.Error("credential transport")
		}
		w.Header().Set("Content-Type", "application/json")
		switch r.URL.Path {
		case "/configuration":
			_, _ = w.Write([]byte(`{"images":{}}`))
		case "/search/multi":
			count.Add(1)
			_, _ = w.Write([]byte(`{"total_pages":1,"results":[{"id":42,"media_type":"movie","title":"The Example","release_date":"2020-01-01"}]}`))
		case "/movie/42":
			_, _ = w.Write([]byte(`{"title":"The Example","overview":"Enriched description","poster_path":"/poster.jpg","genres":[{"name":"Drama"}],"credits":{"cast":[{"name":"Actor"}]}}`))
		default:
			http.NotFound(w, r)
		}
	}))
	defer server.Close()
	a.tmdb.base = server.URL
	if w := call(a, "PUT", "/api/v1/metadata/settings", a.admin, `{"enabled":true,"token":"fixture-token"}`); w.Code != 200 {
		t.Fatal(w.Code, w.Body.String())
	}
	if err := a.store.update(func(s *State) { s.Refresh.EPGDaily = true }); err != nil {
		t.Fatal(err)
	}
	restored, e := openStore(a.store.dir)
	if e != nil || restored.snapshot().TMDB.Token != "fixture-token" {
		t.Fatal("settings not retained")
	}
	raw, _ := os.ReadFile(filepath.Join(a.store.dir, "state.enc"))
	if strings.Contains(string(raw), "fixture-token") {
		t.Fatal("plaintext credential")
	}
	if w := call(a, "GET", "/api/v1/metadata/settings", a.admin, ""); strings.Contains(w.Body.String(), "fixture-token") {
		t.Fatal("token leaked")
	}
	token := strings.Repeat("b", 64)
	_ = a.store.update(func(s *State) { s.Devices = append(s.Devices, Device{ID: "device", TokenHash: tokenHash(token)}) })
	if w := call(a, "PUT", "/api/v1/metadata/settings", token, `{"enabled":false}`); w.Code != 403 {
		t.Fatal("device changed server credentials")
	}
	if w := call(a, "POST", "/api/v1/metadata/lookup", "", `{"title":"The Example"}`); w.Code != 401 {
		t.Fatal("anonymous lookup")
	}
	query := MetadataQuery{Title: "The Example"}
	first := a.tmdb.lookup(query)
	if first.State != "pending" {
		t.Fatal(first)
	}
	var got metadataEntry
	deadline := time.Now().Add(3 * time.Second)
	for time.Now().Before(deadline) {
		got = a.tmdb.lookup(query)
		if got.State == "matched" {
			break
		}
		time.Sleep(10 * time.Millisecond)
	}
	if got.Data == nil || got.Data.ID != 42 || got.Data.Overview != "Enriched description" {
		t.Fatalf("missing enrichment: %+v", got)
	}
	if count.Load() != 1 {
		t.Fatal("duplicate lookups")
	}
	other := newTMDB(restored)
	defer other.close()
	if other.lookup(query).Data == nil {
		t.Fatal("cache did not survive restart")
	}
	if w := call(a, "DELETE", "/api/v1/metadata/settings", a.admin, ""); w.Code != 200 {
		t.Fatal(w.Code)
	}
	if a.tmdb.lookup(query).State != "disabled" || a.store.snapshot().TMDB.Token != "" {
		t.Fatal("disconnect failed")
	}
}
func TestTMDBOutageAndProviderPreservation(t *testing.T) {
	a := testApp(t)
	t.Cleanup(a.close)
	var calls atomic.Int32
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) { calls.Add(1); w.WriteHeader(429) }))
	defer server.Close()
	a.tmdb.base = server.URL
	_ = a.store.update(func(s *State) { s.TMDB = TMDBSettings{Token: "fixture-token", Enabled: true} })
	now := time.Now().UTC().Truncate(time.Second)
	p := Programme{ChannelID: "c", Title: "The Example", Start: now, End: now.Add(time.Hour), Description: "Original", Year: "2020"}
	a.guide = []Programme{p}
	a.tmdb.lookup(MetadataQuery{Title: p.Title})
	deadline := time.Now().Add(2 * time.Second)
	for time.Now().Before(deadline) {
		a.tmdb.mu.Lock()
		retry := a.tmdb.retry
		a.tmdb.mu.Unlock()
		if !retry.IsZero() {
			break
		}
		time.Sleep(10 * time.Millisecond)
	}
	for i := 0; i < 10; i++ {
		if a.tmdb.lookup(MetadataQuery{Title: "Another Example"}).State != "unavailable" {
			t.Fatal("missing backoff")
		}
	}
	if calls.Load() != 1 {
		t.Fatal("outage retried excessively")
	}
	w := call(a, "GET", "/api/v1/guide?channelId=c", a.admin, "")
	var body struct {
		Programmes []Programme `json:"programmes"`
	}
	if json.Unmarshal(w.Body.Bytes(), &body) != nil || len(body.Programmes) != 1 || body.Programmes[0] != p {
		t.Fatal("provider guide changed")
	}
}
