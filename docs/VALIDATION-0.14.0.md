# Version 0.14.0 validation

This is milestone 1 of the approved product upgrade, not completion of all eight workstreams. See PRODUCT-UPGRADE.md for implemented behavior and remaining work.

- Go suite passed; go vet passed.
- Synthetic FFmpeg timeshift test retained ten HLS segments including the initial segment and decoded buffered media successfully. It does not validate a full 60-minute soak or upstream outage continuity.
- 32 JavaScript tests passed, including seek bounds, density virtualization and recent-channel ordering.
- Android build and 52 JVM tests passed; lint: 0 errors, 28 warnings (including SharedPreferences KTX suggestions).
- Headless Edge against an isolated localhost demo server passed login/catalogue, large-row sizing, programme search and saved timeshift settings with no JavaScript page errors. Screenshot inspected.
- Physical Shield navigation, Android seek behavior against a populated server window, actual provider outages, and long-duration storage/load tests remain unverified.
- Local test server used synthetic content and disposable configuration. Production Unraid configuration and provider credentials were not used.

Timeshift is off by default. Enabling it applies to new sessions; configure enough scratch capacity for the bitrate and number of streams. Reconnects reset the rolling window; a session stops if its byte budget is exceeded. Standalone Android rolling disk buffering is pending.
