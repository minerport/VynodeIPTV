# 0.16.5 playback quality and diagnostics

Browser quality: Auto converts to compatible H.264/AAC; Original remuxes provider codecs and requires browser support; Data Saver converts to at most 854x480 with a bounded encoding rate. This reduces server output traffic, not provider ingest traffic. Changes apply to newly opened streams.

Android stores quality per connection. Companion Auto/Original use native codecs; Data Saver requires this server version. Standalone Data Saver prefers lower renditions when available; it cannot reduce fixed-quality feeds.

Support reports use explicit numeric/enum allowlists, excluding channel names, tokens, addresses and arbitrary error messages. Browser reports download as JSON; Android reports copy to the clipboard.

Linux CPU diagnostics report host-wide /proc/stat utilization, not just this container. Unsupported hosts return unavailable. The first sample needs another poll to compute utilization.

Validation: browser tests including report privacy; Go tests and vet; FFmpeg landscape, portrait and small-source conversion; isolated mobile-browser synthetic playback confirmed 854x480 and diagnostics without page overflow. Android build, unit tests and lint are release gates. No real provider, physical Shield or physical Safari validation is claimed.
