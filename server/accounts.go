package main

import (
	"context"
	"fmt"
	"net/http"
	"strings"
	"time"
)

func providerIdentity(p Provider) string {
	return tokenHash(strings.TrimRight(p.URL, "/") + "|" + p.Username)
}
func (a *App) providerBusy(id string) bool {
	a.mu.Lock()
	defer a.mu.Unlock()
	for _, provider := range a.recordingLeases {
		if provider == id {
			return true
		}
	}
	for _, s := range a.sessions {
		if s.provider == id {
			return true
		}
	}
	return false
}
func (a *App) replaceCatalog(id string, channels []Channel) {
	a.mu.Lock()
	defer a.mu.Unlock()
	kept := []Channel{}
	for _, c := range a.channels {
		if c.ProviderID != id {
			kept = append(kept, c)
		}
	}
	a.channels = append(kept, channels...)
}
func (a *App) saveProvider(w http.ResponseWriter, r *http.Request) {
	a.catalogMu.Lock()
	defer a.catalogMu.Unlock()
	a.accountMu.Lock()
	defer a.accountMu.Unlock()
	var p Provider
	if !decode(w, r, &p) {
		return
	}
	p.URL = strings.TrimRight(p.URL, "/")
	p.ID = r.PathValue("id")
	state := a.store.snapshot()
	var old *Provider
	for _, v := range state.Providers {
		v := v
		if v.ID == p.ID {
			old = &v
		}
		if providerIdentity(v) == providerIdentity(p) && v.ID != p.ID {
			fail(w, 409, "Account already saved; edit the existing account")
			return
		}
	}
	if p.ID != "" {
		if old == nil {
			fail(w, 404, "Provider not found")
			return
		}
		if a.providerBusy(p.ID) {
			fail(w, 409, "Stop this provider's active streams before editing its account")
			return
		}
		if p.Password == "" {
			p.Password = old.Password
		}
	} else {
		p.ID = state.ProviderIDs[providerIdentity(p)]
		if p.ID == "" {
			p.ID = providerIdentity(p)[:16]
		}
		for _, existing := range state.Providers {
			if existing.ID == p.ID {
				p.ID = randomID(16)
				break
			}
		}
		for key, id := range state.ProviderIDs {
			if key != providerIdentity(p) && id == p.ID {
				p.ID = randomID(16)
				break
			}
		}
	}
	if err := validateProvider(p); err != nil {
		fail(w, 400, err.Error())
		return
	}
	ctx, cancel := context.WithTimeout(r.Context(), 60*time.Second)
	defer cancel()
	channels, err := loadChannels(ctx, p)
	if err != nil {
		fail(w, 422, err.Error())
		return
	}
	err = a.store.update(func(s *State) {
		if s.ProviderIDs == nil {
			s.ProviderIDs = map[string]string{}
		}
		if old != nil && providerIdentity(*old) != providerIdentity(p) {
			for key, id := range s.ProviderIDs {
				if id == p.ID {
					delete(s.ProviderIDs, key)
				}
			}
		}
		s.ProviderIDs[providerIdentity(p)] = p.ID
		kept := []Provider{}
		for _, v := range s.Providers {
			if v.ID != p.ID {
				kept = append(kept, v)
			}
		}
		s.Providers = append(kept, p)
	})
	if err != nil {
		fail(w, 500, "Could not save encrypted account")
		return
	}
	a.replaceCatalog(p.ID, channels)
	cacheErr := a.store.saveCatalog(p.ID, channels, nil)
	if cacheErr != nil {
		cacheErr = fmt.Errorf("Channels loaded but cache could not be saved; check config storage")
	}
	a.recordImport(p.ID, "channels", cacheErr)
	status := 200
	if old == nil {
		status = 201
	}
	send(w, status, map[string]any{"id": p.ID, "channels": len(channels)})
}
func (a *App) refreshChannels(ctx context.Context, only ...string) map[string]string {
	a.catalogMu.Lock()
	defer a.catalogMu.Unlock()
	result := map[string]string{}
	for _, p := range a.store.snapshot().Providers {
		if len(only) > 0 && p.ID != only[0] {
			continue
		}
		cctx, cancel := context.WithTimeout(ctx, 60*time.Second)
		channels, err := loadChannels(cctx, p)
		cancel()
		if err != nil {
			result[p.ID] = err.Error()
			a.recordImport(p.ID, "channels", err)
			continue
		}
		a.replaceCatalog(p.ID, channels)
		cacheErr := a.store.saveCatalog(p.ID, channels, nil)
		if cacheErr != nil {
			cacheErr = fmt.Errorf("Channels loaded but cache could not be saved; check config storage")
		}
		a.recordImport(p.ID, "channels", cacheErr)
		result[p.ID] = fmt.Sprintf("Imported %d channels", len(channels))
	}
	return result
}
func (a *App) accountRoutes(m *http.ServeMux) {
	m.HandleFunc("PUT /api/v1/providers/{id}", a.auth(true, a.saveProvider))
	m.HandleFunc("DELETE /api/v1/providers/{id}", a.auth(true, func(w http.ResponseWriter, r *http.Request) {
		a.catalogMu.Lock()
		defer a.catalogMu.Unlock()
		a.accountMu.Lock()
		defer a.accountMu.Unlock()
		id := r.PathValue("id")
		if a.providerBusy(id) {
			fail(w, 409, "Stop this provider's active streams before deleting its login")
			return
		}
		found := false
		err := a.store.update(func(s *State) {
			if s.ProviderIDs == nil {
				s.ProviderIDs = map[string]string{}
			}
			kept := []Provider{}
			for _, p := range s.Providers {
				if p.ID == id {
					found = true
					s.ProviderIDs[providerIdentity(p)] = p.ID
				} else {
					kept = append(kept, p)
				}
			}
			s.Providers = kept
		})
		if err != nil {
			fail(w, 500, "Could not remove login")
			return
		}
		if !found {
			fail(w, 404, "Provider not found")
			return
		}
		a.replaceCatalog(id, nil)
		a.mu.Lock()
		kept := []Programme{}
		for _, p := range a.guide {
			if !strings.HasPrefix(p.ChannelID, id+":") {
				kept = append(kept, p)
			}
		}
		a.guide = kept
		a.mu.Unlock()
		send(w, 200, map[string]bool{"deleted": true, "preferencesRetained": true})
	}))
	m.HandleFunc("POST /api/v1/channels/refresh", a.auth(false, func(w http.ResponseWriter, r *http.Request) { send(w, 200, a.refreshChannels(r.Context())) }))
	m.HandleFunc("GET /api/v1/refresh/settings", a.auth(true, func(w http.ResponseWriter, r *http.Request) { send(w, 200, a.store.snapshot().Refresh) }))
	m.HandleFunc("PUT /api/v1/refresh/settings", a.auth(true, func(w http.ResponseWriter, r *http.Request) {
		var b RefreshSettings
		if !decode(w, r, &b) {
			return
		}
		err := a.store.update(func(s *State) { s.Refresh.ChannelsDaily = b.ChannelsDaily; s.Refresh.EPGDaily = b.EPGDaily })
		if err != nil {
			fail(w, 500, "Could not save refresh settings")
			return
		}
		send(w, 200, a.store.snapshot().Refresh)
	}))
}
func dailyDue(enabled bool, last int64, now time.Time) bool {
	return enabled && (last == 0 || now.Unix()-last >= 86400)
}
func (a *App) scheduledRefresh(ctx context.Context) {
	ticker := time.NewTicker(time.Minute)
	defer ticker.Stop()
	var retryChannels, retryEPG time.Time
	for {
		select {
		case <-ctx.Done():
			return
		case now := <-ticker.C:
			a.retryImports(ctx, now)
			settings := a.store.snapshot().Refresh
			if dailyDue(settings.ChannelsDaily, settings.LastChannels, now) && !now.Before(retryChannels) {
				if refreshSucceeded(a.refreshChannels(ctx)) {
					_ = a.store.update(func(s *State) { s.Refresh.LastChannels = now.Unix() })
				}
				retryChannels = now.Add(time.Hour)
			}
			if dailyDue(settings.EPGDaily, settings.LastEPG, now) && !now.Before(retryEPG) {
				if refreshSucceeded(a.syncGuide(ctx)) {
					_ = a.store.update(func(s *State) { s.Refresh.LastEPG = now.Unix() })
				}
				retryEPG = now.Add(time.Hour)
			}
		}
	}
}

func refreshSucceeded(results map[string]string) bool {
	for _, v := range results {
		if !strings.HasPrefix(v, "Imported ") {
			return false
		}
	}
	return true
}
