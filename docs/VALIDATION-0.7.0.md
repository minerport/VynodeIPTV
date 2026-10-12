# 0.7.0 validation

Server and Android version: 0.7.0. Android versionCode: 11. Container target: linux/amd64.

## Implemented

- Dashboard account creation and username/password login; salted PBKDF2-SHA256 password hashes (600,000 iterations), bounded login attempts, opaque 24-hour browser sessions and server-side logout revocation. Server restarts invalidate browser sessions.
- Existing installations require the previous administrator key once to authorize account creation. An already signed-in browser supplies it automatically. Account creation disables that key for API/media authentication. Existing provider state and paired device credentials remain intact.
- Corrupt account files fail closed; JSON-only account endpoints reject cross-site form submissions. Account hashes and temporary files are excluded from Git.
- Browser and Android guides open at Now on app/tab reopening. Left/right navigation works from channel rows, and horizontal dragging scrolls the timeline. Rows share aligned times. The browser also accepts horizontal trackpad and Shift+wheel input.
- Fullscreen return preserves guide time/position. Companion EPG loads a cached 12-hour range around the viewport, rather than requesting a new page on every horizontal movement. Standalone Android displays the provider's supplied short EPG.
- Unraid template follows `:latest`; numbered `:0.7.0` remains available for pinning. The release workflow verifies before updating either tag.

## Checked locally

- Go race tests and vet passed, including account migration, password persistence, invalid credentials, legacy-key rejection, logout/expiry, device role preservation, rate limiting and corrupt-account rejection.
- Ten JavaScript behavior tests passed (guide clipping/virtualization/group order and independent video-wall ownership/audio/recovery).
- Android debug build, 22 unit tests and lint passed. Lint has existing warnings; no lint errors.
- An isolated container with synthetic media passed account setup/login/logout, device pairing, role checks, authenticated HLS and MPEG-TS delivery, and forced-worker recovery from generation 1 to 2.
- Browser fixture with 250 synthetic channels: password login, logout/re-login, left/right movement from a channel row, drag scrolling without accidental tuning, Now after reopening Live TV, and preserved timeline after fullscreen return.

## Limits

Physical NVIDIA Shield remote/touch behavior and this release on physical Unraid still need user acceptance testing. Synthetic recovery cannot guarantee uninterrupted playback from an unavailable upstream provider. EPG browsing does not provide catch-up video, and listings are limited to what the provider supplies. The APK is a debug-signed preview for sideload testing, not a Play Store production artifact.

See the GitHub release and Container workflow for published-image verification. Local development data and real provider credentials were not used in the synthetic tests or included in release assets.
