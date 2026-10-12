package main

import (
	"strings"
	"sync"
)

// Only predefined categories survive; raw worker output can contain provider credentials.
type workerDiagnostics struct {
	mu       sync.Mutex
	category string
}

func (d *workerDiagnostics) Write(p []byte) (int, error) {
	s := strings.ToLower(string(p))
	category := ""
	switch {
	case strings.Contains(s, "not on whitelist") && strings.Contains(s, "http"):
		category = "Provider requires HTTP streams; enable Allow HTTP for this provider"
	case strings.Contains(s, "401"):
		category = "Provider rejected stream authorization (HTTP 401)"
	case strings.Contains(s, "403"):
		category = "Provider denied this stream (HTTP 403)"
	case strings.Contains(s, "404"):
		category = "Provider stream not found (HTTP 404)"
	case strings.Contains(s, "429"):
		category = "Provider rate limit reached (HTTP 429)"
	case strings.Contains(s, "certificate"):
		category = "Provider TLS certificate could not be verified"
	case strings.Contains(s, "timed out"):
		category = "Provider stream timed out"
	case strings.Contains(s, "invalid data"):
		category = "Provider returned invalid media"
	case strings.Contains(s, "encoder") || strings.Contains(s, "filter"):
		category = "Server could not convert this stream for the browser"
	}
	if category != "" {
		d.mu.Lock()
		d.category = category
		d.mu.Unlock()
	}
	return len(p), nil
}
func (d *workerDiagnostics) reason() string {
	d.mu.Lock()
	defer d.mu.Unlock()
	if d.category != "" {
		return d.category
	}
	return "Upstream stream ended"
}
