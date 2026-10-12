# Android 0.4.5 — fully exit wall mode

Removing the last occupied panel now invokes the same complete cleanup as Exit wall & stop all and Remove all streams: pending requests invalidated, engines closed, selections/status cleared, active slot reset, layout reset, wall mode disabled and cleared scene saved. Removing one of several panels preserves the remaining wall and selects a remaining channel if needed.

Back no longer enables wall mode during normal single-channel viewing. Scene persistence records wall intent separately from channel occupancy; restoring a single channel no longer implicitly enables panels. Legacy multi-channel scenes retain wall intent; legacy single-channel scenes default to normal viewing.

Passed: assembleDebug, eleven unit tests (including three wall exit/restore regressions), and lintDebug. Physical Shield reproduction/verification remains pending. Acceptance: clear an entire wall or remove its last panel, select another guide channel, confirm normal fullscreen playback; Back should show the guide and must not reactivate panels. Repeat after restarting the app.

APK: artifacts/Signal-TV-0.4.5-debug.apk. Checksum: artifacts/SHA256SUMS-0.4.5.txt. Server/dashboard unchanged.
