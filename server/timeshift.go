package main

import "net/http"

type TimeshiftSettings struct {
	Minutes int `json:"minutes"`
	MaxMB   int `json:"maxMB"`
}

func (v TimeshiftSettings) normalized() TimeshiftSettings {
	if v.MaxMB == 0 {
		v.MaxMB = 512
	}
	return v
}
func (v TimeshiftSettings) valid() bool {
	return (v.Minutes == 0 || v.Minutes == 5 || v.Minutes == 15 || v.Minutes == 30 || v.Minutes == 60) && v.MaxMB >= 256 && v.MaxMB <= 4096
}
func (v TimeshiftSettings) segments() int {
	if v.Minutes == 0 {
		return 8
	}
	return v.Minutes * 30
}
func (a *App) timeshiftRoutes(m *http.ServeMux) {
	m.HandleFunc("GET /api/v1/timeshift", a.auth(false, func(w http.ResponseWriter, r *http.Request) { send(w, 200, a.store.snapshot().Timeshift.normalized()) }))
	m.HandleFunc("PUT /api/v1/timeshift", a.auth(true, func(w http.ResponseWriter, r *http.Request) {
		var v TimeshiftSettings
		if !decode(w, r, &v) {
			return
		}
		if !v.valid() {
			fail(w, 400, "Choose 0, 5, 15, 30 or 60 minutes and 256–4096 MB per session")
			return
		}
		if a.store.update(func(s *State) { s.Timeshift = v }) != nil {
			fail(w, 500, "Cannot save timeshift settings")
			return
		}
		send(w, 200, v)
	}))
}
