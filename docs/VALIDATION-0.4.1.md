# Android 0.4.1 video wall — 2026-10-12

Changes:
- Two/four/six-panel wall fills the screen with rectangular, touching tiles. No gutters, padding, persistent titles, status labels or control rows. A thin focus outline is overlaid without shrinking the picture.
- Wall video uses aspect-preserving zoom-to-fill. This crops source edges where tile and source aspect ratios differ; fullscreen retains normal fit. Empty tiles offer channel selection.
- Remote focus immediately selects the panel and routes audio. Other panels are muted and their audio tracks disabled, rather than relying only on volume zero for passthrough audio. Switching selection retains each independent playback engine/session.
- New players start muted. Audio ownership is reconciled after registering them, so a late-starting background panel cannot take audio. Repeated selection of the same panel does not toggle its audio tracks.
- Back from multiview fullscreen returns directly to the wall, restoring the selected panel's focus. Back on the wall opens a setup dialog with layout, Change, Remove, Guide and Remove all controls. Standalone single-channel fullscreen retains its guide return behavior.

Validation: assembleDebug, testDebugUnitTest and lintDebug. Eight unit tests cover recovery budgets, stale panel requests, focus/audio transfer without overlapping owners, late background starts, and focusing an empty panel before it starts. Actual Android focus, video surface transitions, audio track handoff latency, passthrough behavior and visual layout require Shield testing; no runtime device verification is claimed.

Device checks: populate two panels; move D-pad focus repeatedly while listening; enter fullscreen on each and verify matching video/audio; Back must restore the same wall panel. Repeat with four/six panels where subscription and decoder limits allow. Open controls with Back, switch layout, change/remove a panel and confirm remaining streams retain their sessions. Test both PCM and passthrough output. Stream/decoder limits remain unchanged.

Server/dashboard remain version 0.3.0. No provider configuration or server connection limits changed. APK and checksum: artifacts/Signal-TV-0.4.1-debug.apk and artifacts/SHA256SUMS-0.4.1.txt.
