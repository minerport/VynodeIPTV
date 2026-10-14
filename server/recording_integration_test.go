package main

import (
	"context"
	"os/exec"
	"path/filepath"
	"testing"
	"time"
)

func TestSyntheticRecordingRestartAndDecode(t *testing.T) {
	ffmpeg, err := exec.LookPath("ffmpeg")
	if err != nil {
		t.Skip("ffmpeg unavailable; container smoke covers synthetic DVR")
	}
	a := testApp(t)
	a.ffmpeg = ffmpeg
	j := Recording{ID: randomID(16), Owner: "admin", Channel: Channel{ID: "demo:1", ProviderID: "demo"}, Start: time.Now(), End: time.Now().Add(22 * time.Second), Status: "scheduled"}
	if err = a.store.update(func(s *State) { s.Recordings = []Recording{j} }); err != nil {
		t.Fatal(err)
	}
	ctx, cancel := context.WithCancel(context.Background())
	done := make(chan struct{})
	go func() { a.captureRecording(ctx, j, Provider{ID: "demo", MaxConnections: 8}); close(done) }()
	time.Sleep(7 * time.Second)
	cancel()
	<-done
	saved, _ := a.recording(j.ID)
	if saved.Status != "scheduled" || saved.Gaps < 1 {
		t.Fatalf("restart not persisted: %+v", saved)
	}
	a.captureRecording(context.Background(), saved, Provider{ID: "demo", MaxConnections: 8})
	saved, _ = a.recording(j.ID)
	if saved.Status != "partial" || saved.Bytes == 0 || saved.Attempts < 2 {
		t.Fatalf("restart failed: %+v", saved)
	}
	cmd := exec.Command(ffmpeg, "-v", "error", "-i", filepath.Join(a.recordings, j.ID, "recording.m3u8"), "-t", "1", "-f", "null", "-")
	if data, err := cmd.CombinedOutput(); err != nil {
		t.Fatalf("recording cannot decode: %v %s", err, data)
	}
}
