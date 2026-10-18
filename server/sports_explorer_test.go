package main

import (
	"io"
	"net/http"
	"strings"
	"testing"
	"time"
)

func TestSportsExplorerDestinations(t *testing.T) {
	now := time.Date(2026, 10, 18, 1, 0, 0, 0, time.UTC)
	for _, v := range []struct{ k, l, team, year string }{{"scoreboard", "NFL", "", ""}, {"standings", "NBA", "", "2027"}, {"statistics", "NFL", "nfl:22", "2026"}, {"schedule", "NFL", "nfl:22", ""}, {"leaders", "NHL", "", ""}, {"athlete", "NFL", "12483", ""}} {
		u, _, err := sportsDataURL(v.k, v.l, v.team, v.year, now)
		if err != nil || !strings.HasPrefix(u, "https://site.") {
			t.Fatal(v, u, err)
		}
	}
	for _, v := range []struct{ k, l, team, year string }{{"bad", "NFL", "", ""}, {"schedule", "NFL", "../secret", ""}, {"statistics", "NFL", "nba:1", ""}, {"standings", "NFL", "", "9000"}, {"athlete", "NFL", "12/../../x", ""}, {"scoreboard", "https://evil.test", "", ""}} {
		if _, _, err := sportsDataURL(v.k, v.l, v.team, v.year, now); err == nil {
			t.Fatal("accepted", v)
		}
	}
	u, ttl, _ := sportsDataURL("scoreboard", "NFL", "", "", now)
	if !strings.Contains(u, "limit=1000") || ttl != time.Minute {
		t.Fatal(u, ttl)
	}
}
func TestSportsExplorerCacheBound(t *testing.T) {
	sportsDataCache.Lock()
	sportsDataCache.slots = map[string]sportsSlot{}
	sportsDataCache.Unlock()
	for i := 0; i < 100; i++ {
		sportsDataEntry(time.Unix(int64(i), 0).String())
	}
	sportsDataCache.Lock()
	defer sportsDataCache.Unlock()
	if len(sportsDataCache.slots) > 64 {
		t.Fatal("unbounded cache")
	}
}

func TestSportsLeaderPayloadTrimming(t *testing.T) {
	old := sportsHTTP
	defer func() { sportsHTTP = old }()
	body := `{"leaders":{"categories":[{"name":"passing","displayName":"Passing","leaders":[{"athlete":{"id":"12483","displayName":"Player","unused":"` + strings.Repeat("x", 2300000) + `"},"team":{"id":"1","displayName":"Team"},"value":1200,"displayValue":"1,200"}]}]}}`
	sportsHTTP = &http.Client{Transport: sportsTransport(func(r *http.Request) (*http.Response, error) {
		return &http.Response{StatusCode: 200, Body: io.NopCloser(strings.NewReader(body)), Header: http.Header{}}, nil
	})}
	data, err := readSportsData("https://site.api.espn.com/example", "leaders")
	if err != nil {
		t.Fatal(err)
	}
	if len(data) > 1000 || strings.Contains(string(data), "unused") || !strings.Contains(string(data), "1,200") {
		t.Fatal("leader cache should retain only used fields")
	}
}

func TestSportsExplorerAuthenticationAndFreshness(t *testing.T) {
	old := sportsHTTP
	defer func() { sportsHTTP = old }()
	address, _, _ := sportsDataURL("scoreboard", "NFL", "", "", time.Now())
	sportsDataCache.Lock()
	delete(sportsDataCache.slots, address)
	sportsDataCache.Unlock()
	status := 200
	calls := 0
	sportsHTTP = &http.Client{Transport: sportsTransport(func(r *http.Request) (*http.Response, error) {
		calls++
		if r.Header.Get("Authorization") != "" || r.URL.Host != "site.api.espn.com" {
			t.Fatal("source credential isolation")
		}
		return &http.Response{StatusCode: status, Body: io.NopCloser(strings.NewReader(`{"events":[]}`)), Header: http.Header{}}, nil
	})}
	a := testApp(t)
	path := "/api/v1/sports/data?kind=scoreboard&league=NFL"
	if w := call(a, "GET", path, "", ""); w.Code == 200 {
		t.Fatal("unauthenticated")
	}
	for i := 0; i < 2; i++ {
		if w := call(a, "GET", path, a.admin, ""); w.Code != 200 {
			t.Fatal(w.Body.String())
		}
	}
	if calls != 1 {
		t.Fatal("cache miss", calls)
	}
	e := sportsDataEntry(address)
	e.updated = time.Now().Add(-2 * time.Minute)
	e.attempted = time.Time{}
	status = 503
	if w := call(a, "GET", path, a.admin, ""); w.Code != 200 || !strings.Contains(w.Body.String(), `"stale":true`) {
		t.Fatal("missing stale label", w.Body.String())
	}
	e.updated = time.Now().Add(-6 * time.Minute)
	if w := call(a, "GET", path, a.admin, ""); w.Code != 503 {
		t.Fatal("expired scoreboard")
	}
}
