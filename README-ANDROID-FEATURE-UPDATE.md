# Project Osiris Android — navigation, audio and EPG update

## Changes
- Categories tab now opens a category's channel list, rather than modifying category visibility.
- Settings category checkboxes are staged until Save Settings; Check All, Uncheck All and Exit (Discard) added. Category list scrolls with the full settings dialog.
- Multiview allows one main audio stream at a time; selection shows a yellow tile border for two seconds. Audio volume is set independently for each playing tile.
- Guide prompts for category selection and shows a horizontally swipeable timeline from the current half-hour through the next two hours. Missing XMLTV data is labelled "No EPG available". XMLTV start/stop timestamps are parsed with time-zone offsets.
- Fullscreen return button hides after two seconds and reappears when the video is touched.
- Landscape header uses a smaller logo and navigation height.
- Android Back restores category browsing and previous top-level navigation where possible.

## Build
Push the complete project to GitHub, then run the existing **Build Project Osiris Android APK** workflow. Download the artifact under the successful run's Summary tab.

## Notes
This is source code, not a precompiled APK. The project has not been compiled or tested on a device in this environment. Your provider must supply XMLTV EPG information for program titles to appear. Android does not implement the Windows FFmpeg DVR buffer.
