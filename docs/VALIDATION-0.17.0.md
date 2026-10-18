# 0.17.0 — Personal interests and For You

- New owner-scoped encrypted server interests and authenticated recommendation endpoints. Snapshot copying, persistence, input bounds and administrator/device isolation are regression-tested.
- Shared bundled directory: 154 NFL/NBA/MLB/NHL/MLS teams, emblem URLs, genres and US state aliases. Browser and Android catalogues are byte-identical.
- Matches use imported EPG, explicit local-station overrides, city/state, and XMLTV categories with clearly labelled text fallback. Full catalog scan; no stream acquisition or per-channel network fan-out. Hidden groups and expired/out-of-window programmes are excluded; each current/upcoming section is capped at 40.
- Android standalone uses its local EPG cache and account-scoped settings. Companion uses its paired server identity. Regression tests cover ambiguous team names, category precedence, local overrides, hidden groups and offset timestamps.
- Browser fixture checked desktop and 390px phone layouts, saved interests, reload persistence, cancel, current/upcoming sections, emblem display and no horizontal page overflow.
- Android 11 emulator checked pairing to the isolated fixture, opening For You, repeated team check/uncheck/save, retained settings and current sports listings. Emblem network loading fell back to abbreviations on that emulator. No VynodeIPTV crash was recorded during these actions. This is not physical Shield acceptance.
- Android build, 72 JVM tests and lint passed; 49 browser unit tests and Go tests/vet passed. Linux race and playback/recovery smoke checks gate container publication.
