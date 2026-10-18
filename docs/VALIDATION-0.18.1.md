# 0.18.1 — All upcoming team games

- Removes the sports seven-day cutoff in both browser and Android. Upcoming selected-team games are shown independently of channel/EPG matches; recent/live games and Coming up have separate shelves.
- Adds browser and Android regression cases for a selected-team game 100 days away, exclusion of unrelated teams, and absence of matching channels.
- Browser: 57 tests and desktop/mobile sports explorer UI fixture passed.
- Android: assembleDebug, 78 JVM tests and lint passed, retaining the existing signing identity. VersionName 0.18.1, versionCode 41.
- The 0.18.0 foundation passed Linux race/vet, container build and authenticated synthetic media/recovery checks. The same checks gate this patch's publication. Provider-specific broadcast availability is not verified by ESPN schedules.
