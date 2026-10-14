# Version 0.11.0 validation

Server and Android preview 0.11.0. Android versionCode 17; application ID and signing identity retained.

## Local checks

- Go tests and vet passed. Recording tests cover encrypted schedule persistence, per-owner access, cancellation/deletion, overlapping reservations, live/recording connection accounting, safe playlist assembly and expired jobs.
- Real FFmpeg synthetic test: interrupted a recording, resumed it within the original window, verified partial status and multiple attempts, and decoded the retained playlist.
- Authenticated API smoke: live media and forced-worker recovery; scheduled recording completed independently, authenticated saved MPEG-TS retrieved, cross-owner cancellation denied, playback position persisted.
- Browser behavior: 20 tests passed, including watch/record EPG choices, independent recording cancellation, and confirmation before deletion.
- Android: APK assembly, 39 JVM tests and lint passed (no lint errors). Windows OneDrive locked the original test-result directory; final checks used an isolated build directory under the local temporary folder.

## Publishing gate

The tag workflow runs Linux Go race tests/vet, browser tests, container build, and the extended authenticated live/DVR synthetic smoke before publishing the image. Versioned and latest registry manifests must match and be anonymously readable before the GitHub release is made public.

## Not verified on hardware

No Android device or browser automation surface was connected during this release. Docker Desktop did not respond locally; Linux container runtime validation uses CI. Physical Shield/USB capture, D-pad screen usability, real-provider recording compatibility, Unraid installation and long-duration/sleep/boot behavior still require device acceptance testing. No such hardware result is claimed.

Standalone recording currently requires the provider MPEG-TS endpoint; original codecs may not play in every browser. Series rules, watch-while-recording, shared ingest, automatic retention and automatic EPG retiming are not included. See [recording guide](RECORDING.md).
