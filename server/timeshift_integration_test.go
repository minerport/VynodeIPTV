package main

import (
	"context"
	"os"
	"os/exec"
	"path/filepath"
	"strings"
	"testing"
	"time"
)

func TestSyntheticTimeshiftRetainsPlayableHistory(t *testing.T) {
	ffmpeg, err := exec.LookPath("ffmpeg")
	if err != nil {
		t.Skip("ffmpeg unavailable")
	}
	a := testApp(t)
	a.ffmpeg = ffmpeg
	if err := a.store.update(func(s *State) { s.Timeshift = TimeshiftSettings{Minutes: 5, MaxMB: 256} }); err != nil {
		t.Fatal(err)
	}
	session, err := a.startSession("admin", Channel{ID: "demo:1", ProviderID: "demo"}, Provider{ID: "demo", MaxConnections: 1})
	if err != nil {
		t.Fatal(err)
	}
	defer func() { session.cancel(); <-session.done }()
	playlist := filepath.Join(a.scratch, session.ID, "1", "live.m3u8")
	deadline := time.Now().Add(40 * time.Second)
	for time.Now().Before(deadline) {
		data, _ := os.ReadFile(playlist)
		if strings.Count(string(data), "#EXTINF") >= 10 {
			if !strings.Contains(string(data), "segment-000000.ts") {
				t.Fatal("start of rolling window was discarded at old eight-segment limit")
			}
			ctx, cancel := context.WithTimeout(context.Background(), 10*time.Second)
			defer cancel()
			output, err := exec.CommandContext(ctx, ffmpeg, "-v", "error", "-live_start_index", "0", "-i", playlist, "-t", "1", "-f", "null", "-").CombinedOutput()
			if err != nil {
				t.Fatalf("buffered media cannot decode: %v %s", err, output)
			}
			return
		}
		time.Sleep(time.Second)
	}
	t.Fatal("timeshift did not retain ten playable segments")
}
