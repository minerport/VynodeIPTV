# Android 0.16.6 — Shield toggle crash

## Change

Pin Material 3 to 1.3.2 while retaining the existing Compose UI and Foundation versions. The physical Shield crash trace identified `ChildSemanticsNode.applySemantics` calling `visitAncestors` on an unattached node. Material 3 1.4.0 introduced the affected internal semantics path used by toggle surfaces and icon buttons; the compatibility version does not contain that class.

Android versionCode is 36. No server or saved-account format changes.

## Validation (2026-10-17)

- Debug APK assembly, JVM unit tests, and Android lint passed.
- Inspected the final APK to confirm the affected ChildSemanticsNode class is absent.
- Installed the signed APK in place on the user's NVIDIA Shield (Android API 30), preserving app data.
- User confirmed group visibility toggles and Favorite changes work after installation in server-linked mode.

This is targeted physical-device confirmation, not a comprehensive regression test of every Android device or standalone playback path. Keep the compatibility pin until a replacement is verified on Shield hardware.

## Distribution

Android-only release `android-v0.16.6`; the server/container remains 0.16.5.
