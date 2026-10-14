package main

import (
	"context"
	"fmt"
	"io"
	"os"
	"os/exec"
	"path/filepath"
	"strconv"
	"strings"
	"sync"
	"time"
)

type Session struct {
	mu           sync.Mutex
	ID           string `json:"id"`
	ChannelID    string `json:"channelId"`
	State        string `json:"state"`
	Reason       string `json:"reason"`
	Generation   int    `json:"generation"`
	Attempts     int    `json:"attempts"`
	MediaURL     string `json:"mediaUrl"`
	Profile      string `json:"profile"`
	owner        string
	provider     string
	lease        time.Time
	cancel       context.CancelFunc
	workerCancel context.CancelFunc
	done         chan struct{}
	health       SessionHealth
}
type SessionView struct {
	ID         string        `json:"id"`
	ChannelID  string        `json:"channelId"`
	State      string        `json:"state"`
	Reason     string        `json:"reason"`
	Generation int           `json:"generation"`
	Attempts   int           `json:"attempts"`
	MediaURL   string        `json:"mediaUrl"`
	Profile    string        `json:"profile"`
	Health     SessionHealth `json:"health"`
}

func (s *Session) view() SessionView {
	s.mu.Lock()
	defer s.mu.Unlock()
	h := s.health
	h.Events = append([]HealthEvent{}, h.Events...)
	if !h.LastSegmentAt.IsZero() {
		h.SegmentAgeMS = time.Since(h.LastSegmentAt).Milliseconds()
	}
	return SessionView{s.ID, s.ChannelID, s.State, s.Reason, s.Generation, s.Attempts, s.MediaURL, s.Profile, h}
}
func (s *Session) set(state, reason string) {
	s.mu.Lock()
	defer s.mu.Unlock()
	if s.State != state || s.Reason != reason {
		s.health.Events = append(s.health.Events, HealthEvent{time.Now(), state, reason})
		if len(s.health.Events) > 32 {
			s.health.Events = s.health.Events[len(s.health.Events)-32:]
		}
	}
	s.State = state
	s.Reason = reason
}
func (a *App) startSession(owner string, c Channel, p Provider, profiles ...string) (*Session, error) {
	a.mu.Lock()
	defer a.mu.Unlock()
	if a.closing || len(a.sessions)+len(a.recordingLeases)+a.preparingRecordingsLocked() >= 8 {
		return nil, fmt.Errorf("Server session limit reached")
	}
	count := 0
	for _, provider := range a.recordingLeases {
		if provider == p.ID {
			count++
		}
	}
	for _, s := range a.sessions {
		if s.provider == p.ID {
			count++
		}
	}
	limit := p.MaxConnections
	if limit < 1 {
		limit = 1
	}
	if count >= limit {
		return nil, fmt.Errorf("Provider connection limit reached; stop another session first")
	}
	ctx, cancel := context.WithCancel(context.Background())
	s := &Session{ID: randomID(16), ChannelID: c.ID, State: "starting", owner: owner, provider: p.ID, lease: time.Now().Add(90 * time.Second), cancel: cancel, done: make(chan struct{})}
	s.Profile = "remux"
	if len(profiles) > 0 && profiles[0] == "browser" {
		s.Profile = "browser"
	}
	a.sessions[s.ID] = s
	s.health.StartedAt = time.Now()
	go a.runSession(ctx, s, c, p)
	return s, nil
}
func (a *App) runSession(ctx context.Context, s *Session, c Channel, p Provider) {
	defer close(s.done)
	root := filepath.Join(a.scratch, s.ID)
	defer os.RemoveAll(root)
	inputURL := ""
	if c.ProviderID != "demo" {
		relay, err := newMediaRelay(p)
		if err != nil {
			s.set("failed", "Could not start local media transport")
			return
		}
		defer relay.close()
		inputURL = relay.local(streamURL(p, c))
	}
	attempts := 0
	for ctx.Err() == nil {
		s.mu.Lock()
		s.Generation++
		s.health.Restarts = s.Generation - 1
		gen := s.Generation
		s.Attempts = attempts
		s.State = "starting"
		s.Reason = "Opening live stream"
		s.MediaURL = fmt.Sprintf("/media/%s/%d/live.m3u8", s.ID, gen)
		s.mu.Unlock()
		dir := filepath.Join(root, strconv.Itoa(gen))
		if err := os.MkdirAll(dir, 0700); err != nil {
			s.set("failed", "Scratch directory unavailable")
			return
		}
		// Keep the previous generation briefly addressable during client transition.
		if gen > 2 {
			_ = os.RemoveAll(filepath.Join(root, strconv.Itoa(gen-2)))
		}
		args := []string{"-hide_banner", "-loglevel", "error", "-nostdin", "-filter_threads", "2", "-filter_complex_threads", "2"}
		if c.ProviderID == "demo" {
			args = append(args, "-re", "-f", "lavfi", "-i", "testsrc2=size=1280x720:rate=30", "-re", "-f", "lavfi", "-i", "sine=frequency=440:sample_rate=48000", "-c:v", "libx264", "-threads", "2", "-preset", "ultrafast", "-tune", "zerolatency", "-g", "60", "-sc_threshold", "0", "-c:a", "aac", "-b:a", "96k")
		} else {
			args = append(args, "-rw_timeout", "12000000", "-protocol_whitelist", "http,tcp,crypto", "-i", inputURL, "-map", "0:v:0?", "-map", "0:a:0?")
			if s.Profile == "browser" {
				args = append(args, "-vf", "scale=w='min(1280,iw)':h=-2", "-r", "30", "-c:v", "libx264", "-threads", "2", "-preset", "veryfast", "-tune", "zerolatency", "-pix_fmt", "yuv420p", "-g", "60", "-sc_threshold", "0", "-c:a", "aac", "-ac", "2", "-b:a", "128k")
			} else {
				args = append(args, "-c", "copy")
			}
		}
		args = append(args, "-f", "hls", "-hls_time", "2", "-hls_list_size", "8", "-hls_delete_threshold", "3", "-hls_flags", "delete_segments+temp_file", "-hls_segment_filename", filepath.Join(dir, "segment-%06d.ts"), filepath.Join(dir, "live.m3u8"))
		workerCtx, workerCancel := context.WithCancel(ctx)
		s.mu.Lock()
		s.workerCancel = workerCancel
		s.mu.Unlock()
		cmd := exec.CommandContext(workerCtx, a.ffmpeg, args...)
		cmd.Stdout = io.Discard
		diagnostics := &workerDiagnostics{}
		cmd.Stderr = diagnostics
		if err := cmd.Start(); err != nil {
			workerCancel()
			s.set("failed", "Media worker could not start; verify FFmpeg installation")
			return
		}
		exited := make(chan error, 1)
		go func() { exited <- cmd.Wait() }()
		ticker := time.NewTicker(time.Second)
		started := time.Now()
		lastProgress := started
		lastSegment := ""
		healthySince := time.Time{}
		reason := "Upstream stream ended"
		running := true
		for running {
			select {
			case <-ctx.Done():
				workerCancel()
				<-exited
				ticker.Stop()
				s.set("stopped", "Stopped")
				return
			case <-exited:
				reason = diagnostics.reason()
				running = false
			case <-ticker.C:
				segment := newestSegment(dir)
				if segment != "" && segment != lastSegment {
					lastSegment = segment
					lastProgress = time.Now()
					s.mu.Lock()
					s.health.LastSegmentAt = lastProgress
					s.health.SegmentsObserved++
					s.mu.Unlock()
					s.set("playing", "Server output progressing")
					if healthySince.IsZero() {
						healthySince = time.Now()
					}
					if time.Since(healthySince) > 60*time.Second {
						attempts = 0
						s.mu.Lock()
						s.Attempts = 0
						s.mu.Unlock()
					}
				}
				deadline := 20 * time.Second
				if lastSegment != "" {
					deadline = 12 * time.Second
				}
				if time.Since(lastProgress) > deadline {
					reason = "No new media segments; reconnecting"
					workerCancel()
					<-exited
					running = false
				}
				// Bound per-session disk even for unusually high bitrate sources.
				var bytes int64
				_ = filepath.WalkDir(root, func(_ string, d os.DirEntry, e error) error {
					if e == nil && !d.IsDir() {
						if i, e := d.Info(); e == nil {
							bytes += i.Size()
						}
					}
					return nil
				})
				s.mu.Lock()
				s.health.ScratchBytes = bytes
				s.mu.Unlock()
				if bytes > 256<<20 {
					workerCancel()
					if running {
						<-exited
					}
					ticker.Stop()
					s.set("failed", "Session exceeded 256 MB scratch budget")
					return
				}
			}
		}
		ticker.Stop()
		workerCancel()
		attempts++
		if attempts >= 5 {
			s.mu.Lock()
			s.Attempts = attempts
			s.mu.Unlock()
			s.set("failed", reason+". Recovery limit reached; retry this channel.")
			return
		}
		s.set("recovering", reason)
		delay := time.Duration(1<<uint(attempts-1)) * time.Second
		select {
		case <-ctx.Done():
			s.set("stopped", "Stopped")
			return
		case <-time.After(delay):
		}
	}
	s.set("stopped", "Stopped")
}
func validMediaName(name string) bool {
	if name == "live.m3u8" {
		return true
	}
	if !strings.HasPrefix(name, "segment-") || !strings.HasSuffix(name, ".ts") {
		return false
	}
	n := strings.TrimSuffix(strings.TrimPrefix(name, "segment-"), ".ts")
	if len(n) < 6 || len(n) > 12 {
		return false
	}
	for _, c := range n {
		if c < '0' || c > '9' {
			return false
		}
	}
	return true
}
func (a *App) reap(ctx context.Context) {
	t := time.NewTicker(10 * time.Second)
	defer t.Stop()
	for {
		select {
		case <-ctx.Done():
			return
		case <-t.C:
			a.mu.Lock()
			for id, s := range a.sessions {
				s.mu.Lock()
				expired := time.Now().After(s.lease)
				s.mu.Unlock()
				if expired {
					s.cancel()
					select {
					case <-s.done:
						delete(a.sessions, id)
					default:
					}
				}
			}
			for key, p := range a.pairings {
				if time.Now().After(p.Expires) {
					delete(a.pairings, key)
				}
			}
			a.mu.Unlock()
		}
	}
}
func (a *App) close() {
	a.mu.Lock()
	a.closing = true
	preparations := []*recordingPlayback{}
	for _, p := range a.recordingPlaybacks {
		if p.state == "preparing" {
			p.cancel()
		}
		preparations = append(preparations, p)
	}
	all := []*Session{}
	for _, s := range a.sessions {
		s.cancel()
		all = append(all, s)
	}
	a.mu.Unlock()
	for _, p := range preparations {
		<-p.done
	}
	for _, s := range all {
		<-s.done
	}
}
