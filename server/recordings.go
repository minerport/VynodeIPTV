package main

import (
	"context"
	"errors"
	"fmt"
	"io"
	"net/http"
	"os"
	"os/exec"
	"path/filepath"
	"regexp"
	"sort"
	"strconv"
	"strings"
	"sync"
	"time"
)

// Metadata is stored with the encrypted account state. No stream URLs or
// credentials are copied into jobs; each attempt resolves the saved provider.
type Recording struct {
	ID       string    `json:"id"`
	Owner    string    `json:"owner"`
	Channel  Channel   `json:"channel"`
	Title    string    `json:"title"`
	Start    time.Time `json:"start"`
	End      time.Time `json:"end"`
	Status   string    `json:"status"`
	Reason   string    `json:"reason"`
	Attempts int       `json:"attempts"`
	Gaps     int       `json:"gaps"`
	Bytes    int64     `json:"bytes"`
	Resume   float64   `json:"resume"`
}

func pendingRecording(r Recording) bool { return r.Status == "scheduled" || r.Status == "recording" }
func (a *App) updateRecording(id string, fn func(*Recording)) error {
	return a.store.update(func(s *State) {
		for i := range s.Recordings {
			if s.Recordings[i].ID == id {
				fn(&s.Recordings[i])
				return
			}
		}
	})
}
func (a *App) recording(id string) (Recording, bool) {
	for _, r := range a.store.snapshot().Recordings {
		if r.ID == id {
			return r, true
		}
	}
	return Recording{}, false
}
func (a *App) recordingRoutes(m *http.ServeMux) {
	m.HandleFunc("GET /api/v1/recordings", a.auth(false, func(w http.ResponseWriter, r *http.Request) {
		owner, _ := a.identity(r)
		out := []Recording{}
		for _, j := range a.store.snapshot().Recordings {
			if j.Owner == owner {
				out = append(out, j)
			}
		}
		send(w, 200, out)
	}))
	m.HandleFunc("POST /api/v1/recordings", a.auth(false, a.scheduleRecording))
	m.HandleFunc("POST /api/v1/recordings/{id}/cancel", a.auth(false, func(w http.ResponseWriter, r *http.Request) {
		j, ok := a.ownedRecording(w, r)
		if !ok {
			return
		}
		if err := a.updateRecording(j.ID, func(j *Recording) {
			if pendingRecording(*j) {
				j.Status = "cancelled"
				j.Reason = "Stopped by user"
			}
		}); err != nil {
			fail(w, 500, "Cannot save recording state")
			return
		}
		send(w, 200, map[string]bool{"ok": true})
	}))
	m.HandleFunc("PUT /api/v1/recordings/{id}/resume", a.auth(false, func(w http.ResponseWriter, r *http.Request) {
		j, ok := a.ownedRecording(w, r)
		if !ok {
			return
		}
		var v struct {
			Seconds float64 `json:"seconds"`
		}
		if !decode(w, r, &v) {
			return
		}
		if v.Seconds < 0 || v.Seconds > 172800 {
			fail(w, 400, "Invalid playback position")
			return
		}
		if a.updateRecording(j.ID, func(j *Recording) { j.Resume = v.Seconds }) != nil {
			fail(w, 500, "Cannot save playback position")
			return
		}
		send(w, 200, map[string]bool{"ok": true})
	}))
	m.HandleFunc("DELETE /api/v1/recordings/{id}", a.auth(false, func(w http.ResponseWriter, r *http.Request) {
		j, ok := a.ownedRecording(w, r)
		if !ok {
			return
		}
		a.mu.Lock()
		defer a.mu.Unlock()
		if _, active := a.recordingLeases[j.ID]; active || pendingRecording(j) {
			fail(w, 409, "Cancel the recording and wait for it to stop before deleting")
			return
		}
		if err := os.RemoveAll(filepath.Join(a.recordings, j.ID)); err != nil {
			fail(w, 500, "Cannot remove recording files")
			return
		}
		if err := a.store.update(func(s *State) {
			out := []Recording{}
			for _, v := range s.Recordings {
				if v.ID != j.ID {
					out = append(out, v)
				}
			}
			s.Recordings = out
		}); err != nil {
			fail(w, 500, "Cannot remove library entry")
			return
		}
		send(w, 200, map[string]bool{"ok": true})
	}))
	m.HandleFunc("GET /recorded/{id}/{file}", a.auth(false, func(w http.ResponseWriter, r *http.Request) {
		j, ok := a.ownedRecording(w, r)
		if !ok {
			return
		}
		file := r.PathValue("file")
		if !recordingFile.MatchString(file) {
			fail(w, 404, "Media not found")
			return
		}
		if pendingRecording(j) {
			fail(w, 409, "Recording is still in progress")
			return
		}
		w.Header().Set("Cache-Control", "private, no-store")
		if strings.HasSuffix(file, ".m3u8") {
			w.Header().Set("Content-Type", "application/vnd.apple.mpegurl")
		}
		http.ServeFile(w, r, filepath.Join(a.recordings, j.ID, file))
	}))
}

