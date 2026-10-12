# Install VynodeIPTV on Unraid

Target: x86-64 Unraid with Docker enabled. Image: `ghcr.io/minerport/vynodeiptv:0.6.1`.

## 1. Prepare persistent storage

In the Unraid terminal, create dedicated folders. Replace `/mnt/cache` with your existing SSD pool path if different:

```sh
install -d -m 700 -o 10001 -g 10001 /mnt/user/appdata/vynodeiptv
install -d -m 700 -o 10001 -g 10001 /mnt/cache/appdata/vynodeiptv-transcode
```

For migration, back up the existing configuration and key together, and point `/config` to that existing directory instead. Starting with an empty configuration creates a new administrator token and requires re-entering providers and pairing devices.

## 2. Install the template

```sh
mkdir -p /boot/config/plugins/dockerMan/templates-user
curl -fSL https://raw.githubusercontent.com/minerport/VynodeIPTV/main/deploy/unraid/iptv-server.xml \
  -o /boot/config/plugins/dockerMan/templates-user/my-VynodeIPTV.xml
```

Open **Docker → Add Container**, select **VynodeIPTV** from the user templates, check both paths, and apply. Keep bridge networking, port 8580 mapped to container port 8080, privileged mode off, and the supplied security options. Choose another host port if 8580 is occupied. The template is not yet listed in Community Applications.

## 3. Sign in and pair Android

```sh
docker exec VynodeIPTV cat /config/admin.token
```

Treat that token as a password. Enter it at `http://YOUR_UNRAID_IP:8580`. Add your provider, set its permitted connection limit, and refresh EPG. Enable HTTP only when the provider requires it.

On Android, choose Companion mode and enter the same LAN URL. For HTTP, enable the connection's HTTP option. Approve the matching pairing code in the dashboard. Android then chooses its own channel; its session is independent of any stream running in the browser.

## 4. Verify and maintain

- Check Docker reports healthy and the dashboard loads channels and guide data.
- Enable `IPTV_DEMO=1` temporarily for the synthetic test channel if useful; default is off.
- Try a single channel before adding wall tiles. Every tile uses a connection and browser conversion uses CPU.
- Back up all of `/config`, including `state.enc`, `encryption.key` and `admin.token`. Never upload that directory to GitHub.
- To update, back up config, change the image tag and recreate through Unraid. Keep config and scratch mappings. To roll back, select the previous tested tag; keep your backup for any future incompatible state migration.
- A health check proves the HTTP service responds; session diagnostics separately show media freshness and worker recovery.

No PUID/PGID support, GPU devices, privileged access or Docker socket mount is needed. Physical Unraid/Shield operation and long-duration stability are not yet claimed. Keep access on your trusted LAN or an authenticated private network.

References: [Unraid container configuration](https://docs.unraid.net/unraid-os/using-unraid-to/run-docker-containers/managing-and-customizing-containers/) and [GitHub Container Registry](https://docs.github.com/en/packages/working-with-a-github-packages-registry/working-with-the-container-registry).
