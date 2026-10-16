# Guide controls

## Android TV / Shield

- Up/Down: move through channels or actions.
- Left/Right on the programme timeline: browse earlier/later guide windows. This changes the guide, not the playing channel.
- Short OK/Select on a channel or timeline: watch that channel live. The action occurs on release.
- Hold OK/Select (or press the remote Menu key): open actions for the focused channel/programme. Releasing a held key does not tune.
- Actions: Watch channel live, Add/Remove favorite, Programme details, Record/Schedule. Recording is omitted for broadcasts that have ended. Recording a future programme opens the existing scheduling form; it does not start the channel.
- Back from actions/details: close the dialog. Back from the full guide: reveal groups. Back from fullscreen: return to the guide's channel, or the existing wall when fullscreen was entered from a wall.
- Search stays available beside the guide menu. Text entry still requires opening its editor.

These controls work for standalone and linked profiles. Provider catch-up is not implied by selecting an earlier broadcast; Watch always means live.

## Browser / touch

- Click a channel: watch live. Click a programme: programme details.
- Right-click a channel/programme, hold it on touch, or focus it and press Shift+F10 / Context Menu: actions.
- Moving a held pointer cancels the hold so scrolling does not open actions.
- Escape or Close dismisses actions. Arrow keys retain guide navigation.

## Logos and fallback

Logos come from the provider's stream_icon field, not TMDB. Refresh channels to import them. Only HTTPS URLs without embedded credentials/query strings/fragments are accepted. Android does not follow image redirects. The browser applies its usual HTTPS image redirect handling. Failed, missing, or rejected logos leave names and channel playback intact. No server/admin token is sent to artwork hosts. TMDB programme posters and metadata are independent.

## Validation limits

The keyboard contract is covered by pure press-state tests and emulator flows. Real Shield remote models, TalkBack and unusually large system font scales still require device acceptance. Emulator success is not physical Shield certification.
