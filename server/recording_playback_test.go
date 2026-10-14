package main

import (
	"context"
	"encoding/json"
	"math"
	"os"
	"os/exec"
	"path/filepath"
	"strconv"
	"strings"
	"testing"
	"time"
)

func TestBrowserRecordingConversionAndCache(t *testing.T) {
	ffmpeg, err := exec.LookPath("ffmpeg")
	if err != nil {
		t.Skip("ffmpeg unavailable")
	}
	probe, err := exec.LookPath("ffprobe")
	if err != nil {
		t.Skip("ffprobe unavailable")
	}
	a := testApp(t)
	a.ffmpeg = ffmpeg
	t.Cleanup(a.close)
	id := randomID(16)
	dir := filepath.Join(a.recordings, id)
	if err = os.MkdirAll(dir, 0700); err != nil {
		t.Fatal(err)
	}
	// MPEG-2/AC3 is playable by many TVs but is not a portable browser format.
	args := []string{"-v", "error", "-f", "lavfi", "-i", "testsrc2=size=320x180:rate=25", "-f", "lavfi", "-i", "sine=frequency=440:sample_rate=48000", "-t", "8", "-c:v", "mpeg2video", "-threads", "2", "-g", "50", "-c:a", "ac3", "-f", "hls", "-hls_time", "2", "-hls_list_size", "0", "-hls_segment_filename", filepath.Join(dir, "part-1-%08d.ts"), filepath.Join(dir, "recording.m3u8")}
	if out, e := exec.Command(ffmpeg, args...).CombinedOutput(); e != nil {
		t.Fatalf("fixture: %v %s", e, out)
	}
	before, err := os.ReadFile(filepath.Join(dir, "recording.m3u8"))
	if err != nil {
		t.Fatal(err)
	}
	if err = a.store.update(func(s *State) { s.Recordings = []Recording{{ID: id, Owner: "admin", Status: "completed", Bytes: 100}} }); err != nil {
		t.Fatal(err)
	}
	path := "/api/v1/recordings/" + id + "/playback"
	w := call(a, "POST", path, a.admin, "")
	if w.Code != 200 {
		t.Fatal(w.Code, w.Body.String())
	}
	a.mu.Lock()
	p := a.recordingPlaybacks[id]
	a.mu.Unlock()
	select {
	case <-p.done:
	case <-time.After(30 * time.Second):
		t.Fatal("conversion timed out")
	}
	v := a.recordingPlaybackView(id)
	if v.State != "complete" || math.Abs(v.Available-8) > .15 {
		t.Fatalf("conversion: %+v", v)
	}
	after, _ := os.ReadFile(filepath.Join(dir, "recording.m3u8"))
	if string(before) != string(after) {
		t.Fatal("original changed")
	}
	// Decode the entire output, including segment boundaries, not just its first second.
	file := filepath.Join(dir, "browser.m3u8")
	if out, e := exec.Command(ffmpeg, "-v", "error", "-xerror", "-i", file, "-f", "null", "-").CombinedOutput(); e != nil || len(out) > 0 {
		t.Fatalf("decode: %v %s", e, out)
	}
	out, e := exec.Command(probe, "-v", "error", "-show_streams", "-show_packets", "-of", "json", file).Output()
	if e != nil {
		t.Fatal(e)
	}
	var info struct {
		Streams []struct {
			Codec string `json:"codec_name"`
			Type  string `json:"codec_type"`
		}
		Packets []struct {
			Index int    `json:"stream_index"`
			DTS   string `json:"dts_time"`
		}
	}
	if json.Unmarshal(out, &info) != nil {
		t.Fatal("invalid probe")
	}
	codecs := map[string]bool{}
	for _, s := range info.Streams {
		codecs[s.Codec] = true
	}
	if !codecs["h264"] || !codecs["aac"] {
		t.Fatal("unexpected codecs", codecs)
	}
	first, last := map[int]float64{}, map[int]float64{}
	for _, p := range info.Packets {
		d, e := strconv.ParseFloat(p.DTS, 64)
		if e != nil {
			continue
		}
		if old, ok := last[p.Index]; ok {
			if d <= old {
				t.Fatal("nonmonotonic packet timing")
			}
		} else {
			first[p.Index] = d
		}
		last[p.Index] = d
	}
	if len(last) != 2 {
		t.Fatal("missing audio or video")
	}
	for i, end := range last {
		if end-first[i] < 7.8 {
			t.Fatal("truncated timeline")
		}
	}
	if math.Abs(last[0]-last[1]) > .2 {
		t.Fatal("audio/video end drift", last)
	}
	if got := call(a, "GET", "/recorded/"+id+"/browser.m3u8", a.admin, ""); got.Code != 200 {
		t.Fatal("derived playlist inaccessible")
	}
	// Cache survives process restart and does not need FFmpeg again.
	a.mu.Lock()
	delete(a.recordingPlaybacks, id)
	a.mu.Unlock()
	a.ffmpeg = "missing-ffmpeg"
	if got := call(a, "POST", path, a.admin, ""); got.Code != 200 || !strings.Contains(got.Body.String(), `"state":"complete"`) {
		t.Fatal("cache not reused", got.Body.String())
	}
}

func TestBrowserRecordingIsolationAndCancellation(t *testing.T) {
	a := testApp(t)
	id := randomID(16)
	if err := a.store.update(func(s *State) {
		s.Recordings = []Recording{{ID: id, Owner: "admin", Status: "completed", Bytes: 100}}
		s.Devices = []Device{{ID: "other", TokenHash: tokenHash(strings.Repeat("b", 64))}}
	}); err != nil {
		t.Fatal(err)
	}
	path := "/api/v1/recordings/" + id + "/playback"
	for _, method := range []string{"GET", "POST", "DELETE"} {
		if got := call(a, method, path, strings.Repeat("b", 64), ""); got.Code != 404 {
			t.Fatal("cross owner access", method, got.Code)
		}
	}
	ctx, cancel := context.WithCancel(context.Background())
	p := &recordingPlayback{state: "preparing", cancel: cancel, done: make(chan struct{})}
	a.recordingPlaybacks[id] = p
	go func() { <-ctx.Done(); a.mu.Lock(); p.state = "failed"; a.mu.Unlock(); close(p.done) }()
	if got := call(a, "DELETE", "/api/v1/recordings/"+id, a.admin, ""); got.Code != 409 {
		t.Fatal("deleted active preparation")
	}
	if got := call(a, "POST", path, a.admin, ""); got.Code != 200 {
		t.Fatal("duplicate not reused")
	}
	if got := call(a, "DELETE", path, a.admin, ""); got.Code != 200 {
		t.Fatal("cancel failed")
	}
	if a.recordingPlaybackView(id).State != "failed" {
		t.Fatal("cancel did not wait")
	}
	a.close()
}
