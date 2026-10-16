package main

import "testing"

func TestChannelLogoPolicy(t *testing.T) {
	for _, s := range []string{"http://example.com/a.png", "https://user:secret@example.com/a.png", "https://example.com/a?token=secret", "javascript:alert(1)", "//example.com/a", "https://example.com/a#fragment", ""} {
		if safeChannelLogo(s) != "" {
			t.Errorf("accepted unsafe logo")
		}
	}
	if safeChannelLogo("https://example.com/a.png") != "https://example.com/a.png" {
		t.Fatal("valid logo rejected")
	}
}
