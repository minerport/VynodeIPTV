package main

import (
	"crypto/pbkdf2"
	"crypto/sha256"
	"crypto/subtle"
	"encoding/hex"
	"encoding/json"
	"mime"
	"net/http"
	"os"
	"path/filepath"
	"strings"
	"sync"
	"time"
)

type adminAccount struct {
	Username string `json:"username"`
	Salt     string `json:"salt"`
	Hash     string `json:"hash"`
}
type loginState struct {
	mu       sync.Mutex
	account  *adminAccount
	broken   bool
	sessions map[string]time.Time
	attempts int
	window   time.Time
}

func (a *App) loadLogin() {
	a.login.sessions = map[string]time.Time{}
	b, err := os.ReadFile(filepath.Join(a.store.dir, "admin-login.json"))
	if os.IsNotExist(err) {
		return
	}
	var account adminAccount
	if err != nil || json.Unmarshal(b, &account) != nil || len(account.Salt) != 32 || len(account.Hash) != 64 || account.Username == "" {
		a.login.broken = true
		return
	}
	if _, err := hex.DecodeString(account.Hash); err != nil {
		a.login.broken = true
		return
	}
	if _, err := hex.DecodeString(account.Salt); err != nil {
		a.login.broken = true
		return
	}
	a.login.account = &account
}

func passwordHash(password, salt string) string {
	b, _ := hex.DecodeString(salt)
	key, err := pbkdf2.Key(sha256.New, password, b, 600000, 32)
	if err != nil {
		return ""
	}
	return hex.EncodeToString(key)
}

func (a *App) adminIdentity(token string) bool {
	a.login.mu.Lock()
	defer a.login.mu.Unlock()
	if a.login.broken {
		return false
	}
	if a.login.account == nil {
		return subtle.ConstantTimeCompare([]byte(token), []byte(a.admin)) == 1
	}
	expires, ok := a.login.sessions[tokenHash(token)]
	if ok && time.Now().Before(expires) {
		return true
	}
	delete(a.login.sessions, tokenHash(token))
	return false
}

func (a *App) loginRoutes(m *http.ServeMux) {
	m.HandleFunc("GET /api/v1/auth/status", func(w http.ResponseWriter, r *http.Request) {
		a.login.mu.Lock()
		defer a.login.mu.Unlock()
		if a.login.broken {
			fail(w, 503, "Account file cannot be read. Restore admin-login.json from your config backup.")
			return
		}
		state := a.store.snapshot()
		send(w, 200, map[string]bool{"setup": a.login.account == nil, "migration": a.login.account == nil && (len(state.Providers) > 0 || len(state.Devices) > 0)})
	})
	for _, action := range []string{"setup", "login"} {
		m.HandleFunc("POST /api/v1/auth/"+action, func(w http.ResponseWriter, r *http.Request) {
			mediaType, _, _ := mime.ParseMediaType(r.Header.Get("Content-Type"))
			if mediaType != "application/json" {
				fail(w, 415, "JSON required")
				return
			}
			var body struct {
				Username string `json:"username"`
				Password string `json:"password"`
			}
			if !decode(w, r, &body) {
				return
			}
			body.Username = strings.TrimSpace(body.Username)
			a.login.mu.Lock()
			defer a.login.mu.Unlock()
			if a.login.broken {
				fail(w, 503, "Account file unavailable")
				return
			}
			now := time.Now()
			if now.Sub(a.login.window) >= time.Minute {
				a.login.attempts = 0
				a.login.window = now
			}
			if a.login.attempts >= 10 {
				w.Header().Set("Retry-After", "60")
				fail(w, 429, "Too many attempts. Try again in a minute.")
				return
			}
			a.login.attempts++
			if len(body.Username) < 1 || len(body.Username) > 80 || len(body.Password) > 256 {
				fail(w, 400, "Enter a username and password (maximum 80 and 256 characters).")
				return
			}
			if action == "setup" {
				if a.login.account != nil {
					fail(w, 409, "An administrator account already exists")
					return
				}
				state := a.store.snapshot()
				if len(state.Providers) > 0 || len(state.Devices) > 0 {
					legacy := strings.TrimPrefix(r.Header.Get("Authorization"), "Bearer ")
					if subtle.ConstantTimeCompare([]byte(legacy), []byte(a.admin)) != 1 {
						fail(w, 401, "Authorize this one-time upgrade with your existing administrator key.")
						return
					}
				}
				if len(body.Password) < 12 {
					fail(w, 400, "Choose a password of at least 12 characters.")
					return
				}
				account := adminAccount{Username: body.Username, Salt: randomID(16)}
				account.Hash = passwordHash(body.Password, account.Salt)
				b, _ := json.Marshal(account)
				path := filepath.Join(a.store.dir, "admin-login.json")
				if err := os.WriteFile(path+".tmp", b, 0600); err != nil {
					fail(w, 500, "Could not save administrator account")
					return
				}
				if err := os.Rename(path+".tmp", path); err != nil {
					fail(w, 500, "Could not save administrator account")
					return
				}
				a.login.account = &account
			} else {
				account := a.login.account
				if account == nil {
					fail(w, 409, "Create your administrator account first")
					return
				}
				hash := passwordHash(body.Password, account.Salt)
				if subtle.ConstantTimeCompare([]byte(hash), []byte(account.Hash)) != 1 || body.Username != account.Username {
					fail(w, 401, "Incorrect username or password")
					return
				}
			}
			for key, expires := range a.login.sessions {
				if now.After(expires) {
					delete(a.login.sessions, key)
				}
			}
			if len(a.login.sessions) >= 32 {
				fail(w, 429, "Too many active logins. Sign out of another browser or wait for it to expire.")
				return
			}
			token := randomID(32)
			a.login.sessions[tokenHash(token)] = now.Add(24 * time.Hour)
			send(w, 200, map[string]string{"token": token, "username": body.Username})
		})
	}
	m.HandleFunc("POST /api/v1/auth/logout", func(w http.ResponseWriter, r *http.Request) {
		a.login.mu.Lock()
		defer a.login.mu.Unlock()
		delete(a.login.sessions, tokenHash(strings.TrimPrefix(r.Header.Get("Authorization"), "Bearer ")))
		send(w, 200, map[string]bool{"ok": true})
	})
}
