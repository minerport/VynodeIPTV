package main

import (
	"context"
	"fmt"
	"net/http"
	"net/http/httptest"
	"strings"
	"sync/atomic"
	"testing"
	"time"
)

func TestEpisodeNumbersAndIdentity(t *testing.T) {
	for _, c := range []struct {
		system, value string
		s, e          int
		ok            bool
	}{{"xmltv_ns", "0 . 2 .", 1, 3, true}, {"xmltv_ns", "1/9.4/20.0/1", 2, 5, true}, {"onscreen", "S00E03", 0, 3, true}, {"onscreen", "S02E00", 0, 0, false}, {"xmltv_ns", ".2.", 0, 0, false}, {"other", "S02E03", 0, 0, false}} {
		s, e, ok := parseEpisodeNumber(c.system, c.value)
		if ok != c.ok || (ok && (s != c.s || e != c.e)) {
			t.Fatalf("%+v got %d %d %v", c, s, e, ok)
		}
	}
	q := cleanMetadataQuery(MetadataQuery{Title: "The Example S02E03", Year: "2025"})
	if q.Title != "The Example" || q.Season != 2 || q.Episode != 3 {
		t.Fatal(q)
	}
	other := q
	other.Episode = 4
	if metadataKey(q) == metadataKey(other) {
		t.Fatal("episode cache collision")
	}
	tv := tmdbCandidate{ID: 1, Kind: "tv", Name: "The Example", FirstAir: "2010-01-01"}
	movie := tmdbCandidate{ID: 2, Kind: "movie", Title: "The Example", Release: "2025-01-01"}
	if got := chooseTMDB(q, []tmdbCandidate{movie, tv}, 1); got == nil || got.Kind != "tv" {
		t.Fatal("episode year treated as series premiere")
	}
	remake := tv
	remake.ID = 3
	if chooseTMDB(q, []tmdbCandidate{tv, remake}, 1) != nil {
		t.Fatal("ambiguous series guessed")
	}
}
func TestXMLTVEpisodeFields(t *testing.T) {
	now := time.Date(2026, 10, 14, 12, 0, 0, 0, time.UTC)
	xml := `<tv><programme channel="c" start="20261014120000 +0000" stop="20261014130000 +0000"><title>The Example</title><sub-title>The Arrival</sub-title><desc>Provider episode synopsis</desc><date>2025</date><episode-num system="xmltv_ns">1.2.</episode-num></programme></tv>`
	ps, e := parseEPG(strings.NewReader(xml), []Channel{{ID: "p:c", EPGID: "c"}}, now)
	if e != nil {
		t.Fatal(e)
	}
	p := ps[0]
	if p.Season != 2 || p.Episode != 3 || p.EpisodeTitle != "The Arrival" || p.Description != "Provider episode synopsis" || !p.Start.Equal(now) {
		t.Fatal(p)
	}
}
func TestEpisodeDetailsAndFallback(t *testing.T) {
	app := testApp(t)
	mode := "matched"
	upstream := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		switch r.URL.Path {
		case "/search/multi":
			fmt.Fprint(w, `{"total_pages":1,"results":[{"id":42,"media_type":"tv","name":"The Example","first_air_date":"2010-01-01"}]}`)
		case "/tv/42":
			fmt.Fprint(w, `{"name":"The Example","overview":"Series synopsis","poster_path":"/poster.jpg"}`)
		case "/tv/42/season/2/episode/3":
			if mode == "missing" {
				http.NotFound(w, r)
				return
			}
			if mode == "limited" {
				w.WriteHeader(429)
				return
			}
			fmt.Fprint(w, `{"name":"The Arrival","season_number":2,"episode_number":3,"overview":"Episode synopsis","still_path":"/still.jpg"}`)
		default:
			t.Error("unexpected path", r.URL.Path)
			http.NotFound(w, r)
		}
	}))
	defer upstream.Close()
	app.tmdb.base = upstream.URL
	q := MetadataQuery{Title: "The Example", Year: "2025", Season: 2, Episode: 3, EpisodeTitle: "The Arrival"}
	got, e := app.tmdb.fetch(context.Background(), "fixture", q)
	if e != nil || got.Episode != 3 || got.Overview != "Episode synopsis" || got.Still == "" {
		t.Fatal(got, e)
	}
	q.EpisodeTitle = "Different episode"
	got, e = app.tmdb.fetch(context.Background(), "fixture", q)
	if e != nil || got.Episode != 0 || got.Overview != "Series synopsis" {
		t.Fatal("subtitle mismatch not rejected", got, e)
	}
	q.EpisodeTitle = ""
	mode = "missing"
	got, e = app.tmdb.fetch(context.Background(), "fixture", q)
	if e != nil || got.Episode != 0 {
		t.Fatal("404 must retain series", got, e)
	}
	mode = "limited"
	if _, e = app.tmdb.fetch(context.Background(), "fixture", q); e == nil {
		t.Fatal("429 must trigger backoff")
	}
}
func TestBackgroundWindowAndPriority(t *testing.T) {
	now := time.Now()
	rows := []Programme{{Title: "Already ended", Start: now.Add(-time.Hour), End: now}, {Title: "Too far away", Start: now.Add(25 * time.Hour), End: now.Add(26 * time.Hour)}}
	for i := 0; i < 350; i++ {
		p := Programme{Title: fmt.Sprintf("Show %03d", i), Start: now.Add(time.Duration(i) * time.Minute), End: now.Add(time.Duration(i+1) * time.Minute)}
		rows = append(rows, p, p)
	}
	queries := upcomingMetadata(rows, now)
	if len(queries) != 300 || queries[0].Title != "Show 000" {
		t.Fatal("horizon, deduplication or bound failed")
	}
	app := testApp(t)
	off := false
	app.store.update(func(s *State) { s.TMDB = TMDBSettings{Enabled: true, Token: "fixture", Background: &off} })
	app.tmdb.warmOne(queries)
	if len(app.tmdb.pending) != 0 {
		t.Fatal("disabled background scheduled")
	}
	app.store.update(func(s *State) { s.TMDB.Background = nil })
	app.tmdb.mu.Lock()
	app.tmdb.pending["foreground"] = true
	app.tmdb.mu.Unlock()
	app.tmdb.warmOne(queries)
	app.tmdb.mu.Lock()
	defer app.tmdb.mu.Unlock()
	if len(app.tmdb.pending) != 1 {
		t.Fatal("background did not yield")
	}
}

