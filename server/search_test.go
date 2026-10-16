package main

import (
	"strings"
	"testing"
	"time"
)

func TestSearchScopeAndSchedule(t *testing.T) {
	now := time.Now()
	c := Channel{ID: "a", Name: "Cinema"}
	p := Programme{ChannelID: "a", Title: "Ocean adventure", Start: now, End: now.Add(time.Hour)}
	old := p
	old.End = now.Add(-time.Minute)
	orphan := p
	orphan.ChannelID = "gone"
	jobs := []Recording{{ID: "mine", Owner: "viewer", Title: "Ocean adventure"}, {ID: "private", Owner: "other", Title: "Ocean adventure"}}
	r, _ := searchCatalog("OCEAN adventure", "viewer", []Channel{c}, []Programme{old, p, orphan}, jobs, now)
	if len(r) != 2 || r[0].Kind != "programme" || r[1].Recording.ID != "mine" {
		t.Fatalf("wrong scope: %+v", r)
	}
}
func TestSearchLimitsAndAuthentication(t *testing.T) {
	a := testApp(t)
	if w := call(a, "GET", "/api/v1/search?q=live", "", ""); w.Code != 401 {
		t.Fatal(w.Code)
	}
	if w := call(a, "GET", "/api/v1/search?q="+strings.Repeat("x", 121), a.admin, ""); w.Code != 400 {
		t.Fatal(w.Code)
	}
	channels := make([]Channel, 105)
	for i := range channels {
		channels[i] = Channel{Name: "Example"}
	}
	r, truncated := searchCatalog("example", "admin", channels, nil, nil, time.Now())
	if len(r) != 100 || !truncated {
		t.Fatal(len(r), truncated)
	}
	r, _ = searchCatalog("   ", "admin", channels, nil, nil, time.Now())
	if len(r) != 0 {
		t.Fatal("empty query exposed catalog")
	}
}
