# 0.16.7 — Live guide clock

Android standalone, server-linked Android, and the browser advance the guide window every 15 seconds. Future windows remain fixed until real time catches up. Browser visibility changes catch up immediately. Expired browser programme focus falls back to the same channel; Android refreshes expired focused programme details.

Validation before publication: Android assembly, 67 JVM tests and lint passed; browser tests cover live clock progression, future browsing, overnight suspension, and hidden/logged-out guards. The Material 3 1.3.2 compatibility pin remains in place. Physical Shield confirmation applies to the prior group/Favorite crash fix; this guide update has not been physically validated.

Container publication is gated on Linux race tests, vet, browser tests and authenticated synthetic playback/recovery checks in GitHub Actions.
