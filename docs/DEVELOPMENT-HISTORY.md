# Signal TV — IPTV foundation

Private development builds: **Android 0.5.0 / server 0.6.0**. Native Android TV client with standalone Xtream login and an optional self-hosted Docker companion server. Target device: NVIDIA Shield.

## What runs now

- Go server and browser dashboard with an authenticated live player; Docker image and draft Unraid template.
- Encrypted provider/device persistence using AES-GCM. Generated administrator token in `/config/admin.token`; no credentials in application logs.
- Xtream account validation, channel import, XMLTV import on startup/manual refresh and short EPG in standalone mode.
- Device pairing, revocation and authenticated media delivery.
- FFmpeg HLS remux workers with bounded restart attempts, stale-output watchdog, connection-slot reservations, scratch limits and expiring client leases.
- Android APK with companion pairing, standalone login, a TV-style timeline guide, provider categories, persistent custom groups, searchable channels, Media3 playback and bounded retry handling.
- Synthetic H.264/AAC channel and a reproducible worker-failure smoke test.

## Run locally

```powershell
.\scripts\start-local.ps1 -Build
```

Open **http://localhost:8580**. On Windows, copy the token from `%LOCALAPPDATA%/SignalTV/config/admin.token` into the dashboard; the startup script exports this local convenience copy. Enter provider credentials in **Add an Xtream provider**. Keep the provider's connection limit at its actual account limit; default is one. Saving verifies account access and imports channels but does not open a live stream. Select **Refresh EPG** afterwards. Active configuration and scratch live in local Docker named volumes (`signal-iptv-config` and `signal-iptv-transcode`), outside the OneDrive workspace. On Linux, run `docker compose -p signal-iptv -f deploy/docker/compose.yaml up -d --build` and retrieve `/config/admin.token` from the container. Use the explicit host-path mappings in the Unraid template on Unraid.

The default Compose mapping binds only the local computer. To connect a physical Shield, configure a LAN binding, firewall scope and preferably trusted TLS first. The Android emulator can reach a loopback binding with `adb reverse tcp:8580 tcp:8580`; use `http://127.0.0.1:8580` and explicitly allow HTTP in the test app. No physical Shield or Unraid deployment is claimed by this build.

Stop the local app with `docker compose -p signal-iptv -f deploy/docker/compose.yaml down`. This preserves named volumes and the encryption key; do not use `down -v` unless intentionally deleting configuration. Back up the active `/config` volume's encrypted state and key together. The AppData token copy is not a configuration backup. Losing the key requires re-entering providers and re-pairing devices.

## Build Android

Java 17, Android SDK 36, Gradle 8.13. From `android/`:

```text
.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
```

The debug APK is at `android/app/build/outputs/apk/debug/app-debug.apk`. A copied handoff APK is placed in `artifacts/` for this local run. Debug signing is for private testing, not store distribution. Credentials are stored through Android Keystore encryption and excluded from backup. The app releases stream sessions when it leaves the foreground and resumes retained panels on return. Panel selections and layout are saved per connection; after a fresh launch, select Multiview or Fullscreen to resume them.

## Verify the server

With the Compose app running and demo enabled:

```powershell
node scripts/verify-server.mjs
docker run --rm -v "${PWD}/server:/src" -w /src golang:1.27.2-bookworm go test -race ./...
```

The smoke test pairs a temporary test device, requests the synthetic channel, fetches authenticated HLS and a TS segment, interrupts its worker, verifies a later healthy generation, then stops and revokes its test resources. It never contacts a real IPTV provider. Results are saved in `.local/verification/`.

## Unraid

`deploy/unraid/iptv-server.xml` is a private template, not a published Community Applications entry. Build/load `iptv-server:0.6.0` on the target first. Set appdata and scratch paths to existing writable storage owned by UID/GID 10001. Choose an SSD pool for scratch; `/mnt/cache` is only an example. Use bridge networking and a selected LAN port. No privileged mode, Docker socket access or GPU is needed for remux. Do not expose this early build to the public internet.

## Known development gaps

This is the first vertical slice, not completion of the architecture plan:

- Recovery restarts the HLS output generation; companion re-prepares playback. It is **not gapless**, and missing provider content cannot be recovered.
- Server watchdog monitors segment progress. Android monitors playback position, input bytes, buffer duration, visible frame delivery and observable selected-audio decoder progress, with bounded staged recovery. Content-level frozen-image/silence detection, seamless timeline stitching and hardware validation remain pending.
- Provider/device configuration is an encrypted atomic JSON snapshot. SQLite/Room persistence, scheduled EPG refresh and guide overrides are pending. EPG is in memory and reloads after restart; channels reload from providers. Custom groups persist in encrypted server state or local Android preferences.
- Stream format must be chosen explicitly. No automatic format fallback, GPU transcoding, automatic output profile negotiation, shared ingest, recording or catch-up. Android companion can choose original-quality remux or H.264/AAC CPU conversion.
- Paired devices can access the configured catalog. Per-user channel permissions, expiring/refreshable tokens and device-user roles are pending.
- API metadata redirects are same-host only. Server media passes through a private loopback relay that validates upstream TLS and enforces HTTP opt-in across redirects, playlists, keys and segments. Android allows cross-protocol redirects only for opted-in standalone connections.
- Native TV focus, decoder compatibility, audio passthrough and long-session behavior still require emulator and physical Shield validation.
- Browser playback uses a vendored hls.js 1.7.3 build and license; React administration is deferred. Docker runtime packages use Debian security updates; exact installed FFmpeg version is captured in validation notes.

