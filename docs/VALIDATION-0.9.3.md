# Version 0.9.3 validation

Fullscreen requests now use navigationUI=hide on supported standard APIs, prefixed WebKit container fullscreen when present, and video.webkitEnterFullscreen for Safari devices that only expose native video fullscreen. Native video exit restores the wall/guide while retaining stream sessions. Unsupported, rejected or not-ready fullscreen reports a user-visible message rather than pretending a CSS-expanded player is native fullscreen.

Validation: 17 JavaScript tests passed, covering standard hidden-navigation requests, Safari video fallback, container rejection, not-ready media and failed-fullscreen cleanup without deleting playback. Existing wall return tests remain included. Container publication is gated by Go race/vet, image build and synthetic authenticated media/recovery tests.

The Safari API paths were tested with browser API doubles; a physical Safari device is not connected, so native Safari address-bar hiding, native-player controls and return behavior still require device acceptance. Safari's native player controls its own aspect ratio and controls; CSS rules for the web player cannot override its native UI. Android remains 0.9.0.

API references: [Apple video fullscreen](https://developer.apple.com/documentation/webkitjs/htmlvideoelement/1630493-webkitdisplayingfullscreen), [Fullscreen request options](https://developer.mozilla.org/en-US/docs/Web/API/Element/requestFullscreen).