var recordingFile = regexp.MustCompile(`^(recording\.m3u8|part-[0-9]+-[0-9]+\.ts)$`)

func (a *App) ownedRecording(w http.ResponseWriter, r *http.Request) (Recording, bool) {
	j, ok := a.recording(r.PathValue("id"))
	owner, _ := a.identity(r)
	if !ok || j.Owner != owner {
		fail(w, 404, "Recording not found")
		return Recording{}, false
	}
	return j, true
}
func (a *App) scheduleRecording(w http.ResponseWriter, r *http.Request) {
	var in struct {
		ChannelID string    `json:"channelId"`
		Title     string    `json:"title"`
		Start     time.Time `json:"start"`
		End       time.Time `json:"end"`
		Before    *int      `json:"beforeMinutes"`
		After     *int      `json:"afterMinutes"`
	}
	if !decode(w, r, &in) {
		return
	}
	now := time.Now()
	if in.Start.IsZero() {
		in.Start = now
	}
	before, after := 1, 3
	if in.Before != nil {
		before = *in.Before
	}
	if in.After != nil {
		after = *in.After
	}
	if before < 0 || before > 60 || after < 0 || after > 120 || !in.End.After(in.Start) || in.End.Sub(in.Start) > 24*time.Hour || in.Start.After(now.Add(30*24*time.Hour)) || !in.End.After(now) {
		fail(w, 400, "Choose a future recording up to 24 hours long, within 30 days")
		return
	}
	start := in.Start.Add(-time.Duration(before) * time.Minute)
	if start.Before(now) {
		start = now
	}
	end := in.End.Add(time.Duration(after) * time.Minute)
	a.accountMu.RLock()
	defer a.accountMu.RUnlock()
	a.mu.Lock()
	defer a.mu.Unlock()
	var channel Channel
	for _, c := range a.channels {
		if c.ID == in.ChannelID {
			channel = c
			break
		}
	}
	if channel.ID == "" {
		fail(w, 404, "Channel not found")
		return
	}
	provider := Provider{ID: "demo", MaxConnections: 8}
	if channel.ProviderID != "demo" {
		provider = Provider{}
		for _, p := range a.store.snapshot().Providers {
			if p.ID == channel.ProviderID {
				provider = p
			}
		}
	}
	if provider.ID == "" {
		fail(w, 404, "Provider unavailable")
		return
	}
	limit := max(1, provider.MaxConnections)
	owner, _ := a.identity(r)
	title := strings.TrimSpace(in.Title)
	if title == "" {
		title = channel.Name
	}
	if len(title) > 240 {
		fail(w, 400, "Title is too long")
		return
	}
	j := Recording{ID: randomID(16), Owner: owner, Channel: channel, Title: title, Start: start.UTC(), End: end.UTC(), Status: "scheduled"}
	err := a.store.update(func(s *State) {
		if recordingConflict(s.Recordings, j, limit) {
			return
		}
		s.Recordings = append(s.Recordings, j)
	})
	if err != nil {
		fail(w, 500, "Cannot persist recording")
		return
	}
	if _, ok := a.recording(j.ID); !ok {
		fail(w, 409, "Scheduled recordings exceed this provider's connection limit")
		return
	}
	select {
	case a.dvrWake <- struct{}{}:
	default:
	}
	send(w, 201, j)
}

