# Version 0.9.2 validation

Fullscreen selected tiles now have no border, outline, inset shadow or selection pseudo-element. Landscape single-stream fullscreen uses object-fit cover to fill the display without distorting the aspect ratio (cropping is expected where display and video ratios differ). Portrait retains object-fit contain. The viewport declares viewport-fit=cover so supported mobile browsers can use device cutout regions; ordinary page content and the return control retain safe-area padding.

Browser fixture checks with a synthetic stream:
- 844x390 landscape host: 844x390, border 0px, outline 0px, box-shadow none, selection pseudo-element display none, video object-fit cover.
- 390x844 portrait: video object-fit contain.
- Existing JavaScript suite passed. Container publication is gated by Go race/vet, build and synthetic authentication/playback/recovery checks.

Physical phone cutout and browser fullscreen acceptance remain pending. Android remains 0.9.0.
