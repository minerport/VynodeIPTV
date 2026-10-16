# Third-party notices

VynodeIPTV includes or depends on the following components. The root MIT license covers VynodeIPTV original code, templates, metadata and artwork. Third-party components retain their respective licenses; the root license does not relicense them.

- **hls.js 1.7.3**, Apache-2.0: the vendored browser bundle and complete notice are in `server/web/vendor/`; upstream source: https://github.com/video-dev/hls.js/tree/v1.7.3.
- **Inter** and **Atkinson Hyperlegible Next** variable fonts, SIL Open Font License 1.1: bundled locally in `server/web/fonts/` and Android `res/font/`. Complete licenses are in `server/web/fonts/INTER-LICENSE.txt`, `ATKINSON-LICENSE.txt`, and Android assets. Upstream sources: https://github.com/google/fonts/tree/main/ofl/inter and https://github.com/google/fonts/tree/main/ofl/atkinsonhyperlegiblenext.
- **Go**, BSD-style license: https://go.dev/LICENSE. The server is built using the official Go container image.
- **Debian runtime packages**, including FFmpeg, tini, curl, CA certificates and timezone data: licenses are retained in the image under `/usr/share/doc/<package>/copyright`. Exact installed versions can be inspected with `dpkg-query -W` inside the image. Debian corresponding source packages are available at https://deb.debian.org/debian/pool/ and https://security.debian.org/debian-security/pool/; see each package's copyright file and source metadata. FFmpeg upstream: https://ffmpeg.org/legal.html; tini: https://github.com/krallin/tini; curl: https://curl.se/docs/copyright.html.
- **Android dependencies**, including AndroidX, Media3, Kotlin and Gradle, are retrieved by the Android build and retain their upstream license terms. The Gradle wrapper is included for reproducible build invocation; https://github.com/gradle/gradle/blob/master/LICENSE.

No provider media, subscription, login, encryption key, administrator token or device signing key is distributed with this repository or container build context.

## TMDB

This product uses the TMDB API but is not endorsed or certified by TMDB.

Optional metadata and artwork remain subject to TMDB's terms, https://www.themoviedb.org/api-terms-of-use. TMDB branding is not covered by this repository's MIT license. The approved gradient logo in `server/web/tmdb-logo.svg` is the original logo mirrored at https://commons.wikimedia.org/wiki/File:Tmdb.new.logo.svg, which identifies its official TMDB source. `android/app/src/main/res/drawable/tmdb_logo.xml` reproduces the same path, colors, gradient and proportions as a native Android vector drawable. Attribution and branding requirements: https://www.themoviedb.org/about/logos-attribution.
