# Foundation validation — October 12, 2026

Implemented and tested on the local Windows development computer with Docker Desktop. This report is for the initial development slice, not the full architecture roadmap.

| Check | Result |
|---|---|
| Go unit tests | 10 tests passed |
| Go race detector | Passed |
| Go vet | Passed |
| Docker image | `iptv-server:0.1.0` built; running under `signal-iptv` Compose project |
| HTTP health | Local endpoint responds; container health check passed |
| Synthetic media smoke | Authenticated playlist/TS retrieval passed; unauthorized retrieval rejected |
| Worker restart | Injected demo-worker termination recovered from output generation 1 to 2 |
| Actual media decoding | Retrieved synthetic MPEG-TS decoded through FFmpeg without error; H.264/AAC 1280×720 fixture |
| Credential behavior | Encryption round-trip, missing-key rejection, pairing, role restriction and revocation tests passed |
| Guide behavior | Timezone parsing, malformed feed preservation and provider-namespace isolation tests passed |
| Android build | `assembleDebug` passed |
| Android unit tests | 2 retry-budget tests passed |
| Android lint | 0 errors, 11 warnings; report under `android/app/build/reports/` |
| Browser | Login page rendered and reported server online; API flows tested separately |
| Source checks | JavaScript syntax check passed |

Artifacts: `artifacts/Signal-TV-0.1.0-debug.apk` and `artifacts/SHA256SUMS`. Debug-signed APK; private development distribution only. Built with AGP 8.12.0, Kotlin 2.2.20, Gradle 8.13, Android SDK 36 and Media3 1.11.1. Container build uses Go 1.27.2 and the tested runtime reports FFmpeg 5.1.9-0+deb12u1.

The latest smoke result is `.local/verification/server-result.json`. Synthetic sample media is retained there for inspection. Tests create and remove their own device/session and do not open a real provider stream.

Active Docker configuration and live media scratch use named volumes outside the source folder. A local AppData token copy supports private administrator login. The development server binds only `127.0.0.1:8580`; physical Shield access requires deliberate LAN/TLS setup.

Not yet verified: a real Xtream account, Android runtime playback on an emulator or Shield, Shield remote navigation, Unraid installation, real-provider loss-of-signal behavior, hardware transcoding, and soak tests. The Android app has been compiled and statically checked, not hardware-validated. Reconnection can interrupt playback; stable HLS timeline stitching is not implemented. See README for the remaining feature gaps.
