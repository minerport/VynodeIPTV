package main

import (
	"encoding/json"
	"strings"
	"testing"
	"time"
)

func TestInterestMatching(t *testing.T) {
	now := time.Now()
	c := Channel{ID: "c", ProviderID: "p", Category: "g", Name: "ABC Austin TX"}
	base := Programme{ChannelID: "c", Start: now.Add(-time.Minute), End: now.Add(time.Hour)}
	p := InterestSettings{Teams: []string{"nfl:22"}, City: "Austin", State: "TX", Genres: []string{"Comedy"}}
	cases := []struct {
		title, genres string
		sports, genre bool
	}{{"Arizona Cardinals vs Seattle Seahawks", "Sports", true, false}, {"NFL Football: Cardinals vs Seahawks", "Sports", true, false}, {"MLB Baseball: Cardinals vs Cubs", "Sports", false, false}, {"Cardinals of Rome", "Documentary", false, false}, {"Comedy special", "Comedy", false, true}, {"Comedy in a dramatic life", "Drama", false, false}}
	for _, tt := range cases {
		v := base
		v.Title = tt.title
		v.Genres = tt.genres
		hits, _ := interestMatches(p, []Channel{c}, []Programme{v}, nil, now)
		counts := map[string]int{}
		for _, h := range hits {
			counts[h.Section]++
		}
		if (counts["sports"] > 0) != tt.sports || (counts["genres"] > 0) != tt.genre || counts["local"] != 1 {
			t.Fatal(tt, hits)
		}
	}
	if hits, _ := interestMatches(p, []Channel{c}, []Programme{base}, []string{"provider:p:g"}, now); len(hits) != 0 {
		t.Fatal("hidden group leaked")
	}
	expired := base
	expired.End = now
	if hits, _ := interestMatches(p, []Channel{c}, []Programme{expired}, nil, now); len(hits) != 0 {
		t.Fatal("expired listing")
	}
	if !interestState(interestWords("ABC Austin TX"), "Texas") {
		t.Fatal("state abbreviation not resolved")
	}
	wrong := p
	wrong.State = "CA"
	if hits, _ := interestMatches(wrong, []Channel{c}, []Programme{base}, nil, now); len(hits) != 0 {
		t.Fatal("wrong state")
	}
	wrong.LocalChannels = []string{"c"}
	if hits, _ := interestMatches(wrong, []Channel{c}, []Programme{base}, nil, now); len(hits) != 1 {
		t.Fatal("manual station missing")
	}
}
func TestInterestsPersistAndIsolate(t *testing.T) {
	a := testApp(t)
	a.store.update(func(s *State) { s.Devices = append(s.Devices, Device{ID: "tv", TokenHash: tokenHash("tv-token")}) })
	body := `{"teams":["nfl:22"],"city":"Austin","state":"TX","genres":["Comedy"],"localChannels":["demo:1"]}`
	for _, path := range []string{"/api/v1/interests", "/api/v1/for-you"} {
		if w := call(a, "GET", path, "", ""); w.Code == 200 {
			t.Fatal("unauthenticated read")
		}
	}
	if w := call(a, "PUT", "/api/v1/interests", a.admin, body); w.Code != 200 {
		t.Fatal(w.Body.String())
	}
	if w := call(a, "GET", "/api/v1/interests", "tv-token", ""); strings.Contains(w.Body.String(), "Austin") {
		t.Fatal("owner leak")
	}
	store, err := openStore(a.store.dir)
	if err != nil {
		t.Fatal(err)
	}
	if store.snapshot().Interests["admin"].City != "Austin" {
		t.Fatal("not persisted")
	}
	snap := store.snapshot()
	snap.Interests["admin"].Teams[0] = "changed"
	if store.snapshot().Interests["admin"].Teams[0] != "nfl:22" {
		t.Fatal("snapshot alias")
	}
	for _, bad := range []string{`{"teams":["invalid"]}`, `{"genres":["invalid"]}`, `{"city":"` + strings.Repeat("x", 81) + `"}`} {
		if w := call(a, "PUT", "/api/v1/interests", a.admin, bad); w.Code != 400 {
			t.Fatal("bad interests accepted")
		}
	}
	if w := call(a, "PUT", "/api/v1/interests", "tv-token", body); w.Code != 200 {
		t.Fatal("companion cannot save")
	}
	var p InterestSettings
	w := call(a, "GET", "/api/v1/interests", a.admin, "")
	json.Unmarshal(w.Body.Bytes(), &p)
	if p.City != "Austin" {
		t.Fatal("failed update erased settings")
	}
}
func TestInterestSectionsBounded(t *testing.T) {
	now := time.Now()
	ps := []Programme{}
	for i := 0; i < 100; i++ {
		ps = append(ps, Programme{ChannelID: "c", Title: "Arizona Cardinals", Start: now.Add(time.Duration(i) * time.Hour), End: now.Add(time.Duration(i+1) * time.Hour)})
	}
	hits, truncated := interestMatches(InterestSettings{Teams: []string{"nfl:22"}}, []Channel{{ID: "c"}}, ps, nil, now)
	if !truncated || len(hits) != 41 {
		t.Fatal(len(hits), truncated)
	}
}
