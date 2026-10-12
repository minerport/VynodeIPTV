package main

import (
	"context"
	"io"
	"net/http"
	"net/http/httptest"
	"net/url"
	"strings"
	"testing"
)

func TestRelayPlaylistAndTransport(t *testing.T) {
	upstream := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		if r.URL.Path == "/start" {
			http.Redirect(w, r, "/hls/live.m3u8", 302)
			return
		}
		if r.URL.Path == "/hls/live.m3u8" {
			io.WriteString(w, "#EXTM3U\n#EXT-X-KEY:METHOD=AES-128,URI=\"key.bin\"\n#EXTINF:2,\n../segment.ts\n")
			return
		}
		io.WriteString(w, "synthetic-media-bytes")
	}))
	defer upstream.Close()
	relay, err := newMediaRelay(Provider{AllowHTTP: true})
	if err != nil {
		t.Fatal(err)
	}
	defer relay.close()
	local := relay.local(upstream.URL + "/start")
	if strings.Contains(local, upstream.URL) {
		t.Fatal("remote URL leaked")
	}
	response, err := http.Get(local)
	if err != nil {
		t.Fatal(err)
	}
	body, _ := io.ReadAll(response.Body)
	response.Body.Close()
	if response.StatusCode != 200 || strings.Contains(string(body), upstream.URL) || !strings.Contains(string(body), "/media.ts") {
		t.Fatal("playlist not rebased", string(body))
	}
	lines := strings.Split(string(body), "\n")
	response, err = http.Get(lines[3])
	if err != nil {
		t.Fatal(err)
	}
	media, _ := io.ReadAll(response.Body)
	response.Body.Close()
	if string(media) != "synthetic-media-bytes" {
		t.Fatal("rebased segment failed")
	}
	relay.allowHTTP = false
	response, err = http.Get(local)
	if err != nil {
		t.Fatal(err)
	}
	response.Body.Close()
	if response.StatusCode != 403 {
		t.Fatal("HTTP opt-in bypass")
	}
	base, _ := url.Parse("https://provider.invalid/list.m3u8")
	if _, err = relay.rewrite("#EXTM3U\nhttp://provider.invalid/segment.ts", base); err == nil {
		t.Fatal("HTTP playlist child accepted")
	}
}
func TestRelayVerifiesTLS(t *testing.T) {
	upstream := httptest.NewTLSServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) { io.WriteString(w, "not-trusted-media") }))
	defer upstream.Close()
	relay, err := newMediaRelay(Provider{})
	if err != nil {
		t.Fatal(err)
	}
	defer relay.close()
	req, _ := http.NewRequestWithContext(context.Background(), "GET", relay.local(upstream.URL), nil)
	response, err := http.DefaultClient.Do(req)
	if err != nil {
		t.Fatal(err)
	}
	response.Body.Close()
	if response.StatusCode != 502 {
		t.Fatal("untrusted TLS accepted")
	}
}
