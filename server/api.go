package main

import (
	"context"
	"crypto/sha256"
	"crypto/subtle"
	"embed"
	"encoding/hex"
	"encoding/json"
	"fmt"
	"io/fs"
	"net"
	"net/http"
	"os"
	"path/filepath"
	"strconv"
	"strings"
	"sync"
	"time"
)

//go:embed web/*
var webFiles embed.FS

type Pairing struct {
	ID       string    `json:"id"`
	Code     string    `json:"code"`
	Name     string    `json:"name"`
	Expires  time.Time `json:"expires"`
	secret   string
	token    string
	approved bool
}
type App struct {
	tmdb               *tmdbService
	recordings         string
	recordingLeases    map[string]string
	recordingPlaybacks map[string]*recordingPlayback
	closing            bool
	dvrWake            chan struct{}
	imports            map[string]importStatus
	catalogMu          sync.Mutex
	accountMu          sync.RWMutex
	login              loginState
	mu                 sync.Mutex
	store              *Store
	admin              string
	scratch, ffmpeg    string
	demo               bool
	channels           []Channel
	guide              []Programme
	sessions           map[string]*Session
	pairings           map[string]*Pairing
	lastPair           map[string]time.Time
	syncing            bool
	guideStatus        string
}

func newApp(s *Store, token, scratch, ffmpeg string, demo bool) *App {
	a := &App{store: s, admin: token, scratch: scratch, ffmpeg: ffmpeg, demo: demo, sessions: map[string]*Session{}, pairings: map[string]*Pairing{}, lastPair: map[string]time.Time{}, channels: []Channel{}, guide: []Programme{}, guideStatus: "Not imported"}
	a.recordings = filepath.Join(s.dir, "recordings")
	a.recordingLeases = map[string]string{}
	a.recordingPlaybacks = map[string]*recordingPlayback{}
	a.dvrWake = make(chan struct{}, 1)
	a.loadLogin()
	a.tmdb = newTMDB(s)
	if demo {
		a.channels = append(a.channels, Channel{ID: "demo:1", ProviderID: "demo", StreamID: "1", Name: "Signal Lab · live test", Category: "Diagnostics", EPGID: "demo", CategoryName: "Signal Lab"})
		now := time.Now().Truncate(time.Hour)
		a.guide = append(a.guide, Programme{ChannelID: "demo:1", Title: "Live test pattern and audio tone", Start: now, End: now.Add(24 * time.Hour)})
	}
	providers := s.snapshot().Providers
	a.restoreCachedCatalog(providers)
	if len(providers) > 0 {
		go a.restoreCatalog(providers)
	}
	return a
}
func (a *App) restoreCatalog(providers []Provider) {
	a.refreshChannels(context.Background())
	if len(providers) > 0 {
		ctx, cancel := context.WithTimeout(context.Background(), 3*time.Minute)
		defer cancel()
		a.syncGuide(ctx)
	}
}
func tokenHash(t string) string { h := sha256.Sum256([]byte(t)); return hex.EncodeToString(h[:]) }
func (a *App) identity(r *http.Request) (string, bool) {
	t := strings.TrimPrefix(r.Header.Get("Authorization"), "Bearer ")
	if t == "" {
		return "", false
	}
	if a.adminIdentity(t) {
		return "admin", true
	}
	h := tokenHash(t)
	for _, d := range a.store.snapshot().Devices {
		if subtle.ConstantTimeCompare([]byte(h), []byte(d.TokenHash)) == 1 {
			return d.ID, false
		}
	}
	return "", false
}
func send(w http.ResponseWriter, status int, v any) {
	w.Header().Set("Content-Type", "application/json")
	w.WriteHeader(status)
	_ = json.NewEncoder(w).Encode(v)
}
func fail(w http.ResponseWriter, status int, message string) {
	send(w, status, map[string]string{"error": message})
}
func decode(w http.ResponseWriter, r *http.Request, v any) bool {
	r.Body = http.MaxBytesReader(w, r.Body, 16384)
	d := json.NewDecoder(r.Body)
	d.DisallowUnknownFields()
	if d.Decode(v) != nil {
		fail(w, 400, "Invalid request")
		return false
	}
	return true
}
func (a *App) auth(admin bool, h http.HandlerFunc) http.HandlerFunc {
	return func(w http.ResponseWriter, r *http.Request) {
		id, isAdmin := a.identity(r)
		if id == "" {
			fail(w, 401, "Sign in required")
			return
		}
		if admin && !isAdmin {
			fail(w, 403, "Administrator access required")
			return
		}
		h(w, r)
	}
}
func (a *App) routes() http.Handler {
	m := http.NewServeMux()
	a.loginRoutes(m)
	a.tmdbRoutes(m)
	a.recordingRoutes(m)
	a.searchRoutes(m)
	a.interestRoutes(m)
	a.timeshiftRoutes(m)
	a.appearanceRoutes(m)
	a.recordingPlaybackRoutes(m)
	a.groupRoutes(m)
	m.HandleFunc("GET /api/v1/diagnostics/runtime", a.auth(true, a.runtimeMetrics))
	m.HandleFunc("GET /healthz", func(w http.ResponseWriter, r *http.Request) {
		send(w, 200, map[string]string{"status": "ok", "version": "0.17.0"})
	})
	m.HandleFunc("GET /api/v1/capabilities", a.auth(false, func(w http.ResponseWriter, r *http.Request) {
		send(w, 200, map[string]any{"apiVersion": 1, "version": "0.17.0", "demo": a.demo, "profiles": []string{"remux", "browser", "data-saver"}, "limitations": []string{"recovery re-prepares client on output generation changes", "one ingest per session", "no GPU transcoding yet"}})
	}))
	m.HandleFunc("GET /api/v1/channels", a.auth(false, func(w http.ResponseWriter, r *http.Request) {
		a.mu.Lock()
		defer a.mu.Unlock()
		send(w, 200, a.channels)
	}))
	m.HandleFunc("GET /api/v1/guide", a.auth(false, func(w http.ResponseWriter, r *http.Request) {
		a.mu.Lock()
		defer a.mu.Unlock()
		out := []Programme{}
		channel := r.URL.Query().Get("channelId")
		ids := map[string]bool{}
		for _, id := range strings.Split(r.URL.Query().Get("channelIds"), ",") {
			if id != "" {
				ids[id] = true
			}
		}
		now := time.Now()
		from := now.Add(-time.Hour)
		to := now.Add(24 * time.Hour)
		if parsed, err := time.Parse(time.RFC3339, r.URL.Query().Get("from")); err == nil {
			from = parsed
		}
		if parsed, err := time.Parse(time.RFC3339, r.URL.Query().Get("to")); err == nil && parsed.After(from) && parsed.Sub(from) <= 24*time.Hour {
			to = parsed
		}
		for _, p := range a.guide {
			if (channel == "" || p.ChannelID == channel) && (len(ids) == 0 || ids[p.ChannelID]) && p.End.After(from) && p.Start.Before(to) {
				out = append(out, p)
				if len(out) >= 5000 {
					break
				}
			}
		}
		send(w, 200, map[string]any{"programmes": out, "status": a.guideStatus})
	}))
	m.HandleFunc("GET /api/v1/providers", a.auth(true, func(w http.ResponseWriter, r *http.Request) {
		out := []map[string]any{}
		for _, p := range a.store.snapshot().Providers {
			out = append(out, map[string]any{"id": p.ID, "name": p.Name, "format": p.Format, "maxConnections": p.MaxConnections, "allowHttp": p.AllowHTTP, "httpFallback": p.HTTPFallback, "url": p.URL, "username": p.Username, "imports": a.providerImportStatus(p.ID)})
		}
		send(w, 200, out)
	}))
	m.HandleFunc("POST /api/v1/providers", a.auth(true, a.saveProvider))
	a.accountRoutes(m)
	m.HandleFunc("PUT /api/v1/providers/{id}/transport", a.auth(true, func(w http.ResponseWriter, r *http.Request) {
		a.catalogMu.Lock()
		defer a.catalogMu.Unlock()
		var b struct {
			AllowHTTP bool `json:"allowHttp"`
		}
		if !decode(w, r, &b) {
			return
		}
		found := false
		err := a.store.update(func(s *State) {
			for i := range s.Providers {
				if s.Providers[i].ID == r.PathValue("id") {
					s.Providers[i].AllowHTTP = b.AllowHTTP
					if !b.AllowHTTP {
						s.Providers[i].HTTPFallback = false
					}
					found = true
				}
			}
		})
		if err != nil {
			fail(w, 500, "Could not update provider")
			return
		}
		if !found {
			fail(w, 404, "Provider not found")
			return
		}
		send(w, 200, map[string]bool{"updated": true})
	}))
	m.HandleFunc("POST /api/v1/guide/refresh", a.auth(false, a.refreshGuide))
	m.HandleFunc("POST /api/v1/pairing/requests", a.requestPairing)
	m.HandleFunc("POST /api/v1/pairing/poll", a.pollPairing)
	m.HandleFunc("GET /api/v1/pairing/requests", a.auth(true, func(w http.ResponseWriter, r *http.Request) {
		a.mu.Lock()
		defer a.mu.Unlock()
		out := []Pairing{}
		for _, p := range a.pairings {
			if !p.approved && time.Now().Before(p.Expires) {
				out = append(out, *p)
			}
		}
		send(w, 200, out)
	}))
	m.HandleFunc("POST /api/v1/pairing/approve", a.auth(true, a.approvePairing))
	m.HandleFunc("GET /api/v1/devices", a.auth(true, func(w http.ResponseWriter, r *http.Request) {
		out := []map[string]string{}
		for _, d := range a.store.snapshot().Devices {
			out = append(out, map[string]string{"id": d.ID, "name": d.Name})
		}
		send(w, 200, out)
	}))
	m.HandleFunc("DELETE /api/v1/devices/{id}", a.auth(true, func(w http.ResponseWriter, r *http.Request) {
		id := r.PathValue("id")
		if err := a.store.update(func(s *State) {
			out := []Device{}
			for _, d := range s.Devices {
				if d.ID != id {
					out = append(out, d)
				}
			}
			s.Devices = out
		}); err != nil {
			fail(w, 500, "Could not revoke device")
			return
		}
		a.mu.Lock()
		for key, s := range a.sessions {
			if s.owner == id {
				s.cancel()
				<-s.done
				delete(a.sessions, key)
			}
		}
		a.mu.Unlock()
		send(w, 200, map[string]bool{"revoked": true})
	}))
	m.HandleFunc("POST /api/v1/playback/sessions", a.auth(false, a.createSession))
	m.HandleFunc("GET /api/v1/playback/sessions", a.auth(false, func(w http.ResponseWriter, r *http.Request) {
		owner, admin := a.identity(r)
		a.mu.Lock()
		defer a.mu.Unlock()
		out := []SessionView{}
		for _, s := range a.sessions {
			if admin || s.owner == owner {
				out = append(out, s.view())
			}
		}
		send(w, 200, out)
	}))
	m.HandleFunc("GET /api/v1/playback/sessions/{id}", a.auth(false, func(w http.ResponseWriter, r *http.Request) {
		s := a.getSession(w, r)
		if s != nil {
			send(w, 200, s.view())
		}
	}))
	m.HandleFunc("POST /api/v1/playback/sessions/{id}/heartbeat", a.auth(false, func(w http.ResponseWriter, r *http.Request) {
		s := a.getSession(w, r)
		if s == nil {
			return
		}
		s.mu.Lock()
		s.lease = time.Now().Add(90 * time.Second)
		s.mu.Unlock()
		send(w, 200, s.view())
	}))
	m.HandleFunc("DELETE /api/v1/playback/sessions/{id}", a.auth(false, func(w http.ResponseWriter, r *http.Request) {
		s := a.getSession(w, r)
		if s == nil {
			return
		}
		s.cancel()
		<-s.done
		a.mu.Lock()
		delete(a.sessions, s.ID)
		a.mu.Unlock()
		send(w, 200, map[string]bool{"stopped": true})
	}))
	m.HandleFunc("POST /api/v1/playback/sessions/{id}/interrupt", a.auth(true, func(w http.ResponseWriter, r *http.Request) {
		s := a.getSession(w, r)
		if s == nil {
			return
		}
		if !a.demo || s.provider != "demo" {
			fail(w, 403, "Fault injection is limited to the demo channel")
			return
		}
		s.mu.Lock()
		if s.workerCancel != nil {
			s.workerCancel()
		}
		s.mu.Unlock()
		send(w, 200, map[string]bool{"interrupted": true})
	}))
	m.HandleFunc("GET /media/{id}/{generation}/{name}", a.auth(false, a.serveMedia))
	assets, _ := fs.Sub(webFiles, "web")
	m.Handle("/", http.FileServer(http.FS(assets)))
	return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.Header().Set("X-Content-Type-Options", "nosniff")
		w.Header().Set("Referrer-Policy", "no-referrer")
		w.Header().Set("Cache-Control", "no-store")
		w.Header().Set("Content-Security-Policy", "default-src 'self'; script-src 'self'; style-src 'self' 'unsafe-inline'; connect-src 'self'; media-src 'self' blob:; worker-src 'self' blob:; img-src 'self' data: https:; frame-ancestors 'none'")
		m.ServeHTTP(w, r)
	})
}
func (a *App) refreshGuide(w http.ResponseWriter, r *http.Request) {
	send(w, 200, a.syncGuide(r.Context()))
}
func (a *App) syncGuide(ctx context.Context, only ...string) map[string]string {
	a.catalogMu.Lock()
	defer a.catalogMu.Unlock()
	a.mu.Lock()
	if a.syncing {
		a.mu.Unlock()
		return map[string]string{"status": "Guide refresh already running"}
	}
	a.syncing = true
	channels := append([]Channel{}, a.channels...)
	a.mu.Unlock()
	defer func() { a.mu.Lock(); a.syncing = false; a.mu.Unlock() }()
	results := map[string]string{}
	for _, p := range a.store.snapshot().Providers {
		if len(only) > 0 && p.ID != only[0] {
			continue
		}
		body, err := providerGet(ctx, p, "xmltv.php", "")
		if err != nil {
			results[p.ID] = err.Error()
			a.recordImport(p.ID, "epg", err)
			continue
		}
		providerChannels := []Channel{}
		for _, c := range channels {
			if c.ProviderID == p.ID {
				providerChannels = append(providerChannels, c)
			}
		}
		programmes, err := parseEPG(body, providerChannels, time.Now())
		body.Close()
		if err != nil {
			results[p.ID] = err.Error()
			a.recordImport(p.ID, "epg", err)
			continue
		}
		a.mu.Lock()
		kept := []Programme{}
		for _, v := range a.guide {
			if !strings.HasPrefix(v.ChannelID, p.ID+":") {
				kept = append(kept, v)
			}
		}
		a.guide = append(kept, programmes...)
		a.guideStatus = "Updated " + time.Now().UTC().Format(time.RFC3339)
		a.mu.Unlock()
		err = a.store.saveCatalog(p.ID, nil, programmes)
		if err != nil {
			err = fmt.Errorf("Guide loaded but cache could not be saved; check config storage")
		}
		a.recordImport(p.ID, "epg", err)
		results[p.ID] = fmt.Sprintf("Imported %d programmes", len(programmes))
	}
	return results
}
func (a *App) requestPairing(w http.ResponseWriter, r *http.Request) {
	var body struct {
		Name string `json:"name"`
	}
	if !decode(w, r, &body) {
		return
	}
	if len(body.Name) < 1 || len(body.Name) > 80 {
		fail(w, 400, "Device name must be 1–80 characters")
		return
	}
	a.mu.Lock()
	defer a.mu.Unlock()
	host, _, _ := net.SplitHostPort(r.RemoteAddr)
	if last := a.lastPair[host]; time.Since(last) < 10*time.Second {
		w.Header().Set("Retry-After", "10")
		fail(w, 429, "Wait 10 seconds before requesting another code")
		return
	}
	for key, last := range a.lastPair {
		if time.Since(last) > time.Minute {
			delete(a.lastPair, key)
		}
	}
	if len(a.lastPair) >= 1024 {
		fail(w, 429, "Pairing is busy; try again later")
		return
	}
	a.lastPair[host] = time.Now()
	if len(a.pairings) >= 32 {
		fail(w, 429, "Pairing capacity reached; wait for expired requests")
		return
	}
	secret := randomID(32)
	p := &Pairing{ID: randomID(16), Code: strings.ToUpper(randomID(3)), Name: body.Name, Expires: time.Now().Add(5 * time.Minute), secret: tokenHash(secret)}
	a.pairings[p.ID] = p
	send(w, 201, map[string]any{"id": p.ID, "code": p.Code, "deviceSecret": secret, "expires": p.Expires})
}
func (a *App) pollPairing(w http.ResponseWriter, r *http.Request) {
	var b struct {
		ID     string `json:"id"`
		Secret string `json:"deviceSecret"`
	}
	if !decode(w, r, &b) {
		return
	}
	a.mu.Lock()
	defer a.mu.Unlock()
	p := a.pairings[b.ID]
	if p == nil || time.Now().After(p.Expires) || subtle.ConstantTimeCompare([]byte(p.secret), []byte(tokenHash(b.Secret))) != 1 {
		fail(w, 404, "Pairing request expired or invalid")
		return
	}
	if !p.approved {
		send(w, 202, map[string]string{"state": "pending"})
		return
	}
	send(w, 200, map[string]string{"token": p.token, "state": "approved"})
}
func (a *App) approvePairing(w http.ResponseWriter, r *http.Request) {
	var b struct {
		ID   string `json:"id"`
		Code string `json:"code"`
	}
	if !decode(w, r, &b) {
		return
	}
	a.mu.Lock()
	defer a.mu.Unlock()
	p := a.pairings[b.ID]
	if p == nil || p.approved || time.Now().After(p.Expires) || !strings.EqualFold(p.Code, b.Code) {
		fail(w, 400, "Pairing code invalid or expired")
		return
	}
	token := randomID(32)
	if err := a.store.update(func(s *State) { s.Devices = append(s.Devices, Device{p.ID, p.Name, tokenHash(token)}) }); err != nil {
		fail(w, 500, "Could not persist device")
		return
	}
	p.token = token
	p.approved = true
	send(w, 200, map[string]bool{"approved": true})
}
func (a *App) createSession(w http.ResponseWriter, r *http.Request) {
	if !a.accountMu.TryRLock() {
		fail(w, 409, "Account update in progress; try again shortly")
		return
	}
	defer a.accountMu.RUnlock()
	var b struct {
		ChannelID string `json:"channelId"`
		Profile   string `json:"profile"`
	}
	if !decode(w, r, &b) {
		return
	}
	a.mu.Lock()
	var c Channel
	for _, v := range a.channels {
		if v.ID == b.ChannelID {
			c = v
			break
		}
	}
	a.mu.Unlock()
	if c.ID == "" {
		fail(w, 404, "Channel not found")
		return
	}
	p := Provider{ID: "demo", MaxConnections: 2}
	if c.ProviderID != "demo" {
		p = Provider{}
		for _, v := range a.store.snapshot().Providers {
			if v.ID == c.ProviderID {
				p = v
				break
			}
		}
		if p.ID == "" {
			fail(w, 404, "Provider not found")
			return
		}
	}
	owner, _ := a.identity(r)
	if b.Profile != "" && b.Profile != "remux" && b.Profile != "browser" && b.Profile != "data-saver" {
		fail(w, 400, "Unknown playback profile")
		return
	}
	s, err := a.startSession(owner, c, p, b.Profile)
	if err != nil {
		fail(w, 409, err.Error())
		return
	}
	send(w, 201, s.view())
}
func (a *App) getSession(w http.ResponseWriter, r *http.Request) *Session {
	owner, admin := a.identity(r)
	a.mu.Lock()
	s := a.sessions[r.PathValue("id")]
	a.mu.Unlock()
	if s == nil || (!admin && s.owner != owner) {
		fail(w, 404, "Session not found")
		return nil
	}
	return s
}
func (a *App) serveMedia(w http.ResponseWriter, r *http.Request) {
	s := a.getSession(w, r)
	if s == nil {
		return
	}
	gen, err := strconv.Atoi(r.PathValue("generation"))
	if err != nil || gen < 1 || !validMediaName(r.PathValue("name")) {
		fail(w, 404, "Media not found")
		return
	}
	path := filepath.Join(a.scratch, s.ID, strconv.Itoa(gen), r.PathValue("name"))
	if _, err = os.Stat(path); err != nil {
		fail(w, 404, "Media not ready")
		return
	}
	if r.PathValue("name") == "live.m3u8" {
		w.Header().Set("Content-Type", "application/vnd.apple.mpegurl")
	} else {
		w.Header().Set("Content-Type", "video/mp2t")
	}
	http.ServeFile(w, r, path)
}
