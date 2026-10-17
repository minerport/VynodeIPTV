package main

import (
	"fmt"
	"net/http"
	"os"
	"strconv"
	"strings"
	"sync"
	"time"
)

// /proc/stat describes host CPU, including FFmpeg, not container-only usage.
var cpuSample struct {
	sync.Mutex
	total, idle uint64
	at          time.Time
	percent     *float64
}

func cpuTicks(raw string) (uint64, uint64, error) {
	fields := strings.Fields(strings.SplitN(raw, "\n", 2)[0])
	if len(fields) < 5 || fields[0] != "cpu" {
		return 0, 0, fmt.Errorf("CPU counters unavailable")
	}
	var total, idle uint64
	// guest time is already included in user/nice; do not count it twice.
	for i := 1; i < len(fields) && i <= 8; i++ {
		v, e := strconv.ParseUint(fields[i], 10, 64)
		if e != nil {
			return 0, 0, e
		}
		total += v
		if i == 4 || i == 5 {
			idle += v
		}
	}
	return total, idle, nil
}
func serverCPU() map[string]any {
	result := map[string]any{"scope": "host CPU (not container-only)", "cpuPercent": nil, "sampleAgeMs": nil}
	raw, err := os.ReadFile("/proc/stat")
	if err != nil {
		return result
	}
	total, idle, err := cpuTicks(string(raw))
	if err != nil {
		return result
	}
	cpuSample.Lock()
	defer cpuSample.Unlock()
	now := time.Now()
	if cpuSample.at.IsZero() {
		cpuSample.total = total
		cpuSample.idle = idle
		cpuSample.at = now
		return result
	}
	if now.Sub(cpuSample.at) >= time.Second {
		if total > cpuSample.total && idle >= cpuSample.idle && idle-cpuSample.idle <= total-cpuSample.total {
			p := 100 * (1 - float64(idle-cpuSample.idle)/float64(total-cpuSample.total))
			cpuSample.percent = &p
		} else {
			cpuSample.percent = nil
		}
		cpuSample.total = total
		cpuSample.idle = idle
		cpuSample.at = now
	}
	result["cpuPercent"] = cpuSample.percent
	result["sampleAgeMs"] = time.Since(cpuSample.at).Milliseconds()
	return result
}
func (a *App) runtimeMetrics(w http.ResponseWriter, r *http.Request) { send(w, 200, serverCPU()) }
