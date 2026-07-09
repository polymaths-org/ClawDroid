# Changelog

## v0.1.0 Stable Candidate

- Added continuation handoff for `Continue in new chat`, including hidden previous-session context, last response, recent transcript, compacted summary, and recent tool activity.
- Fixed notification content taps so they open the app without automatically opening the overlay.
- Improved overlay completion state so completed tasks are visible and not mistaken for running work.
- Reduced overlay animation glitches by replacing height expansion with transform/fade motion and avoiding animated height changes on completion.
- Fixed overlay keyboard behavior by removing duplicate IME padding from the floating dock.
- Added clearer current-task and latest-response sections to the overlay.
- Added local release signing support through ignored `signing/release-signing.properties`.
- Updated public repository links to `polymaths-org/ClawDroid`.

## Release Notes

This is intended as the first stable APK candidate for sideload testing before publishing a GitHub Release.
