# Docker / browser 0.6.0 validation

Date: 2026-10-12. Android remains 0.5.0; this release changes the Docker server and browser dashboard.

## Implemented

- Tiered icon/group navigation with group selection collapsing into the full guide; neutral glass styling; preview and controls above guide.
- Independent browser tiles in single, two, four and six layouts. Audio selection follows focus/hover. Fullscreen returns to retained wall, then setup. Clearing exits wall mode completely.
- Server segment watchdog checks playlist-referenced, nonempty local media files, rather than modification timestamps alone.
- Per-session output freshness, observed progress count, worker restarts, scratch usage and 32-event bounded history. Diagnostics contain controlled reason strings, not provider URLs.
- Browser bounded recovery backoff, healthy-budget reset and independent heartbeats. Existing server provider-slot enforcement is retained.

## Passed

- Go tests with race detector and go vet.
- 10 JavaScript tests: guide filtering/clipping/virtualization/order; separate players/audio; clear-to-single and session deletion; fullscreen return; failed deletion retains ownership; bounded recovery.
- Docker production build, including Go tests.
- Live local smoke: unauthenticated API/media denied, paired device cannot administer providers, HLS and valid MPEG-TS generated, forced worker interruption recovered from generation 1 to 2, health freshness and recovery event reported. Temporary session/device removed.
- Isolated browser QA with 250 synthetic channels: group collapse, guide, two simultaneously advancing 1280x720 streams, selected tile unmuted and other muted, fullscreen-to-wall-to-guide, clear then next tune single, third session rejected by two-connection fixture limit.
- Browser fault injection: selected synthetic tile restarted into a new output generation while the other tile continued advancing; selected audio routing was retained.
- Local upgrade: health reports 0.6.0; provider settings match pre-upgrade snapshot; 6,225 catalog channels; no remaining test sessions. Configuration/key backup in ignored `.local/recovery-before-0.6`; previous image retained.

## Limits

No real provider video was opened during this release's tests. Browser audio routing was checked through independent video elements and mute states, not external speakers. Four/six layouts are implemented but only two concurrent decoded streams were exercised. No Unraid deployment, physical Shield test or long-duration soak test was performed.

Recovery can briefly interrupt playback; unavailable upstream content cannot be reconstructed. Server monitoring measures segment output, not the semantic content of pictures or audio. Browser monitoring measures playback position. Frozen encoded content and silence detection, shared ingest, GPU transcoding, server-restart session recreation and seamless timeline stitching remain future work.

## Unraid handoff

Use `deploy/unraid/iptv-server.xml`, targeting local image `iptv-server:0.6.0`. Build the image on Unraid with `docker build -f deploy/docker/Dockerfile -t iptv-server:0.6.0 .`, or export/load it using Docker save/load. Preserve `/config` and its encryption key. Map `/transcode` to writable SSD-backed scratch; UID/GID 10001 must have access. Set the host LAN port using the template. Retrieve the administrator token from `/config/admin.token` privately. No GPU or privileged container mode is required. The existing Windows Compose service remains bound to localhost:8580 and is not reachable by a physical Shield until a LAN binding is deliberately configured.
