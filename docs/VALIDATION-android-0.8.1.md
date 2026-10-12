# Android 0.8.1 validation

- Back from single-stream fullscreen restores the playing channel row and remote focus, retaining the selected group and time window. If that channel is excluded by the current group/search, the guide falls back to All channels. Existing fullscreen-to-video-wall behavior is retained.
- Fullscreen metadata selects the programme whose interval contains the current time on the playing channel. Historical or future listings are not used as a fallback. Missing current listings show No EPG data.
- Fullscreen metadata refreshes every 30 seconds, with channel-scoped state and cancellation when leaving fullscreen. A failed refresh retains only that channel's known listings, still filtered by current time.
- Android debug build, 29 unit tests and lint passed. The added regression test covers out-of-order history/current/future listings, exact programme boundaries and gaps.
- Physical Shield remote-focus and playback validation remains pending. Server remains version 0.8.0.