The full roadmap and rationale are in [IPTV-ARCHITECTURE-PLAN.md](../IPTV-ARCHITECTURE-PLAN.md).

## Companion behavior in 0.2

The Android app is the controller. It browses the full catalog and guide, chooses any channel, and requests a session from the server. The server opens the provider connection, remuxes or converts media, buffers HLS segments and restarts stalled workers. Android consumes only the server feed and re-prepares on a changed output generation. Channel switching waits for the previous session to close so a one-connection provider is not opened twice intentionally. The dashboard player is a separate controller and consumes its own provider connection when playing.

Standalone mode retains the same guide/group workflow and connects directly to Xtream. For providers that redirect HTTPS media to HTTP, enable **Connection → Allow HTTP streams and redirects**, then Save. The profile is retained; credentials do not need re-entry. Companion mode uses the provider transport preference saved on the server.

Custom groups support creation, renaming, membership edits and deletion (50 groups, 500 channels each). Provider groups are imported without modification. Standalone groups belong to that device/provider profile; companion groups persist on the server per paired device; administrator dashboard groups belong to the administrator. They are not yet shared across identities. Guide cells tune the live channel, not catch-up programmes. Missing EPG listings are shown explicitly.

Run `npm ci --ignore-scripts` and `npm run vendor` to refresh the pinned browser player assets before building Docker. `npm test` verifies guide clipping and group filtering. See `docs/VALIDATION-0.2.0.md` for the current evidence and hardware limitations.

## Guide and player navigation in 0.3

Both clients now scroll continuously through channels instead of showing pages. The browser uses a small moving window of rows; Android uses a lazy list. Listings are fetched for nearby visible channels, and caches are bounded.

Favorites is always visible at the top. Use the star beside a channel to add/remove it. Other groups start in alphabetical order. **Manage groups** (browser) or **Manage** (Android) lets users move groups up/down and hide or restore them. Provider and custom groups can be interleaved. Hidden groups retain their channels and can still be restored in the manager. Preferences persist with the same per-profile/per-device ownership as custom groups.

On Android, Back from fullscreen keeps the same stream/player running in a small preview and restores the guide's group, search, time window and scroll position. Use **Fullscreen** or select the playing channel to expand without reopening the provider session. **Stop**, another Back from the guide, changing channel, or leaving the app stops/replaces playback. The fullscreen title overlay follows player-control visibility and disappears after approximately 3.5 seconds. Physical Shield validation remains pending.


## Android 0.4 controls

- Text inputs are remote-focusable buttons. Press Select/OK to open the editor, then Done to apply or Cancel to discard.
- Neutral charcoal glass-style surfaces, brighter focus borders, and larger programme labels replace the green accent.
- Saved connections open the guide immediately. Cached channels appear during refresh; network failures do not send an already configured user back to login. Keystore errors show Retry and an explicit reset option.
- Companion EPG requests batch the visible channels. Standalone EPG uses at most three concurrent requests, with a five-minute in-memory cache. Channel parsing and metadata cache IO run off the UI thread. Speed improvements have not been benchmarked on Shield.
- Multiview offers side-by-side, quad and six-panel layouts. Choose an empty panel, pick a channel from the guide, and repeat. Focus a picture to switch audio immediately; select it for fullscreen. Back from fullscreen returns directly to the wall. Back on the wall opens setup controls, including Guide. Smaller layouts retain additional panels and their streams.
- Only the selected panel has audio. Change replaces one panel; Remove removes one; Remove all clears the scene. Selections survive app restarts. This is in-app multiview, not Android's floating system PiP window.
- Each distinct channel consumes a provider connection and a decoder. Standalone checks the advertised account maximum; companion mode relies on server admission limits. Existing server limits are unchanged. Two/four/six simultaneous streams are subject to account, server, codec and Shield hardware limits.

See [Android 0.4 validation](VALIDATION-0.4.0.md) for the tested scope and device acceptance checklist.


### Android 0.4.1 video wall

