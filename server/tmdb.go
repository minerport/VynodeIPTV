package main

import (
	"context"
	"encoding/json"
	"errors"
	"fmt"
	"io"
	"net/http"
	"net/url"
	"os"
	"path/filepath"
	"regexp"
	"strings"
	"sync"
	"time"
	"unicode"
)

type TMDBSettings struct {
	Token   string `json:"token"`
	Enabled bool   `json:"enabled"`
}
type MetadataQuery struct {
	Title string `json:"title"`
	Year  string `json:"year"`
}
type Metadata struct {
	ID       int      `json:"id"`
	Kind     string   `json:"kind"`
	Title    string   `json:"title"`
	Year     string   `json:"year"`
	Overview string   `json:"overview"`
	Poster   string   `json:"poster"`
	Backdrop string   `json:"backdrop"`
	Genres   []string `json:"genres"`
	Cast     []string `json:"cast"`
	URL      string   `json:"url"`
}
type metadataEntry struct {
	State   string    `json:"state"`
	Data    *Metadata `json:"data,omitempty"`
	Expires time.Time `json:"expires"`
}
type tmdbCandidate struct {
	ID            int    `json:"id"`
	Kind          string `json:"media_type"`
	Title         string `json:"title"`
	Name          string `json:"name"`
	OriginalTitle string `json:"original_title"`
	OriginalName  string `json:"original_name"`
	Release       string `json:"release_date"`
	FirstAir      string `json:"first_air_date"`
	Adult         bool   `json:"adult"`
}
type tmdbService struct {
	mu         sync.Mutex
	cache      map[string]metadataEntry
	pending    map[string]bool
	store      *Store
	client     *http.Client
	base       string
	status     string
	retry      time.Time
	gate       chan struct{}
	generation int
	ctx        context.Context
	cancel     context.CancelFunc
	wg         sync.WaitGroup
	closed     bool
}

func newTMDB(s *Store) *tmdbService {
	ctx, cancel := context.WithCancel(context.Background())
	t := &tmdbService{store: s, cache: map[string]metadataEntry{}, pending: map[string]bool{}, client: &http.Client{Timeout: 8 * time.Second, CheckRedirect: func(*http.Request, []*http.Request) error { return errors.New("redirect blocked") }}, base: "https://api.themoviedb.org/3", gate: make(chan struct{}, 1), ctx: ctx, cancel: cancel}
	if raw, e := os.ReadFile(filepath.Join(s.dir, "tmdb-cache.json")); e == nil && len(raw) <= 8<<20 {
		_ = json.Unmarshal(raw, &t.cache)
	}
	if t.cache == nil {
		t.cache = map[string]metadataEntry{}
	}
	t.persist()
	t.wg.Add(1)
	go func() {
		defer t.wg.Done()
		ticker := time.NewTicker(time.Hour)
		defer ticker.Stop()
		for {
			select {
			case <-t.ctx.Done():
				return
			case <-ticker.C:
				t.mu.Lock()
				t.persist()
				t.mu.Unlock()
			}
		}
	}()
	return t
}
func (t *tmdbService) close() { t.mu.Lock(); t.closed = true; t.cancel(); t.mu.Unlock(); t.wg.Wait() }
func (t *tmdbService) prune() {
	for k, v := range t.cache {
		if time.Now().After(v.Expires) {
			delete(t.cache, k)
		}
	}
	for len(t.cache) > 1000 {
		for k := range t.cache {
			delete(t.cache, k)
			break
		}
	}
}
func (t *tmdbService) persist() {
	t.prune()
	raw, e := json.Marshal(t.cache)
	if e == nil && len(raw) <= 8<<20 {
		path := filepath.Join(t.store.dir, "tmdb-cache.json")
		if os.WriteFile(path+".tmp", raw, 0600) == nil {
			_ = os.Rename(path+".tmp", path)
		}
	}
}

var metadataYear = regexp.MustCompile(`\s*\((19[0-9]{2}|20[0-9]{2})\)\s*$`)

