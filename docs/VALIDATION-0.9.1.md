# Version 0.9.1 validation

Browser fullscreen now contains the complete video frame rather than cropping it to the viewport. Portrait phones letterbox widescreen broadcasts; landscape keeps the original frame ratio. Fullscreen fills the dynamic viewport, accounts for safe-area insets on the return control and locks background scrolling. Expanded two-stream portrait walls stack vertically; six-stream walls use two columns and three rows. Single-stream fullscreen always uses one full-height grid cell.

Checked through the browser with an isolated synthetic 1280x720 stream:
- 390x844 portrait: fullscreen host 390x844, single row 844px, video object-fit contain, body overflow hidden; decoded video dimensions 1280x720.
- Rotated to 844x390: host and single grid row resized correctly, object-fit contain retained.
- Back to guide: expanded state cleared, body scrolling restored, one video tile retained.
- 11 existing JavaScript guide/player tests passed, including fullscreen wall return without deleting stream sessions.

Physical mobile browser, display notch and OS fullscreen behavior still require device acceptance. Android APK remains 0.9.0. GitHub container publication is gated by Go race/vet, image build and synthetic authentication/playback/recovery tests.
