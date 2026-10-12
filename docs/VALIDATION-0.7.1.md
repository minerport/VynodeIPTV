# 0.7.1 mobile dashboard

Server/web update only; Android remains 0.7.0.

At widths up to 700 CSS pixels the dashboard uses a horizontal icon menu, dismissible channel-group drawer, full-width preview, collapsible player controls and stacked forms. The guide keeps channel names visible and shows one hour, with horizontal dragging and arrow navigation. Desktop retains its existing layout, three-hour guide and expanded controls. Resizing updates the layout without stopping streams or resetting guide time.

## Validation

- Ten browser behavior tests passed.
- Synthetic browser fixture with 250 channels checked at 320, 390, 768 and 1440 pixel widths: no page-width overflow in live view/settings; desktop vertical rail, six time labels and expanded controls retained.
- Phone group selection closes the drawer; close button works; custom-group dialog fits the screen; player controls expand; row arrow keys and dragging move the timeline.
- No changes to authentication, stream workers, provider connections or Android source.
- The Container release workflow runs Go race tests/vet, browser tests and synthetic authenticated media/recovery before publishing versioned and latest images.

These are browser viewport checks, not physical iPhone/Android browser certification. Playback still requires the existing HLS.js-compatible browser capabilities; this release does not add a native-HLS authentication fallback for older browsers.