func normalizeMetadata(s string) string {
	return strings.Join(strings.FieldsFunc(strings.ToLower(s), func(r rune) bool { return !unicode.IsLetter(r) && !unicode.IsDigit(r) }), " ")
}
func cleanMetadataQuery(q MetadataQuery) MetadataQuery {
	if !regexp.MustCompile(`^(19|20)[0-9]{2}$`).MatchString(q.Year) {
		q.Year = ""
	}
	q.Title = strings.TrimSpace(q.Title)
	if m := metadataYear.FindStringSubmatch(q.Title); len(m) > 1 {
		q.Title = strings.TrimSpace(metadataYear.ReplaceAllString(q.Title, ""))
		if q.Year == "" {
			q.Year = m[1]
		}
	}
	return q
}
func metadataAllowed(q MetadataQuery) bool {
	title := normalizeMetadata(q.Title)
	if len(title) < 4 || len(q.Title) > 240 {
		return false
	}
	for _, word := range []string{"news", "football", "basketball", "baseball", "hockey", "soccer", "tennis", "golf", "nfl", "nba", "mlb", "nhl", "paid programming", "untitled programme", "no epg data", "to be announced"} {
		if strings.Contains(" "+title+" ", " "+word+" ") {
			return false
		}
	}
	return true
}
func chooseTMDB(q MetadataQuery, results []tmdbCandidate, pages int) *tmdbCandidate {
	if pages > 1 || !metadataAllowed(q) {
		return nil
	}
	matches := []tmdbCandidate{}
	title := normalizeMetadata(q.Title)
	for _, c := range results {
		if c.Adult || c.ID <= 0 || (c.Kind != "movie" && c.Kind != "tv") {
			continue
		}
		name, original, date := c.Title, c.OriginalTitle, c.Release
		if c.Kind == "tv" {
			name, original, date = c.Name, c.OriginalName, c.FirstAir
		}
		if normalizeMetadata(name) != title && normalizeMetadata(original) != title {
			continue
		}
		if q.Year != "" && !strings.HasPrefix(date, q.Year+"-") {
			continue
		}
		matches = append(matches, c)
	}
	if len(matches) != 1 {
		return nil
	}
	return &matches[0]
}
func (t *tmdbService) get(ctx context.Context, token, path string, out any) error {
	req, e := http.NewRequestWithContext(ctx, "GET", t.base+path, nil)
	if e != nil {
		return errors.New("Invalid metadata request")
	}
	req.Header.Set("Authorization", "Bearer "+token)
	res, e := t.client.Do(req)
	if e != nil {
		return errors.New("TMDB unavailable; provider guide retained")
	}
	defer res.Body.Close()
	if res.StatusCode == 429 {
		return errors.New("TMDB rate limit; retrying later")
	}
	if res.StatusCode == 401 || res.StatusCode == 403 {
		return errors.New("TMDB rejected the token")
	}
	if res.StatusCode != 200 {
		return errors.New("TMDB unavailable; provider guide retained")
	}
	raw, e := io.ReadAll(io.LimitReader(res.Body, (2<<20)+1))
	if e != nil || len(raw) > 2<<20 {
		return errors.New("Invalid TMDB response")
	}
	if json.Unmarshal(raw, out) != nil {
		return errors.New("Invalid TMDB response")
	}
	return nil
}

var imagePath = regexp.MustCompile(`^/[A-Za-z0-9_-]+\.(jpg|png)$`)

