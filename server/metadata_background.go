package main

import (
	"context"
	"fmt"
	"regexp"
	"sort"
	"strconv"
	"strings"
	"time"
)

func metadataKey(q MetadataQuery) string {
	q = cleanMetadataQuery(q)
	return fmt.Sprintf("v2|%s|%s|%d|%d|%s", normalizeMetadata(q.Title), q.Year, q.Season, q.Episode, normalizeMetadata(q.EpisodeTitle))
}
func programmeQuery(p Programme) MetadataQuery {
	return MetadataQuery{Title: p.Title, Year: p.Year, Season: p.Season, Episode: p.Episode, EpisodeTitle: p.EpisodeTitle}
}

var onscreenEpisode = regexp.MustCompile(`(?i)^S([0-9]{1,3})\s*E([0-9]{1,4})$`)
var xmltvEpisode = regexp.MustCompile(`^\s*([0-9]+)(?:/[0-9]+)?\s*\.\s*([0-9]+)(?:/[0-9]+)?\s*\.`)

func parseEpisodeNumber(system, value string) (int, int, bool) {
	var m []string
	offset := 0
	if system == "xmltv_ns" {
		m = xmltvEpisode.FindStringSubmatch(value)
		offset = 1
	} else if system == "onscreen" {
		m = onscreenEpisode.FindStringSubmatch(strings.TrimSpace(value))
	}
	if len(m) != 3 {
		return 0, 0, false
	}
	s, e := strconv.Atoi(m[1])
	n, e2 := strconv.Atoi(m[2])
	s += offset
	n += offset
	return s, n, e == nil && e2 == nil && s >= 0 && s <= 1000 && n > 0 && n <= 10000
}

// Bound work by horizon and unique programme count, ordered current/soonest first.
func upcomingMetadata(programmes []Programme, now time.Time) []MetadataQuery {
	rows := make([]Programme, 0)
	for _, p := range programmes {
		if p.End.After(now) && p.Start.Before(now.Add(24*time.Hour)) {
			rows = append(rows, p)
		}
	}
	sort.SliceStable(rows, func(i, j int) bool { return rows[i].Start.Before(rows[j].Start) })
	seen := map[string]bool{}
	out := []MetadataQuery{}
	for _, p := range rows {
		q := cleanMetadataQuery(programmeQuery(p))
		key := metadataKey(q)
		if !metadataAllowed(q) || seen[key] {
			continue
		}
		seen[key] = true
		out = append(out, q)
		if len(out) == 300 {
			break
		}
	}
	return out
}
func (t *tmdbService) warmOne(queries []MetadataQuery) {
	t.mu.Lock()
	settings := t.store.snapshot().TMDB
	if t.closed || !settings.Enabled || (settings.Background != nil && !*settings.Background) || len(t.pending) > 0 || time.Now().Before(t.retry) {
		t.mu.Unlock()
		return
	}
	var next *MetadataQuery
	for _, q := range queries {
		if c, ok := t.cache[metadataKey(q)]; !ok || time.Now().After(c.Expires) {
			copy := q
			next = &copy
			break
		}
	}
	t.mu.Unlock()
	if next != nil {
		t.lookup(*next)
	}
}
func (a *App) runMetadata(ctx context.Context) {
	tick := time.NewTicker(2 * time.Second)
	defer tick.Stop()
	var queries []MetadataQuery
	var refresh time.Time
	for {
		select {
		case <-ctx.Done():
			return
		case <-tick.C:
			settings := a.store.snapshot().TMDB
			if !settings.Enabled || (settings.Background != nil && !*settings.Background) {
				continue
			}
			if time.Now().After(refresh) {
				a.mu.Lock()
				snapshot := append([]Programme(nil), a.guide...)
				a.mu.Unlock()
				queries = upcomingMetadata(snapshot, time.Now())
				refresh = time.Now().Add(time.Minute)
			}
			a.tmdb.warmOne(queries)
		}
	}
}
