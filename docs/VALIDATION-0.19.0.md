# Sports workspace validation

- Full Go suite and go vet passed. Added fixed-host player destination and invalid sort/page/phase/team tests.
- Browser: 60 tests passed. Desktop tables, career drilldown, standings filtering and team navigation checked. Inspected a 390px preview and corrected a legacy mobile flex rule.
- Android: 82 tests, debug build and lint. Parser tests use real JVM org.json. API-30 emulator with isolated localhost server displayed live ESPN multi-column tables.
- Public NFL/NBA/MLB/NHL player feeds and a team roster endpoint verified. MLS empty coverage remains explicit.
- Material 3 remains 1.3.2 for Shield compatibility.

Limits: player search applies to the current page; no full league-season schedule without choosing teams; no player comparison or play-by-play. Linked player tables require server 0.19.0. Release and physical Shield verification are recorded separately.
