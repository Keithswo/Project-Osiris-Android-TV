# Project Osiris — Chrome UI update

This is an updated Android source project based on the uploaded working GitHub repository.

## UI changes
- O-only logo in a black/charcoal header (existing `osiris_icon.png` preserved)
- Five equally spaced, compact navigation tabs: Live TV, Guide, Multiview, Categories, Settings
- Metallic gradient styling and generated grayscale background asset
- Smaller channel rows with channel numbers, selection outline, and favorite stars
- Live player and program title presented first on phones; side-by-side channel list/player on wide landscape devices
- Category selector and search button above the channel list

## Preserved
M3U, Xtream, XMLTV, Media3 playback, fullscreen, multiview, category visibility, saved settings, and GitHub Actions workflow are retained from the supplied source.

## Build
Upload the extracted project contents to the existing GitHub repository, then run `Build Project Osiris Android APK` in Actions. The resulting artifact contains `app-debug.apk`.

## Limitations
This is source-level UI work and has not been compiled on an Android SDK in this environment. It is not a pixel-perfect conversion of the concept art. Native Media3 controls remain in use, and EPG details appear only where the source XMLTV provides them. Verify on a phone and TV before distribution.
