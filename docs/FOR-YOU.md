# For You

Open **For you** in the browser navigation or the Android guide menu. Choose **Edit interests**, then select teams, enter your city/state, pick genres, and optionally select specific local stations. Save once; preferences survive restarts and logout. Android text fields require Select before editing, and checkboxes remain in their list after a toggle.

- **Your teams:** current and upcoming programmes that mention a selected team. NFL, NBA, MLB, NHL and MLS are included (154 teams). Full team names match directly; short nicknames require league/sport context to reduce ambiguous matches. Team emblems load from ESPN with abbreviation fallbacks.
- **Around home:** channel names/group names containing the city and state, including US state-name/abbreviation aliases. Providers often omit geography or use callsigns: select those stations manually. No GPS, IP geolocation or claimed broadcast-market lookup is used.
- **Your genres:** XMLTV categories are preferred; title/description text is a fallback when categories are absent. Each card labels the match reason. Refresh EPG after upgrading to import categories into an existing cache.
- Each section separates **On now** and **Coming up**, using local device time and up to seven days of available EPG. Up to 40 listings per section/time bucket are shown. Hidden provider groups are excluded. Selecting a future listing opens its details/guide for scheduling; it does not imply catch-up or a live game feed.

The page uses the entire imported guide, not the twelve-channel sample used by Collection. It never opens streams to find recommendations or makes one remote provider request per channel. Refresh EPG through the existing controls if guide data is missing. A team mention may refer to a replay, documentary or studio programme; no live score or external fixture schedule is implied.

## Storage and modes

Browser interests are stored in the server's encrypted configuration for the signed-in administrator. Paired Android devices have their own server identity and interests; changing one does not overwrite another. Standalone Android interests are stored locally under the saved provider account key. Switching modes keeps each profile's choices, and no server is needed for standalone discovery. Interests are not automatically shared between the administrator and paired devices. Include app/server configuration in your normal backups.

## Images and limitations

The bundled team directory works offline; emblem images require access to a.espncdn.com and may be blocked on some networks. Only emblem URLs are requested, without IPTV credentials. Team identities/colors are a dated snapshot, and trademarks belong to the teams. Matches depend on provider spelling, guide categories, import freshness and schedule coverage; no available listing means an honest empty state, not a fabricated broadcast.

## Sports statistics (0.17.1)

Sports center shows standings for each selected league and season summaries for selected teams: records, available conference positions, streaks, scoring for/against and differential. Expand Season statistics in the browser, or select a team on Android, for the full available summary. League standings include all teams. Football/basketball use points, baseball uses runs, hockey/soccer use goals. Missing fields are omitted, never invented as zero.

Sports EPG cards show both teams' season records and scoring when the title explicitly identifies exactly two teams in the selected league and contains a matchup marker (vs, versus, at or @). Ambiguous titles show “Opponent not identified.” A weekly show is not assigned the next scheduled opponent. These are current source-season statistics, not historical replay statistics or live game scores. The source season and preseason/regular/postseason phase are displayed. No player leaderboards or play-by-play are included.

Source: [ESPN standings](https://site.api.espn.com/apis/v2/sports/football/nfl/standings), using corresponding NFL/NBA/MLB/NHL/MLS endpoints. This public endpoint has no availability guarantee. Refresh is cached for 15 minutes; previously fetched data may be served for up to 24 hours with an explicit cached label. Beyond that, statistics are unavailable. Server memory cache resets on restart; standalone Android stores public statistics locally. No IPTV credentials are sent to ESPN. Linked Android uses the server endpoint and needs server 0.17.1; standalone Android fetches independently without a server. Outages never block guide/playback.
