# 0.13.0 validation

Episode-aware metadata and bounded background enrichment for server/browser, standalone Android and server-linked Android. Provider titles, descriptions and broadcast times remain authoritative.

Local verification:
- Go tests and vet passed. Added coverage for XMLTV numbering and parsed episode fields, title markers, episode cache isolation, episode-year vs series-premiere matching, ambiguous series, episode subtitle mismatch, 404 fallback, 429 propagation, 24-hour horizon, 300-item bound, deduplication, background disabled/foreground priority, and warming/reusing a cache without a viewer.
- 29 browser behavior tests passed, including episode query/cache identity and SxxExx labels.
- Headless Edge at 390px and 1280px rendered synthetic episode details/still artwork under the actual CSP, retained keyboard focus and original timeline row height. Phone-width fixture screenshot inspected.
- Android assemble, 48 JVM tests and lint passed (zero errors, 25 warnings). Version 0.13.0/code 20 uses the existing signing certificate.

Publication is gated by Linux race tests, browser tests, Docker build, live-stream recovery and recording/browser-copy smoke. No real TMDB credential was used for automated tests. Real provider episode numbering, Safari and physical Shield acceptance remain user-device checks.

Background warming stores metadata/image URLs, not bulk image files. Server runs without viewers; standalone Android uses active-app warming plus the existing OS-scheduled refresh job, which Android may defer. Both require imported EPG data. Missing/ambiguous metadata leaves provider guide information available.
