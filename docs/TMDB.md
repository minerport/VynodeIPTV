# Optional TMDB guide enrichment

VynodeIPTV can enrich visible guide entries and selected/current movie and series programmes with TMDB posters, synopsis, release year, genres and up to five cast names. No VynodeArr connection is needed. Provider titles, descriptions, channels and broadcast times remain intact. Details arrive asynchronously after the guide is visible; playback never waits for TMDB.

## Connect

Get your **API Read Access Token** from https://www.themoviedb.org/settings/api. This is the bearer read token, not your account password or the shorter v3 API key. Agree to TMDB's applicable terms when obtaining your token. No token is distributed with VynodeIPTV.

- **Server/browser:** Server settings → Metadata · TMDB. Enter the token, enable enrichment, and choose Test & save. The token is verified with TMDB's configuration endpoint before enabled settings are saved. Leave the token field blank to retain the saved value. Disconnect removes the saved credential and metadata cache.
- **Android standalone:** Connection settings → Metadata · TMDB. Enter and enable a local token. It is encrypted using Android Keystore. This setting is independent of provider selection and normal logout.
- **Android companion:** metadata comes from the linked IPTV server; configure its connection through the server WebUI. Paired devices cannot read or modify the server's TMDB token. Older servers without these endpoints simply show the provider guide.

Server credentials are retained in encrypted `/config/state.enc`; API responses never return the token. Requests send it only in the Authorization header to the fixed HTTPS TMDB API, with redirects disabled. Visible and selected programme titles are sent to TMDB for searching; provider addresses, usernames and passwords are not. Artwork is fetched from TMDB's image CDN.

## Matching and caching

Matching uses exact normalized movie/series title matching, optionally disambiguated by the provider production year or a trailing `(YYYY)` in the title. An ambiguous result, partial search result set, adult/person result, generic news/sports title or unsupported programme remains unchanged. Series metadata is labeled as series-level information; it does not claim to identify a specific episode. Episode matching, fuzzy matching, manual overrides, localization and whole-catalog prefetch are not included.

Visible guide cells gain small posters, release year and genres. Original titles and time slots stay unchanged; provider episode descriptions take priority over generic TMDB synopses. Artwork falls back to a backdrop when a poster is absent. Browser image policy permits the fixed TMDB image CDN. Enrichment runs on the current visible window (up to 64 entries), as well as programme focus/selection and current playback. Scrolling abandons queued work for the previous window. A short debounce avoids requests for every item passed during navigation. Server requests are deduplicated, use a bounded queue and one lookup at a time, with a delay between jobs. Android standalone uses one lookup at a time. Matches are cached for seven days, misses for six hours, with up to 1,000 entries. Errors trigger a one-minute backoff; missing metadata never replaces usable provider information. Server caches are pruned at startup and hourly. Local Android cache entries are expired on use. Cached public metadata is separate from credentials.

No real TMDB credential is needed for automated tests: fake HTTP responses cover authentication transport, ambiguous matches, cache persistence, outages and credential isolation. A user-supplied token and actual device are required for live TMDB acceptance.

## Credits

This product uses the TMDB API but is not endorsed or certified by TMDB.

Official references:
- Authentication: https://developer.themoviedb.org/docs/authentication-application
- Search: https://developer.themoviedb.org/reference/search-multi
- Artwork: https://developer.themoviedb.org/docs/image-basics
- Attribution: https://developer.themoviedb.org/docs/faq
- Rate limiting: https://developer.themoviedb.org/docs/rate-limiting
- Terms: https://www.themoviedb.org/api-terms-of-use
