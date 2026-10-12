# Android 0.4.4 — icon navigation rail

Replaced narrow text controls with five local vector icons: Search, Favorites, Video wall, Refresh channels, and Connection settings. Every target has a 48 dp button inside a fixed 56 dp container. Focus adds a light rounded highlight and a non-focusable single-line label beside the rail. Accessibility content descriptions name each action. No text labels or query values are laid out inside the narrow icon column.

Search activation opens its editor directly, with Search and Cancel actions. Merely focusing the search icon does not open a keyboard. Other actions retain their existing behavior.

Validation: assembleDebug, all eight existing unit tests, and lintDebug passed. No physical Shield/emulator visual or focus verification was performed. Check D-pad traversal, labels, Search/Cancel, and action activation on the device. Server/dashboard unchanged.

Artifact: artifacts/Signal-TV-0.4.4-debug.apk. Checksum: artifacts/SHA256SUMS-0.4.4.txt.
