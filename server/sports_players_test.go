package main

import (
	"net/url"
	"strings"
	"testing"
	"time"
)

func TestSportsPlayerDestinationsAndQuery(t *testing.T) {
	for _, team := range []string{"", "nfl:2"} {
		u, _, e := sportsDataURL("players", "NFL", team, "2026", time.Now())
		if e != nil {
			t.Fatal(e)
		}
		u, e = sportsPlayerQuery(u, url.Values{"sort": {"passing.passingYards:desc"}, "page": {"2"}, "seasontype": {"2"}})
		if e != nil || !strings.Contains(u, "page=2") {
			t.Fatal(u, e)
		}
		if team != "" && !strings.Contains(u, "/teams/2/athletes/statistics") {
			t.Fatal(u)
		}
	}
	for _, q := range []url.Values{{"sort": {"//evil/x"}}, {"page": {"0"}}, {"page": {"101"}}, {"seasontype": {"9"}}} {
		if _, e := sportsPlayerQuery("https://site.web.api.espn.com/test", q); e == nil {
			t.Fatal(q)
		}
	}
	if _, _, e := sportsDataURL("players", "NFL", "nba:2", "", time.Now()); e == nil {
		t.Fatal("cross league team accepted")
	}
}
