# Signal TV 0.2.0 validation — 2026-10-12

## Implemented

- Android TV guide with provider group names, search, paged channel rows, a 90-minute timeline, current-time marker, and local programme times.
- Browser dashboard player with a three-hour guide, provider groups and persistent custom group editing.
- Custom group CRUD: encrypted server persistence with owner isolation; local Android persistence scoped to provider profile.
- Android standalone HTTP redirect opt-in, retained login/settings, safer playback diagnostics.
- Companion generation reload fix and orderly session shutdown before the next channel opens.
- Android remains the channel controller. The server supplies the requested feed and owns upstream transport, HLS buffering, remux/conversion and bounded recovery.
- Companion output choice: original-quality remux or server CPU conversion to H.264/AAC. Browser uses the conversion profile.
- Session-private loopback media relay: verified TLS, explicit HTTP opt-in for every request/redirect/playlist child, HLS URI rebasing, read deadlines, opaque worker URLs and bounded resource map.
- Provider HTTP opt-in enabled with the user's explicit approval. No provider login, token or remote stream URL is included in this report.

## Passed

- 14 Go tests; race detector and go vet. Tests include category import, guide window filtering, persistent group ownership, denied cross-owner edits/deletes, redacted diagnostics, HLS URL rewriting and untrusted TLS rejection.
- Two JavaScript tests: group namespace/filtering and programme clipping.
- Android assembleDebug, two unit tests and lintDebug: 0 errors, 13 warnings.
- Docker image build and health endpoint version 0.2.0.
- Production local catalog restored: 6,225 channels across 46 groups (includes synthetic test channel/group). Provider XMLTV import completed; guide endpoint returned its 5,000-entry page cap.
- Browser UI on isolated synthetic server: live 1280x720 video, readyState 4, not paused, advancing playback time; custom group created and retained after page reload; Stop releases playback.
- Local server smoke: authenticated playlist and MPEG-TS segment, API/media denial without authentication, device denied administrator operations, worker interrupted and recovered from generation 1 to 2, test session/device cleanup.
- Real provider MPEG-TS ingress through the relay: conversion profile produced H.264 1280x720 plus stereo AAC; captured segment decoded without errors. Original-quality remux also produced valid HLS segments. A second session was rejected at the configured one-connection limit.

## Deliverables

- `artifacts/Signal-TV-0.2.0-debug.apk`
- SHA256: `9a70ff93b1bae23807519dec9e8be42a6cad7e8b0ec5827a12d3230e7c3ec2e9`
- `artifacts/Signal-TV-0.2-guide-preview.png` — synthetic browser playback, not a Shield screenshot.
- Local server: http://localhost:8580 ; configured source data retained in its named Docker volume.
- Unraid template updated to the 0.2.0 local image; not deployed to Unraid.

## Limits and next acceptance gates

- APK has not been runtime-tested on an emulator or physical NVIDIA Shield. TV focus behavior, hardware decode, audio passthrough and sustained playback still need device validation.
- Standalone users must enable Connection → Allow HTTP streams and redirects for this provider. The server setting does not modify settings on an Android device.
- EPG availability depends on provider mappings and listings. Standalone fetches short EPG for visible channels and caches for five minutes; it cannot supply listings absent from that endpoint. Future cells tune live, not catch-up.
- Custom groups belong to the administrator or paired device that creates them; cross-device sharing is not implemented.
- Recovery is bounded and may rebuffer on generation changes. It is not gapless failover. No shared ingest, GPU conversion, automatic format negotiation, catch-up, recording, or scheduled EPG refresh yet.
- Server transport TLS and playlist rewriting were tested independently; the live provider integration used MPEG-TS ingress. Real-provider HLS ingestion remains a separate acceptance case.
- Docker binds localhost only. Physical companion access needs the intended LAN/Unraid deployment; it was not opened to the LAN in this change.

Reference checked for the Android redirect change: https://developer.android.com/reference/androidx/media3/datasource/DefaultHttpDataSource.Factory (cross-protocol redirects default to false).
