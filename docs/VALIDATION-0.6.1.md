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
