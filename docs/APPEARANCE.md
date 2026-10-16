# Appearance Studio

Available in Android and the browser starting with preview 0.15.0. Android works independently in standalone mode and with a linked server. Appearance changes do not edit provider accounts, favorites, groups, recordings or programme metadata.

Open **Guide menu → Guide appearance** or **Settings → Appearance Studio** on Android. In the browser, select **Appearance** above the guide, or **Server settings → Appearance Studio**.

## Layouts and startup

| Layout | Purpose |
| --- | --- |
| Classic | Balanced preview and programme details above the guide. |
| Guide First | Smaller preview and condensed details to prioritize channel browsing. |
| Cinema | Larger preview and programme presentation, with the guide below. |
| Side Preview | Preview and details beside the guide on wide displays; stacked on phone browsers. |
| Wall First | Larger video area and a shorter guide; existing wall controls remain available. |

Layouts adapt to available space; Android TV and a phone browser do not use identical dimensions. The existing video wall remains available independently of the guide layout.

## Personalization

- Seven palettes: Cinema, Obsidian, Arctic, Midnight, Ember, Pearl (light), and High Contrast.
- Matte, Frosted and Smoked surfaces. Browser menus use supported backdrop effects; Android uses lightweight shaded/translucent surfaces. Video and guide cells are never blurred.
- Standard system font, Modern (Inter), or Readable (Atkinson Hyperlegible Next). Fonts ship with the app and server and need no external font service.
- Text size: 90%, 100%, 115% or 130%. Android also honors the system font scale.
- Compact, Comfortable or Large guide rows; Square, Soft or Rounded corners; Clear or Strong borders; Outline, Fill or Glow focus treatment. Android Glow emphasizes the focus border rather than applying an expensive blur.
- Artwork everywhere, in the header only, or off. This controls presentation; it does not disconnect TMDB or delete matches. Guide First intentionally condenses the header.
- Eight complete starting presets: Cinema Classic, Arctic Glass, Pure Guide, Home Theater, Daylight, Easy Read, Side Lounge and Wall First.

Changes first appear in the sample guide. Select **Apply** to try them across the app, then **Keep** within 15 seconds. **Revert**, closing the confirmation, or the timer restores the previous appearance. **Cancel** discards the draft. **Reset defaults** changes the draft only until applied and kept.

Save up to 20 named presets on the device. Export/import uses a versioned JSON file containing appearance values only, capped at 32 KiB. Unknown styles are ignored; arbitrary CSS, URLs and account details cannot be applied. Android uses the system document picker, whose availability depends on the device's installed file provider. The same preset format works in both clients.

## Storage and performance

Android uses app preferences independent of provider and mode. Browser preferences belong to that browser/origin; another browser or private browsing session has separate preferences. Clearing app/browser data removes local appearance settings. Optional shared-theme loading is available in 0.16.1, as described below.

Existing guide-density and artwork preferences are migrated when appearance has not yet been saved. Appearance changes use the existing player and guide state. Fonts are local, palettes share a catalog, and guide virtualization uses the selected row height. Main palette text/background and filled-focus color pairs are checked for at least 4.5:1 contrast; this is not a complete accessibility certification.

Automatic artwork-derived palettes, arbitrary custom colors and individual user-profile theme synchronization remain future work.

## Discover home (0.16.1)

Open Discover from the browser navigation or Android Guide menu. It contains recently watched channels, favorites, On now, upcoming favorites and recordings. Programme artwork uses the existing TMDB configuration and falls back to readable text when no match is available. Recording cards open the existing recording library; future programme cards open details or the guide rather than tuning a future broadcast.

Appearance Studio has an independent **Open on startup** choice: Guide (default), Home, or Wall. Choosing a layout does not force Home. Android Wall startup opens the existing video wall; browser Wall startup enlarges the wall area and focuses its layout controls. No extra provider connection is started simply by choosing Home or a layout.

Highlights sample at most 12 channels, prioritizing favorites, then recents. This is not a full-lineup recommendation index. Browser and companion use one bounded guide batch; standalone Android reads its local EPG and loaded guide cache without making provider requests for every channel. Open the guide or refresh EPG if standalone highlights are empty. Up to 24 programme cards are enriched on Android. Missing data, disabled TMDB and unavailable recordings have explicit fallback text.

## Share appearance through your server

1. Update the server and Android client to 0.16.1 or newer.
2. In browser Appearance Studio, configure a draft and choose **Publish shared theme**. This requires the server administrator and confirmation. Publishing does not force another device to change immediately.
3. A linked Android device can choose **Load shared theme** to preview/apply it once, or enable **Load server theme on connection**. The browser has an equivalent sign-in option. This option is off by default.
4. Enabled devices fetch the shared theme on connection/app launch; browser clients fetch on sign-in/page reload. There is no background polling or surprise theme change during a viewing session. If the server is unavailable, the last usable appearance remains.

Startup destination always remains local, even when a shared theme includes another startup choice. Android's follow preference belongs to its saved server connection; standalone mode never contacts a server for themes. Disabling follow retains the current appearance, which can then be edited locally.

There is one administrator-published theme per server, not per-user theme accounts. Paired devices can read it but cannot publish it. Concurrent browser publication uses revision checks and rejects stale updates. Only known appearance values are accepted, with no credentials, URLs or arbitrary styling. Stored themes are included in encrypted server state. This does not synchronize named preset libraries, favorites, recordings or provider accounts.
