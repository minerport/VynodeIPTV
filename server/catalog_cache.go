package main

import (
	"context"
	"crypto/rand"
	"encoding/json"
	"errors"
	"os"
	"path/filepath"
	"strings"
	"time"
)

// Metadata is encrypted separately so preference edits do not rewrite a large guide.
type catalogSnapshot struct {
	Channels []Channel   `json:"channels"`
	Guide    []Programme `json:"guide"`
}

func (s *Store) catalogPath(id string) string {
	return filepath.Join(s.dir, "catalog-"+tokenHash(id)+".enc")
}
func (s *Store) readCatalog(id string) (catalogSnapshot, error) {
	s.mu.Lock()
	defer s.mu.Unlock()
	return s.readCatalogLocked(id)
}
func (s *Store) readCatalogLocked(id string) (catalogSnapshot, error) {
	var result catalogSnapshot
	b, err := os.ReadFile(s.catalogPath(id))
	if err != nil {
		return result, err
	}
	if len(b) < s.aead.NonceSize() {
		return result, errors.New("Invalid cached catalog")
	}
	plain, err := s.aead.Open(nil, b[:s.aead.NonceSize()], b[s.aead.NonceSize():], []byte("catalog-v1:"+id))
	if err != nil {
		return result, errors.New("Could not unlock cached catalog")
	}
	err = json.Unmarshal(plain, &result)
	return result, err
}
func (s *Store) saveCatalog(id string, channels []Channel, guide []Programme) error {
	s.mu.Lock()
	defer s.mu.Unlock()
	cached, _ := s.readCatalogLocked(id)
	if channels != nil {
		cached.Channels = channels
	}
	if guide != nil {
		cached.Guide = guide
	}
	plain, err := json.Marshal(cached)
	if err != nil {
		return err
	}
	nonce := make([]byte, s.aead.NonceSize())
	if _, err = rand.Read(nonce); err != nil {
		return err
	}
	encrypted := s.aead.Seal(nonce, nonce, plain, []byte("catalog-v1:"+id))
	tmp, err := os.CreateTemp(s.dir, "catalog-*.tmp")
	if err != nil {
		return err
	}
	name := tmp.Name()
	defer os.Remove(name)
	if _, err = tmp.Write(encrypted); err == nil {
		err = tmp.Sync()
	}
	closeErr := tmp.Close()
	if err == nil {
		err = closeErr
	}
	if err != nil {
		return err
	}
	return os.Rename(name, s.catalogPath(id))
}
func (a *App) restoreCachedCatalog(providers []Provider) {
	for _, p := range providers {
		cached, err := a.store.readCatalog(p.ID)
		if err != nil {
			continue
		}
		a.channels = append(a.channels, cached.Channels...)
		a.guide = append(a.guide, cached.Guide...)
		if len(cached.Guide) > 0 {
			a.guideStatus = "Saved guide restored; checking provider in background"
		}
	}
}

type importStatus struct {
	Message   string    `json:"message"`
	NextRetry time.Time `json:"nextRetry"`
	Failures  int       `json:"-"`
}

func (a *App) recordImport(id, kind string, err error) {
	a.mu.Lock()
	defer a.mu.Unlock()
	if a.imports == nil {
		a.imports = map[string]importStatus{}
	}
	key := id + "/" + kind
	if err == nil {
		delete(a.imports, key)
		if len(a.imports) == 0 && strings.HasSuffix(a.guideStatus, "Saved listings retained.") {
			a.guideStatus = "Provider reconnected; listings available"
		}
		return
	}
	state := a.imports[key]
	state.Failures++
	delay := time.Minute * time.Duration(1<<min(state.Failures-1, 5))
	state.Message = err.Error()
	a.guideStatus = err.Error() + ". Saved listings retained."
	state.NextRetry = time.Now().Add(delay)
	a.imports[key] = state
}
func (a *App) retryImports(ctx context.Context, now time.Time) {
	for _, p := range a.store.snapshot().Providers {
		for _, kind := range []string{"channels", "epg"} {
			a.mu.Lock()
			state, failed := a.imports[p.ID+"/"+kind]
			a.mu.Unlock()
			if failed && !now.Before(state.NextRetry) {
				if kind == "channels" {
					a.refreshChannels(ctx, p.ID)
				} else {
					a.syncGuide(ctx, p.ID)
				}
			}
		}
	}
}
func (a *App) providerImportStatus(id string) map[string]importStatus {
	a.mu.Lock()
	defer a.mu.Unlock()
	result := map[string]importStatus{}
	for _, kind := range []string{"channels", "epg"} {
		if s, ok := a.imports[id+"/"+kind]; ok {
			result[kind] = s
		}
	}
	return result
}
