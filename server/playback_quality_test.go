package main

import (
	"os/exec"
	"testing"
)

func TestDataSaverConversion(t *testing.T) {
	ffmpeg, err := exec.LookPath("ffmpeg")
	if err != nil {
		t.Skip("ffmpeg unavailable")
	}
	for _, size := range []string{"1280x720", "720x1280", "320x240"} {
		t.Run(size, func(t *testing.T) {
			args := []string{"-hide_banner", "-loglevel", "error", "-f", "lavfi", "-i", "testsrc2=size=" + size + ":rate=25", "-t", "0.2"}
			args = append(args, dataSaverArgs()...)
			args = append(args, "-f", "null", "-")
			if output, err := exec.Command(ffmpeg, args...).CombinedOutput(); err != nil {
				t.Fatalf("Data Saver failed: %v %s", err, output)
			}
		})
	}
}
