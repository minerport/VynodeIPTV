package main

import (
	"encoding/json"
	"strings"
	"testing"
)

func TestPreferencesPersistAndIsolateOwners(t *testing.T) {
	a := testApp(t)
	a.store.update(func(s *State) { s.Devices = append(s.Devices, Device{ID: "tv", TokenHash: tokenHash("tv-token")}) })
	w := call(a, "PUT", "/api/v1/guide/preferences", a.admin, `{"favorites":["demo:1","demo:1"],"groupOrder":["provider:demo:Diagnostics","favorites","all"],"hiddenGroups":["provider:demo:Diagnostics","favorites"]}`)
	if w.Code != 200 {
		t.Fatal(w.Body.String())
	}
	var p GuidePreferences
	json.Unmarshal(w.Body.Bytes(), &p)
	if len(p.Favorites) != 1 || len(p.GroupOrder) != 2 || len(p.HiddenGroups) != 1 {
		t.Fatal("deduplication or pinned group exclusion failed")
	}
	w = call(a, "PUT", "/api/v1/guide/preferences", a.admin, `{"groupOrder":["all"]}`)
	json.Unmarshal(w.Body.Bytes(), &p)
	if len(p.Favorites) != 1 || len(p.HiddenGroups) != 1 {
		t.Fatal("order update lost favorites")
	}
	w = call(a, "GET", "/api/v1/guide/preferences", "tv-token", "")
	if strings.Contains(w.Body.String(), "demo:1") {
		t.Fatal("preferences leaked to another device")
	}
	store, err := openStore(a.store.dir)
	if err != nil {
		t.Fatal(err)
	}
	snap := store.snapshot()
	if snap.Preferences["admin"].Favorites[0] != "demo:1" {
		t.Fatal("favorite not persisted")
	}
	snap.Preferences["admin"].Favorites[0] = "changed"
	if store.snapshot().Preferences["admin"].Favorites[0] != "demo:1" {
		t.Fatal("preference snapshot aliases live state")
	}
	if w = call(a, "PUT", "/api/v1/guide/preferences", a.admin, `{"favorites":["`+strings.Repeat("x", 161)+`"]}`); w.Code != 400 {
		t.Fatal("oversized identifier accepted")
	}
}
