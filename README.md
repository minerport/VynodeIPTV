# VynodeIPTV

Self-hosted IPTV server, browser player, and Android TV companion. **Server preview 0.10.0 · Android preview 0.10.0.**

The server accepts your Xtream connection, imports channels/groups and EPG, and supplies authenticated HLS to each player. Android can use the server as its streaming middleware or connect directly in standalone mode. No subscriptions, channels or provider credentials are included.

## Install on Unraid

Image: **`ghcr.io/minerport/vynodeiptv:latest`** (`linux/amd64`).

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
  ghcr.io/minerport/vynodeiptv:latest
```

Create your administrator username and password in the dashboard. Existing installations require their previous administrator key once (or reuse the already signed-in browser tab) to authorize the upgrade. After setup the old key no longer grants dashboard or media access. Add a provider and retain its actual permitted connection limit. The command above is localhost-only; use the Unraid template for the intended LAN service. Internet exposure is not supported by this preview.

Configuration, encryption key and password hash live in `/config`. Back up that directory together. `/transcode` is disposable media scratch. Removing either a container or image does not require deleting its persistent volumes.

The browser dashboard adapts to phones with a group drawer, compact player controls, a one-hour guide window and full-width settings forms. Desktop retains the existing TV layout.

## Features

- Xtream channel/group import, XMLTV guide, favorites, hidden/reordered groups and user-created collections.
- Saved-account management, independent channel/EPG refresh and optional daily refresh on server and standalone Android.
- Android keeps standalone Xtream and companion connections side by side, with explicit mode switching and account-specific preferences.
- Continuous guide opens at Now, with left/right navigation from any row, touch/trackpad scrolling, a collapsible group sidebar and preview player.
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

The GitHub workflow tests the Go server and browser behavior, starts an isolated synthetic stream, verifies authentication and forced-worker recovery, and only then publishes version tags to GHCR. `latest` tracks the most recently published version tag without a prerelease suffix. The current numbered image is `ghcr.io/minerport/vynodeiptv:0.10.0`; pin it for controlled upgrades. The application remains preview software.

For the existing local development Compose setup, run `scripts/start-local.ps1 -Build`. It uses the existing Signal TV local volumes and does not switch the running development installation to GHCR automatically.

Build Android with Java 17 and Android SDK 36: `android/gradlew -p android :app:assembleDebug :app:testDebugUnitTest :app:lintDebug`. Debug APKs and signing keys are not committed. Provider credentials, runtime state and private test data are excluded from Git and the container build context.

See [release validation](docs/VALIDATION-0.10.0.md), [architecture roadmap](IPTV-ARCHITECTURE-PLAN.md), [development history](docs/DEVELOPMENT-HISTORY.md) and [third-party notices](THIRD-PARTY-NOTICES.md).

## Current limits

Recovery may briefly rebuffer; unavailable upstream media cannot be reconstructed. Segment/clock monitoring cannot identify an encoded still image or silence. Hardware transcoding, gapless timeline stitching, shared ingest, recording, catch-up and server-restart session recreation remain future work. Unraid compatibility is packaged and container-tested; installation on physical Unraid and Shield hardware still requires acceptance testing.


## Community Apps submission and licensing

The repository includes the root MIT `LICENSE`, required `ca_profile.xml`, app icon and one canonical Docker app template at `deploy/unraid/iptv-server.xml`. Submit this repository URL: **https://github.com/minerport/VynodeIPTV**. In the Community Apps portal, use Re-check, Validate and Scan before submitting for review. Repository readiness does not mean the app has been accepted or listed.

VynodeIPTV's original source, templates, metadata and artwork are licensed under [MIT](LICENSE). Bundled dependencies retain their own licenses; see [third-party notices](THIRD-PARTY-NOTICES.md). The container image includes separately licensed components such as FFmpeg and Debian packages.

See the [Community Apps submission checklist](docs/COMMUNITY-APPS.md).
