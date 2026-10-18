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
