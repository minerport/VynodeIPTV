# Version 0.9.0 validation

The Android and browser palettes now use neutral charcoal surfaces, warm-white text/focus states and restrained amber live markers. The browser retains its existing desktop and phone layout. Android gains a compact Vynode identity/clock header, rounded preview and navigation surfaces, a stronger programme title, live/upcoming/past context, programme duration and current-programme progress.

Programme details use provider EPG data and the live preview; this release does not invent poster artwork or fetch external imagery. Missing listings still show No EPG data. Android 0.8.1 fullscreen metadata and channel-focus fixes remain included.

Validation:
- Android debug build, all 29 unit tests, and lint passed.
- Browser fixture visually checked with synthetic channels, including missing EPG rows and the shared neutral palette.
- Existing JavaScript tests and the GitHub container workflow gate publication; workflow includes Go race/vet, image build, authentication, pairing, HLS playback and forced worker recovery.
- Android visual and remote interaction acceptance on physical NVIDIA Shield remains pending. No device was connected during this release.
