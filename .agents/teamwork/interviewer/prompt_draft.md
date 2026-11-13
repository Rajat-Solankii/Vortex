Fix the mini player embedding (Picture-in-Picture) and add a cast option so users can watch movies on their TV in production.

Working directory: E:\Vortex-main
Integrity mode: development

## Requirements

### R1. Picture-in-Picture (PiP) Clean Video Viewport
When Android enters Picture-in-Picture mode (`isInPictureInPictureMode` / `onPictureInPictureModeChanged`):
- SHOW: Only the video viewport (actual video player surface) expanding to fill the 16:9 PiP container on a black background.
- HIDE: Top player toolbar, back arrow, cast icon, fullscreen icon, reload icon, title/meta text, server selection, Vortex Shield, episode selector, and all other controls and overlays.
- When returning from PiP back to normal app, restore all normal player controls without resetting playback state or server/episode selection.
- Set source aspect ratio to 16:9 (`Rational(16, 9)`) and set proper source rect hint if supported.

### R2. Cast Option for TV
- Add a clearly identifiable Cast icon/option in PlayerScreen top toolbar.
- On click, launch Cast / Screen Mirroring intent (`android.provider.Settings.ACTION_CAST_SETTINGS` or fallback to wireless display / display settings or Google Cast integration if available) with graceful fallback and informative toast so users can cast/mirror movies to their TV.

## Verification Resources
- Attached Android emulator `Pixel_10a` (`emulator-5554`) is running and available for app deployment and testing.
- Gradle build command: `assembleDebug` builds universal APK.

## Acceptance Criteria

### PiP Viewport Display
- [ ] In Picture-in-Picture mode, only the video viewport is visible, filling the 16:9 window on a black background with no controls, headers, overlays, or metadata visible.
- [ ] Exiting PiP back to normal app restores all player controls, header, and overlays without resetting current video playback position, server, or episode selection.
- [ ] PictureInPictureParams configures 16:9 aspect ratio (`Rational(16, 9)`) and valid source rect hint.

### Cast Action
- [ ] PlayerScreen top toolbar displays a Cast icon button.
- [ ] Clicking the Cast icon launches `android.provider.Settings.ACTION_CAST_SETTINGS`, falling back to wireless display / display settings or displaying an informative toast if settings cannot be opened.

### Build and Device Verification
- [ ] Project compiles successfully via Gradle `assembleDebug`.
- [ ] Application installs and runs on emulator `emulator-5554` without crashing when toggling PiP and clicking the Cast button.