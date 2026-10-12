# IPTV for NVIDIA Shield and a self-hosted Docker server

Research and implementation plan — October 12, 2026

Status: researched design, not an implemented or hardware-tested application. Scope includes standalone Android TV playback, a companion mode, and a locally hosted server compatible with Unraid. Numerical settings below are initial engineering proposals to validate, not measured performance claims.

## 1. Recommended product architecture

Build one native Android TV app with two connection modes, plus one self-hosted server with a browser administration interface.

| Responsibility | Standalone mode | Companion mode |
|---|---|---|
| Xtream credentials | Encrypted on Shield | Encrypted on server; never sent to companion |
| Provider login and channel synchronization | Shield | Server |
| EPG download, parsing and mapping | Shield | Server; client caches queried results |
| Upstream connections and provider limits | Shield | Server |
| Upstream monitoring and reconnection | Shield | Server |
| Buffering and stream packaging | Shield playback buffer | Server rolling buffer plus client playback buffer |
| Optional codec/bitrate conversion | Limited to local decoding capabilities | Server FFmpeg workers, GPU when supported |
| Final video/audio decode and display | Shield | Shield |
| Last-hop network and renderer monitoring | Shield | Shield |

Companion mode must receive media through the server, not a redirect to a provider URL. The server can handle acquisition, buffering, remuxing, transcoding and recovery. The Shield still decodes the resulting compressed stream, renders it, handles audio output and monitors its own connection. Moving all decoding to the server would require a different remote-display design and is unnecessary here.

Use a native Kotlin application with Media3 ExoPlayer, Compose for TV, Room and coroutines. Recommend a Go server for APIs, scheduling and worker supervision; React/TypeScript for administration; SQLite in WAL mode for one-server deployments; and pinned FFmpeg binaries for media processing. These are design choices, not requirements imposed by Xtream. Keep one Docker image for initial installation simplicity; do not require Redis, Kubernetes or an external database.

Two paths:

```text
Standalone: Provider API/EPG ──> Android repositories ──> TV UI
            Provider media ──> Media3 + health controller ──> HDMI

Companion:  Provider API/EPG ──> Docker server database ──> Android cache/UI
            Provider media ──> ingest supervisor ──> rolling HLS output
                            ──> authenticated LAN delivery ──> Media3 ──> HDMI
            Browser admin ──> provider / user / device / stream management
```

The server is optional. No cloud account or remote dependency is required to use either mode. A server outage does not silently switch to direct provider access: standalone credentials and permission must be configured separately, and automatic fallback is initially disabled to avoid extra provider sessions.

## 2. What seamless recovery can mean

Aim for automatic recovery with minimal visible interruption. A brief outage can be hidden when playable buffered media lasts longer than the outage plus reconnect and refill time. A long provider outage cannot be concealed indefinitely, and missing broadcast content cannot be reconstructed. An alternate source might have a different delay, advertisement or regional feed, so failover cannot promise frame-perfect continuity.

The design should preserve the playback screen, avoid returning users to channel lists, avoid unnecessary player recreation, and use a discreet reconnecting overlay only when playback actually stops. Keeping the last frame visible is helpful, but does not count as uninterrupted playback.

Three separate measurements are required: upstream outage duration, visible interruption duration, and time to stable recovery. A successful HTTP connection alone is not recovery.

## 3. Initial feature scope

Release 1 includes Xtream server URL/username/password login; live categories and channels; favorites; search; recently watched; full-screen playback; audio/subtitle selection; now/next and grid EPG; per-channel guide correction; automatic recovery; diagnostics; standalone/companion setup; server user/device management; and Docker/Unraid installation.

Defer recording, catch-up playback, movies/series libraries, multi-view, remote internet streaming, cloud synchronization and automatic cross-provider backup feeds until live playback is dependable. Include identifiers in the schema that can support those later without enabling them now.

The default setup screen offers “Connect to my server” and “Use a provider directly.” Both lead to the same guide and player experience. Show the active source in settings so users understand where credentials and recovery are managed.

## 4. Provider adapter and Xtream compatibility

