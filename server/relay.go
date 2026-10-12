package main

import (
	"bufio"
	"context"
	"errors"
	"io"
	"net"
	"net/http"
	"net/url"
	"path"
	"regexp"
	"strings"
	"sync"
	"time"
)

// A session-private loopback relay owns provider TLS, redirects and read deadlines.
// FFmpeg receives only local URLs; provider credentials never enter its arguments.
type deadlineConn struct{ net.Conn }

func (c deadlineConn) Read(b []byte) (int, error) {
	_ = c.SetReadDeadline(time.Now().Add(15 * time.Second))
	return c.Conn.Read(b)
}

var playlistURI = regexp.MustCompile(`URI="([^"]+)"`)

type mediaRelay struct {
	server    *http.Server
	transport *http.Transport
	prefix    string
	allowHTTP bool
	mu        sync.Mutex
	urls      map[string]string
	ids       map[string]string
	order     []string
}

func newMediaRelay(p Provider) (*mediaRelay, error) {
	listener, err := net.Listen("tcp", "127.0.0.1:0")
	if err != nil {
		return nil, err
	}
	transport := http.DefaultTransport.(*http.Transport).Clone()
	transport.ResponseHeaderTimeout = 12 * time.Second
	transport.TLSHandshakeTimeout = 10 * time.Second
	transport.DialContext = func(ctx context.Context, network, address string) (net.Conn, error) {
		c, e := (&net.Dialer{Timeout: 10 * time.Second}).DialContext(ctx, network, address)
		if e != nil {
			return nil, e
		}
		return deadlineConn{c}, nil
	}
	relay := &mediaRelay{transport: transport, allowHTTP: p.AllowHTTP, urls: map[string]string{}, ids: map[string]string{}, prefix: "http://" + listener.Addr().String() + "/" + randomID(24) + "/"}
	relay.server = &http.Server{Handler: http.HandlerFunc(relay.serve), ReadHeaderTimeout: 5 * time.Second}
	go func() { _ = relay.server.Serve(listener) }()
	return relay, nil
}
func (r *mediaRelay) close() { _ = r.server.Close(); r.transport.CloseIdleConnections() }
func (r *mediaRelay) local(remote string) string {
	r.mu.Lock()
	defer r.mu.Unlock()
	id := r.ids[remote]
	if id == "" {
		id = randomID(12)
		r.ids[remote] = id
		r.urls[id] = remote
		r.order = append(r.order, id)
		if len(r.order) > 8192 {
			old := r.order[0]
			r.order = r.order[1:]
			delete(r.ids, r.urls[old])
			delete(r.urls, old)
		}
	}
	u, _ := url.Parse(remote)
	extension := path.Ext(u.Path)
	if extension == "" {
		extension = ".ts"
	}
	return r.prefix + id + "/media" + extension
}
func (r *mediaRelay) allowed(u *url.URL) bool {
	return u.Host != "" && u.User == nil && (u.Scheme == "https" || (r.allowHTTP && u.Scheme == "http"))
}
func (r *mediaRelay) serve(w http.ResponseWriter, request *http.Request) {
	prefix, _ := url.Parse(r.prefix)
	if request.Method != "GET" || !strings.HasPrefix(request.URL.Path, prefix.Path) {
		http.NotFound(w, request)
		return
	}
	parts := strings.Split(strings.TrimPrefix(request.URL.Path, prefix.Path), "/")
	r.mu.Lock()
	raw, known := r.urls[parts[0]]
	r.mu.Unlock()
	if !known {
		http.NotFound(w, request)
		return
	}
	target, err := url.Parse(raw)
	if err != nil || !r.allowed(target) {
		http.Error(w, "Media transport not allowed", 403)
		return
	}
	req, err := http.NewRequestWithContext(request.Context(), http.MethodGet, target.String(), nil)
	if err != nil {
		http.Error(w, "Invalid media address", 400)
		return
	}
	req.Header.Set("User-Agent", "SignalTV/0.2")
	if value := request.Header.Get("Range"); value != "" {
		req.Header.Set("Range", value)
	}
	client := &http.Client{Transport: r.transport, CheckRedirect: func(next *http.Request, via []*http.Request) error {
		if len(via) > 5 || !r.allowed(next.URL) {
			return errors.New("media redirect blocked")
		}
		return nil
	}}
	response, err := client.Do(req)
	if err != nil {
		http.Error(w, "Provider connection or transport rejected", 502)
		return
	}
	defer response.Body.Close()
	if response.StatusCode != 200 && response.StatusCode != 206 {
		http.Error(w, "Provider rejected media request", response.StatusCode)
		return
	}
	reader := bufio.NewReader(response.Body)
	signature, _ := reader.Peek(7)
	if string(signature) == "#EXTM3U" {
		raw, err := io.ReadAll(io.LimitReader(reader, (2<<20)+1))
		if err != nil || len(raw) > 2<<20 {
			http.Error(w, "Invalid or oversized playlist", 502)
			return
		}
		rewritten, err := r.rewrite(string(raw), response.Request.URL)
		if err != nil {
			http.Error(w, "Unsupported playlist transport", 502)
			return
		}
		w.Header().Set("Content-Type", "application/vnd.apple.mpegurl")
		_, _ = io.WriteString(w, rewritten)
		return
	}
	for _, key := range []string{"Content-Type", "Content-Range", "Accept-Ranges", "Content-Length"} {
		if v := response.Header.Get(key); v != "" {
			w.Header().Set(key, v)
		}
	}
	w.WriteHeader(response.StatusCode)
	buffer := make([]byte, 32*1024)
	for {
		n, readErr := reader.Read(buffer)
		if n > 0 {
			if _, err = w.Write(buffer[:n]); err != nil {
				return
			}
			if f, ok := w.(http.Flusher); ok {
				f.Flush()
			}
		}
		if readErr != nil {
			return
		}
	}
}
func (r *mediaRelay) rewrite(body string, base *url.URL) (string, error) {
	var rewriteErr error
	resolve := func(value string) string {
		relative, err := url.Parse(value)
		if err != nil {
			rewriteErr = err
			return ""
		}
		absolute := base.ResolveReference(relative)
		if !r.allowed(absolute) {
			rewriteErr = errors.New("unsupported media transport")
			return ""
		}
		return r.local(absolute.String())
	}
	lines := strings.Split(body, "\n")
	for i, line := range lines {
		line = strings.TrimSpace(line)
		if line == "" {
			continue
		}
		if strings.HasPrefix(line, "#") {
			lines[i] = playlistURI.ReplaceAllStringFunc(line, func(match string) string {
				parts := playlistURI.FindStringSubmatch(match)
				return `URI="` + resolve(parts[1]) + `"`
			})
		} else {
			lines[i] = resolve(line)
		}
	}
	return strings.Join(lines, "\n"), rewriteErr
}
