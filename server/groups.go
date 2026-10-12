package main

import (
	"net/http"
	"strings"
)

func (a *App) groupRoutes(m *http.ServeMux) {
	a.preferenceRoutes(m)
	m.HandleFunc("GET /api/v1/groups", a.auth(false, func(w http.ResponseWriter, r *http.Request) {
		owner, _ := a.identity(r)
		out := []CustomGroup{}
		for _, g := range a.store.snapshot().Groups {
			if g.Owner == owner {
				g.Owner = ""
				out = append(out, g)
			}
		}
		send(w, 200, out)
	}))
	m.HandleFunc("POST /api/v1/groups", a.auth(false, a.saveGroup))
	m.HandleFunc("PUT /api/v1/groups/{id}", a.auth(false, a.saveGroup))
	m.HandleFunc("DELETE /api/v1/groups/{id}", a.auth(false, func(w http.ResponseWriter, r *http.Request) {
		owner, _ := a.identity(r)
		found := false
		err := a.store.update(func(s *State) {
			out := []CustomGroup{}
			for _, g := range s.Groups {
				if g.ID == r.PathValue("id") && g.Owner == owner {
					found = true
				} else {
					out = append(out, g)
				}
			}
			s.Groups = out
		})
		if err != nil {
			fail(w, 500, "Could not save groups")
			return
		}
		if !found {
			fail(w, 404, "Group not found")
			return
		}
		send(w, 200, map[string]bool{"deleted": true})
	}))
}
func (a *App) saveGroup(w http.ResponseWriter, r *http.Request) {
	var body struct {
		Name       string   `json:"name"`
		ChannelIDs []string `json:"channelIds"`
	}
	if !decode(w, r, &body) {
		return
	}
	body.Name = strings.TrimSpace(body.Name)
	if body.Name == "" || len(body.Name) > 80 || len(body.ChannelIDs) > 500 {
		fail(w, 400, "Use a group name up to 80 characters and at most 500 channels")
		return
	}
	a.mu.Lock()
	known := map[string]bool{}
	for _, c := range a.channels {
		known[c.ID] = true
	}
	a.mu.Unlock()
	unique := []string{}
	seen := map[string]bool{}
	for _, id := range body.ChannelIDs {
		if !known[id] {
			fail(w, 400, "Group contains an unknown channel")
			return
		}
		if !seen[id] {
			unique = append(unique, id)
			seen[id] = true
		}
	}
	owner, _ := a.identity(r)
	id := r.PathValue("id")
	creating := id == ""
	if creating {
		id = randomID(12)
	}
	saved := CustomGroup{id, body.Name, unique, owner}
	found := false
	overLimit := false
	err := a.store.update(func(s *State) {
		count := 0
		for i, g := range s.Groups {
			if g.Owner == owner {
				count++
			}
			if g.ID == id && g.Owner == owner && !creating {
				s.Groups[i] = saved
				found = true
			}
		}
		if creating {
			if count >= 50 {
				overLimit = true
				return
			}
			s.Groups = append(s.Groups, saved)
			found = true
		}
	})
	if err != nil {
		fail(w, 500, "Could not persist group")
		return
	}
	if overLimit {
		fail(w, 409, "Maximum 50 custom groups")
		return
	}
	if !found {
		fail(w, 404, "Group not found")
		return
	}
	saved.Owner = ""
	status := 200
	if creating {
		status = 201
	}
	send(w, status, saved)
}
