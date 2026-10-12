# Android 0.4.3 — tiered TV guide

Implemented from the supplied layout references:
- Narrow left navigation rail, separate groups tier, and preview/timeline workspace.
- Choosing a group hides both left tiers and moves remote focus to the first channel in the expanded guide. Back or Groups reopens the group tier without clearing timeline/scroll state.
- Search, Favorites, Wall, Sync and Setup are accessible through the rail. Favorites pinning, custom groups, hiding and reordering remain available.
- Removed the large app toolbar. Blue-gray panels and a light focused row accompany the preview above the timeline, programme title/time beside it, and active group name above the channel list.
- Focusing guide entries updates the programme details without retuning the preview. Standalone descriptions display when supplied by Xtream; the current companion guide API does not expose descriptions. No placeholder descriptions are invented.
- Preview actions remain underneath the video. Existing video-wall/fullscreen routing is unchanged.

Validation: Android assembleDebug, eight existing unit tests, and lintDebug. No emulator/Shield visual or remote-focus runtime verification was performed. In particular, test group selection/collapse, Back/reopen, scroll restoration, search, and switching between fullscreen and the expanded guide on the physical remote.

Artifact: artifacts/Signal-TV-0.4.3-debug.apk and artifacts/SHA256SUMS-0.4.3.txt. Server/dashboard remain 0.3.0.
