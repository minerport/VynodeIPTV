# 0.13.2 validation

TMDB credentials accept the shorter v3 API key as well as existing bearer tokens. Android adds explicit, masked clipboard paste and explains that companion mode uses server settings. Clipboard is read only after pressing Paste; it does not synchronize a phone clipboard to a TV.

Checks cover both credential transports, key type discrimination, existing EPG behavior, Android build/unit tests/lint and release-gated container streaming/DVR smoke. Real TMDB keys and physical Shield remote/clipboard behavior require user-device verification. Credentials remain encrypted and are never logged or returned from settings APIs.

Local verification passed: Go suite and vet, 29 browser tests, Android assemble and 50 JVM tests. Lint: zero errors, 25 warnings. APK version 0.13.2/code 22 retains the existing signing certificate.
