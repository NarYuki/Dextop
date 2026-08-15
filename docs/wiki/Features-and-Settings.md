# Features and settings

## Home

- **Session status:** shows readiness, startup, active session, errors, and recoverable sessions.
- **Start / Stop:** creates or stops the desktop session.
- **App launcher:** launches an installed app on the desktop display.
- **Workspace:** opens saved application arrangements.
- **Resolution:** selects the device or custom resolution and DPI.
- **Landscape / Portrait:** sets virtual-display orientation.

## Settings categories

### Theme

- **System:** follows Android light/dark appearance.
- **Light:** always uses the light theme.
- **Dark:** always uses the dark theme.

### Display

- **Secure display:** marks the virtual display as secure and allows protected content. When enabled, Android may block screenshots or screen recording of that display.
- **Display arrangement:** positions Dextop and external displays, saves the arrangement, and enables cross-display pointer routing.
- **Mirroring method:** selects Automatic, VirtualDisplay, WindowManager, or SurfaceControl according to device compatibility.
- **Automatic taskbar hiding:** hides the desktop taskbar when supported by the active desktop environment.
- **Built-in display 120 Hz:** reapplies 120 Hz when an external monitor would otherwise limit a supported phone display to 60 Hz.
- **Automatically detect laptop mode:** uses hinge-angle events to enable laptop mode automatically. This setting does not disable manual laptop mode.
- **Foldable laptop mode:** can be opened manually from the Dextop overlay on a supported foldable and provides a US keyboard and trackpad on the lower display area.
- **Foldable main/cover-display switching:** partial support. Dynamic resolution and panel transitions remain incomplete on some devices and firmware versions.

### Apps and workspace

- Search and select launchable applications.
- Create, rename, edit, duplicate, reorder, and delete workspaces.
- Select two-pane, three-pane, four-pane, wide/narrow, and vertical layouts.
- Assign each application to a layout position.
- Import and export workspace JSON.

### Input and gestures

- Choose cursor or direct-touch mode from the desktop overlay.
- Configure actions for three-finger tap, two-finger tap, and long press.
- Use the three-finger edge swipe to open the operation overlay.
- Experimental multi-touch can be enabled under App information.

### Status and diagnostics

- Performance overlay switch
- Current FPS and display refresh rate
- Application and available memory
- Battery percentage and estimated power usage
- Current input mode
- Compatibility checks for the selected Stellar or Shizuku service, secure settings, accessibility, overlay, physical input, virtual display, launcher, Quick Settings, and foldable layout

### Device and permissions

- Detected manufacturer, model, and Android version
- Selected desktop environment
- Keep screen awake during a Dextop session
- Link to Android accessibility settings

### App information

- Project source link
- Open-source licenses
- Operation log and device diagnostics
- Copy, share, refresh, or clear the diagnostic report
- Experimental multi-touch switch

Settings that require virtual-display recreation cannot change an already active session. Stop and start Dextop after changing them.
