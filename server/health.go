package main

import (
	"os"
	"path/filepath"
	"strings"
	"time"
)

type HealthEvent struct {
	At     time.Time `json:"at"`
	State  string    `json:"state"`
	Reason string    `json:"reason"`
}
type SessionHealth struct {
	StartedAt        time.Time     `json:"startedAt"`
	LastSegmentAt    time.Time     `json:"lastSegmentAt"`
	SegmentAgeMS     int64         `json:"segmentAgeMs"`
	SegmentsObserved int64         `json:"segmentsObserved"`
	ScratchBytes     int64         `json:"scratchBytes"`
	Restarts         int           `json:"restarts"`
	Events           []HealthEvent `json:"events"`
}

// A rewritten playlist alone is not evidence of new playable output.
func newestSegment(dir string) string {
	data, err := os.ReadFile(filepath.Join(dir, "live.m3u8"))
	if err != nil {
		return ""
	}
	latest := ""
	for _, line := range strings.Split(string(data), "\n") {
		name := strings.TrimSpace(line)
		if !strings.HasPrefix(name, "segment-") || !validMediaName(name) {
			continue
		}
		if info, err := os.Stat(filepath.Join(dir, name)); err == nil && info.Mode().IsRegular() && info.Size() > 0 {
			latest = name
		}
	}
	return latest
}
