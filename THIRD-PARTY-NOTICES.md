# Third-party notices

VynodeIPTV includes or depends on the following components. Their respective licenses continue to apply; publishing this repository does not relicense third-party software.

- **hls.js 1.7.3**, Apache-2.0: the vendored browser bundle and complete notice are in `server/web/vendor/`; upstream source: https://github.com/video-dev/hls.js/tree/v1.7.3.
- **Go**, BSD-style license: https://go.dev/LICENSE. The server is built using the official Go container image.
- **Debian runtime packages**, including FFmpeg, tini, curl, CA certificates and timezone data: licenses are retained in the image under `/usr/share/doc/<package>/copyright`. Exact installed versions can be inspected with `dpkg-query -W` inside the image. Debian corresponding source packages are available at https://deb.debian.org/debian/pool/ and https://security.debian.org/debian-security/pool/; see each package's copyright file and source metadata. FFmpeg upstream: https://ffmpeg.org/legal.html; tini: https://github.com/krallin/tini; curl: https://curl.se/docs/copyright.html.
- **Android dependencies**, including AndroidX, Media3, Kotlin and Gradle, are retrieved by the Android build and retain their upstream license terms. The Gradle wrapper is included for reproducible build invocation; https://github.com/gradle/gradle/blob/master/LICENSE.

No provider media, subscription, login, encryption key, administrator token or device signing key is distributed with this repository or container build context.
