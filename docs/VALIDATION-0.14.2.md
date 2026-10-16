# Version 0.14.2 validation

This advances guide interaction and channel identity. It does not complete the eight-part product roadmap.

- Go test suite and go vet passed, including synthetic media/DVR tests and provider-logo import validation.
- 33 JavaScript tests passed.
- Android assembleDebug, 57 JVM tests and lint passed; lint reports 0 errors and 28 warnings.
- APK identity: local.signaltv.app, versionName 0.14.2, versionCode 29.
- Headless Edge against the final isolated localhost build passed: HTTPS logo rendering, right-click favorite save, keyboard context menu, pointer hold without tuning, and programme details; no page errors. Browser screenshot inspected.
- TV emulator with linked disposable demo server passed: D-pad navigation to a channel, hold OK opening actions without tuning, Back restoring channel focus, Menu-key actions and favorite persistence without a crash. Pure press-state tests cover short press, repeat/hold suppression, cancellation and focus loss.
- Emulator checks use synthetic content. Physical Shield remotes, real provider logo/CDN compatibility, standalone runtime/device acceptance, TalkBack and high font-scale acceptance remain unverified. Both Android repository modes import logos and share the action UI; no provider accounts or production Unraid configuration were used in testing.

HTTPS logos without query strings/embedded credentials are supported. Android image downloads do not follow redirects, use two concurrent requests, cap encoded images at 512 KiB, sample decoded dimensions to 128px and maintain a 4 MiB memory cache. Browser artwork loads directly under the HTTPS image CSP. Missing/rejected images fall back to channel names. Existing programme metadata and broadcast timing remain unchanged.

0.14.1 was not released: its CI smoke assertion still expected the prior TMDB-only image CSP. 0.14.2 updates that assertion while retaining script/connect restrictions. The failed tag is retained rather than rewritten.

The corrected verify-server smoke passed against an isolated Windows demo process: authenticated HLS, forced-worker recovery, independent DVR capture, ownership checks, saved resume and browser conversion. Linux container CI is the release gate.
