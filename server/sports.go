package main

import (
	"encoding/json"
	"fmt"
	"io"
	"net/http"
	"sync"
	"time"
)

// Public sports data is isolated from provider transport and credentials.
var sportsPaths = map[string]string{"NFL": "football/nfl", "NBA": "basketball/nba", "MLB": "baseball/mlb", "NHL": "hockey/nhl", "MLS": "soccer/usa.1"}

type sportsEntry struct {
	mu        sync.Mutex
	data      json.RawMessage
	updated   time.Time
	attempted time.Time
}

var sportsCache = func() map[string]*sportsEntry {
	m := map[string]*sportsEntry{}
	for k := range sportsPaths {
		m[k] = &sportsEntry{}
	}
	return m
}()
var sportsHTTP = &http.Client{Timeout: 8 * time.Second, CheckRedirect: func(*http.Request, []*http.Request) error { return http.ErrUseLastResponse }}

func fetchSports(path string) (json.RawMessage, error) {
	r, err := sportsHTTP.Get("https://site.api.espn.com/apis/v2/sports/" + path + "/standings")
	if err != nil {
		return nil, err
	}
	defer r.Body.Close()
	if r.StatusCode != 200 {
		return nil, fmt.Errorf("sports source unavailable")
	}
	b, err := io.ReadAll(io.LimitReader(r.Body, 2*1024*1024+1))
	if err != nil || len(b) > 2*1024*1024 || !json.Valid(b) {
		return nil, fmt.Errorf("invalid sports data")
	}
	var root struct {
		Children []json.RawMessage `json:"children"`
	}
	if json.Unmarshal(b, &root) != nil || len(root.Children) == 0 {
		return nil, fmt.Errorf("standings unavailable")
	}
	return b, nil
}
func (a *App) sportsRoutes(m *http.ServeMux) {
	m.HandleFunc("GET /api/v1/sports/standings", a.auth(false, func(w http.ResponseWriter, r *http.Request) {
		league := r.URL.Query().Get("league")
		entry, ok := sportsCache[league]
		if !ok {
			fail(w, 400, "Unsupported league")
			return
		}
		entry.mu.Lock()
		defer entry.mu.Unlock()
		now := time.Now()
		if now.Sub(entry.updated) > 15*time.Minute && now.Sub(entry.attempted) > time.Minute {
			entry.attempted = now
			if b, err := fetchSports(sportsPaths[league]); err == nil {
				entry.data = b
				entry.updated = now
			}
		}
		if len(entry.data) == 0 || now.Sub(entry.updated) > 24*time.Hour {
			fail(w, 503, "Sports statistics temporarily unavailable")
			return
		}
		send(w, 200, map[string]any{"data": entry.data, "updatedAt": entry.updated, "stale": now.Sub(entry.updated) > 15*time.Minute, "source": "ESPN"})
	}))
}
