# Project Osiris Android — Phase 1 UI and playback build

This is an incremental source update based on the previously working Android Studio project.

## Included
- Dark charcoal / gray theme and the supplied metallic O app icon
- Top navigation: Live TV, Guide, Multiview, Categories, Settings
- Live TV channel list beneath the preview in portrait, beside the preview in landscape
- Dedicated Categories tab for enabling/disabling channel groups
- Fullscreen live playback with Back control and aspect-ratio FIT
- Existing M3U, Xtream, XMLTV, and Multiview code retained
- GitHub Actions APK workflow included

## Still to implement or improve
- Exact polished mockup styling and TV guide timeline
- Playback handoff without restarting on entering/exiting fullscreen or rotation
- Native 10-minute DVR buffer and full Windows parity
- Remote D-pad focus testing, background playback handling, and device-specific Multiview validation

## Build
Upload extracted contents to the Android repository (replace matching files). Open Actions > Build Project Osiris Android APK > Run workflow. Download the artifact and extract app-debug.apk.

The source is packaged and syntax sanity-checked, but has not been compiled against the Android SDK in this environment.
