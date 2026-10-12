package main

import (
	"context"
	"crypto/rand"
	"encoding/hex"
	"log"
	"net/http"
	"os"
	"os/signal"
	"path/filepath"
	"syscall"
	"time"
)

func randomID(n int) string {
	b := make([]byte, n)
	if _, err := rand.Read(b); err != nil {
		panic(err)
	}
	return hex.EncodeToString(b)
}
func env(key, fallback string) string {
	if v := os.Getenv(key); v != "" {
		return v
	}
	return fallback
}
func main() {
	config := env("IPTV_CONFIG", "./data")
	scratch := env("IPTV_SCRATCH", "./transcode")
	if err := os.MkdirAll(config, 0700); err != nil {
		log.Fatal(err)
	}
	if err := os.MkdirAll(scratch, 0700); err != nil {
		log.Fatal(err)
	}
	tokenPath := filepath.Join(config, "admin.token")
	token, err := os.ReadFile(tokenPath)
	if os.IsNotExist(err) {
		token = []byte(randomID(32))
		err = os.WriteFile(tokenPath, token, 0600)
	}
	if err != nil || len(token) < 32 {
		log.Fatal("Cannot load admin token; inspect config directory permissions")
	}
	store, err := openStore(config)
	if err != nil {
		log.Fatal("Cannot open encrypted state: ", err)
	}
	app := newApp(store, string(token), scratch, env("FFMPEG_PATH", "ffmpeg"), os.Getenv("IPTV_DEMO") == "1")
	srv := &http.Server{Addr: env("IPTV_LISTEN", ":8080"), Handler: app.routes(), ReadHeaderTimeout: 5 * time.Second, IdleTimeout: 60 * time.Second, MaxHeaderBytes: 16384}
	ctx, stop := signal.NotifyContext(context.Background(), os.Interrupt, syscall.SIGTERM)
	defer stop()
	go app.reap(ctx)
	go func() {
		<-ctx.Done()
		shutdown, cancel := context.WithTimeout(context.Background(), 10*time.Second)
		defer cancel()
		_ = srv.Shutdown(shutdown)
		app.close()
	}()
	log.Printf("IPTV foundation listening on %s; admin token is in %s (never logged)", srv.Addr, tokenPath)
	if err = srv.ListenAndServe(); err != nil && err != http.ErrServerClosed {
		log.Fatal(err)
	}
}
