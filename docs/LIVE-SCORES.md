# Live score overlay

While a live channel is playing, open **Live scores** in the browser player controls or Android fullscreen menu (press OK). On Android, **Up** while the fullscreen menu is closed toggles the panel. The panel starts hidden and remembers its settings on the device; Android settings are scoped to the account profile.

- **Same sport** detects a unique league from the channel or programme's team names. Select a league explicitly when detection is ambiguous; NFL is the initial fallback.
- **All live sports** includes supported leagues; **Pinned games** lets you choose up to 12 live games across NFL, NBA, MLB, NHL and MLS.
- Six corner/edge positions, compact/larger text, three background opacities, red/gold/ice accents, and one/three/five visible games.
- Additional games use pages. Optional rotation advances every 12 seconds; manual paging is in the panel footer (browser) or Live scores settings (Android).
- **Last play** shows an optional two-line description when ESPN supplies it. NFL games also show a possession diamond beside the team and down/distance when available. Missing details are omitted.
- **Spoiler delay**: Off (default), 15, 30, 60, 90 or 120 seconds. Whole observations are held, including scores, plays, clocks and final status. The panel and game picker use the same delayed observations. Initial startup shows **Building delay buffer** until enough history exists. This is an additional delay, not automatic video synchronization.
- **Updated … ago** describes the age of the displayed source observation, including intentional delay; it is not the age of the play. ESPN may publish later than the real action.

ESPN scoreboard status determines whether a game is live. Scheduled, finished, delayed and cancelled games are excluded. Refresh attempts run about every 15 seconds while the panel/settings are active, with 15-second server and standalone caches. Network time and concurrent cache users can add delay. Browser requests pause when its tab is hidden. These are periodic scores, not a real-time or betting feed. Finished pins remain saved but no longer appear on the panel; clear them in settings.

Refresh does not clear existing scores. Failed or stale responses preserve the previous observation and display **Update delayed · last known score**. Observations older than 90 seconds are not certified live; last-known in-progress scores remain visible for up to five minutes. After expiry the panel reports unavailable/retrying rather than falsely reporting no games. A successful empty scoreboard can confirm no games. Delayed observations are kept only in a bounded in-memory buffer. Android parses scoreboards on a background dispatcher; the panel does not rebuild the video player. Android limits rows to available screen height when details are expanded.

Same-sport/all-live modes omit the match being watched only when both team names can be identified in the current EPG title. An explicit pin can include that match. Pinning never tunes a channel or changes audio. The Android panel does not take remote focus and temporarily gives way to the normal fullscreen information/menu.

Android works standalone or server-linked. Browser support includes inline and container fullscreen playback. **iPhone native video fullscreen cannot display HTML score overlays**; use the inline browser player for scores on that device. No credentials or provider stream URLs are sent to ESPN.

Validation for 0.20.1: Android debug build and lint, 88 Android unit tests, 68 browser tests, and targeted Go sports tests. Tests cover optional play parsing, fifteen-second server caching, delayed final/empty observations, and bounded stale-score retention. Browser fixture checks include failed-refresh retention and delayed labels. Fixture scores are synthetic. Physical Shield installation does not by itself verify live game performance.
