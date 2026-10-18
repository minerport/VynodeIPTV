package main

import (
	"encoding/json"
	"fmt"
	"io"
	"net/http"
	"net/url"
	"regexp"
	"strconv"
	"strings"
	"sync"
	"time"
)

type sportsSlot struct {
	entry   *sportsEntry
	touched time.Time
}

var sportsDataCache = struct {
	sync.Mutex
	slots map[string]sportsSlot
}{slots: map[string]sportsSlot{}}
var sportsRequests = make(chan struct{}, 4)

var sportsSortPattern = regexp.MustCompile(`^[a-zA-Z][a-zA-Z0-9]{0,39}\.[a-zA-Z][a-zA-Z0-9]{0,49}:(asc|desc)$`)

func sportsPlayerQuery(address string, q url.Values) (string, error) {
	u, err := url.Parse(address)
	if err != nil {
		return "", err
	}
	p := u.Query()
	if s := q.Get("sort"); s != "" {
		if !sportsSortPattern.MatchString(s) {
			return "", fmt.Errorf("Invalid statistic")
		}
		p.Set("sort", s)
	}
	if s := q.Get("page"); s != "" {
		n, e := strconv.Atoi(s)
		if e != nil || n < 1 || n > 100 {
			return "", fmt.Errorf("Invalid page")
		}
		p.Set("page", s)
	}
	if s := q.Get("seasontype"); s != "" {
		if s != "1" && s != "2" && s != "3" {
			return "", fmt.Errorf("Invalid season phase")
		}
		p.Set("seasontype", s)
	}
	u.RawQuery = p.Encode()
	return u.String(), nil
}

