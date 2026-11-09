# Project Osiris Android — v2.3.7 Android Preview

Android Studio source for **Android TV / Google TV / Fire TV** and **Android phones/tablets**.

## Build in Android Studio

1. Open this folder (the one containing `settings.gradle`) in Android Studio.
2. Allow Gradle to sync and install Android SDK 35 if prompted. Use JDK 17.
3. Select **Build → Build APK(s)**. Find the result at `app/build/outputs/apk/debug/app-debug.apk`.
4. Install on a device with Android 6.0+ (API 23+) by USB debugging or by sideloading the APK.

## GitHub Actions

Upload the **contents** of this folder to a GitHub repository. The workflow is `.github/workflows/build-android.yml`. Open **Actions → Build Project Osiris Android APK → Run workflow**. Download the artifact after the job succeeds. It contains `app-debug.apk`.

This is a **debug-signed APK for sideload testing**, not a Play Store release. Android Studio can produce a separately signed release build.

## Implemented

- Native Android Java application using Media3 ExoPlayer for HLS/DASH/other Android-supported streams
- Project Osiris logo as launcher and TV banner
- M3U URL parsing and Xtream Codes live-channel authentication / category loading
- Live TV channel selection and playback
- Android TV and touchscreen navigation (focusable buttons, native selection dialogs)
- Multiview with 2, 3 or 4 separate video players, Select Channel, Watch Solo
- Active Categories visibility checkboxes, saved locally
- Simple channel guide with optional XMLTV *currently airing* program titles
- Settings validation and Save Settings button

## Important differences from Windows v2.3.7

- **10-minute DVR/rewind is NOT implemented**. No background rolling recorder is started; the Windows DVR checkbox is intentionally replaced by a clear notice.
- Android playback uses **Media3**, not the Windows FFmpeg transcoder. Some provider codecs/transport streams that play on Windows may not play on Android.
- Multiview depends on device decoder limits, network bandwidth, and provider simultaneous-stream allowance; four streams may not work on low-end Fire TV hardware.
- XMLTV support currently shows the **current program title** only, not the full Windows horizontal time-grid guide. XMLTV gzip and Xtream automatic EPG discovery are not included yet.
- No import of existing Windows settings; configure the provider separately on Android.
- Provider credentials are stored in app-private Android SharedPreferences (not encrypted). Use HTTPS where supported; do not use on untrusted devices. The app is for streams you have permission to access.
- This source has **not been compiled or device-tested** in this environment. Run GitHub Actions/Android Studio and share any errors for fixes.

## Troubleshooting

- For HTTP 403, verify provider credentials, subscription status, allowed devices and connection limits.
- For black screens, try a single channel first; test HLS (`.m3u8`) if available from your provider.
- The Android app is separate from the Windows Electron code; keep them in separate GitHub repositories or place this folder under an `android/` directory and adjust workflow paths accordingly.
