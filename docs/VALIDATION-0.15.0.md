# Version 0.15.0 validation

Appearance Studio adds three layouts, seven palettes, three surface treatments, bundled fonts, guide presentation controls, safe preview/apply/revert and named preset import/export to Android and the browser.

- Go tests and vet passed, including synthetic media/DVR tests.
- 38 JavaScript tests passed. New checks cover shared Android/browser catalog parity, bounded versioned imports, normalization, scaled row metrics, preset validity and palette contrast.
- Android assembleDebug, 60 JVM tests and lint passed: 0 errors, 33 warnings (including existing warnings and preference/Compose API suggestions).
- APK identity: local.signaltv.app, versionName 0.15.0, versionCode 30. Existing signing identity retained.
- Headless Edge with an isolated localhost synthetic stream verified unchanged player element/session count and retained guide scroll position during theme changes, Pearl at 130% text, automatic reversion, named presets, reload persistence, Cinema layout, desktop and 390px phone settings panels without horizontal overflow. Screenshots inspected.
- Android TV emulator verified opening Appearance Studio with the companion server unavailable, selecting presets using D-pad/OK, preview/keep, timeout reversion, and preferences retained across an APK update. Easy Read and Daylight guide presentations were inspected. No real provider was used.

Physical Shield acceptance, Android document-picker availability, Android live-stream continuity while changing layouts, TalkBack, all system font-scale combinations and long-duration performance remain unverified. Android glass is simulated; browser backdrop effects depend on support. Preferences are local to each device/browser; no theme synchronization is claimed. No production Unraid instance was changed.

The existing Linux container workflow remains the publication gate: race tests, vet, browser tests, container build, authenticated synthetic media and worker-recovery/DVR smoke before GHCR publication.
