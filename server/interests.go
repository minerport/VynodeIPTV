package main

import (
	"encoding/json"
	"net/http"
	"regexp"
	"slices"
	"sort"
	"strings"
	"time"
)

type InterestSettings struct {
	Teams         []string `json:"teams"`
	City          string   `json:"city"`
	State         string   `json:"state"`
	Genres        []string `json:"genres"`
	LocalChannels []string `json:"localChannels"`
}
type InterestTeam struct {
	ID      string   `json:"id"`
	Name    string   `json:"name"`
	League  string   `json:"league"`
	Aliases []string `json:"aliases"`
}

var interestCatalog = func() struct {
	Teams  []InterestTeam      `json:"teams"`
	Genres map[string][]string `json:"genres"`
	States map[string]string   `json:"states"`
} {
	var c struct {
		Teams  []InterestTeam      `json:"teams"`
		Genres map[string][]string `json:"genres"`
		States map[string]string   `json:"states"`
	}
	b, err := webFiles.ReadFile("web/interests-catalog.json")
	if err != nil {
		panic(err)
	}
	if err = json.Unmarshal(b, &c); err != nil {
		panic(err)
	}
	return c
}()

func cloneInterests(source map[string]InterestSettings) map[string]InterestSettings {
	out := map[string]InterestSettings{}
	for k, v := range source {
		v.Teams = append([]string{}, v.Teams...)
		v.Genres = append([]string{}, v.Genres...)
		v.LocalChannels = append([]string{}, v.LocalChannels...)
		out[k] = v
	}
	return out
}
func (p InterestSettings) valid() bool {
	if len(p.Teams) > 30 || len(p.Genres) > 20 || len(p.LocalChannels) > 100 || len(p.City) > 80 || len(p.State) > 80 {
		return false
	}
	for _, id := range p.Teams {
		if !slices.ContainsFunc(interestCatalog.Teams, func(t InterestTeam) bool { return t.ID == id }) {
			return false
		}
	}
	for _, g := range p.Genres {
		if _, ok := interestCatalog.Genres[g]; !ok {
			return false
		}
	}
	for _, id := range p.LocalChannels {
		if len(id) > 160 || id == "" {
			return false
		}
	}
	return true
}

var interestPunctuation = regexp.MustCompile(`[^\p{L}\p{N}]+`)

func interestWords(s string) string {
	return " " + strings.TrimSpace(interestPunctuation.ReplaceAllString(strings.ToLower(s), " ")) + " "
}
func interestHas(text, phrase string) bool {
	return strings.TrimSpace(phrase) != "" && strings.Contains(text, interestWords(phrase))
}

type InterestHit struct {
	Channel   Channel   `json:"channel"`
	Programme Programme `json:"programme"`
	Section   string    `json:"section"`
	Reason    string    `json:"reason"`
	TeamID    string    `json:"teamId,omitempty"`
}

