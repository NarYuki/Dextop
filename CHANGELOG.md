# Changelog

## 1.1.0

### New

- Added a VirtualDisplay-based display mirroring backend.
- Added a display setting for selecting Automatic, VirtualDisplay, WindowManager, or SurfaceControl mirroring.
- Added GitHub Release update checks at app startup and from App information.
- Added update indicators to the Settings navigation icon and App information entry.
- Added device-aware physical mouse and keyboard routing controls for supported external-display configurations.
- Added external-display hot-plug detection and automatic input-route restoration when a display is disconnected.
- Added localized device compatibility reports with automatically collected device details and email submission.
- Added Firebase Analytics screen and desktop-start event collection when Firebase is configured.

### Improved

- VirtualDisplay is now the default mirroring method and the first method attempted in Automatic compatibility mode.
- Improved physical mouse and touchpad cursor switching. The Dextop cursor is hidden when physical mouse movement is detected and restored when the touchscreen is tapped.
- Improved multi-touch forwarding by preserving pointer IDs, action indices, timing, history, pressure, and gesture data for smoother scrolling and reliable pinch zoom.
- App version labels now use the version installed in the APK instead of a fixed value.
- Improved update-check status reporting by distinguishing unchecked, checking, up-to-date, update available, and retrieval failure states.
- Added detailed update-check events to the Flutter debug log.
- Removed the default Flutter ripple and highlight effects from the orientation and theme segmented controls.
- Expanded the customizable overlay control row from three to five columns when input-routing controls are available.
- Updated the gesture demonstration to introduce the mouse and keyboard routing controls.
- Improved the overlay gesture flow and made multi-touch the production default.

### Fixed

- Fixed transparent or invalid app entries being captured when saving the current app arrangement from the overlay.
- Fixed rejected input injection events being incorrectly treated as successful.
- Fixed stale touch streams that could leave one-finger input unresponsive after closing the overlay.
- Fixed portrait overlay gestures so the three-finger swipe opens the panel from the top edge.
