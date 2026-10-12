# Version 0.8.0 validation

Android now uses the dashboard navy, slate and pale-blue palette, including guide rows, focus states, navigation and Material controls. Both guides fill missing schedule intervals with “No EPG data” instead of empty tracks.

Standalone Android requests the full Xtream EPG table and falls back to the short EPG when full data is unavailable or has no current programme. It accepts epoch seconds, epoch milliseconds and provider-local date strings. The server retains provider-supplied XMLTV listings within seven days before and after import time. This cannot recover history or future listings the provider does not supply, and browsing past listings does not add catch-up playback.

## Checks completed

- Android debug APK assembled; 28 unit tests passed; Android lint completed successfully.
- JavaScript guide/player tests: 11 passed.
- Go race tests and go vet passed, including past/current/future XMLTV retention and blank-title normalization.
- Browser fixture with 250 synthetic channels verified past, current and future cells, a channel with no listings, and a partial schedule gap. All missing intervals displayed No EPG data.
- No physical NVIDIA Shield or real-provider acceptance test was performed for this release. Android visual acceptance on the Shield remains pending.

The release workflow additionally gates container publication on server tests, image build and synthetic authentication, pairing, HLS and worker-recovery checks. Current workflow results are available in GitHub Actions.
