package main

import "net/http"

type GuidePreferences struct {
	Favorites    []string `json:"favorites"`
	GroupOrder   []string `json:"groupOrder"`
	HiddenGroups []string `json:"hiddenGroups"`
}

func clonePreferences(source map[string]GuidePreferences) map[string]GuidePreferences {
	result := map[string]GuidePreferences{}
	for owner, p := range source {
		result[owner] = GuidePreferences{append([]string{}, p.Favorites...), append([]string{}, p.GroupOrder...), append([]string{}, p.HiddenGroups...)}
	}
	return result
}
func (a *App) preferenceRoutes(m *http.ServeMux) {
	m.HandleFunc("GET /api/v1/guide/preferences", a.auth(false, func(w http.ResponseWriter, r *http.Request) {
		owner, _ := a.identity(r)
		p := a.store.snapshot().Preferences[owner]
		if p.Favorites == nil {
			p.Favorites = []string{}
		}
		if p.GroupOrder == nil {
			p.GroupOrder = []string{}
		}
		if p.HiddenGroups == nil {
			p.HiddenGroups = []string{}
		}
		send(w, 200, p)
	}))
	m.HandleFunc("PUT /api/v1/guide/preferences", a.auth(false, func(w http.ResponseWriter, r *http.Request) {
		var body struct {
			Favorites    *[]string `json:"favorites"`
			GroupOrder   *[]string `json:"groupOrder"`
			HiddenGroups *[]string `json:"hiddenGroups"`
		}
		if !decode(w, r, &body) {
			return
		}
		clean := func(values *[]string) ([]string, bool) {
			out := []string{}
			seen := map[string]bool{}
			if values == nil {
				return out, true
			}
			if len(*values) > 500 {
				return nil, false
			}
			for _, value := range *values {
				if value == "" || len(value) > 160 {
					return nil, false
				}
				if value != "favorites" && !seen[value] {
					out = append(out, value)
					seen[value] = true
				}
			}
			return out, true
		}
		favorites, validFavorites := clean(body.Favorites)
		order, validOrder := clean(body.GroupOrder)
		hidden, validHidden := clean(body.HiddenGroups)
		if !validFavorites || !validOrder || !validHidden {
			fail(w, 400, "Use at most 500 favorites or ordered groups with valid identifiers")
			return
		}
		owner, _ := a.identity(r)
		var saved GuidePreferences
		err := a.store.update(func(s *State) {
			if s.Preferences == nil {
				s.Preferences = map[string]GuidePreferences{}
			}
			saved = s.Preferences[owner]
			if body.Favorites != nil {
				saved.Favorites = favorites
			}
			if body.GroupOrder != nil {
				saved.GroupOrder = order
			}
			if body.HiddenGroups != nil {
				saved.HiddenGroups = hidden
			}
			if saved.HiddenGroups == nil {
				saved.HiddenGroups = []string{}
			}
			if saved.Favorites == nil {
				saved.Favorites = []string{}
			}
			if saved.GroupOrder == nil {
				saved.GroupOrder = []string{}
			}
			s.Preferences[owner] = saved
		})
		if err != nil {
			fail(w, 500, "Could not save guide preferences")
			return
		}
		send(w, 200, saved)
	}))
}
