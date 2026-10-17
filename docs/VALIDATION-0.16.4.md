# Server 0.16.4 validation

Mobile-only CSS polish. Android remains at 0.16.3.

- 41 browser behavior tests passed.
- Headless Edge layout fixtures checked at 320, 390, 844 and 1440 pixel widths without page-level horizontal overflow. The bottom navigation applies only at mobile widths.
- Visual fixture inspected at 390 pixels; playback controls revised to avoid clipping Fullscreen.
- Publishing is gated by Linux Go race tests, vet, container build, and synthetic playback, recovery and DVR checks.
- Physical iPhone Safari and production Unraid have not been tested for this change.
