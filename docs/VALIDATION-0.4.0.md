# Android 0.4.0 — 2026-10-12

Android-only release; the running Docker server and dashboard remain 0.3.0. No provider settings or connection limits were changed.

## Implemented

- Select-to-edit controls for login, channel search, and custom group editors. Remote focus does not instantiate a text editor. Activation opens an editing dialog; Done applies, Cancel discards.
- Neutral charcoal/silver palette, layered gradient and translucent panels, brighter remote-focus borders, larger programme text.
- Saved profile recognized before network loading. Metadata cache renders channels while refreshing; channel parsing runs off the main thread. Failed decryption preserves the saved data and offers Retry or explicit reset.
- Visible EPG fetched in one companion request or at most three concurrent standalone requests. Existing five-minute cache retained; the repository semaphore also bounds requests across scrolling cancellations.
- Independent two/four/six-panel players. A smaller layout retains extra panels. Selecting a panel opens fullscreen; Back returns to the retained guide; Back again opens multiview. Navigation does not close players. Only the selected panel has audio.
- Explicit per-panel removal and Remove all. Scene IDs/layout/selection persist per connection without serializing credentials. Fresh launch restores selections; Multiview or Fullscreen resumes them. Leaving the foreground releases sessions; returning resumes retained selections.
- Delayed starts are invalidated per panel and across lifecycle changes. Replacing one panel waits for its previous session cleanup. Standalone respects the advertised account maximum; companion session admission stays on the server.

## Verification

Android assembleDebug, testDebugUnitTest and lintDebug passed. Five unit tests: two existing retry-budget tests plus three regressions for rapid replacement, independent pending panels/removal, and lifecycle cancellation. Lint: zero errors and 15 warnings (dependency/update notices, existing resource/layout warnings, and Kotlin extension suggestions). Exact packaged APK checksum is in artifacts/SHA256SUMS-0.4.0.txt.

No emulator or physical Shield runtime verification was performed. Compilation and unit tests do not establish visual quality, keyboard timing, decoder capacity, surface handoff continuity, actual startup speed, or six-stream stability. No live provider streams were opened for this release. Existing server validation is in VALIDATION-0.3.0.md.

## Device acceptance

1. Upgrade without clearing app data. Cold launch should show the guide/loading state, not connection setup. Repeat with networking unavailable; saved login must remain.
2. D-pad through login/search/group fields: keyboard appears only after Select. Verify Done, Cancel, and password masking.
3. Compare warm channel-list loading and guide population. Scroll quickly and return from fullscreen; group, search, timeline and scroll position should remain.
4. With enough authorized provider connections, fill two panels, then four/six. Check one audible panel, change/remove individual channels, smaller/larger layout retention, and independent recovery.
5. Select a panel: fullscreen -> Back -> guide -> Back -> multiview. Verify no extra provider sessions are created during these transitions.
6. Background/resume and restart. Confirm sessions release in background, saved panels restore, and explicit removals persist. Check actual server session counts during rapid channel replacement.

This implements in-app multiview, not Android system floating PiP. Each distinct channel uses one provider connection and decoder; layout availability does not guarantee the subscription or Shield can decode that many streams. Existing server provider limits may allow fewer than the displayed panels. Recovery remains bounded and can rebuffer; it is not gapless.
