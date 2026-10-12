# Signal TV 0.3.0 validation — 2026-10-12

Implemented in both clients: continuous vertical channel scrolling, Favorites pinned at the top, channel star toggles, alphabetical default group ordering, saved custom ordering, and hide/restore group controls. Provider and custom groups can be reordered together. Favorites cannot be hidden or moved. Preferences retain the existing per-profile/per-device ownership model.

Android fullscreen now hides the channel/title overlay with the controls after 3.5 seconds. Back changes presentation to a small preview without stopping or recreating the PlaybackEngine. GuideUiState retains selected group, search, timeline, both lazy-list scroll states and cached listings while fullscreen is displayed. Selecting the preview, Fullscreen, or the playing channel expands the same player. Stop, another Back while browsing, or leaving the app ends playback.

Passed:
- 15 Go tests with race detection and go vet, including persistent owner-isolated preferences, partial updates, hidden groups and Favorites pinning validation.
- Five JavaScript tests covering group filtering, timeline clipping, default alphabetical ordering, saved user ordering and bounded continuous viewport rendering.
- Android assembleDebug, two existing recovery unit tests, lintDebug: 0 errors, 14 warnings.
- Browser UI on a disposable 250-channel fixture: initial alphabetical groups; continuous scroll from channel 1 to 250; nearby EPG loaded; final channel favorited; group reordered; another hidden and restored; favorite/order/visibility retained after reload. Only 12 rows were rendered at the bottom of the 250-channel list.
- Local production container updated to 0.3.0; 6,225 channels restored, new preference endpoint available, configured source retained.
- Post-update stream smoke: authenticated HLS and TS segment, denied unauthenticated access, denied device administration, worker recovered from generation 1 to 2; temporary session/device cleaned up.

Not runtime-verified: Android remote focus, fullscreen overlay timing, video surface transition, or Back/mini-player behavior on an emulator or NVIDIA Shield. These are implemented and build-checked; physical device acceptance remains required. The browser fixture did not play provider media. Existing provider/media validation is recorded separately in VALIDATION-0.2.0.md.

Guide channel lists are continuous; Earlier/Now/Later still move the displayed time window. Hidden groups do not remove their channels from All channels or Favorites. New unranked groups appear alphabetically after explicitly ranked groups. Group visibility and favorites do not change provider subscriptions or streaming permissions.

Artifacts: Signal-TV-0.3.0-debug.apk, SHA256SUMS-0.3.0.txt and Signal-TV-0.3-guide-preview.png under artifacts/. The preview is a browser fixture screenshot, not a Shield capture. Local dashboard: http://localhost:8580. Unraid template references 0.3.0 but no Unraid deployment occurred.

APK SHA256: 516bde8f2bc860224cad848be65aa1933d819867ef4f1f4eede2e317e42c2201
