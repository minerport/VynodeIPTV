# 0.16.3 release validation

Includes the local 0.16.2 guide changes and 0.16.3 Android fullscreen menu.

- Android debug APK builds with version code 34; 64 JVM tests passed.
- Android lint passed before the final guide-navigation adjustment; release checks rerun lint.
- Browser tests cover the Now boundary and existing playback behavior.
- Container publishing is gated by Linux Go race tests, vet, browser tests, synthetic playback, recovery and DVR checks in GitHub Actions.
- No physical NVIDIA Shield, real-provider or production Unraid validation is claimed for this release.

Android fullscreen information hides after 2.75 seconds. OK opens guide/history/channel shortcuts, and Back closes the menu first. Held OK in the guide dispatches on release to avoid accidental tuning. Guide browsing can return from the future to Now but cannot move into the past.
