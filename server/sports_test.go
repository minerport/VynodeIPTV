package main

import (
	"io"
	"net/http"
	"strings"
	"testing"
	"time"
)

type sportsTransport func(*http.Request) (*http.Response, error)

func (f sportsTransport) RoundTrip(r *http.Request) (*http.Response, error) { return f(r) }
func TestSportsCacheAndIsolation(t *testing.T) {
	old := sportsHTTP
	oldEntry := sportsCache["NFL"]
	defer func() { sportsHTTP = old; sportsCache["NFL"] = oldEntry }()
	sportsCache["NFL"] = &sportsEntry{}
	calls := 0
	status := 200
	sportsHTTP = &http.Client{Transport: sportsTransport(func(r *http.Request) (*http.Response, error) {
		calls++
		if r.Header.Get("Authorization") != "" || r.URL.Host != "site.api.espn.com" {
			t.Fatal("credentials or wrong host")
		}
		return &http.Response{StatusCode: status, Body: io.NopCloser(strings.NewReader(`{"children":[{"name":"A","standings":{"entries":[]}}]}`)), Header: http.Header{}}, nil
	})}
	a := testApp(t)
	if w := call(a, "GET", "/api/v1/sports/standings?league=NFL", "", ""); w.Code == 200 {
		t.Fatal("unauthenticated")
	}
	if w := call(a, "GET", "/api/v1/sports/standings?league=https://evil.test", a.admin, ""); w.Code != 400 {
		t.Fatal("arbitrary destination")
	}
	for i := 0; i < 2; i++ {
		if w := call(a, "GET", "/api/v1/sports/standings?league=NFL", a.admin, ""); w.Code != 200 {
			t.Fatal(w.Body.String())
		}
	}
	if calls != 1 {
		t.Fatal("cache missed", calls)
	}
	entry := sportsCache["NFL"]
	entry.updated = time.Now().Add(-time.Hour)
	entry.attempted = time.Time{}
	status = 503
	w := call(a, "GET", "/api/v1/sports/standings?league=NFL", a.admin, "")
	if w.Code != 200 || !strings.Contains(w.Body.String(), `"stale":true`) {
		t.Fatal("stale cache not labeled", w.Body.String())
	}
	entry.updated = time.Now().Add(-25 * time.Hour)
	if w := call(a, "GET", "/api/v1/sports/standings?league=NFL", a.admin, ""); w.Code != 503 {
		t.Fatal("expired stats exposed")
	}
}
