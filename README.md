# Project Osiris Android TV — v2.3.9 initial port

This is a **first Android TV source build**, not a fully feature-complete Android release.

## What's preserved
The original v2.3.9 `index.html`, `style.css`, `app.js` and original image assets are copied from the supplied Windows GitHub ZIP. This preserves the UI structure, branding, categories, guide, settings, favorites and recent-channel interface.

## What works in source
- Android TV launcher, landscape UI and DPAD-capable WebView.
- M3U URL retrieval and Android file-picker import via Java native bridge.
- Xtream Codes live-category and channel loading and XMLTV fetching via Java native bridge.
- Local UI preferences, guide and channel browsing.
- Direct WebView video playback for formats supported by the device's WebView.

## Known gaps / must fix before calling it complete
- **Not compiled or tested on an Android TV device yet.**
- WebView video decoding depends on Android TV device and stream formats; MPEG-TS and HLS are not guaranteed.
- Windows FFmpeg DVR / 10-minute timeshift is **not ported**. DVR reports unavailable.
- Multiview tiles are carried over but simultaneous playback is not validated.
- Provider profiles are session-only; the Java bridge does not yet implement encrypted credential persistence.
- Remote focus behavior needs testing and refinement.
- GitHub debug APK is for testing, not a signed Play Store release.

## GitHub build
Create a new GitHub repo, upload **the contents** of this ZIP (including `.github`), open Actions, select **Build Project Osiris Android TV APK**, Run workflow, then download `Project-Osiris-Android-TV-APK` from Artifacts.

This Android repository does not modify the Windows application.
