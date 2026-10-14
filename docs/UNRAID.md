# Install VynodeIPTV on Unraid

Target: x86-64 Unraid with Docker enabled. Image: `ghcr.io/minerport/vynodeiptv:latest` (current numbered version: `0.11.0`).

## 1. Prepare persistent storage

In the Unraid terminal, create dedicated folders. Replace `/mnt/cache` with your existing SSD pool path if different:

```sh
install -d -m 700 -o 10001 -g 10001 /mnt/user/appdata/vynodeiptv
install -d -m 700 -o 10001 -g 10001 /mnt/cache/appdata/vynodeiptv-transcode
install -d -m 700 -o 10001 -g 10001 /mnt/user/recordings/vynodeiptv
```

For migration, back up the existing configuration and key together, and point `/config` to that existing directory instead. Starting with an empty configuration opens account setup and requires re-entering providers and pairing devices.

## 2. Install the template

```sh
mkdir -p /boot/config/plugins/dockerMan/templates-user
curl -fSL https://raw.githubusercontent.com/minerport/VynodeIPTV/main/deploy/unraid/iptv-server.xml \
  -o /boot/config/plugins/dockerMan/templates-user/my-VynodeIPTV.xml
```

Open **Docker → Add Container**, select **VynodeIPTV** from the user templates, check all three paths, and apply. Keep bridge networking, port 8580 mapped to container port 8080, privileged mode off, and the supplied security options. Choose another host port if 8580 is occupied. The template is not yet listed in Community Applications.

## 3. Sign in and pair Android

Open `http://YOUR_UNRAID_IP:8580` and create a username and password (12–256 characters). Future visits use that account.

For an existing installation, the already signed-in browser tab authorizes account creation automatically. If using another tab, retrieve the previous key privately with `docker exec VynodeIPTV cat /config/admin.token` and enter it in the **one-time upgrade key** field. This prevents another LAN user from claiming your saved providers. The key is disabled for API access after account creation. Passwords are stored as salted PBKDF2-SHA256 hashes; browser sessions expire after 24 hours or a server restart. Paired Android devices remain paired.

Add your provider, set its permitted connection limit, and refresh EPG. Enable HTTP only when the provider requires it.

On Android, choose Companion mode and enter the same LAN URL. For HTTP, enable the connection's HTTP option. Approve the matching pairing code in the dashboard. Android then chooses its own channel; its session is independent of any stream running in the browser.

## 4. Verify and maintain

- Check Docker reports healthy and the dashboard loads channels and guide data.
- Enable `IPTV_DEMO=1` temporarily for the synthetic test channel if useful; default is off.
- Try a single channel before adding wall tiles. Every tile uses a connection and browser conversion uses CPU.
- Back up all of `/config`, including `state.enc`, `encryption.key`, `admin-login.json` and `admin.token`. Never upload that directory to GitHub.
- To update, back up config and use Unraid **Check for Updates → Update** with `:latest`. Existing installations pinned to `:0.6.1` must edit Repository to `ghcr.io/minerport/vynodeiptv:latest` once and Apply. Keep config and scratch mappings. To roll back, select the previous tested tag; keep your backup for any future incompatible state migration.
- A health check proves the HTTP service responds; session diagnostics separately show media freshness and worker recovery.

No PUID/PGID support, GPU devices, privileged access or Docker socket mount is needed. Physical Unraid/Shield operation and long-duration stability are not yet claimed. Keep access on your trusted LAN or an authenticated private network.

