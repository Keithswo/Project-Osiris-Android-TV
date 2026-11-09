Project Osiris Android — Windows-inspired UI refresh

This update modifies the existing Android source rather than replacing its IPTV functionality.
Changes: dark header with logo, horizontally scrolling navigation, highlighted active tab, dark player panel, cleaner guide and settings sections, and 2-column Multiview tiles on screens 650dp or wider (single column on phones).

GitHub installation:
1. Extract the ZIP.
2. Upload the extracted files to your existing Android repository, replacing matching files. Include the hidden .github folder.
3. Commit changes and run Actions > Build Project Osiris Android APK.
4. Download the Project-Osiris-Android-APK artifact and extract app-debug.apk.

Notes: This is a source update; the APK must be built through GitHub Actions. The existing Android player uses Media3, not the Windows FFmpeg pipeline. DVR and a full timeline-based EPG are not implemented. Test playback and remote navigation on actual devices.
