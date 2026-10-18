# Live score overlay

While a live channel is playing, open **Live scores** in the browser player controls or Android fullscreen menu (press OK). On Android, **Up** while the fullscreen menu is closed toggles the panel. The panel starts hidden and remembers its settings on the device; Android settings are scoped to the account profile.

- **Same sport** detects a unique league from the channel or programme's team names. Select a league explicitly when detection is ambiguous; NFL is the initial fallback.
- **All live sports** includes supported leagues; **Pinned games** lets you choose up to 12 live games across NFL, NBA, MLB, NHL and MLS.
- Six corner/edge positions, compact/larger text, three background opacities, red/gold/ice accents, and one/three/five visible games.
- Additional games use pages. Optional rotation advances every 12 seconds; manual paging is in the panel footer (browser) or Live scores settings (Android).

ESPN scoreboard status determines whether a game is live. Scheduled, finished, delayed, cancelled and stale games are excluded. Data is requested every 30 seconds while needed, subject to the existing one-minute sports cache. Source observations older than 90 seconds are excluded. These are periodic scores, not a real-time or betting feed. Finished pins remain saved but no longer appear on the live panel; clear them in settings.

Same-sport/all-live modes omit the match being watched only when both team names can be identified in the current EPG title. An explicit pin can include that match. Pinning never tunes a channel or changes audio. The Android panel does not take remote focus and temporarily gives way to the normal fullscreen information/menu.

Android works standalone or server-linked. Browser support includes inline and container fullscreen playback. **iPhone native video fullscreen cannot display HTML score overlays**; use the inline browser player for scores on that device. No credentials or provider stream URLs are sent to ESPN.

Validation for 0.20.0: Android debug build and lint, 85 Android unit tests, 65 browser tests; browser fixture checks for cross-sport selection, placement and hide/show. Fixture scores are synthetic. Physical Shield installation does not by itself verify live game behavior.