func interestMatches(p InterestSettings, channels []Channel, programmes []Programme, hidden []string, now time.Time) ([]InterestHit, bool) {
	if len(p.Teams) == 0 && len(p.Genres) == 0 && len(p.LocalChannels) == 0 && (p.City == "" || p.State == "") {
		return []InterestHit{}, false
	}
	tokens := map[string]string{}
	has := func(text, phrase string) bool {
		if strings.TrimSpace(phrase) == "" {
			return false
		}
		token, ok := tokens[phrase]
		if !ok {
			token = interestWords(phrase)
			tokens[phrase] = token
		}
		return strings.Contains(text, token)
	}
	byID := map[string]Channel{}
	for _, c := range channels {
		if !slices.Contains(hidden, "provider:"+c.ProviderID+":"+c.Category) {
			byID[c.ID] = c
		}
	}
	teams := []InterestTeam{}
	for _, t := range interestCatalog.Teams {
		if slices.Contains(p.Teams, t.ID) {
			teams = append(teams, t)
		}
	}
	hits := []InterestHit{}
	seen := map[string]bool{}
	for _, v := range programmes {
		c, ok := byID[v.ChannelID]
		if !ok || !v.End.After(now) || !v.End.After(v.Start) || !v.Start.Before(now.Add(7*24*time.Hour)) {
			continue
		}
		text := interestWords(v.Title + " " + v.EpisodeTitle + " " + v.Description + " " + v.Genres)
		station := interestWords(c.Name + " " + c.CategoryName)
		add := func(section, reason, team string) {
			key := section + ":" + c.ID + ":" + v.Start.String()
			if !seen[key] {
				seen[key] = true
				hits = append(hits, InterestHit{c, v, section, reason, team})
			}
		}
		for _, t := range teams {
			league := map[string]string{"NFL": "football", "NBA": "basketball", "MLB": "baseball", "NHL": "hockey", "MLS": "soccer"}[t.League]
			matched := has(text, t.Name)
			if has(text, t.League) || has(text, league) {
				for _, alias := range t.Aliases {
					matched = matched || has(text, alias)
				}
			}
			if matched {
				add("sports", t.Name, t.ID)
				break
			}
		}
		if slices.Contains(p.LocalChannels, c.ID) {
			add("local", "Your selected local station", "")
		} else if p.City != "" && p.State != "" && has(station, p.City) && interestState(station, p.State) {
			add("local", p.City+", "+p.State+" · channel listing match", "")
		}
		genreText := text
		if v.Genres != "" {
			genreText = interestWords(v.Genres)
		}
		for _, g := range p.Genres {
			for _, term := range interestCatalog.Genres[g] {
				if has(genreText, term) {
					reason := g + " · EPG category"
					if v.Genres == "" {
						reason = g + " · guide text match"
					}
					add("genres", reason, "")
					break
				}
			}
		}
	}
	sort.SliceStable(hits, func(i, j int) bool { return hits[i].Programme.Start.Before(hits[j].Programme.Start) })
	// Bound each section independently so busy local stations cannot crowd out sports.
	counts := map[string]int{}
	out := []InterestHit{}
	truncated := false
	for _, hit := range hits {
		bucket := hit.Section
		if hit.Programme.Start.After(now) {
			bucket += "-next"
		}
		counts[bucket]++
		if counts[bucket] <= 40 {
			out = append(out, hit)
		} else {
			truncated = true
		}
	}
	return out, truncated
}
func (a *App) interestRoutes(m *http.ServeMux) {
	a.sportsRoutes(m)
	m.HandleFunc("GET /api/v1/interests", a.auth(false, func(w http.ResponseWriter, r *http.Request) {
		owner, _ := a.identity(r)
		send(w, 200, cloneInterests(a.store.snapshot().Interests)[owner])
	}))
	m.HandleFunc("PUT /api/v1/interests", a.auth(false, func(w http.ResponseWriter, r *http.Request) {
		var p InterestSettings
		if !decode(w, r, &p) {
			return
		}
		p.City = strings.TrimSpace(p.City)
		p.State = strings.TrimSpace(p.State)
		if !p.valid() {
			fail(w, 400, "Choose up to 30 teams, 20 genres and 100 local stations; city/state must be at most 80 characters")
			return
		}
		owner, _ := a.identity(r)
		if err := a.store.update(func(s *State) {
			if s.Interests == nil {
				s.Interests = map[string]InterestSettings{}
			}
			s.Interests[owner] = p
		}); err != nil {
			fail(w, 500, "Could not save interests")
			return
		}
		send(w, 200, p)
	}))
	m.HandleFunc("GET /api/v1/for-you", a.auth(false, func(w http.ResponseWriter, r *http.Request) {
		owner, _ := a.identity(r)
		s := a.store.snapshot()
		a.mu.Lock()
		channels := append([]Channel{}, a.channels...)
		guide := append([]Programme{}, a.guide...)
		a.mu.Unlock()
		hits, truncated := interestMatches(s.Interests[owner], channels, guide, s.Preferences[owner].HiddenGroups, time.Now())
		send(w, 200, map[string]any{"results": hits, "truncated": truncated})
	}))
}

func interestState(text, state string) bool {
	if interestHas(text, state) {
		return true
	}
	for code, name := range interestCatalog.States {
		if strings.EqualFold(state, code) || strings.EqualFold(state, name) {
			return interestHas(text, code) || interestHas(text, name)
		}
	}
	return false
}
