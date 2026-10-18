# Community Apps submission

Repository URL: https://github.com/minerport/VynodeIPTV

## Repository requirements

- Public and active GitHub repository.
- Root `LICENSE`: standard MIT license covering original source, templates and metadata. Dependencies retain their own licenses.
- Root `ca_profile.xml`: non-empty Profile, repository icon and project page.
- Exactly one Docker app entry: `deploy/unraid/iptv-server.xml`, with Container version 2, image Repository, Name, Overview, Project/Support, WebUI and raw TemplateURL.
- Public image: `ghcr.io/minerport/vynodeiptv:0.6.1`, linux/amd64. Anonymous pulls and a synthetic playback/recovery smoke test passed for this release.
- Public icon: `assets/vynodeiptv.png`; installation instructions linked by ReadMe; preview status and fixed UID/GID requirements declared in the template.
- Explicit configuration/scratch storage and port mappings. Privileged mode is false. No provider credentials or media are included.

The maintained template stays at its original path to preserve existing raw URLs and avoid duplicate app entries. `templates/` is the starter's suggested layout; the published XML requirements do not require moving an existing valid template there. Other XML files under `android/` are Android resources, not Docker app templates.

## Portal steps

1. Enter the repository URL in https://ca.unraid.net/submit/new.
2. Click Re-check to refresh the license and repository checks.
3. Run Validate and Scan to validate the repository profile and app entry.
4. Review any portal findings, then complete the submission as the repository owner.

The repository metadata has been checked locally and its public URLs verified. Final portal validation, submission acceptance and listing are separate checks; no acceptance is implied here. Physical Unraid and Shield testing also remains pending. Follow `docs/UNRAID.md` to prepare writable directories before launch; this image runs as UID/GID 10001 and does not implement PUID/PGID.

## Official requirements consulted

- https://ca.unraid.net/submit/help
- https://ca.unraid.net/submit/help/repository-xml
- https://ca.unraid.net/submit/help/repository-info-xml
- https://github.com/unraid/unraid-community-apps-starter
