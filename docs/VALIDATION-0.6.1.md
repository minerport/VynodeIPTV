# Server 0.6.1 packaging validation

This release packages the existing 0.6 browser/server feature set for x86-64 Unraid and GitHub Container Registry. Android source remains 0.5.0.

Packaging changes: OCI image metadata, timezone database, VynodeIPTV browser/repository branding, versioned Unraid template, install documentation, pinned GitHub Action revisions and restricted container build context. The container retains UID/GID 10001 and existing persistent-state compatibility.

Passed locally:

- 10 JavaScript behavior tests and the Docker build's Go tests.
- Fresh container running as UID/GID 10001 with all Linux capabilities dropped, no privileged mode and no-new-privileges.
- Authenticated HLS and valid MPEG-TS delivery; unauthorized API/media denied; paired device denied administrator operations.
- Forced synthetic worker interruption recovered from generation 1 to 2; freshness and recovery history present; test session/device cleaned up.
- Staged-source scan against the configured provider credentials, administrator token and encoded encryption key: no matches. Private runtime data, backups, APKs and signing keys excluded.

GitHub Actions repeats Go race/vet, browser tests, container build and synthetic recovery before publication. No physical Unraid deployment or Shield test is implied by a successful image build or registry publication.


## Published release verification

- Public repository and release: https://github.com/minerport/VynodeIPTV/releases/tag/v0.6.1
- Release source commit: `e8f552ba794a32771578f5de2cc13a72c1e709ae`.
- GitHub Actions verify and publish jobs passed: https://github.com/minerport/VynodeIPTV/actions/runs/38284073997
- Public registry tags `0.6.1` and `latest`: `ghcr.io/minerport/vynodeiptv`.
- Published digest: `sha256:d35bfa6653a2972121ac4e294b733af18eccd7b1de03b23e328c3f74413ffd51`.
- Anonymous manifest retrieval and a complete Docker pull using an empty Docker credential configuration passed.
- The pulled registry image was started with the Unraid security options; authenticated media, access restrictions, worker interruption recovery and diagnostic freshness passed again. Its temporary session, device, container and volumes were removed.
- Release archive, checksum and Unraid XML were confirmed downloadable anonymously. The gzip archive was opened and its image name, UID/GID and release revision inspected. Its locally built image differs in digest from the CI build; both use the release commit.
- The existing local server installation was left running separately. Physical Unraid installation remains pending.
