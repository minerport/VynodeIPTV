package main

import (
	"os"
	"path/filepath"
	"testing"
	"time"
)

func TestSegmentHealthRequiresPlayableNewFile(t *testing.T) {
	dir := t.TempDir()
	write := func(name, data string) {
		t.Helper()
		if err := os.WriteFile(filepath.Join(dir, name), []byte(data), 0600); err != nil {
			t.Fatal(err)
		}
	}
	write("live.m3u8", "#EXTM3U\nsegment-000001.ts\n")
	if newestSegment(dir) != "" {
		t.Fatal("missing segment is not healthy")
	}
	write("segment-000001.ts", "")
	if newestSegment(dir) != "" {
		t.Fatal("empty segment is not healthy")
	}
	write("segment-000001.ts", "media")
	if newestSegment(dir) != "segment-000001.ts" {
		t.Fatal("valid segment not observed")
	}
	write("live.m3u8", "#EXTM3U\n# rewritten\nsegment-000001.ts\n../../outside.ts\nsegment-000002.ts\n")
	if newestSegment(dir) != "segment-000001.ts" {
		t.Fatal("playlist rewrite or missing next segment reported progress")
	}
	write("segment-000002.ts", "media")
	if newestSegment(dir) != "segment-000002.ts" {
		t.Fatal("new segment not observed")
	}
}
func TestSessionHealthSnapshotIsBoundedAndIndependent(t *testing.T) {
	s := &Session{}
	for i := 0; i < 50; i++ {
		s.set("playing", "Output")
		s.set("recovering", "Retry")
	}
	s.health.LastSegmentAt = time.Now().Add(-time.Second)
	view := s.view()
	if len(view.Health.Events) != 32 || view.Health.SegmentAgeMS < 1000 {
		t.Fatal("invalid health snapshot")
	}
	view.Health.Events[0].Reason = "modified"
	if s.view().Health.Events[0].Reason == "modified" {
		t.Fatal("snapshot aliases session history")
	}
}
