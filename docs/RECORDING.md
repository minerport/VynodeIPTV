# Built-in recording (server 0.11.1 / Android 0.11.0 preview)

Recording is part of VynodeIPTV. No separate DVR application or subscription is required.

## Android standalone

Open **Recordings** beside the guide preview. Choose **Use internal storage** or **Choose USB / folder**; USB storage is recommended for Shield. The app remembers the choice. Each saved schedule retains its own destination even if the default is changed later. Removing a drive never redirects recordings to internal storage.

Use **Record / schedule** for the highlighted channel/programme. Set the end time to the programme end or a custom time. The recording editor also supports choosing a different channel and a manual local start/end time. Padding defaults to one minute before and three after. **Allow scheduled recordings** opens Android's Alarms & reminders permission when needed.

The Android foreground service records without the server and without keeping the guide or player open. The device must remain powered on and connected. Android force-stop, unplugging the device, revoked alarm permission, or a missing USB drive can prevent recording. After a restart, saved schedules are restored; an interrupted recording still within its time window resumes with an interruption marker. Missed windows are reported rather than silently moved.

This first standalone recorder uses the provider's **MPEG-TS (`.ts`) endpoint**, even when live playback uses HLS. Providers offering only HLS are not supported for standalone recording in this release. The service validates the transport stream, keeps separate packet-aligned parts, flushes progress to storage, applies read timeouts and bounded retries, and records interruptions as partial results. Opening the recording library does not cancel recordings. Local footage remains after provider logout or removal; future jobs need the saved account.

## Server / Unraid

Before upgrading, add a persistent writable mapping:

| Container path | Suggested Unraid host path |
|---|---|
| `/recordings` | `/mnt/user/recordings/vynodeiptv` |

Create the host folder as UID/GID **10001**. Existing containers do not automatically gain the new mapping when their image updates. Do not use `/transcode` for recordings. Keep `/config` and its encryption key because recording metadata and schedules live there. Back up both configuration and recorded media.

Browser **Recordings**, the preview **Record** button, and EPG programme actions use the built-in server scheduler. In Android companion mode, the same recording screen controls server jobs. The server continues recording when the browser closes or Android disconnects. Each signed-in identity has its own library; a paired Android device and the administrator are separate identities.

The server accepts either provider HLS or MPEG-TS, records original codecs using its bundled FFmpeg, and builds a local playback playlist from finalized segments. Restarts preserve completed segments and resume active windows with a discontinuity marker. A partial recording may omit footage lost during the interruption; reconnection cannot recreate a live broadcast that was never received.

## Limits and controls

- Recordings and live wall tiles share the provider connection budget. Overlapping reservations that exceed the budget are rejected. If live streams fill the budget when a schedule starts, the recording fails with an explicit conflict; it does not interrupt existing streams. Stop a live tile before recording when your provider permits only one connection. Other IPTV apps using the same account are outside VynodeIPTV's local budget.
- A server has a combined eight-worker limit. Recordings use disk and provider connections; they are independent of player leases.
- The library shows scheduled, recording, completed, partial, failed and cancelled results. Completed/partial footage can be played/resumed. Cancellation keeps existing footage. Deletion is explicit; no automatic retention deletion runs.
- Server storage must have at least 512 MiB free before and during capture. Android checks 512 MiB before creating a part and stops below 128 MiB during writes. These reserves are safeguards, not estimates of enough space for the whole programme.
- Schedules retain the chosen title and UTC time window. EPG changes do **not** automatically retime recordings in this version; review changed programme schedules manually.
- Browser playback prepares a separate H.264/AAC copy (up to 720p, 30 fps, stereo), with audio clock correction. Existing recordings benefit too; the original provider-quality files are retained for Android. Playback begins after 12 seconds beyond the resume position are prepared, or when conversion completes. Preparing a long recording at a late resume point can take time. Preparation uses CPU and additional space alongside the original in `/recordings`; it does not contact the provider or consume another provider connection. One preparation runs at a time, within the combined eight-worker limit. It stops if free space falls below 512 MiB or output stops progressing for 90 seconds. Slow CPUs may still rebuffer during preparation; a completed copy is reused on subsequent plays and across server restarts.
- Closing the recording player cancels unfinished preparation; reopening starts that preparation again. Completed copies remain until the recording is explicitly deleted. Browser wall decoders pause during recorded playback and resume on close; server live sessions retain their leases. Conversion cannot recreate missing footage or guarantee repair of a damaged source recording.
- Series rules, shared live/recording ingest, watch-while-recording, automatic retention and automatic EPG-change reconciliation remain future work.

## Device acceptance checklist

Before relying on a long recording, test a short programme on the actual provider and device: record with the guide closed, stop and play it, schedule a second recording, restart during capture, and check the retained result. Test internal storage and the intended USB folder separately. Confirm D-pad navigation and permissions on the physical Shield. Container smoke tests and APK compilation do not replace this device verification.
