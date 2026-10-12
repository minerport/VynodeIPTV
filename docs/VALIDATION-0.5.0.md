# Android 0.5.0 — first playback-health phase

## Implementation

Each PlaybackEngine has independent byte/frame counters, a health detector, retry budget and a bounded 32-event history. Once per second it samples player position/state, buffer duration, visible frame delivery, selected-audio decoder progress when available, dropped frames and audio underruns. Deliberately hidden video surfaces and muted/audio-passthrough outputs without an active decoder are not treated as stalled renderers. A provider-encoded still image or silence can continue producing normal frames/buffers and is not detected by this implementation.

Recovery is deliberately bounded: initial startup grace 25 seconds; sustained lack of progress 12 seconds; backoff 1/2/4/8/16 seconds. First attempt seeks live only when supported and no player error is present; otherwise reconnects. Second reconnects. Later attempts rebuild only the affected ExoPlayer, rebind its view and preserve selected audio ownership. Sixty seconds of continuously healthy observations reset the budget. A recovered transient cancels the need for its delayed action. Explicit access failures pause instead of repeatedly reconnecting. Rebuild failures pause with a controlled error.

Companion media reconnect/rebuild retains the existing session and does not request an extra provider connection. New server generations cause reprepare as before. Existing server segment-progress recovery remains unchanged; clients do not kill healthy server workers. The server was not redeployed in this release.

Playback health is available from Connection settings and the wall's Back menu. It displays per-panel measurements, first-playback latency, recovery history and catalog cache/refresh timing. Diagnostic events contain only controlled categories/counts, not raw exception messages, URLs, tokens or passwords. They are in-memory only and reset when an engine closes.

## Verification

- Android assembleDebug, testDebugUnitTest and lintDebug.
- 22 unit tests: 11 prior regressions plus 11 health tests covering startup grace, data starvation, frame stall with advancing clock, independent audio stalls, hidden/muted outputs, surface reattachment, pause/resume, buffered HLS download gaps, sustained buffering with incoming bytes, independent panel detectors/counter resets, escalation and bounded history.
- ADB device inventory was empty. No Shield/emulator playback, network-outage timing, codec/passthrough checks or endurance tests could be performed. Unit tests exercise deterministic signals, not actual Media3/network playback. No live provider stream was opened for this release.

## Hardware acceptance still required

Record catalog/cache timing and first-playback latency from the health screen for cold/warm launches and repeated channel changes. While watching a controlled source, test 5/15/30-second network interruptions, audio/video delivery stalls, intentional pause/resume, wall focus changes, fullscreen transitions and a server worker restart. Verify recovery only affects the unhealthy panel and session count stays constant through client rebuilds. Run permitted 2/4/6-panel combinations for an extended session and review dropped frames, memory, underruns and recovery history. Establish thresholds from these measurements before claiming improvements on Shield.

Shared upstream ingest, indexed EPG storage, prefetch and tunable resilience buffers are not part of this phase. Recovery may rebuffer and cannot recreate missing provider content.

## API references checked

- [Media3 transfer callbacks](https://developer.android.com/reference/androidx/media3/datasource/TransferListener)
- [Video frame metadata callbacks](https://developer.android.com/reference/androidx/media3/exoplayer/video/VideoFrameMetadataListener) report frames about to be rendered, not pixel-content health.
- [Decoder counters](https://developer.android.com/reference/androidx/media3/exoplayer/DecoderCounters) require ensureUpdated before cross-thread reads and are diagnostic signals, not proof of audible sound.
- [Analytics callbacks](https://developer.android.com/reference/androidx/media3/exoplayer/analytics/AnalyticsListener) distinguish decoder lifecycle/underruns; codec diagnostic callbacks alone are not used to initiate recovery.

APK: artifacts/Signal-TV-0.5.0-debug.apk. Checksum: artifacts/SHA256SUMS-0.5.0.txt.
