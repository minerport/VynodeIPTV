# Android 0.4.2 — guide layout

- Compact 210 dp-wide, 48 dp-minimum-height search control in the app header; editing still requires activation.
- Preview moved inside the guide's right column, above the timeline. Groups sidebar height no longer depends on preview visibility.
- Larger adaptive 16:9 preview (38% of available workspace height, bounded to 100–200 dp), with Fullscreen and Remove all streams directly beneath it. Channel and programme details sit beside it.
- Timeline retains its independent scrolling state. Existing multiview and audio routing behavior unchanged.

Validation: assembleDebug, all eight existing unit tests and lintDebug passed. No new implementation-mirroring tests added for this layout change. Layout, remote focus traversal and device-specific sizing have not been visually verified on a Shield or emulator.

Artifact: artifacts/Signal-TV-0.4.2-debug.apk; checksum: artifacts/SHA256SUMS-0.4.2.txt. Docker server/dashboard remain 0.3.0.
