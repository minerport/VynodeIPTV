package main

import (
	"net/http"
	"slices"
)

type AppearanceSettings struct {
	Layout  string  `json:"layout"`
	Startup string  `json:"startup"`
	Palette string  `json:"palette"`
	Surface string  `json:"surface"`
	Font    string  `json:"font"`
	Scale   float64 `json:"scale"`
	Density string  `json:"density"`
	Corners string  `json:"corners"`
	Borders string  `json:"borders"`
	Focus   string  `json:"focus"`
	Artwork string  `json:"artwork"`
}
type SharedAppearance struct {
	Revision int64              `json:"revision"`
	Settings AppearanceSettings `json:"settings"`
}

func (s AppearanceSettings) valid() bool {
	for _, pair := range []struct {
		value   string
		allowed []string
	}{{s.Layout, []string{"classic", "guide", "cinema", "side", "wall"}}, {s.Startup, []string{"guide", "home", "wall"}}, {s.Palette, []string{"cinema", "obsidian", "arctic", "midnight", "ember", "pearl", "contrast"}}, {s.Surface, []string{"matte", "frosted", "smoked"}}, {s.Font, []string{"standard", "modern", "readable"}}, {s.Density, []string{"compact", "comfortable", "large"}}, {s.Corners, []string{"square", "soft", "rounded"}}, {s.Borders, []string{"clear", "strong"}}, {s.Focus, []string{"outline", "fill", "glow"}}, {s.Artwork, []string{"all", "header", "off"}}} {
		if !slices.Contains(pair.allowed, pair.value) {
			return false
		}
	}
	return slices.Contains([]float64{.9, 1, 1.15, 1.3}, s.Scale)
}
func (a *App) appearanceRoutes(m *http.ServeMux) {
	m.HandleFunc("GET /api/v1/appearance/shared", a.auth(false, func(w http.ResponseWriter, r *http.Request) { send(w, 200, a.store.snapshot().Appearance) }))
	m.HandleFunc("PUT /api/v1/appearance/shared", a.auth(true, func(w http.ResponseWriter, r *http.Request) {
		var input SharedAppearance
		if !decode(w, r, &input) {
			return
		}
		if !input.Settings.valid() || input.Revision < 0 {
			fail(w, 400, "Invalid appearance settings")
			return
		}
		conflict := false
		err := a.store.update(func(s *State) {
			if s.Appearance.Revision != input.Revision {
				conflict = true
				return
			}
			input.Revision++
			s.Appearance = input
		})
		if err != nil {
			fail(w, 500, "Could not save appearance")
			return
		}
		if conflict {
			fail(w, 409, "Shared theme changed. Reload it before publishing.")
			return
		}
		send(w, 200, input)
	}))
}
