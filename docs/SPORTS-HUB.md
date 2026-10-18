# Sports hub — 0.19.0

Open For You in the guide sidebar, then Games, Teams, Standings, Players or Compare. Browser and Android use the same organization; Android works standalone or linked.

- Games includes all supplied upcoming games for selected teams, live/recent results, and full supplied team schedules. Without a selected team it shows the current league scoreboard, not a complete league-season schedule. Compare matchup selects both opponents.
- Teams shows identity, record, conference, scoring summaries and categorized season stats. Quick team modals remain on For You.
- Standings has numeric column sorting, conference and My teams filters, and name search. Select a team to open its page. Missing values remain last.
- Players has season/phase and team filters, category tabs, pagination (50 per source page), seven useful columns, and career drilldowns. Sort statistic exposes the other supplied category fields. Search filters the current page, not every player in the league. Team filtering uses the actual roster endpoint.
- Compare aligns categorized stats for up to four teams. Player-versus-player comparisons and play-by-play are not included.

NFL, NBA, MLB and NHL full-player feeds were verified. Coverage varies by phase and season; the tested MLS full-player feed was empty. Missing data is explicitly unavailable, never replaced by zeros.

TV controls support D-pad focus. Search opens on selection. Browser tables scroll horizontally on phones and retain the identity column. The workspace uses Vynode styling with information hierarchy informed by [ESPN](https://www.espn.com/nfl/stats/player) and [NFL](https://www.nfl.com/stats/player-stats/).

ESPN determines game opponents, time and status. EPG provides possible viewing channels on For You; a team channel never certifies a live game. Games without matching channels remain listed, with no seven-day cutoff for team schedules. The full workspace handles stats and schedules; channel playback remains on For You.

Scoreboards cache one minute, schedules five minutes and statistics 15 minutes. Game cache expires after five minutes; stats can remain cached for 24 hours with a label. Live labels age out after 90 seconds. For You refreshes games each minute; use Refresh in the workspace for newer data. Fixed HTTPS ESPN destinations receive no provider credentials.

Linked Android player tables require server 0.19.0. Standalone Android fetches independently. Interests remain per user/provider profile. Emblems belong to their teams, and public source availability is not guaranteed.