func TestBackgroundWarmsCacheWithoutViewer(t *testing.T) {
	app := testApp(t)
	var searches atomic.Int32
	upstream := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		if r.URL.Path == "/search/multi" {
			searches.Add(1)
			fmt.Fprint(w, `{"total_pages":1,"results":[{"id":7,"media_type":"movie","title":"The Example"}]}`)
		} else {
			fmt.Fprint(w, `{"title":"The Example","overview":"Cached before browsing"}`)
		}
	}))
	defer upstream.Close()
	app.tmdb.base = upstream.URL
	app.store.update(func(s *State) { s.TMDB = TMDBSettings{Enabled: true, Token: "fixture"} })
	q := MetadataQuery{Title: "The Example"}
	app.tmdb.warmOne([]MetadataQuery{q})
	deadline := time.Now().Add(3 * time.Second)
	ready := false
	for time.Now().Before(deadline) {
		app.tmdb.mu.Lock()
		ready = app.tmdb.cache[metadataKey(q)].Data != nil
		app.tmdb.mu.Unlock()
		if ready {
			break
		}
		time.Sleep(10 * time.Millisecond)
	}
	if !ready {
		t.Fatal("background did not populate cache")
	}
	app.tmdb.warmOne([]MetadataQuery{q})
	entry := app.tmdb.lookup(q)
	if entry.State != "matched" || entry.Data.Overview != "Cached before browsing" || searches.Load() != 1 {
		t.Fatal("viewer did not reuse background cache", entry)
	}
}
