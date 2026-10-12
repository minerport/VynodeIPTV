package main

import (
	"fmt"
	"strings"
	"testing"
	"time"
)

func TestEPGPreservesPastCurrentFutureAndNamesUntitled(t *testing.T) {
	now := time.Date(2026, 10, 12, 12, 0, 0, 0, time.UTC)
	var body strings.Builder
	body.WriteString("<tv>")
	for _, offset := range []time.Duration{-48 * time.Hour, -30 * time.Minute, 48 * time.Hour} {
		from := now.Add(offset)
		fmt.Fprintf(&body, `<programme channel="epg" start="%s" stop="%s"><title> </title></programme>`, from.Format("20060102150405 -0700"), from.Add(time.Hour).Format("20060102150405 -0700"))
	}
	body.WriteString("</tv>")
	entries, err := parseEPG(strings.NewReader(body.String()), []Channel{{ID: "p:1", EPGID: "epg"}}, now)
	if err != nil || len(entries) != 3 {
		t.Fatalf("history lost: %d, %v", len(entries), err)
	}
	for _, p := range entries {
		if p.Title != "Untitled programme" {
			t.Fatal("blank title remained")
		}
	}
}
