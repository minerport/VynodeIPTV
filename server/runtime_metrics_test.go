package main

import "testing"

func TestCPUTicks(t *testing.T) {
	total, idle, err := cpuTicks("cpu 10 2 3 80 5 1 2 1 9 1\ncpu0 0")
	if err != nil || total != 104 || idle != 85 {
		t.Fatalf("%d %d %v", total, idle, err)
	}
	for _, s := range []string{"", "cpu0 1 2 3 4", "cpu 1 x 3 4"} {
		if _, _, e := cpuTicks(s); e == nil {
			t.Fatalf("accepted %q", s)
		}
	}
}
