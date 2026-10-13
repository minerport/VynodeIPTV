# Install VynodeIPTV on Unraid

Target: x86-64 Unraid with Docker enabled. Image: `ghcr.io/minerport/vynodeiptv:latest` (current numbered version: `0.9.2`).

## 1. Prepare persistent storage

In the Unraid terminal, create dedicated folders. Replace `/mnt/cache` with your existing SSD pool path if different:

```sh
install -d -m 700 -o 10001 -g 10001 /mnt/user/appdata/vynodeiptv
install -d -m 700 -o 10001 -g 10001 /mnt/cache/appdata/vynodeiptv-transcode
```

For migration, back up the existing configuration and key together, and point `/config` to that existing directory instead. Starting with an empty configuration opens account setup and requires re-entering providers and pairing devices.

## 2. Install the template

```sh
mkdir -p /boot/config/plugins/dockerMan/templates-user
curl -fSL https://raw.githubusercontent.com/minerport/VynodeIPTV/main/deploy/unraid/iptv-server.xml \
  -o /boot/config/plugins/dockerMan/templates-user/my-VynodeIPTV.xml
```

Open **Docker → Add Container**, select **VynodeIPTV** from the user templates, check both paths, and apply. Keep bridge networking, port 8580 mapped to container port 8080, privileged mode off, and the supplied security options. Choose another host port if 8580 is occupied. The template is not yet listed in Community Applications.

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
