# Version 0.16.0 validation

Adds optional Discover home, Side Preview and Wall First layouts, Guide/Home/Wall startup choices and opt-in administrator-published appearance sharing.

- Go tests and vet passed, including synthetic media/DVR checks. New shared-theme checks cover anonymous and paired-device write denial, authenticated device reads, strict field validation, stale revisions and encrypted-state persistence.
- 40 JavaScript tests passed, including bounded/deduplicated Discover candidates and upcoming-programme filtering.
- Android assembleDebug, 62 JVM tests and lint passed: 0 errors, 34 warnings. Package local.signaltv.app, versionName 0.16.0, versionCode 31; existing signing certificate retained.
- Headless Edge against the final isolated localhost build passed Discover navigation, matched artwork presentation using synthetic metadata/image responses, Home/Wall/Guide switching with the same live video element, Side Preview and Wall First layouts, startup Home, 390px mobile overflow checks, theme publication and a second opt-in browser receiving the theme while retaining local Guide startup. No page errors. Screenshots inspected.
- Android TV emulator passed Discover with an unavailable companion, Side Preview rendering, shared theme retrieval after pairing to a disposable demo server, retaining local Guide startup, and Home startup after a restart with theme following enabled. Light and Arctic presentations were inspected. Runtime crash log was empty during checks.

No real provider, production Unraid data or physical NVIDIA Shield was used. Real-library artwork match quality, standalone provider acceptance, all font-scale/layout combinations, long-duration playback, physical remote accessibility and Android document picker compatibility remain pending. Android standalone Discover reads local EPG/cache; it does not generate a new provider request for every home card. Recording cards open the existing library. Shared themes are one administrator-published setting per server, fetched on connection, not per-user or live background synchronization.

The Linux workflow remains the publication gate: race tests, vet, browser tests, container build and authenticated synthetic streaming/recovery/DVR smoke before publishing GHCR tags.
