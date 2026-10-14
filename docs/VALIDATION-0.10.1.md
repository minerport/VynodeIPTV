# Version 0.10.1 validation

Provider URL and username are editable again. A verified edit retains its provider ID and preferences; failed edits retain the original credentials. Re-adding a former address cannot overwrite the edited account.

Server metadata is cached encrypted in /config and restored before network imports. Failed imports retry independently of optional daily schedules, with exponential backoff capped at 32 minutes. Removed providers are excluded from cache restoration. Android saved connections retain their cached lineup on startup failure and retry without requiring sign-in.

HTTP fallback is explicitly opt-in and off by default. TLS verification remains enabled. Tests cover default refusal to downgrade, opt-in same-host fallback, preservation of encoded credentials, and no downgrade on HTTP 401. Android fallback applies only to standalone provider calls/playback, not companion authorization. Transport failures and provider diagnostics avoid echoing credential-bearing URLs.

Validation: Go tests/vet; 17 browser behavior tests and JavaScript syntax; Android APK assembly, 36 JVM tests and lint. Added server regressions cover encrypted cache restore, outage retention and recovery with daily scheduling disabled, safe URL/username edits and identity collisions. Publication requires Linux race/vet, container build and authenticated synthetic media/recovery checks. No physical Shield, Safari or Unraid UI acceptance was performed. The user's actual provider fallback was not enabled or tested with credentials.

Earlier server releases did not persist the catalog. Existing installations require one successful import to seed the cache. Cache persistence cannot recover a lineup already lost by an older restart, repair provider certificates, renew subscriptions or guarantee uninterrupted upstream media.