func sportsDataURL(kind, league, team, season string, now time.Time) (string, time.Duration, error) {
	path, ok := sportsPaths[league]
	if !ok {
		return "", 0, fmt.Errorf("Unsupported league")
	}
	params := url.Values{}
	if season != "" {
		year, err := strconv.Atoi(season)
		if err != nil || year < 2020 || year > now.Year()+1 {
			return "", 0, fmt.Errorf("Invalid season")
		}
		params.Set("season", season)
	}
	base := "https://site.api.espn.com/apis/site/v2/sports/" + path
	ttl := 15 * time.Minute
	switch kind {
	case "standings":
		base = "https://site.api.espn.com/apis/v2/sports/" + path + "/standings"
	case "scoreboard":
		base += "/scoreboard"
		params = url.Values{"limit": {"1000"}}
		ttl = 15 * time.Second
	case "leaders":
		base = "https://site.api.espn.com/apis/site/v3/sports/" + path + "/leaders"
	case "players":
		if team == "" {
			base = "https://site.web.api.espn.com/apis/common/v3/sports/" + path + "/statistics/byathlete"
			params.Set("limit", "50")
		} else {
			valid := false
			for _, t := range interestCatalog.Teams {
				if t.ID == team && t.League == league {
					valid = true
					break
				}
			}
			if !valid {
				return "", 0, fmt.Errorf("Unknown team")
			}
			base += "/teams/" + strings.Split(team, ":")[1] + "/athletes/statistics"
		}
	case "athlete":
		if len(team) == 0 || len(team) > 10 || strings.IndexFunc(team, func(r rune) bool { return r < '0' || r > '9' }) >= 0 {
			return "", 0, fmt.Errorf("Invalid athlete")
		}
		base = "https://site.web.api.espn.com/apis/common/v3/sports/" + path + "/athletes/" + team + "/stats"
		params = url.Values{}
	case "statistics", "schedule":
		valid := false
		for _, t := range interestCatalog.Teams {
			if t.ID == team && t.League == league {
				valid = true
				break
			}
		}
		if !valid {
			return "", 0, fmt.Errorf("Unknown team")
		}
		base += "/teams/" + strings.Split(team, ":")[1] + "/" + kind
		if kind == "schedule" {
			ttl = 5 * time.Minute
		}
	default:
		return "", 0, fmt.Errorf("Unknown sports data type")
	}
	return base + "?" + params.Encode(), ttl, nil
}
func sportsDataEntry(key string) *sportsEntry {
	sportsDataCache.Lock()
	defer sportsDataCache.Unlock()
	if slot, ok := sportsDataCache.slots[key]; ok {
		slot.touched = time.Now()
		sportsDataCache.slots[key] = slot
		return slot.entry
	}
	if len(sportsDataCache.slots) >= 64 {
		var oldest string
		var stamp time.Time
		for k, s := range sportsDataCache.slots {
			if oldest == "" || s.touched.Before(stamp) {
				oldest = k
				stamp = s.touched
			}
		}
		delete(sportsDataCache.slots, oldest)
	}
	entry := &sportsEntry{}
	sportsDataCache.slots[key] = sportsSlot{entry, time.Now()}
	return entry
}
func readSportsData(address, kind string) (json.RawMessage, error) {
	response, err := sportsHTTP.Get(address)
	if err != nil {
		return nil, err
	}
	defer response.Body.Close()
	if response.StatusCode != 200 {
		return nil, fmt.Errorf("Source unavailable")
	}
	limit := 2 * 1024 * 1024
	if kind == "leaders" || kind == "players" {
		limit = 8 * 1024 * 1024
	}
	b, err := io.ReadAll(io.LimitReader(response.Body, int64(limit+1)))
	if err != nil || len(b) > limit {
		return nil, fmt.Errorf("Response too large")
	}
	var root map[string]json.RawMessage
	if json.Unmarshal(b, &root) != nil {
		return nil, fmt.Errorf("Invalid source data")
	}
	key := map[string]string{"standings": "children", "scoreboard": "events", "schedule": "events", "statistics": "results", "leaders": "leaders", "athlete": "categories"}[kind]
	if kind == "players" {
		key = "athletes"
		if _, ok := root["results"]; ok {
			key = "results"
		}
	}
	if _, ok := root[key]; !ok {
		return nil, fmt.Errorf("Data unavailable")
	}
	if kind == "leaders" {
		var trimmed struct {
			CurrentSeason   json.RawMessage `json:"currentSeason,omitempty"`
			RequestedSeason json.RawMessage `json:"requestedSeason,omitempty"`
			Leaders         struct {
				Categories []struct {
					Name        string `json:"name"`
					DisplayName string `json:"displayName"`
					Leaders     []struct {
						Athlete struct {
							ID          string `json:"id"`
							DisplayName string `json:"displayName"`
						} `json:"athlete"`
						Team struct {
							ID          string `json:"id"`
							DisplayName string `json:"displayName"`
						} `json:"team"`
						Value        json.RawMessage `json:"value"`
						DisplayValue string          `json:"displayValue"`
					} `json:"leaders"`
				} `json:"categories"`
			} `json:"leaders"`
		}
		if err := json.Unmarshal(b, &trimmed); err != nil {
			return nil, err
		}
		return json.Marshal(trimmed)
	}
	return b, nil
}
func (a *App) sportsExplorerRoutes(m *http.ServeMux) {
	m.HandleFunc("GET /api/v1/sports/data", a.auth(false, func(w http.ResponseWriter, r *http.Request) {
		q := r.URL.Query()
		kind := q.Get("kind")
		address, ttl, err := sportsDataURL(kind, q.Get("league"), q.Get("team"), q.Get("season"), time.Now())
		if err == nil && kind == "players" {
			address, err = sportsPlayerQuery(address, q)
		}
		if err != nil {
			fail(w, 400, err.Error())
			return
		}
		entry := sportsDataEntry(address)
		entry.mu.Lock()
		defer entry.mu.Unlock()
		now := time.Now()
		retryInterval := 30 * time.Second
		if kind == "scoreboard" {
			retryInterval = 15 * time.Second
		}
		if now.Sub(entry.updated) > ttl && now.Sub(entry.attempted) > retryInterval {
			select {
			case sportsRequests <- struct{}{}:
			case <-r.Context().Done():
				return
			}
			entry.attempted = now
			b, err := readSportsData(address, kind)
			<-sportsRequests
			if err == nil {
				entry.data = b
				entry.updated = time.Now()
			}
		}
		maxAge := 24 * time.Hour
		if kind == "scoreboard" || kind == "schedule" {
			maxAge = 5 * time.Minute
		}
		if len(entry.data) == 0 || time.Since(entry.updated) > maxAge {
			fail(w, 503, "Sports data temporarily unavailable")
			return
		}
		send(w, 200, map[string]any{"data": entry.data, "updatedAt": entry.updated, "stale": time.Since(entry.updated) > ttl, "source": "ESPN"})
	}))
}
