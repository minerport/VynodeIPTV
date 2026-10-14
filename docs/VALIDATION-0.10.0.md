# Version 0.10.0 validation

Implemented separate EPG refresh below channel refresh, optional daily refresh, saved-account CRUD and retained preferences. Android remains fully standalone, supports saved standalone and companion connections together, and offers explicit account/mode switching.

Local validation:
- Go tests and vet passed with Go 1.27.2 on Windows. Added tests cover editing without resending a password, removing/re-adding a provider, preservation of legacy provider IDs across store reload, per-owner preferences, active-session deletion rejection, failed refresh retention and daily schedule permissions/cadence.
- Live HTTP integration against an isolated synthetic provider/server passed: EPG-only import leaves channels unchanged; refresh settings persist; dashboard logout/login and provider deletion/re-add retain favorites, hidden groups and ordering; provider passwords are not returned by account listing.
- 17 JavaScript tests passed; browser app syntax check passed.
- Android debug APK assembly, 34 JVM tests and lint passed. New tests cover identity compatibility, separate provider/users, password changes, XMLTV time offsets and preserving both connections when switching modes.

Publication is gated by the GitHub workflow's Linux Go race/vet checks, browser tests, container build and authenticated synthetic HLS/recovery checks. Local Docker Desktop failed to start because of an inference-listener filesystem error; no Docker reset or changes to real provider data were performed. Browser automation and connected Android devices were unavailable, so visual interaction, Android Keystore migration and background job execution on a physical Shield still require acceptance checks. Unit/API checks do not claim physical hardware verification.

Manual acceptance after upgrade: verify existing connection migration; switch between saved standalone/companion accounts with the server offline; confirm favorites and hidden groups return per account; refresh EPG while playing; restart device/server with daily switches enabled; edit and re-add a provider while preserving group selections. Android scheduling is approximate and constrained by the OS; server scheduling requires the container to run. See UNRAID.md for retention and import limits.
