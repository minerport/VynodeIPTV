# 0.12.0 validation

Server and Android add optional TMDB enrichment. Existing streaming and DVR behavior is retained.

Local checks:
- Go tests passed, including fake TMDB HTTP responses for bearer authentication, secret encryption/redaction, settings preservation, device permissions, cache persistence, ambiguous remakes, year disambiguation, rate-limit backoff and unchanged provider guide values. Go vet passed.
- 25 browser behavior tests passed, including immediate provider details and trusted artwork addresses.
- Android debug build and 44 JVM tests passed; Android lint reported zero errors and 25 warnings (including SharedPreferences KTX suggestions).

Release publication is gated on Linux Go race tests, browser tests, a Docker build, and authenticated live/DVR/browser-recording-copy smoke checks. The smoke also verifies that enrichment starts disabled, tokens are not returned, unauthenticated metadata access is rejected, and the attribution asset is served.

No real TMDB token was used. Live authentication, real provider title matches, artwork presentation on physical Shield/Unraid, and browser visual acceptance still require user verification. The initial integration matches movies/series conservatively and does not identify individual episodes, perform fuzzy matching, or enrich the whole guide in advance. TMDB unavailability does not affect guide loading, streaming or recording.
