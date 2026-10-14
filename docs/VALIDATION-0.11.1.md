# Server 0.11.1 validation

Android remains at 0.11.0; this release changes server/browser recording playback only.

Local validation covers Go tests and vet, 23 browser behavior tests, and real FFmpeg conversion of a multi-segment MPEG-2/AC3 fixture to H.264/AAC. The test decodes the entire converted recording, checks increasing packet timestamps and audio/video duration alignment, verifies the original playlist remains intact, and checks completed cache reuse without FFmpeg. API tests cover owner isolation, duplicate preparation, cancellation and deletion protection. Browser tests cover closing while preparation starts, saved resume preservation and pausing wall decoders without deleting tiles or leases.

The container workflow runs Go race tests with FFmpeg installed, builds the Linux image, and exercises authenticated live recovery, recording capture and browser-copy preparation/serving in an isolated synthetic container before publication.

These tests do not verify the user's affected recording, physical Unraid hardware, or visual playback on Safari/Shield. Actual provider acceptance remains necessary. Conversion preserves the original and cannot reconstruct missing footage. CPU throughput, available recording storage and browser HLS support remain constraints.
