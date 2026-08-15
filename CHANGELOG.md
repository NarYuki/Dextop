# Changelog

## 1.3.5

### Improved

- Added Samsung DeX taskbar auto-hide control to Samsung desktop settings using the launcher-owned `taskbar_show_hide_on_hold_enabled` key.
- Included the Samsung DeX taskbar setting in the Samsung desktop settings backup and restore flow.
- Added runtime resolution of display-topology transaction IDs instead of relying on hard-coded Binder numbers, improving compatibility with OEM Android framework forks.
- Kept an otherwise usable VirtualDisplay session running when the optional topology API is unavailable or rejected, while recording the skipped operation in the session log.

### Fixed

- Separated the generic Android desktop taskbar setting from Samsung DeX. The Display setting now always controls only `desktop_windowing_force_hide_taskbar`, regardless of the device manufacturer.
- Fixed topology routing on newer/vendor Android builds where stale transaction IDs could invoke a protected display-mode operation and fail with `android.permission.RESTRICT_DISPLAY_MODES`.
- Fixed topology activation failures aborting Dextop mirroring instead of degrading gracefully to mirroring without topology routing.

## 1.3.4

### Improved

- Added a complete laptop keyboard theme system with Standard, Crimson, Cloud Pop, and user-created themes.
- Added full-screen localized theme management with scrollable previews, real keyboard-and-trackpad overlay previews, add/edit/delete actions, and per-theme ZIP import and export.
- Added customizable keyboard backgrounds, key, border, text, and trackpad colors, image opacity, background blur, and corner styling.
- Stabilized laptop-mode transitions and theme-preview lifecycle so preview surfaces are separated from the setup overlay and are cleaned up when leaving the editor.
- Extended the privacy-filtered Dextop session report with runtime display geometry, including the target display ID, surface and window dimensions, logical target size, density, rotation, and configuration orientation.
- Added input diagnostics for touch routing, coordinate conversion, active input mode, pointer count, and the result of each sampled `injectInputEvent` call.
- Added orientation lifecycle diagnostics covering requested rotation, applied display rotation locks, surface recreation, mirror reattachment, and rebuild completion or failure.
- Added display lifecycle snapshots when displays are added, removed, changed, resized, or disconnected so foldable and DeX transitions can be compared from one report.

### Fixed

- Fixed diagnostic reports omitting the runtime evidence needed to distinguish display-geometry mismatches from rejected input injection on vendor Android builds.

## 1.3.3

### Improved

- Shift now updates the keyboard legends to their correct symbols (`!@#$%^&*()_+{}|:"<>?`).
- Ctrl, Shift, and Alt modifiers latch on press, allowing modifier chords and multi-touch shortcuts such as Ctrl+C and Ctrl+V.
- Added smoother laptop-mode and keyboard-settings transitions with fade, slide, and scale animations.
- The desktop surface is now black-backed during laptop-mode layout changes so the Android screen cannot show through transparent resize areas.
- Stabilized hinge-angle detection with filtering and debounce to prevent posture-triggered flicker and repeated virtual-display resizing.

### Fixed

- Fixed keyboard-settings navigation returning abruptly without the laptop transition animation.
- Fixed unstable laptop-mode toggling caused by noisy real-device hinge sensor readings near posture thresholds.

## 1.3.2

### Improved

- Laptop mode now registers its on-screen keyboard as an external physical keyboard while the mode is active.
- Gboard and other IMEs now follow Android's physical-keyboard preference instead of being forcibly disabled or hidden.
- The temporary keyboard device is removed when laptop mode, Dextop, or a paused session ends.

### Fixed

- Fixed laptop-mode key input being treated only as synthetic key events, which caused virtual keyboards to appear unexpectedly in text fields.

## 1.3.1

### New

- Added persistent display arrangements so monitor positions are restored when the same external-display configuration reconnects.
- Added laptop mode for foldable devices, with a US keyboard and trackpad in the lower screen area.
- Added laptop keyboard themes, function keys, shortcut labels, and direct access to the Dextop overlay from the trackpad.
- Added optional hinge-angle detection for automatically entering and leaving laptop mode.

### Improved

- Kept manual laptop mode available from the overlay on supported foldable devices even when automatic detection is disabled.
- Updated the README and wiki with multi-display, high-refresh-rate, taskbar, and foldable laptop-mode features.
- Marked foldable main/cover-display switching as incomplete while device-specific panel transitions continue to be improved.

### Fixed

- Fixed hinge-angle detection silently enabling the laptop mode preference.
- Fixed manual laptop mode toggles overwriting the automatic detection preference.

## 1.3.0

### Multi-display support has arrived

- Added an intuitive display topology editor for arranging Dextop and external displays to match their physical layout.
- Added multi-display pointer routing and automatic topology activation when Dextop starts or resumes.
- Added live display hot-plug and topology monitoring. The editor reloads when displays are reconfigured and closes automatically when arrangement is no longer available.
- Added localized display identification, reset, cancel, and apply controls with a responsive desktop and mobile layout.

### New

- Added an option to keep supported built-in displays at 120 Hz while Dextop is running with an external monitor.
- Added automatic 120 Hz reapplication after an external display is connected, reconfigured, or disconnected.
- Added stable display settings for topology participation and automatic desktop taskbar hiding.

### Improved

- Reorganized display settings into Display and Convenience sections using the existing settings design.
- Made display topology available by default and automatically refresh it in the background at every Dextop start and resume.
- Improved the topology canvas so it scales with the available dialog size on phones, foldables, and large desktop windows.
- Removed Dextop's custom cursor and cursor rendering logic for physical mice while preserving touch-panel cursor mode and monitor routing.
- Improved localization for display topology, Samsung desktop settings, warnings, and feature descriptions.

### Fixed

- Fixed stale display topology state after external displays are connected, removed, folded, unfolded, or reconfigured.
- Fixed the display arrangement action remaining available when fewer than two configurable displays exist.
- Fixed built-in display refresh-rate settings being restored when 120 Hz should remain active after external-display disconnection.

## 1.1.2

### Improved

- Improved the three-finger edge swipe sensitivity by triggering from the leading finger instead of the centroid and lowering the required swipe distance, especially in portrait when swiping down from the top edge.

## 1.1.1

### Improved

- Device reports now include a privacy-filtered log from only the most recent Dextop session.
- Reduced noisy Android and Flutter debug logging while retaining device implementation, capability, backend, routing, failure, and restoration events.

### Fixed

- Fixed the device-report email recipient, subject, or body being omitted by some email applications.
- Fixed the updated gesture guide not appearing after upgrading from a version earlier than 1.1.0.

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
