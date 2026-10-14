package main

import (
	"crypto/aes"
	"crypto/cipher"
	"crypto/rand"
	"encoding/json"
	"errors"
	"os"
	"path/filepath"
	"sync"
)

type Provider struct {
	HTTPFallback   bool   `json:"httpFallback"`
	ID             string `json:"id"`
	Name           string `json:"name"`
	URL            string `json:"url"`
	Username       string `json:"username"`
	Password       string `json:"password"`
	Format         string `json:"format"`
	MaxConnections int    `json:"maxConnections"`
	AllowHTTP      bool   `json:"allowHttp"`
}
type Device struct {
	ID        string `json:"id"`
	Name      string `json:"name"`
	TokenHash string `json:"tokenHash"`
}
type RefreshSettings struct {
	ChannelsDaily bool  `json:"channelsDaily"`
	EPGDaily      bool  `json:"epgDaily"`
	LastChannels  int64 `json:"lastChannels"`
	LastEPG       int64 `json:"lastEpg"`
}
type State struct {
	Recordings  []Recording       `json:"recordings,omitempty"`
	Refresh     RefreshSettings   `json:"refresh"`
	ProviderIDs map[string]string `json:"providerIds,omitempty"`

	Providers   []Provider                  `json:"providers"`
	Devices     []Device                    `json:"devices"`
	Groups      []CustomGroup               `json:"groups"`
	Preferences map[string]GuidePreferences `json:"preferences,omitempty"`
}
type CustomGroup struct {
	ID         string   `json:"id"`
	Name       string   `json:"name"`
	ChannelIDs []string `json:"channelIds"`
	Owner      string   `json:"owner,omitempty"`
}

func cloneGroups(groups []CustomGroup) []CustomGroup {
	out := make([]CustomGroup, len(groups))
	for i, g := range groups {
		out[i] = g
		out[i].ChannelIDs = append([]string{}, g.ChannelIDs...)
	}
	return out
}

type Store struct {
	mu    sync.Mutex
	dir   string
	aead  cipher.AEAD
	state State
}

func openStore(dir string) (*Store, error) {
	keyPath := filepath.Join(dir, "encryption.key")
	key, err := os.ReadFile(keyPath)
	if os.IsNotExist(err) {
		if _, stateErr := os.Stat(filepath.Join(dir, "state.enc")); stateErr == nil {
			return nil, errors.New("encryption key missing; restore it with state backup")
		}
		key = make([]byte, 32)
		if _, err = rand.Read(key); err == nil {
			err = os.WriteFile(keyPath, key, 0600)
		}
	}
	if err != nil {
		return nil, err
	}
	block, err := aes.NewCipher(key)
	if err != nil {
		return nil, err
	}
	aead, err := cipher.NewGCM(block)
	if err != nil {
		return nil, err
	}
	s := &Store{dir: dir, aead: aead}
	encrypted, err := os.ReadFile(filepath.Join(dir, "state.enc"))
	if os.IsNotExist(err) {
		return s, nil
	}
	if err != nil {
		return nil, err
	}
	if len(encrypted) < aead.NonceSize() {
		return nil, errors.New("invalid state")
	}
	plain, err := aead.Open(nil, encrypted[:aead.NonceSize()], encrypted[aead.NonceSize():], []byte("iptv-state-v1"))
	if err != nil {
		return nil, err
	}
	err = json.Unmarshal(plain, &s.state)
	return s, err
}
func (s *Store) snapshot() State {
	s.mu.Lock()
	defer s.mu.Unlock()
	return State{Recordings: append([]Recording{}, s.state.Recordings...), Refresh: s.state.Refresh, ProviderIDs: cloneIDs(s.state.ProviderIDs), Providers: append([]Provider{}, s.state.Providers...), Devices: append([]Device{}, s.state.Devices...), Groups: cloneGroups(s.state.Groups), Preferences: clonePreferences(s.state.Preferences)}
}
func (s *Store) update(fn func(*State)) error {
	s.mu.Lock()
	defer s.mu.Unlock()
	next := State{Recordings: append([]Recording{}, s.state.Recordings...), Refresh: s.state.Refresh, ProviderIDs: cloneIDs(s.state.ProviderIDs), Providers: append([]Provider{}, s.state.Providers...), Devices: append([]Device{}, s.state.Devices...), Groups: cloneGroups(s.state.Groups), Preferences: clonePreferences(s.state.Preferences)}
	fn(&next)
	plain, err := json.Marshal(next)
	if err != nil {
		return err
	}
	nonce := make([]byte, s.aead.NonceSize())
	if _, err = rand.Read(nonce); err != nil {
		return err
	}
	encrypted := s.aead.Seal(nonce, nonce, plain, []byte("iptv-state-v1"))
	tmp := filepath.Join(s.dir, "state.tmp")
	f, err := os.OpenFile(tmp, os.O_CREATE|os.O_TRUNC|os.O_WRONLY, 0600)
	if err != nil {
		return err
	}
	if _, err = f.Write(encrypted); err == nil {
		err = f.Sync()
	}
	closeErr := f.Close()
	if err == nil {
		err = closeErr
	}
	if err != nil {
		return err
	}
	if err = os.Rename(tmp, filepath.Join(s.dir, "state.enc")); err != nil {
		return err
	}
	s.state = next
	return nil
}

func cloneIDs(source map[string]string) map[string]string {
	out := map[string]string{}
	for k, v := range source {
		out[k] = v
	}
	return out
}