Treat Xtream as a compatibility family, not a single uniformly implemented specification. Implement adapters in both standalone Android and the server against the same recorded, sanitized contract fixtures. Provider behavior must be tested using an actual authorized account before claiming compatibility.

Expected operations are account/server information through `player_api.php`, `get_live_categories`, `get_live_streams`, full XMLTV through `xmltv.php`, and optional `get_short_epg` or `get_simple_data_table`. The tunlx implementation documents these common patterns; its behavior is evidence for its implementation, not proof that every provider matches it. [Xtream-compatible implementation reference](https://tunlx.io/reference/xc-api/)

Implementation sequence:

1. Normalize the supplied base URL while preserving a legitimate installation subpath and port. Encode credentials correctly in both query parameters and path segments.
2. Verify account response and distinguish malformed responses, bad credentials, disabled/expired accounts, network failure and provider refusal. Missing optional fields are not automatic rejection.
3. Record provider capabilities: output formats, guide support, timezone hints and reported connection limit. Treat active-connection counts as advisory and potentially stale.
4. Import categories and channels transactionally. Namespace stream IDs by provider/profile. Keep favorites stable through refresh and retain the previous catalog if refresh fails.
5. Resolve media URLs through the adapter. Support conventional `/live/{username}/{password}/{streamId}.m3u8` and `.ts` routes only when verified; allow provider-specific redirects and signed URLs. Do not assume changing a suffix creates another supported stream.
6. Prefer HLS when it works well for that provider, with continuous MPEG-TS as a tested alternative. Store successful per-channel transport preferences with expiry and a reset option.
7. Refresh metadata independently of playback. Never re-download the full catalog on every stall.

Support string/numeric ID inconsistencies, null metadata, missing logos, large catalogs, compressed responses, HTML error pages with HTTP 200, and optional base64 fields. Decode text only when the field contract supports it; avoid mangling ordinary titles that happen to resemble base64.

Provider and device credentials must be excluded from logs, crash reports, diagnostics, analytics and browser URLs. Prefer HTTPS. If HTTP-only provider support is enabled, explain the transport limitation during setup and apply an explicit per-provider application policy. Android's static network-security XML cannot dynamically enumerate arbitrary user-entered domains; the networking layer must enforce the selected policy as well. Never disable TLS certificate verification. [Android network security configuration](https://developer.android.com/privacy-and-security/security-config)

## 5. Android design

Separate `ProviderRepository`, `GuideRepository`, `PlaybackResolver`, `PlaybackController`, `PlaybackHealthMonitor`, and `RecoveryCoordinator` from the UI. Use direct-provider and server-backed implementations behind the same interfaces. Keep the recovery logic testable with a fake clock and recorded event sequences.

Media3 supports HLS and progressive MPEG-TS, while actual sample codec support depends on the device and decoder. Validate H.264, HEVC, common AAC/AC-3/E-AC-3 audio, interlaced feeds and subtitles on target Shield hardware rather than assuming that container support guarantees playback. [Media3 format support](https://developer.android.com/media/media3/exoplayer/supported-formats)

Own the player outside individual Compose screens. Opening the guide must not destroy playback. Attach a MediaSession for remote/media commands. Default to stopping/releasing playback when the user exits the viewing experience; introduce a properly configured media service only if background playback becomes an explicit feature. Suspend recovery when paused, stopped, suppressed by audio focus, or intentionally detached from a video surface.

Use TV Material components and standard Compose lazy layouts with stable keys. Virtualize guide channels and time ranges; retain focus when data refreshes. Standard Foundation lazy layouts are the current documented direction for TV lists. [Android TV layouts](https://developer.android.com/training/tv/playback/compose/lists)

Remote behavior: D-pad navigation throughout; OK opens controls; Back first dismisses overlays; guide return restores channel/time focus; long-press actions never become the only way to access settings. Keep the current player alive beneath the guide without starting preview streams for other channels.

Persist only bounded guide/catalog caches and user settings. Encrypt standalone secrets using an application encryption key protected by Android Keystore, with explicit backup exclusions and a re-login path if keys become unavailable. [Android Keystore](https://developer.android.com/privacy-and-security/keystore)

## 6. Monitoring and recovery state machine

States: `IDLE`, `STARTING`, `PLAYING`, `SUSPECT`, `RECOVERING`, `WAITING_NETWORK`, `COOLDOWN`, `NEEDS_USER`, `STOPPED`.

Run a lightweight health sample approximately once per second while viewing. Use monotonic time. Inputs include playback state/error, suppression/pause state, playback-position progression, playable buffer, load activity, recent bytes, decoder counters, first-frame events and timeline/playlist progression. Network callbacks provide context; a connected network is not proof the provider is reachable.

`onRenderedFirstFrame` is a startup/reset event, not a continuous heartbeat. Sample available decoder counters and carefully validated frame timing instrumentation to detect renderer progress. Position advancing with audio does not prove video is advancing. Conversely, repeated image content or black frames with valid advancing timestamps may be legitimate programming; do not restart based solely on visual similarity. [Media3 analytics callbacks](https://developer.android.com/reference/androidx/media3/exoplayer/analytics/AnalyticsListener)

| Symptom | Interpretation | Proposed response |
|---|---|---|
| Short load stall, playback continues | Buffer is doing its job | Allow bounded native retries; preserve playback |
| Buffer exhausted and no progress | Transport or source failure | Reconnect/re-resolve using the mode-specific owner |
| HLS position behind available window | Expired playback position | Seek default live position and prepare |
| Continuous TS ends unexpectedly | Lost live connection | Reopen stream; do not seek a fictional live window |
| Bytes arrive but video counters stop | Possible decoder/source video stall | Corroborate active video and surface state; bounded renderer/player recovery |
| HTTP 401/403 | Authentication, expired URL, or provider policy | Re-resolve once where applicable; recheck account; avoid endless login loops |
| HTTP 429 | Rate limit | Respect Retry-After and reduce attempts |
| HTTP 5xx or timeouts | Transient upstream failure | Bounded backoff and optional approved alternate |
| Network absent | Local connectivity failure | Wait for usable connectivity; preserve remaining buffer |
| Unsupported codec | Compatibility problem | Companion requests compatible server profile; standalone shows actionable error |

Media3 documents default-position recovery for behind-live-window errors and explicitly distinguishes adaptive live windows from progressive live streams. [Live playback behavior](https://developer.android.com/media/media3/exoplayer/live-streaming)

Initial tuning proposals:

- Startup deadline: 15 seconds before escalation, configurable for slow providers.
- Suspect a stalled active renderer after roughly 5 seconds without expected progress; require corroboration and exclude legitimate lifecycle transitions.
- Transport reconnect escalation after approximately 8–12 seconds of genuine stalled playback; HLS playlist-staleness detection must account for target duration, segment cadence and remaining playable buffer.
- Recovery delay ladder: 0, 1, 2, 4, 8 seconds with jitter, subject to an overall budget and Retry-After. Enter cooldown after five attempts or 45 seconds. While the user still wants playback, allow one probe about every 30 seconds; stop automatically after five minutes and offer Retry.
- Declare recovered after fresh video/audio progress and several stable seconds. Reset the failure budget only after 60 seconds of healthy playback to avoid oscillating loops.

These are tunable defaults, not universal constants. Native loader retries and coordinator retries share a budget; never let both independently multiply attempts. Media3 exposes a load-error policy for this integration. [Load error policy](https://developer.android.com/reference/androidx/media3/exoplayer/upstream/DefaultLoadErrorHandlingPolicy)

The recovery ladder is: preserve buffered playback; allow bounded native retry; perform transport-appropriate soft recovery; refresh the current stream locator if needed; rebuild the media source; recreate the player only for a persistent renderer failure; then use a explicitly mapped, authorized alternate if enabled. In companion mode, upstream stages belong to the server. The client repairs only its local delivery, session or decoder problem and consumes server recovery status.

Every tune operation gets a new generation ID. Channel changes, logout, pause and stop cancel applicable old tasks. Discard late callbacks belonging to another generation. Serialize mutations on the player's required thread and enforce one recovery operation per session.

## 7. Server media pipeline

The server has a control plane for accounts/catalog/EPG and a media plane for ingest/output. Suggested modules: provider adapters, guide importer, session manager, connection-budget manager, ingest supervisor, FFmpeg worker adapter, segment store, authenticated media delivery and diagnostics.

Three output profiles:

| Profile | Server work | Use |
|---|---|---|
| Relay | Fetch and forward compatible media | Lowest processing cost; fewer continuity controls |
| Buffered remux | Copy encoded audio/video into server-controlled HLS | Recommended companion default when compatible |
| Transcode | Decode/encode video and/or audio to compatible formats | Codec mismatch, bitrate restriction, deinterlacing or normalization |

Remuxing and transcoding are different operations; a GPU is not required simply to move compatible encoded streams through a server. Transcoding is the expensive path. [Media processing distinctions](https://jellyfin.org/docs/general/post-install/transcoding/)

Start pipelines on demand and stop after the final subscriber leaves, with a short configurable grace period. Share an ingest only when provider authorization allows it, the account/channel identity matches, and output policies are compatible. Different profiles can share ingest but may require separate encoding workers. Use atomic reservations for provider connection slots; include initializing, draining, preview and recovery sessions in accounting. Close old upstream sessions before opening replacements when only one is allowed. Do not probe every channel continuously.

Each viewing session gets a stable app-controlled media route. The server owns an output timeline across worker restarts. Publish complete segments atomically, keep sequence numbers monotonic, use unique generation-specific segment filenames, and mark timestamp/codec transitions with appropriate HLS discontinuities. For fMP4, handle changed initialization segments. Keep old referenced segments until clients have had a bounded grace period to fetch them. Do not expose an FFmpeg output directory as the entire continuity strategy. FFmpeg supplies useful segmentation and temporary-file features, but continuity across processes is application work. [FFmpeg HLS muxer](https://www.ffmpeg.org/ffmpeg-formats.html)

For initial buffered mode, investigate 2–4 second target segments and a 30–60 second retention window, with a client live offset around 8–15 seconds. Segment boundaries in stream-copy mode depend on source keyframes; exact short segments may require transcoding. Retention is not automatically usable outage protection: actual protection depends on the client's chosen playback delay and contiguous media ahead of it. Longer protection increases distance from the live broadcast.

Monitor process exit, ingest-byte progression, output timestamps, segment publication cadence, discontinuities, queue sizes and client-visible errors. A live FFmpeg process with no progressing output is unhealthy. Bound worker restarts and collect redacted diagnostics. FFmpeg offers HTTP reconnect controls; verify options against the pinned build and test them with the supervisor's own retry budget. [FFmpeg protocol options](https://ffmpeg.org/ffmpeg-protocols.html)

If an upstream failure exceeds available media, keep the session and report the outage. Do not relabel old segments as new or endlessly loop the last seconds of programming. Recover into a new valid output generation; use a client re-prepare only when continuity cannot be preserved.

## 8. Companion protocol and user management

Version the API (`/api/v1`) and provide a capability handshake so old clients get useful compatibility errors. Proposed resources:

| Route | Purpose |
|---|---|
| `POST /pairing/requests` | Issue short-lived pairing request and device code |
| `POST /pairing/approvals` | Authenticated administrator approves requested device |
| `GET /capabilities` | API version and server/client negotiation |
| `GET /channels`, `GET /guide` | Paged catalog and bounded UTC guide interval |
| `POST /playback/sessions` | Authorize channel/profile; reserve resources; return session |
| `GET /playback/sessions/{id}` | State and recovery reason |
| `POST /playback/sessions/{id}/heartbeat` | Renew client lease |
| `DELETE /playback/sessions/{id}` | Release subscriber and resources |
| `/media/{session}/...` | Authenticated manifest, segments and keys where applicable |
| `/events` | SSE or WebSocket stream of session/status changes |

Pairing requires a high-entropy device secret plus short human code, expiration and rate limiting. Device tokens are revocable and scoped to the user's permitted channels and session limits. A readable code alone is not authentication. Token refresh must work during long playback without exposing provider credentials.

Authorize all media resources, not just the first playlist. Use authenticated Media3 requests or scoped short-lived delivery tokens; never leak them through logs. Validate media paths against traversal. Close abandoned sessions using leases; a lost network client must not hold a provider slot forever. Apply per-user and server-wide concurrency/resource limits.

The web admin includes providers, users, paired devices, EPG status, active sessions, upstream/downstream health, current processing profile, hardware probe results and redacted support export. Local browser playback is an optional later feature, not required for the first administration UI.

## 9. EPG ingestion and storage

Use XMLTV as the main bulk guide source with optional short-EPG enrichment for current channels. Join provider `epg_channel_id` to XMLTV channel identifiers within the provider namespace. Provide manual mappings and time offsets; do not automatically merge similarly named regional channels. XMLTV represents programmes using channel and time attributes. [XMLTV project schema](https://raw.githubusercontent.com/XMLTV/xmltv/master/xmltv.dtd)

Stream-parse XML rather than building a full DOM. Disable external entity resolution, bound decompressed input/record sizes and duration, and reject invalid timestamps without discarding every valid record. Normalize explicit offsets to UTC, preserve source timezone diagnostics, handle DST, and use a documented fallback for provider data without reliable timezone information. Store user corrections separately from imported data.

Suggested entities: provider, category, channel, EPG channel mapping, programme, favorite, device, user, playback session and health event. Index programmes by provider/channel/start time; query overlapping time intervals. Keep metadata separate from secrets. Stage each import, validate it, then atomically activate it; retain last-known-good data on empty or malformed refreshes. Deduplicate records and prune bounded history.

Initial retention: previous 24 hours and next seven days, only as available from the provider. Refresh about every six hours, with manual refresh and conditional requests where supported. Display last successful update and distinguish “no listings” from “guide download failed.” Guide failures never stop media playback.

Use WorkManager for deferred standalone EPG refresh, not playback recovery: periodic work is inexact and has a minimum 15-minute interval. Server imports use its scheduler. Companion clients sync metadata/deltas and fetch visible guide ranges instead of downloading every programme on each launch. [WorkManager scheduling](https://developer.android.com/develop/background-work/background-tasks/persistent/getting-started/define-work)

## 10. Docker and Unraid deployment

Ship a versioned Linux amd64 image first, containing the server, static admin build and pinned FFmpeg. Add arm64 only after separate packaging and playback verification. Run as a configurable non-root UID/GID, support supplemental GPU groups, use a minimal init, and reap worker processes. The application must shut down workers cleanly on SIGTERM.

Unraid distribution deliverables are a Docker template XML, installation guide, icon/WebUI metadata and release notes; a Compose example also supports other Linux Docker hosts. Community Applications publication is a separate release task. Unraid templates expose ports, paths and environment settings. [Unraid container configuration](https://docs.unraid.net/unraid-os/using-unraid-to/run-docker-containers/managing-and-customizing-containers/)

Proposed mappings (examples to confirm on the actual server):

| Setting | Suggested value |
|---|---|
| Network | Bridge; configurable host port to container 8080 |
| Persistent configuration | `/mnt/user/appdata/iptv-server` to `/config` |
| Media scratch | Dedicated SSD pool directory to `/transcode` |
| Timezone | `TZ=America/New_York`; persist timestamps in UTC |
| Restart | `unless-stopped` |
| Privilege | No privileged mode and no Docker socket mount |
| Health | Liveness and readiness endpoints; provider outages do not fail process liveness |

Place the database on reliable local pool storage; avoid network-mounted SQLite databases. Prefer SSD scratch or explicitly size-limited tmpfs. Enforce per-session and global storage quotas; keep rolling segments out of the container writable layer and Unraid boot USB. Protect appdata backups containing secrets. Save database-consistent backups plus the encryption key, verify restore, and retain the previous image/database backup for migrations that cannot roll backward.

Docker marks failed health checks but does not, by itself, restart a merely unhealthy container. Restart policy handles process exits; add an explicitly designed recovery mechanism for unrecoverable deadlock rather than assuming the health check performs it.

GPU support is optional and discovered at setup:

- Intel/AMD Linux: map the required `/dev/dri` devices and group permissions, then validate the actual decode/encode path with a sample.
- NVIDIA: document the Unraid driver/runtime prerequisites and narrowly expose the selected GPU. An NVIDIA Shield client does not imply an NVIDIA GPU exists on the server.
- If acceleration is unavailable, retain remux operation. Permit software transcoding only within measured limits; reject excessive jobs instead of allowing overload to damage every session.

Codec, driver, GPU and FFmpeg build all affect acceleration; device presence alone proves nothing. [Hardware acceleration reference](https://jellyfin.org/docs/general/post-install/transcoding/hardware-acceleration/)

Default to LAN use. Authenticate administration and clients, rate-limit login/pairing, protect cookie-based actions against CSRF, and offer TLS setup or a trusted reverse proxy. Runtime provider fetching needs scheme/redirect validation and SSRF controls, with explicit handling for intentionally local providers. Launch FFmpeg with argument arrays, never interpolated shell commands. Limit outbound protocols and keep credentials out of exported process diagnostics. Do not expose the service publicly as part of initial deployment.

## 11. Capacity and performance budgets

Measure remux and transcode capacity separately. Budget storage as approximately bitrate × retention ÷ 8, plus segment overlap and multiple output profiles. At 12 Mbps, 60 seconds is about 90 MB per retained stream; six distinct streams use about 540 MB before overhead. This is sizing arithmetic, not a hardware benchmark.

Six clients at 12 Mbps require about 72 Mbps server egress before overhead even when they share one ingest. Six distinct channels can require six upstream sessions. Shared ingest reduces upstream duplication, not the aggregate unicast delivery to clients. Provider permissions and limits still apply.

Cap workers, memory, disk, pending requests and logs. Start with one supported concurrent transcode until hardware measurements justify more. A transcode must maintain real-time output with headroom under the declared supported profile. Report per-session mode, bitrate, startup delay, buffer, reconnect count, current failure reason and server queue saturation.

Keep local bounded telemetry by default. Log opaque session/channel identifiers and error categories, not stream URLs or credentials. Aggregate percentiles for tune time, visible stalls and recovery, and export only redacted diagnostics.

## 12. Validation and release gates

Build a controllable local stream fixture with HLS and continuous TS, a test Xtream API, large XMLTV feeds and fault injection. Verify on physical Shield models in scope; an emulator cannot establish HDMI, hardware decode or long-session reliability. The lower-memory Shield TV and Pro should have separate results. NVIDIA lists the non-Pro model with 2 GB RAM. [NVIDIA Shield specifications](https://www.nvidia.com/en-eu/shield/shield-tv/)

| Test | Required behavior |
|---|---|
| 1–3 second upstream interruption with sufficient buffered media | No visible interruption in controlled fixture |
| 10–30 second upstream interruption | No user intervention; bounded retries; resumed fresh media |
| Frozen HLS playlist returning HTTP 200 | Detect missing timeline progress; avoid declaring HTTP success healthy |
| TS EOF, stalled socket, corrupt segment, timestamp reset | Correct bounded recovery without loop or app crash |
| Audio continues but video decoder stops progressing | Detect genuine video stall without reacting to legitimate still images |
| 401, 403, 429, 5xx and expired URL | Correct classification, Retry-After support and finite budgets |
| Ethernet unplug/replug, Wi-Fi handoff, DNS failure | Differentiate local connectivity and upstream availability |
| Pause, guide overlay, surface recreation, sleep/wake | No false recovery or unintended playback restart |
| Rapid channel changes during retries | Old operations cannot resume the wrong channel |
| One-slot provider with two simultaneous requests | Atomic budget enforcement; no double opening during recovery |
| Worker crash and server restart | No orphan processes; session re-establishment; fresh valid manifests |
| Missing segment, discontinuity and codec switch | Verified client behavior for each supported output profile |
| GPU failure, saturated CPU or full scratch volume | Resource-aware failure; protect unaffected streams |
| Expired/revoked companion token | No unauthorized playlist/segment access |
| Truncated XMLTV, duplicate IDs, DST and 500,000 programmes | Bounded import; correct time mapping; previous guide retained |
| 10,000-channel catalog and guide scrolling | Responsive remote focus and bounded memory |
| 24-hour playback, then 72-hour release soak | No crash, unbounded memory/disk growth, or worker leak |

Proposed lab acceptance targets: warm-channel first frame p95 under 3 seconds and cold-channel p95 under 6 seconds for a declared test profile; at least 99 of 100 recoverable injected outages recover without user input; restored-source to stable playback p95 under 10 seconds for short-segment fixtures; no provider-slot violations; and no false reconnect in an eight-hour healthy control run. Report long-keyframe and slow-provider results separately instead of excluding them silently. These targets are goals to tune, not promises for arbitrary streams.

Measure visible playback continuity with test video timestamps/frame counters and audio markers plus on-device observation, not only server logs. Verify the full Unraid → LAN → Shield → TV/receiver path including passthrough audio, HDR where supported, and sustained playback while EPG imports run.

## 13. Implementation roadmap

Planning estimate for one experienced developer working full time: roughly 12–18 weeks to a tested private beta, with overlap where practical and hardware/provider issues potentially extending it. This is an estimate, not a commitment.

| Phase | Approximate effort | Exit gate |
|---|---|---|
| 0. Compatibility spike | 1–2 weeks | Real Shield/provider profiles, HLS/TS fixture, codec/GPU inventory, agreed metrics |
| 1. Vertical slice in both modes | 2–3 weeks | Standalone login/play plus Docker login → server media → paired Shield playback |
| 2. Recovery foundation | 2–3 weeks | Unified event model, bounded retry ownership, network/worker fault tests |
| 3. Guide and TV navigation | 2–3 weeks | Transactional XMLTV ingestion, mapping, favorites, performant remote guide |
| 4. Server hardening and Unraid | 2–3 weeks | Stable output generations, users/devices, quotas, backups, template, GPU profiles |
| 5. Private beta and soak | 3–4 weeks | Physical-device matrix, 24/72-hour runs, support export, signed release artifacts |

Prioritize the two-mode vertical slice and failure harness before polishing the guide. The highest-risk questions are provider behavior, timeline continuity across reconnects, connection-slot cleanup and actual hardware codec compatibility.

Proposed repository layout:

```text
android/                 Native TV app and both repository implementations
server/                  API, providers, sessions, EPG and worker supervision
web/                     Browser administration
contracts/               OpenAPI, events, compatibility fixtures
tests/streams/           Synthetic media and fault scenarios
deploy/docker/           Image build and Compose example
deploy/unraid/           Template and installation notes
docs/                    Architecture decisions, testing and operations
```

Deliverables: signed APK, versioned Docker image, Unraid template, migration/backup procedures, provider compatibility matrix, physical Shield report, recovery benchmark report and limitations. Review FFmpeg build licensing and bundled notices before distributing binaries.

## 14. Inputs to confirm before implementation

Confirm target Shield generation(s), installed OS, server CPU/GPU/RAM, Unraid version and available SSD pool; expected simultaneous users/channels; LAN-only versus future remote access; provider connection limits and available HLS/TS variants; whether a larger broadcast delay is acceptable for better outage masking; and distribution through private sideloading or eventually an app store.

No credentials are needed in the planning document. Use private entry on the device/server for later integration tests. Until hardware and provider checks occur, the plan deliberately treats transport support, acceleration capacity and timing thresholds as unverified.


## Product clarification — 2026-10-12

Companion mode is an independently controlled TV client backed by a server-managed feed. It is not screen mirroring and does not depend on a channel being selected in the dashboard. Android chooses any entitled channel, browses provider and personal groups, and uses the programme guide just as in standalone mode. The server owns provider ingress, format adaptation, buffering, progress monitoring and recovery; Android owns user interaction and local rendering. The dashboard is another player/controller. Per-account connection limits apply across both.

The implemented 0.2 guide uses the familiar TV-guide arrangement (left group sidebar, channel rows, horizontal programme timeline) with Signal TV styling. Provider category names come from Xtream; personal groups are a separate editable collection. Continuous timeline stitching, shared-ingest fan-out, GPU profiles and preemptive failover remain later milestones. A relay cannot reconstruct content the upstream provider did not deliver, and bounded recovery does not mean gapless recovery.