References: [Unraid container configuration](https://docs.unraid.net/unraid-os/using-unraid-to/run-docker-containers/managing-and-customizing-containers/) and [GitHub Container Registry](https://docs.github.com/en/packages/working-with-a-github-packages-registry/working-with-the-container-registry).


## Install from the release archive instead

The [0.6.1 release](https://github.com/minerport/VynodeIPTV/releases/tag/v0.6.1) also includes the container archive, checksum and XML template. Download `VynodeIPTV-0.6.1-linux-amd64.tar.gz` and `SHA256SUMS-server-0.6.1.txt` into the same directory, then run:

```sh
sha256sum -c SHA256SUMS-server-0.6.1.txt
docker load -i VynodeIPTV-0.6.1-linux-amd64.tar.gz
```

This is an older release using token authentication. When installing its archive, change the template Repository to `ghcr.io/minerport/vynodeiptv:0.6.1`. Normal registry installation is public and requires no GitHub login.

## Password recovery

With local Unraid administrator access, stop the container and back up `/config` first. Rename only `admin-login.json` to `admin-login.json.backup` in the mapped appdata folder, then restart. Open account setup and authorize with the original `admin.token` key if providers or devices are present. Create a new password. Keep the encryption key and provider state intact. A corrupt account file fails closed instead of reopening setup.

## Guide navigation

The guide opens at the current half-hour containing Now. On browser rows, use left/right arrows, horizontal trackpad scrolling, Shift+wheel or drag the programme area. On Android, focus the programme area and use D-pad left/right; touch dragging also scrolls. All rows share an aligned timeline. Now returns to live listings, and backing out of fullscreen preserves your guide position. Listings depend on the EPG supplied by the provider; browsing past programmes does not enable catch-up playback.


## Saved accounts and refresh (0.10.1)

Server settings lists saved provider accounts above the add-account form. Edit changes the display name, provider URL, username, password, stream format, connection limit and transport permissions. Leave the password blank when editing to retain it. Changes to URL or username are verified before replacing the saved login and preserve its internal channel identifiers. Use Add account for an unrelated subscription to keep its preferences separate. Stop that provider's active sessions before editing or deleting its login.

Refresh EPG is directly below Refresh channels. Each refresh imports only the requested metadata and preserves existing data if the provider fails. Independent daily switches run on the server without an open dashboard. The scheduler checks once a minute, refreshes after 24 hours since its last successful scheduled import, and backs off for an hour after failure. Refreshing does not restart active video sessions.

Dashboard logout does not remove provider credentials or preferences. Delete login removes credentials but preserves the account identity and each user's favorites, group order, hidden groups and custom groups. Re-add the same provider URL and username to reconnect those saved identifiers. Providers that change their stream/category identifiers may require favorites or group selections to be updated. Back up /config; deleting appdata deletes saved state.

## Android without a server

Android is independently usable in Standalone / Xtream mode: channel import, direct playback, EPG, favorites, custom and hidden/reordered groups, video wall and manual/daily refresh all work without VynodeIPTV Server. The provider's simultaneous connection limit still applies.

Open Connection settings > Switch mode / accounts. Add a standalone Xtream login, a paired companion server, or both. Use this switches the active connection and stops its current streams. Pairing does not replace the standalone login. Last selected connection is restored on launch; an offline companion server does not prevent switching to a saved standalone account. Enter the Xtream login on Android once; companion pairing does not copy server-held provider passwords to Android.

Android stores credentials encrypted with Android Keystore and retains metadata separately in app-private storage. Existing saved connections migrate automatically. Log out clears the active selection, not saved credentials or preferences. Delete login removes its credentials. Standalone preferences are scoped to provider URL and username; password changes preserve them. Companion preferences remain scoped to the paired device on the server; revoking/re-pairing creates a different server identity. Preferences are not automatically synchronized between standalone and companion modes or different devices.

Daily switches are per saved Android connection and default off. Android checks due work approximately hourly, subject to network, Doze and background-execution limits, and also checks on startup. It cannot guarantee a fixed refresh time while powered off or force-stopped. For an always-on companion schedule, enable the server's daily switches. Standalone EPG refresh uses the provider's XMLTV endpoint (32 MiB download / 300,000 matched programme limit, seven days either side of now); failed or empty imports retain previous guide data. Normal per-channel guide lookup remains available. Clearing Android app data/uninstalling removes local saved data.


## Automatic reconnect and HTTP fallback (0.10.1)

Successful server channel/guide imports now save encrypted catalog snapshots in /config alongside the existing credentials. Restart restores snapshots before contacting providers. Failed imports preserve them and retry automatically, independently of daily-refresh switches, with delays increasing from one minute to a maximum of 32 minutes. The browser picks up recovered lineups automatically. Keep /config and its encryption key when updating.

Old releases did not save server catalog snapshots. If an older installation has already restarted into an outage, it must complete one successful import before this cache can help. Saved listings do not make an unavailable live feed playable. The provider must still accept the connection and account.

Standalone Android restores its cached lineup when a saved connection is unavailable and retries while that connection remains selected, without returning to login setup. Both direct and companion accounts remain selectable.

Automatic HTTP fallback is separate from Allow HTTP streams, off by default, and available in provider editing and standalone Android connection settings. Enable Allow HTTP first, then opt into fallback. It sends provider credentials and media without encryption if the initial HTTPS connection fails. The app does not disable certificate validation. It retries the same host over HTTP (standard port 443 becomes 80; custom ports remain unchanged), never downgrades in response to an HTTP authentication/status rejection, and does not change the saved provider identity. The provider must offer a working HTTP endpoint. Companion-server administrator/pairing requests do not use Android's provider fallback.

## Recording upgrade (0.11.0)

Before updating an existing container, create `/mnt/user/recordings/vynodeiptv` writable by UID/GID 10001 using the command above. In **Docker → VynodeIPTV → Edit → Add another Path**, map that host folder to container path **`/recordings`**, read/write. Apply with repository `ghcr.io/minerport/vynodeiptv:0.11.0` (pinned) or `:latest`. Preserve the existing `/config` and `/transcode` mappings.

Open **Recordings** in the dashboard to schedule and play recordings. The updated APK supports both server recordings and independent local recording on Android. See [built-in recording and current limitations](RECORDING.md). Do not delete the recordings host folder when reinstalling or rolling back.
