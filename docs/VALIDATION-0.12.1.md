# 0.12.1 validation

Fixes the browser Content Security Policy that blocked TMDB posters, and adds asynchronous visible-guide posters/year/genres to browser and Android. Provider schedule geometry and episode descriptions remain authoritative. Enrichment never gates guide loading or playback.

Local checks passed:
- Go tests and vet, including an HTTP response regression check that TMDB image loading is permitted while script/connect restrictions remain unchanged.
- 28 browser behavior tests, including duplicate lookup deduplication, stale completion after reset, provider description precedence and outage backoff.
- Headless Edge at 390px and 1280px with a synthetic poster served through an intercepted TMDB image URL: both detail/guide images decoded under the actual CSP; focus stayed on the original button, guide row height stayed 58px and detail poster width stayed 80px. Mobile fixture screenshot inspected.
- Android assemble, 44 JVM tests and lint (zero errors, 25 warnings). APK version 0.12.1/code 19, existing signing certificate retained.

Publishing is gated by Linux Go race tests, browser tests, container build, authenticated live-stream recovery and DVR/browser-copy smoke. The container smoke also checks the artwork CSP header.

 Real TMDB authentication, image CDN access on the user network, Safari appearance and physical Shield acceptance remain user-device checks; automated metadata fixtures do not establish those results.
