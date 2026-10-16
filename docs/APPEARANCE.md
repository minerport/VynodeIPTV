# Appearance Studio

Available in Android and the browser starting with preview 0.15.0. Android works independently in standalone mode and with a linked server. Appearance changes do not edit provider accounts, favorites, groups, recordings or programme metadata.

Open **Guide menu → Guide appearance** or **Settings → Appearance Studio** on Android. In the browser, select **Appearance** above the guide, or **Server settings → Appearance Studio**.

## Layouts

| Layout | Purpose |
| --- | --- |
| Classic | Balanced preview and programme details above the guide. |
| Guide First | Smaller preview and condensed details to prioritize channel browsing. |
| Cinema | Larger preview and programme presentation, with the guide below. |

Layouts adapt to available space; Android TV and a phone browser do not use identical dimensions. The existing video wall remains available independently of the guide layout.

## Personalization

- Seven palettes: Cinema, Obsidian, Arctic, Midnight, Ember, Pearl (light), and High Contrast.
- Matte, Frosted and Smoked surfaces. Browser menus use supported backdrop effects; Android uses lightweight shaded/translucent surfaces. Video and guide cells are never blurred.
- Standard system font, Modern (Inter), or Readable (Atkinson Hyperlegible Next). Fonts ship with the app and server and need no external font service.
- Text size: 90%, 100%, 115% or 130%. Android also honors the system font scale.
- Compact, Comfortable or Large guide rows; Square, Soft or Rounded corners; Clear or Strong borders; Outline, Fill or Glow focus treatment. Android Glow emphasizes the focus border rather than applying an expensive blur.
- Artwork everywhere, in the header only, or off. This controls presentation; it does not disconnect TMDB or delete matches. Guide First intentionally condenses the header.
- Six complete starting presets: Cinema Classic, Arctic Glass, Pure Guide, Home Theater, Daylight and Easy Read.

Changes first appear in the sample guide. Select **Apply** to try them across the app, then **Keep** within 15 seconds. **Revert**, closing the confirmation, or the timer restores the previous appearance. **Cancel** discards the draft. **Reset defaults** changes the draft only until applied and kept.

Save up to 20 named presets on the device. Export/import uses a versioned JSON file containing appearance values only, capped at 32 KiB. Unknown styles are ignored; arbitrary CSS, URLs and account details cannot be applied. Android uses the system document picker, whose availability depends on the device's installed file provider. The same preset format works in both clients.

## Storage and performance

Android uses app preferences independent of provider and mode. Browser preferences belong to that browser/origin; another browser or private browsing session has separate preferences. Clearing app/browser data removes local appearance settings. There is no automatic cross-device synchronization in this release.

Existing guide-density and artwork preferences are migrated when appearance has not yet been saved. Appearance changes use the existing player and guide state. Fonts are local, palettes share a catalog, and guide virtualization uses the selected row height. Main palette text/background and filled-focus color pairs are checked for at least 4.5:1 contrast; this is not a complete accessibility certification.

This is the first appearance release. Side Preview, Discover/Home, Wall First, automatic artwork-derived palettes, arbitrary custom colors and account-based theme synchronization remain future work.
