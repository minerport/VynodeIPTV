package main

import "testing"

func TestTimeshiftSettings(t *testing.T) {
	a := testApp(t)
	for _, body := range []string{`{"minutes":-1,"maxMB":512}`, `{"minutes":15,"maxMB":10}`, `{"minutes":600,"maxMB":512}`} {
		if w := call(a, "PUT", "/api/v1/timeshift", a.admin, body); w.Code != 400 {
			t.Fatalf("accepted %s", body)
		}
	}
	if w := call(a, "PUT", "/api/v1/timeshift", a.admin, `{"minutes":15,"maxMB":1024}`); w.Code != 200 {
		t.Fatal(w.Body.String())
	}
	v := a.store.snapshot().Timeshift
	if v.segments() != 450 || v.MaxMB != 1024 {
		t.Fatal(v)
	}
	if (TimeshiftSettings{}).normalized().MaxMB != 512 || (TimeshiftSettings{}).segments() != 8 {
		t.Fatal("unsafe defaults")
	}
}
