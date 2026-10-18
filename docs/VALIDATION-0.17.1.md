# 0.17.1 — Sports season statistics

- ESPN standings endpoints directly inspected for NFL, NBA, MLB, NHL and MLS. Sport-specific scoring labels and source season/phase are retained. Missing fields are not converted to zero.
- Authenticated fixed-league server endpoint uses a bounded HTTPS response, timeout, no redirects, no provider credentials, 15-minute shared cache, failure backoff, and labeled stale fallback up to 24 hours. Tests cover authentication, arbitrary destination rejection, credential isolation, cache hits, stale labeling and expiry.
- Browser and Android matcher require exactly two same-league teams and a matchup marker in the EPG title. Weekly shows/unknown opponents do not inherit a future fixture. These are season statistics, not live scores or historical replay stats.
- Android standalone fetches independently; companion uses server statistics. Public cache persists locally. Separate loading/failure state leaves guide/playback untouched. Material 3 Shield compatibility pin is retained.
- 51 browser tests, 74 Android JVM tests, APK build/lint, Go suite and vet passed. Browser desktop/390px phone fixture verified opponent data, expandable details, all 32 NFL standings rows, no page overflow and no JavaScript errors.
- Physical Shield 0.17.0 was installed in place and launched before this sports change. Statistics acceptance against an updated Unraid server remains a user-device check.
- Linux race tests, container build and synthetic playback/recovery smoke gate container publication.