The wall uses the whole screen with no gutters, padding, rounded tiles, labels or permanent controls. Only a thin focus outline is drawn over the selected picture. Video fills each tile while preserving aspect ratio by cropping when necessary; fullscreen retains the normal fit behavior. Back on the wall opens channel/layout/removal controls. Focus transfers audio without replacing or restarting the independent stream engines. Returning from fullscreen restores focus to the selected panel. See docs/VALIDATION-0.4.1.md for validation scope.


### Android 0.4.2 guide layout

Channel search is a compact select-to-edit control in the top app bar. The groups sidebar spans the complete guide workspace height. A larger 16:9 preview sits above the timeline in the right-hand column, with Fullscreen and Remove all streams aligned directly below it and channel/programme details beside it. Preview height adapts to the available workspace, up to 200 dp, leaving a scrolling timeline underneath. Multiview playback and Back navigation are unchanged.


### Android 0.4.3 tiered guide

The guide uses a narrow navigation rail, a separate group tier, and the preview/timeline workspace. Selecting a group closes both left tiers and focuses the channel list, expanding the guide across the screen. Back from this expanded guide reopens groups; the Groups button also toggles them. Search, video wall, refresh and connection controls live in the rail. The preview and focused programme title/time stay above the timeline. Existing fullscreen-to-wall behavior is retained. This supersedes the fixed-sidebar layout in 0.4.2.


### Android 0.4.4 icon rail

Search, Favorites, Video wall, Refresh channels and Connection settings now use consistent vector icons in fixed-size remote targets. Focus shows a light highlight and a single-line label beside the rail, so no navigation text wraps vertically. Selecting Search opens its editor directly; moving focus onto it never opens the keyboard.


### Android 0.4.5 exit wall

Exit wall & stop all, Remove all streams, and removing the last occupied panel now reset wall mode, selection and layout. The next guide selection opens normal fullscreen playback. Back during single-channel viewing cannot activate wall mode. Saved scenes record wall intent explicitly; a legacy single saved channel restores as normal playback, while legacy multi-channel scenes retain wall mode.


## Android 0.5.0 playback health

Open **Connection settings → Playback health & performance**, or **Video wall → Back → Playback health**. Each active panel shows buffer duration, transferred bytes, frame/decoder progress, dropped frames, audio underruns, first-playback timing, recovery count and recent diagnostic events. Catalog cache/refresh timing is measured too. Metrics are local, per active engine, and reset when that engine is closed; no URLs or credentials are recorded in the diagnostic history.

The detector samples once a second, allows 25 seconds for initial startup and treats 12 seconds without required progress as sustained trouble. Pauses, hidden video, muted audio and unavailable passthrough decoder counters are excluded from incompatible checks. Five backoff attempts (1/2/4/8/16 seconds) escalate from a supported live-edge seek, to media reconnect, to rebuilding the affected player. Sixty healthy seconds reset the retry budget. Transient stalls that recover during backoff skip the pending recovery. Companion recovery retains the same server session; server worker recovery remains the existing independent watchdog.

This is the first monitoring/recovery phase, not a guarantee against lost source content. Actual Shield recovery latency, long-run multiview stability and performance baselines remain to be measured. Shared server ingest, indexed local EPG, ahead-of-scroll prefetch and configurable resilience buffers remain follow-up work. See docs/VALIDATION-0.5.0.md.


## Docker and browser 0.6

The browser now includes an icon navigation rail, collapsible channel groups, a continuous full-width guide, a preview above the guide, and neutral glass colors. Favorites, custom groups, hidden groups and saved group order remain available. Programme data is batched into channel caches in a single pass.

Choose Single, Side by side, Quad or Six under the preview. Each selected channel owns a separate server session and browser decoder. Hover or keyboard focus selects the audible tile; click or Enter opens it fullscreen. Escape returns to the retained wall, then Escape again returns to its setup and guide. Clear wall releases every session and resets to Single. Browser autoplay restrictions may require Play / audio. Tiles fill their area, which can crop the source image.

Server settings shows output age, worker restart count, scratch use and a bounded recovery history for every active session, including Android companion sessions. The watchdog requires a new nonempty media segment, rather than just a changed playlist timestamp. Startup timeout remains 20 seconds, stalled-output timeout 12 seconds, with bounded exponential worker retries. Browser stalls use a separate five-retry budget with backoff; 60 seconds of progressing playback resets that budget.

Every tile consumes a provider connection. The configured provider limit and eight-session server cap still apply. Four/six layouts are not a promise that the default two-CPU, 1 GB container can transcode that many feeds in real time. Size the server and provider allowance for the intended concurrency; no automatic limit changes are made. Browser sessions use H.264/AAC CPU conversion; Android companion can use remux.

Local Docker was upgraded with the encrypted configuration and key backed up together. The previous image remains available for rollback. The Unraid template now targets 0.6.0; deployment on Unraid and physical Shield validation are still pending. See [0.6 validation](VALIDATION-0.6.0.md).