// Sweep overlapping intervals, with end events before start events at a boundary.
func recordingConflict(jobs []Recording, next Recording, limit int) bool {
	type event struct {
		at time.Time
		n  int
	}
	events := []event{{next.Start, 1}, {next.End, -1}}
	for _, j := range jobs {
		if pendingRecording(j) && j.Channel.ProviderID == next.Channel.ProviderID && j.Start.Before(next.End) && j.End.After(next.Start) {
			events = append(events, event{maxTime(j.Start, next.Start), 1}, event{minTime(j.End, next.End), -1})
		}
	}
	sort.Slice(events, func(i, j int) bool {
		if events[i].at.Equal(events[j].at) {
			return events[i].n < events[j].n
		}
		return events[i].at.Before(events[j].at)
	})
	count := 0
	for _, e := range events {
		count += e.n
		if count > limit {
			return true
		}
	}
	return false
}
func minTime(a, b time.Time) time.Time {
	if a.Before(b) {
		return a
	}
	return b
}
func maxTime(a, b time.Time) time.Time {
	if a.After(b) {
		return a
	}
	return b
}

func (a *App) runDVR(ctx context.Context) {
	var workers sync.WaitGroup
	defer workers.Wait()
	// A prior process may have died between segments. Keep the gap visible.
	_ = a.store.update(func(s *State) {
		for i := range s.Recordings {
			j := &s.Recordings[i]
			if j.Status == "recording" {
				j.Status = "scheduled"
				j.Gaps++
				j.Reason = "Resuming after server restart"
			}
		}
	})
	ticker := time.NewTicker(time.Second)
	defer ticker.Stop()
	for ctx.Err() == nil {
		for _, j := range a.store.snapshot().Recordings {
			if !pendingRecording(j) || time.Now().Before(j.Start) {
				continue
			}
			a.accountMu.RLock()
			a.mu.Lock()
			_, running := a.recordingLeases[j.ID]
			if running {
				a.mu.Unlock()
				a.accountMu.RUnlock()
				continue
			}
			p := Provider{ID: "demo", MaxConnections: 8}
			if j.Channel.ProviderID != "demo" {
				p = Provider{}
				for _, v := range a.store.snapshot().Providers {
					if v.ID == j.Channel.ProviderID {
						p = v
					}
				}
			}
			count := 0
			for _, id := range a.recordingLeases {
				if id == p.ID {
					count++
				}
			}
			for _, s := range a.sessions {
				if s.provider == p.ID {
					count++
				}
			}
			reason := ""
			if !time.Now().Before(j.End) {
				reason = "Recording window elapsed"
			} else if p.ID == "" {
				reason = "Saved provider was removed"
			} else if len(a.sessions)+len(a.recordingLeases) >= 8 {
				reason = "Server worker limit reached"
			} else if count >= max(1, p.MaxConnections) {
				reason = "Provider connection limit reached; existing streams were preserved"
			}
			if reason != "" {
				a.mu.Unlock()
				a.accountMu.RUnlock()
				a.finishRecording(j.ID, reason)
				continue
			}
			a.recordingLeases[j.ID] = p.ID
			a.mu.Unlock()
			a.accountMu.RUnlock()
			workers.Add(1)
			go func(j Recording, p Provider) {
				defer workers.Done()
				defer func() { a.mu.Lock(); delete(a.recordingLeases, j.ID); a.mu.Unlock() }()
				a.captureRecording(ctx, j, p)
			}(j, p)
		}
		select {
		case <-ctx.Done():
			return
		case <-ticker.C:
		case <-a.dvrWake:
		}
	}
}
func (a *App) finishRecording(id, reason string) {
	dir := filepath.Join(a.recordings, id)
	bytes, manifestErr := buildRecordingPlaylist(dir)
	if manifestErr != nil && reason == "" {
		reason = "Recording playlist could not be finalized; retained available files"
	}
	_ = a.updateRecording(id, func(j *Recording) {
		j.Bytes = bytes
		if bytes == 0 {
			j.Status = "failed"
		} else if reason != "" || j.Gaps > 0 {
			j.Status = "partial"
		} else {
			j.Status = "completed"
		}
		j.Reason = reason
	})
}
func (a *App) captureRecording(ctx context.Context, j Recording, p Provider) {
	dir := filepath.Join(a.recordings, j.ID)
	if os.MkdirAll(dir, 0700) != nil {
		a.finishRecording(j.ID, "Recording storage unavailable")
		return
	}
	current, exists := a.recording(j.ID)
	if !exists || !pendingRecording(current) {
		return
	}
	if a.updateRecording(j.ID, func(j *Recording) {
		if pendingRecording(*j) {
			j.Status = "recording"
		}
	}) != nil {
		return
	}
	input := ""
	if j.Channel.ProviderID != "demo" {
		relay, err := newMediaRelay(p)
		if err != nil {
			a.finishRecording(j.ID, "Media transport unavailable")
			return
		}
		defer relay.close()
		input = relay.local(streamURL(p, j.Channel))
	}
	reason := ""
	failures := 0
	for time.Now().Before(j.End) && ctx.Err() == nil {
		current, ok := a.recording(j.ID)
		if !ok {
			return
		}
		if current.Status == "cancelled" {
			reason = "Stopped by user"
			break
		}
		if free, err := recordingFreeSpace(dir); err != nil || free < 512*1024*1024 {
			reason = "Recording storage unavailable or less than 512 MiB free"
			break
		}
		attempt := current.Attempts + 1
		if a.updateRecording(j.ID, func(j *Recording) { j.Attempts = attempt }) != nil {
			reason = "Cannot persist recording progress"
			break
		}
		worker, cancel := context.WithDeadline(ctx, j.End.Add(5*time.Second))
		args := []string{"-hide_banner", "-loglevel", "error", "-nostdin", "-y"}
		if j.Channel.ProviderID == "demo" {
			args = append(args, "-re", "-f", "lavfi", "-i", "testsrc2=size=640x360:rate=25", "-re", "-f", "lavfi", "-i", "sine=frequency=440:sample_rate=48000", "-c:v", "libx264", "-threads", "2", "-preset", "ultrafast", "-g", "50", "-c:a", "aac")
		} else {
			args = append(args, "-rw_timeout", "12000000", "-protocol_whitelist", "http,tcp,crypto", "-i", input, "-map", "0:v:0?", "-map", "0:a:0?", "-c", "copy")
		}
		prefix := "part-" + strconv.Itoa(attempt) + "-"
		playlist := filepath.Join(dir, fmt.Sprintf("attempt-%06d.m3u8", attempt))
		args = append(args, "-t", fmt.Sprintf("%.3f", max(0.1, time.Until(j.End).Seconds())), "-f", "hls", "-hls_time", "4", "-hls_list_size", "0", "-hls_flags", "temp_file", "-hls_segment_filename", filepath.Join(dir, prefix+"%08d.ts"), playlist)
		cmd := exec.CommandContext(worker, a.ffmpeg, args...)
		cmd.Stdout = io.Discard
		cmd.Stderr = io.Discard
		if err := cmd.Start(); err != nil {
			cancel()
			reason = "Recording worker could not start"
			break
		}
		done := make(chan error, 1)
		go func() { done <- cmd.Wait() }()
		tick := time.NewTicker(time.Second)
		last := time.Now()
		lastStored := time.Now()
		lastSize := int64(-1)
		halt := false
		normalExit := false
		for !halt {
			select {
			case err := <-done:
				normalExit = err == nil
				halt = true
			case <-worker.Done():
				cancel()
				<-done
				halt = true
			case <-tick.C:
				size := int64(0)
				totalBytes := int64(0)
				entries, _ := os.ReadDir(dir)
				for _, e := range entries {
					if strings.HasSuffix(e.Name(), ".ts") {
						if info, err := e.Info(); err == nil {
							totalBytes += info.Size()
						}
					}
					if strings.HasPrefix(e.Name(), prefix) {
						if info, err := e.Info(); err == nil {
							size += info.Size()
						}
					}
				}
				if size != lastSize {
					lastSize = size
					last = time.Now()
				}
				if time.Since(lastStored) > 5*time.Second {
					if a.updateRecording(j.ID, func(j *Recording) { j.Bytes = totalBytes }) != nil {
						reason = "Cannot persist recording progress"
						cancel()
						<-done
						halt = true
						continue
					}
					lastStored = time.Now()
				}
				cur, exists := a.recording(j.ID)
				if !exists || cur.Status == "cancelled" {
					reason = "Stopped by user"
					cancel()
					<-done
					halt = true
				} else if free, err := recordingFreeSpace(dir); err != nil || free < 512*1024*1024 {
					reason = "Recording storage unavailable or full"
					cancel()
					<-done
					halt = true
				} else if time.Since(last) > 30*time.Second {
					cancel()
					<-done
					halt = true
				}
			}
		}
		tick.Stop()
		cancel()
		if ctx.Err() != nil {
			_ = a.updateRecording(j.ID, func(j *Recording) {
				j.Status = "scheduled"
				j.Gaps++
				j.Reason = "Server stopped; recording will resume if its window is still active"
			})
			_, _ = buildRecordingPlaylist(dir)
			return
		}
		if !normalExit && reason == "" && !time.Now().Before(j.End) {
			reason = "Recording ended with an interrupted stream"
		}
		if reason != "" || !time.Now().Before(j.End) || (normalExit && time.Until(j.End) < time.Second) {
			break
		}
		failures++
		_ = a.updateRecording(j.ID, func(j *Recording) { j.Gaps++; j.Reason = "Stream interrupted; retrying" })
		if failures >= 5 {
			reason = "Stream failed after five recovery attempts"
			break
		}
		select {
		case <-ctx.Done():
		case <-time.After(time.Duration(1<<failures) * time.Second):
		}
	}
	if ctx.Err() != nil {
		_ = a.updateRecording(j.ID, func(j *Recording) {
			if pendingRecording(*j) {
				j.Status = "scheduled"
				j.Reason = "Server stopped during recovery; waiting to resume"
			}
		})
		return
	}
	a.finishRecording(j.ID, reason)
}