func tmdbImage(path, size string) string {
	if !imagePath.MatchString(path) {
		return ""
	}
	return "https://image.tmdb.org/t/p/" + size + path
}
func (t *tmdbService) fetch(ctx context.Context, token string, q MetadataQuery) (*Metadata, error) {
	var result struct {
		Results []tmdbCandidate `json:"results"`
		Pages   int             `json:"total_pages"`
	}
	if e := t.get(ctx, token, "/search/multi?include_adult=false&language=en-US&query="+url.QueryEscape(q.Title), &result); e != nil {
		return nil, e
	}
	c := chooseTMDB(q, result.Results, result.Pages)
	if c == nil {
		return nil, nil
	}
	var d struct {
		Title    string `json:"title"`
		Name     string `json:"name"`
		Overview string `json:"overview"`
		Poster   string `json:"poster_path"`
		Backdrop string `json:"backdrop_path"`
		Genres   []struct {
			Name string `json:"name"`
		} `json:"genres"`
		Credits struct {
			Cast []struct {
				Name string `json:"name"`
			} `json:"cast"`
		} `json:"credits"`
	}
	if e := t.get(ctx, token, fmt.Sprintf("/%s/%d?language=en-US&append_to_response=credits", c.Kind, c.ID), &d); e != nil {
		return nil, e
	}
	name, date := d.Title, c.Release
	if c.Kind == "tv" {
		name, date = d.Name, c.FirstAir
	}
	year := ""
	if len(date) >= 4 {
		year = date[:4]
	}
	m := &Metadata{ID: c.ID, Kind: c.Kind, Title: name, Year: year, Overview: d.Overview, Poster: tmdbImage(d.Poster, "w342"), Backdrop: tmdbImage(d.Backdrop, "w780"), URL: fmt.Sprintf("https://www.themoviedb.org/%s/%d", c.Kind, c.ID), Genres: []string{}, Cast: []string{}}
	if len(m.Overview) > 6000 {
		m.Overview = m.Overview[:6000]
	}
	for _, g := range d.Genres {
		if len(m.Genres) < 8 {
			m.Genres = append(m.Genres, g.Name)
		}
	}
	for _, c := range d.Credits.Cast {
		if len(m.Cast) < 5 {
			m.Cast = append(m.Cast, c.Name)
		}
	}
	return m, nil
}
func (t *tmdbService) lookup(q MetadataQuery) metadataEntry {
	q = cleanMetadataQuery(q)
	key := normalizeMetadata(q.Title) + "|" + q.Year
	t.mu.Lock()
	defer t.mu.Unlock()
	settings := t.store.snapshot().TMDB
	if !settings.Enabled || settings.Token == "" || t.closed {
		return metadataEntry{State: "disabled"}
	}
	if !metadataAllowed(q) {
		return metadataEntry{State: "unmatched"}
	}
	if entry, ok := t.cache[key]; ok && time.Now().Before(entry.Expires) {
		return entry
	}
	if t.pending[key] {
		return metadataEntry{State: "pending"}
	}
	if time.Now().Before(t.retry) || len(t.pending) >= 24 {
		return metadataEntry{State: "unavailable"}
	}
	t.pending[key] = true
	generation := t.generation
	t.wg.Add(1)
	go func() {
		defer t.wg.Done()
		select {
		case t.gate <- struct{}{}:
		case <-t.ctx.Done():
			return
		}
		defer func() { <-t.gate }()
		t.mu.Lock()
		allowed := generation == t.generation && time.Now().After(t.retry)
		t.mu.Unlock()
		if !allowed {
			t.mu.Lock()
			if generation == t.generation {
				delete(t.pending, key)
			}
			t.mu.Unlock()
			return
		}
		m, e := t.fetch(t.ctx, settings.Token, q)
		t.mu.Lock()
		if generation == t.generation {
			delete(t.pending, key)
			if e != nil {
				t.status = e.Error()
				t.retry = time.Now().Add(time.Minute)
			} else {
				entry := metadataEntry{State: "matched", Data: m, Expires: time.Now().Add(7 * 24 * time.Hour)}
				if m == nil {
					entry.State = "unmatched"
					entry.Expires = time.Now().Add(6 * time.Hour)
				}
				t.cache[key] = entry
				t.status = "Connected"
				t.persist()
			}
		}
		t.mu.Unlock()
		select {
		case <-time.After(500 * time.Millisecond):
		case <-t.ctx.Done():
		}
	}()
	return metadataEntry{State: "pending"}
}
func (a *App) tmdbRoutes(m *http.ServeMux) {
	m.HandleFunc("GET /api/v1/metadata/settings", a.auth(false, func(w http.ResponseWriter, r *http.Request) {
		s := a.store.snapshot().TMDB
		a.tmdb.mu.Lock()
		status := a.tmdb.status
		a.tmdb.mu.Unlock()
		send(w, 200, map[string]any{"enabled": s.Enabled, "configured": s.Token != "", "status": status})
	}))
	m.HandleFunc("PUT /api/v1/metadata/settings", a.auth(true, func(w http.ResponseWriter, r *http.Request) {
		var input TMDBSettings
		if !decode(w, r, &input) {
			return
		}
		input.Token = strings.TrimSpace(input.Token)
		if len(input.Token) > 4096 || strings.ContainsAny(input.Token, "\r\n") {
			fail(w, 400, "Invalid TMDB token")
			return
		}
		if input.Token == "" {
			input.Token = a.store.snapshot().TMDB.Token
		}
		if input.Enabled && input.Token == "" {
			fail(w, 400, "Enter your TMDB API Read Access Token")
			return
		}
		if input.Enabled {
			var config map[string]any
			if e := a.tmdb.get(r.Context(), input.Token, "/configuration", &config); e != nil {
				fail(w, 400, e.Error())
				return
			}
		}
		a.tmdb.mu.Lock()
		defer a.tmdb.mu.Unlock()
		if e := a.store.update(func(s *State) { s.TMDB = input }); e != nil {
			fail(w, 500, "Could not save TMDB settings")
			return
		}
		a.tmdb.generation++
		a.tmdb.pending = map[string]bool{}
		a.tmdb.retry = time.Time{}
		a.tmdb.status = "Saved"
		send(w, 200, map[string]bool{"ok": true})
	}))
	m.HandleFunc("DELETE /api/v1/metadata/settings", a.auth(true, func(w http.ResponseWriter, r *http.Request) {
		a.tmdb.mu.Lock()
		defer a.tmdb.mu.Unlock()
		if e := a.store.update(func(s *State) { s.TMDB = TMDBSettings{} }); e != nil {
			fail(w, 500, "Could not disconnect TMDB")
			return
		}
		a.tmdb.generation++
		a.tmdb.pending = map[string]bool{}
		a.tmdb.cache = map[string]metadataEntry{}
		a.tmdb.persist()
		a.tmdb.status = "Disconnected"
		send(w, 200, map[string]bool{"ok": true})
	}))
	m.HandleFunc("POST /api/v1/metadata/lookup", a.auth(false, func(w http.ResponseWriter, r *http.Request) {
		var q MetadataQuery
		if !decode(w, r, &q) {
			return
		}
		if len(q.Title) > 240 || len(q.Year) > 4 {
			fail(w, 400, "Invalid programme metadata")
			return
		}
		send(w, 200, a.tmdb.lookup(q))
	}))
}
