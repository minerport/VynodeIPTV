package main

import (
	"encoding/json"
	"testing"
)

func TestSharedAppearancePermissionsRevisionPersistence(t *testing.T) {
	a := testApp(t)
	device := "theme-device-token"
	a.store.update(func(s *State) {
		s.Devices = append(s.Devices, Device{ID: "theme-device", Name: "TV", TokenHash: tokenHash(device)})
	})
	settings := AppearanceSettings{Layout: "side", Startup: "home", Palette: "pearl", Surface: "frosted", Font: "modern", Scale: 1.15, Density: "compact", Corners: "soft", Borders: "clear", Focus: "outline", Artwork: "all"}
	body, _ := json.Marshal(SharedAppearance{Settings: settings})
	for _, token := range []string{"", device} {
		w := call(a, "PUT", "/api/v1/appearance/shared", token, string(body))
		if w.Code == 200 {
			t.Fatal("unauthorized theme publication")
		}
	}
	if w := call(a, "GET", "/api/v1/appearance/shared", "", ""); w.Code == 200 {
		t.Fatal("unauthenticated theme read")
	}
	if w := call(a, "PUT", "/api/v1/appearance/shared", a.admin, string(body)); w.Code != 200 {
		t.Fatal(w.Body.String())
	}
	if w := call(a, "PUT", "/api/v1/appearance/shared", a.admin, string(body)); w.Code != 409 {
		t.Fatal("stale publisher accepted", w.Code)
	}
	w := call(a, "GET", "/api/v1/appearance/shared", device, "")
	var got SharedAppearance
	json.Unmarshal(w.Body.Bytes(), &got)
	if w.Code != 200 || got.Revision != 1 || got.Settings != settings {
		t.Fatal(w.Body.String())
	}
	reopened, err := openStore(a.store.dir)
	if err != nil || reopened.snapshot().Appearance != got {
		t.Fatal("theme persistence", err)
	}
	settings.Layout = "https://unsafe"
	body, _ = json.Marshal(SharedAppearance{Revision: 1, Settings: settings})
	if w := call(a, "PUT", "/api/v1/appearance/shared", a.admin, string(body)); w.Code != 400 {
		t.Fatal("invalid theme accepted")
	}
	if w := call(a, "PUT", "/api/v1/appearance/shared", a.admin, `{"revision":1,"settings":{},"password":"no"}`); w.Code != 400 {
		t.Fatal("unknown fields accepted")
	}
}
