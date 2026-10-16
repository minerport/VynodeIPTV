package main

import (
	"net/http"
	"sort"
	"strings"
	"time"
)

type SearchResult struct {
	Kind      string     `json:"kind"`
	Channel   *Channel   `json:"channel,omitempty"`
	Programme *Programme `json:"programme,omitempty"`
	Recording *Recording `json:"recording,omitempty"`
}

// Search uses the imported catalogue only: typing never opens provider streams
// or makes one remote EPG request per channel.
func searchCatalog(query, owner string, channels []Channel, guide []Programme, recordings []Recording, now time.Time) ([]SearchResult, bool) {
	terms := strings.Fields(strings.ToLower(query))
	results := []SearchResult{}
	if len(terms) == 0 {
		return results, false
	}
	matches := func(value string) bool {
		value = strings.ToLower(value)
		for _, term := range terms {
			if !strings.Contains(value, term) {
				return false
			}
		}
		return true
	}
	byID := map[string]Channel{}
	for _, c := range channels {
		byID[c.ID] = c
	}
	sort.SliceStable(channels, func(i, j int) bool { return strings.ToLower(channels[i].Name) < strings.ToLower(channels[j].Name) })
	for _, c := range channels {
		if matches(c.Name + " " + c.CategoryName) {
			v := c
			results = append(results, SearchResult{Kind: "channel", Channel: &v})
		}
	}
	sort.SliceStable(guide, func(i, j int) bool { return guide[i].Start.Before(guide[j].Start) })
	for _, p := range guide {
		c, ok := byID[p.ChannelID]
		if ok && p.End.After(now) && p.Start.Before(now.Add(7*24*time.Hour)) && matches(p.Title+" "+p.EpisodeTitle) {
			v := p
			results = append(results, SearchResult{Kind: "programme", Channel: &c, Programme: &v})
		}
	}
	for _, r := range recordings {
		if r.Owner == owner && matches(r.Title+" "+r.Channel.Name) {
			v := r
			results = append(results, SearchResult{Kind: "recording", Recording: &v})
		}
	}
	truncated := len(results) > 100
	if truncated {
		results = results[:100]
	}
	return results, truncated
}

func (a *App) searchRoutes(m *http.ServeMux) {
	m.HandleFunc("GET /api/v1/search", a.auth(false, func(w http.ResponseWriter, r *http.Request) {
		q := strings.TrimSpace(r.URL.Query().Get("q"))
		if len(q) > 120 {
			fail(w, 400, "Search must be at most 120 bytes")
			return
		}
		owner, _ := a.identity(r)
		a.mu.Lock()
		channels := append([]Channel{}, a.channels...)
		guide := append([]Programme{}, a.guide...)
		a.mu.Unlock()
		results, truncated := searchCatalog(q, owner, channels, guide, a.store.snapshot().Recordings, time.Now())
		send(w, 200, map[string]any{"results": results, "truncated": truncated})
	}))
}
