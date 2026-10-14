package main

import (
	"context"
	"encoding/json"
	"os"
	"path/filepath"
	"strings"
	"testing"
	"time"
)

func TestRecordingSchedulePersistenceAndIsolation(t *testing.T) {
	a := testApp(t)
	start := time.Now().Add(time.Hour).UTC()
	body := `{"channelId":"demo:1","title":"A test show","start":"` + start.Format(time.RFC3339) + `","end":"` + start.Add(time.Hour).Format(time.RFC3339) + `"}`
	w := call(a, "POST", "/api/v1/recordings", a.admin, body)
	if w.Code != 201 {
		t.Fatal(w.Code, w.Body.String())
	}
	var j Recording
	if json.Unmarshal(w.Body.Bytes(), &j) != nil {
		t.Fatal("invalid response")
	}
	if j.Status != "scheduled" || j.Start.Sub(start.Truncate(time.Second)) != -time.Minute || j.End.Sub(start.Truncate(time.Second).Add(time.Hour)) != 3*time.Minute {
		t.Fatalf("incorrect padded schedule: %+v", j)
	}
	restored, err := openStore(a.store.dir)
	if err != nil || len(restored.snapshot().Recordings) != 1 {
		t.Fatal("schedule not persisted", err)
	}
	deviceToken := strings.Repeat("b", 64)
	if err = a.store.update(func(s *State) { s.Devices = append(s.Devices, Device{ID: "other", TokenHash: tokenHash(deviceToken)}) }); err != nil {
		t.Fatal(err)
	}
	for _, path := range []string{"/api/v1/recordings/" + j.ID + "/cancel", "/recorded/" + j.ID + "/recording.m3u8"} {
		method := "POST"
		if strings.HasPrefix(path, "/recorded/") {
			method = "GET"
		}
		if got := call(a, method, path, deviceToken, ""); got.Code != 404 {
			t.Fatal("cross-owner recording access", got.Code)
		}
	}
	if got := call(a, "GET", "/api/v1/recordings", deviceToken, ""); strings.TrimSpace(got.Body.String()) != "[]" {
		t.Fatal("cross-owner library leak")
	}
	if got := call(a, "GET", "/recorded/"+j.ID+"/recording.m3u8", "", ""); got.Code != 401 {
		t.Fatal("unauthenticated recording access")
	}
	if got := call(a, "DELETE", "/api/v1/recordings/"+j.ID, a.admin, ""); got.Code != 409 {
		t.Fatal("deleted pending job")
	}
	if got := call(a, "POST", "/api/v1/recordings/"+j.ID+"/cancel", a.admin, ""); got.Code != 200 {
		t.Fatal("cancel failed")
	}
	if got := call(a, "DELETE", "/api/v1/recordings/"+j.ID, a.admin, ""); got.Code != 200 {
		t.Fatal("delete failed")
	}
}
func TestRecordingConflictSweep(t *testing.T) {
	now := time.Now()
	job := func(start, end int) Recording {
		return Recording{Channel: Channel{ProviderID: "p"}, Status: "scheduled", Start: now.Add(time.Duration(start) * time.Minute), End: now.Add(time.Duration(end) * time.Minute)}
	}
	if recordingConflict([]Recording{job(0, 10), job(10, 20)}, job(0, 20), 2) {
		t.Fatal("non-overlapping reservations double counted")
	}
	if !recordingConflict([]Recording{job(0, 11), job(10, 20)}, job(0, 20), 2) {
		t.Fatal("overlap accepted")
	}
	if recordingConflict([]Recording{job(0, 10)}, job(10, 20), 1) {
		t.Fatal("boundary conflict")
	}
}
func TestRecordingLeasesProtectProviderAndLiveLimit(t *testing.T) {
	a := testApp(t)
	a.recordingLeases["recording"] = "provider"
	p := Provider{ID: "provider", MaxConnections: 1}
	if !a.providerBusy(p.ID) {
		t.Fatal("active recording provider editable")
	}
	if _, err := a.startSession("admin", Channel{ID: "x"}, p); err == nil {
		t.Fatal("live stream stole recording slot")
	}
}
func TestRecordingPlaylistRetainsSafeCompletedSegments(t *testing.T) {
	dir := t.TempDir()
	for _, name := range []string{"part-1-00000000.ts", "part-2-00000000.ts"} {
		if err := os.WriteFile(filepath.Join(dir, name), []byte("test segment"), 0600); err != nil {
			t.Fatal(err)
		}
	}
	_ = os.WriteFile(filepath.Join(dir, "attempt-000001.m3u8"), []byte("#EXTM3U\n#EXTINF:4.3,\npart-1-00000000.ts\n#EXTINF:4,\n../secret.ts\n#EXTINF:4,\npart-1-00000001.ts\n"), 0600)
	_ = os.WriteFile(filepath.Join(dir, "attempt-000002.m3u8"), []byte("#EXTM3U\n#EXTINF:5,\npart-2-00000000.ts\n"), 0600)
	size, err := buildRecordingPlaylist(dir)
	if err != nil || size != 24 {
		t.Fatal(size, err)
	}
	data, _ := os.ReadFile(filepath.Join(dir, "recording.m3u8"))
	s := string(data)
	if strings.Contains(s, "secret") || strings.Contains(s, "00000001") || !strings.Contains(s, "#EXT-X-DISCONTINUITY") || !strings.HasSuffix(s, "#EXT-X-ENDLIST\n") {
		t.Fatal(s)
	}
}
func TestRecordingFailedWorkerAndExpiredRestart(t *testing.T) {
	a := testApp(t)
	j := Recording{ID: randomID(16), Owner: "admin", Channel: Channel{ID: "demo:1", ProviderID: "demo"}, Start: time.Now().Add(-time.Minute), End: time.Now().Add(time.Hour), Status: "scheduled"}
	if err := a.store.update(func(s *State) { s.Recordings = []Recording{j} }); err != nil {
		t.Fatal(err)
	}
	a.captureRecording(context.Background(), j, Provider{ID: "demo", MaxConnections: 8})
	got, _ := a.recording(j.ID)
	if got.Status != "failed" || got.Reason != "Recording worker could not start" {
		t.Fatal(got)
	}
	_ = a.updateRecording(j.ID, func(j *Recording) { j.Status = "recording"; j.End = time.Now().Add(-time.Minute) })
	ctx, cancel := context.WithCancel(context.Background())
	done := make(chan struct{})
	go func() { a.runDVR(ctx); close(done) }()
	defer func() { cancel(); <-done }()
	deadline := time.Now().Add(3 * time.Second)
	for time.Now().Before(deadline) {
		got, _ = a.recording(j.ID)
		if got.Status == "failed" && got.Reason == "Recording window elapsed" {
			return
		}
		time.Sleep(10 * time.Millisecond)
	}
	t.Fatal("expired recording was not resolved", got)
}
