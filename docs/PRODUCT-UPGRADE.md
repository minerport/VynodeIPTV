# Approved product upgrade

The full eight-part improvement programme was approved on 2026-10-15. This is a staged implementation, not a claim that every feature is complete.

## Milestone 1: guide discovery and server buffer

Implemented in 0.14.0:
- Compact, Comfortable and Large Text guide density on Android and browser, with virtualization using the selected row size.
- Android row-artwork preference independent of density.
- Recent channels (last 20, local to the browser or Android account).
- Search channels, imported current/future programmes and owned recordings. Android standalone searches saved XMLTV data plus its loaded guide; it does not make hundreds of provider calls. Server search requires 0.14.0. Results are capped at 100. VOD is not yet indexed. Recording results open the library.
- Opt-in server HLS rolling window of 5/15/30/60 minutes, per-stream scratch cap, cleanup on session closure, browser pause/rewind/live controls. Linked Android uses its existing seek controls. Reconnects begin a new window; this is not gapless stitching or catch-up.
- Search scope, density arithmetic, recent ordering, seek bounds and timeshift validation tests; synthetic retained-media decode test.

## Remaining approved work

1. Reliability: physical Shield automated/recorded acceptance runs, long-duration soak tests, decoder/network failure scenarios, cross-provider fallback only after explicit mapping, server-unavailable direct fallback with existing saved credentials. Existing byte/frame/audio monitoring, staged player recovery and diagnostics are retained.
2. TV interaction: one documented remote contract, long-press context menu, automated screenshot and D-pad regressions at all densities, channel-logo support, font scaling and accessibility acceptance. Search, recents and density are the first portion only.
3. Timeshift: standalone Android bounded disk ring buffer, byte-budget eviction without losing live playback, seek preview, resume after long pauses, and optional provider catch-up. Current server buffer is session-scoped and stops at the configured byte cap rather than silently filling the disk.
4. Guide intelligence: TMDB correct/remove match actions, supplemental XMLTV mappings and time offsets, genre/type filters and rerun flags when trustworthy; VOD indexing and search. Broadcast times must remain provider authoritative.
5. Multi-provider lineup: persistent logical channel identity, explicit source ranking, remapping across catalog updates, safe alternate-feed transition, and connection accounting.
6. Linking/synchronization: phone/QR onboarding, actual per-user identities across devices, conflict-aware sync, encrypted export/restore, optional direct fallback, shared upstream ingest with independently authenticated viewers. Device-owned server preferences today are not cross-device user sync.
7. DVR: series/new-episode rules, duplicate detection, conflict resolution UI, watch-in-progress, quotas/retention, partial-recording repair and optional commercial detection. Existing manual scheduling, padding and conflict checks are retained.
8. Discovery: optional home screen, continue watching/upcoming favourites, provider VOD libraries, parental profiles, named wall presets, and personal channels from recordings/library content. Preserve direct-to-guide launch.

## Release gates

Do not label a milestone complete based solely on compilation. Run relevant unit/security tests, browser flows, synthetic media tests and device checks. Report physical-device checks separately. Never migrate or discard saved provider accounts, favourites, groups or recordings as part of a layout change. The Android app remains independently usable without a server.
