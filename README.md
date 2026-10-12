# VynodeIPTV

Self-hosted IPTV server, browser player, and Android TV companion. **Server preview 0.6.1 · Android preview 0.5.0.**

The server accepts your Xtream connection, imports channels/groups and EPG, and supplies authenticated HLS to each player. Android can use the server as its streaming middleware or connect directly in standalone mode. No subscriptions, channels or provider credentials are included.

## Install on Unraid

Image: **`ghcr.io/minerport/vynodeiptv:0.6.1`** (`linux/amd64`).

Follow the [Unraid installation guide](docs/UNRAID.md), then open `http://YOUR_UNRAID_IP:8580`. The [Unraid template](deploy/unraid/iptv-server.xml) includes the port, persistent configuration and scratch mappings. This is a manually installed template, not a Community Applications listing.

The container runs as **UID/GID 10001**, without privileged mode or the Docker socket. Its config and scratch directories must be writable by that user. Do not set PUID/PGID variables: this image does not implement them.

## Run with Docker

```sh
docker run -d --name VynodeIPTV \
  --restart unless-stopped \
  --security-opt no-new-privileges --cap-drop ALL --pids-limit 128 \
  -p 127.0.0.1:8580:8080 \
  -v vynodeiptv-config:/config \
  -v vynodeiptv-transcode:/transcode \
  ghcr.io/minerport/vynodeiptv:0.6.1
```

Read the administrator token privately with `docker exec VynodeIPTV cat /config/admin.token`, then enter it in the dashboard. It remains in browser tab storage. Add a provider and retain its actual permitted connection limit. The command above is localhost-only; use the Unraid template for the intended LAN service. Internet exposure is not supported by this preview.

Configuration, encryption key and administrator token live in `/config`. Back up that directory together. `/transcode` is disposable media scratch. Removing either a container or image does not require deleting its persistent volumes.

## Features

- Xtream channel/group import, XMLTV guide, favorites, hidden/reordered groups and user-created collections.
- Continuous guide with a collapsible group sidebar, preview player and icon navigation.
- Independent browser video-wall tiles in 2/4/6 layouts, selected-tile audio, fullscreen return and complete wall clearing.
- Android TV companion pairing and standalone Xtream mode; existing Android source/application retains the Signal TV name and application ID.
- FFmpeg remux for compatible clients or H.264/AAC CPU conversion for browsers.
- Segment-progress watchdog, bounded worker recovery, browser recovery, per-session diagnostics, expiring leases and enforced provider connection limits.
- Encrypted provider state, authenticated media and explicit provider HTTP opt-in.

Each wall tile consumes its own provider connection and server resources. A six-tile layout does not guarantee six simultaneous streams on every server or subscription. Browser playback uses CPU conversion; GPU acceleration and shared ingest are not implemented.

## Development and verification

```sh
npm test
docker run --rm -v "$PWD/server:/src" -w /src golang:1.27.2-bookworm sh -c 'go test -race ./... && go vet ./...'
docker build -f deploy/docker/Dockerfile -t vynodeiptv:local .
```

The GitHub workflow tests the Go server and browser behavior, starts an isolated synthetic stream, verifies authentication and forced-worker recovery, and only then publishes version tags to GHCR. `latest` tracks a tagged non-prerelease version; use a numbered tag for controlled upgrades.

For the existing local development Compose setup, run `scripts/start-local.ps1 -Build`. It uses the existing Signal TV local volumes and does not switch the running development installation to GHCR automatically.

Build Android with Java 17 and Android SDK 36: `android/gradlew -p android :app:assembleDebug :app:testDebugUnitTest :app:lintDebug`. Debug APKs and signing keys are not committed. Provider credentials, runtime state and private test data are excluded from Git and the container build context.

See [release validation](docs/VALIDATION-0.6.1.md), [architecture roadmap](IPTV-ARCHITECTURE-PLAN.md), [development history](docs/DEVELOPMENT-HISTORY.md) and [third-party notices](THIRD-PARTY-NOTICES.md).

## Current limits

Recovery may briefly rebuffer; unavailable upstream media cannot be reconstructed. Segment/clock monitoring cannot identify an encoded still image or silence. Hardware transcoding, gapless timeline stitching, shared ingest, recording, catch-up, scheduled EPG refresh and server-restart session recreation remain future work. Unraid compatibility is packaged and container-tested; installation on physical Unraid and Shield hardware still requires acceptance testing.