// Only finalized segments referenced by an atomically published FFmpeg playlist
// are retained. Each recovery boundary is an HLS discontinuity, never a fake gap.
func buildRecordingPlaylist(dir string) (int64, error) {
	files, err := filepath.Glob(filepath.Join(dir, "attempt-*.m3u8"))
	if err != nil {
		return 0, err
	}
	sort.Strings(files)
	body := []string{}
	bytes := int64(0)
	target := float64(4)
	for _, file := range files {
		raw, err := os.ReadFile(file)
		if err != nil {
			continue
		}
		part := []string{}
		duration := ""
		for _, line := range strings.Split(string(raw), "\n") {
			line = strings.TrimSpace(line)
			if strings.HasPrefix(line, "#EXTINF:") {
				duration = line
				continue
			}
			if duration != "" && recordingFile.MatchString(line) && strings.HasSuffix(line, ".ts") {
				info, e := os.Stat(filepath.Join(dir, line))
				if e == nil && info.Size() > 0 {
					d, _ := strconv.ParseFloat(strings.TrimSuffix(strings.TrimPrefix(duration, "#EXTINF:"), ","), 64)
					if d > target {
						target = d
					}
					part = append(part, duration, line)
					bytes += info.Size()
				}
				duration = ""
			}
		}
		if len(part) > 0 {
			if len(body) > 0 {
				body = append(body, "#EXT-X-DISCONTINUITY")
			}
			body = append(body, part...)
		}
	}
	if len(body) == 0 {
		return 0, errors.New("no finalized segments")
	}
	output := fmt.Sprintf("#EXTM3U\n#EXT-X-VERSION:3\n#EXT-X-TARGETDURATION:%d\n#EXT-X-PLAYLIST-TYPE:VOD\n#EXT-X-MEDIA-SEQUENCE:0\n", int(target)+1) + strings.Join(body, "\n") + "\n#EXT-X-ENDLIST\n"
	tmp := filepath.Join(dir, "recording.m3u8.tmp")
	if err = os.WriteFile(tmp, []byte(output), 0600); err != nil {
		return bytes, err
	}
	return bytes, os.Rename(tmp, filepath.Join(dir, "recording.m3u8"))
}
