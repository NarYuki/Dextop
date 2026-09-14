# Android Auto: Parking and Driving

Current companion release: **2.0.0**. It is designed for **Dextop 1.7.0**.

Dextop connects a dedicated virtual display to Android Auto through two separately installed companion apps:

- **Dextop・Parking** (`moe.n4tsu.dextop.cardex`) is a parked full-screen activity with unrestricted head-unit touch gestures while the vehicle is parked.
- **Dextop・Driving** (`moe.n4tsu.dextop.cardex.driving`) uses an Android for Cars App Library navigation surface. Android Auto supplies only its allowed head-unit input events; use the automatically opened phone controller for trackpad gestures and keyboard input.

Both modes size the same Dextop session for the current host surface. Surface-size changes are handled by resizing the existing display instead of deliberately replacing the desktop session.

## Requirements

- Android 15 or later for Parking, or Android 13 or later for Driving
- A head unit or Desktop Head Unit (DHU) that exposes the selected app category
- Dextop and one or both supported companion packages
- Dextop 1.5.0 or later, which includes built-in privileged access; an existing root, Stellar, Shizuku, or other compatible privileged service is optional
- A parked vehicle when using **Dextop・Parking**

Parking declares `minSdk 35` and requires Android 15 or later. Driving declares `minSdk 33` and supports Android 13 or later because it uses the Android for Cars App Library instead of the Android 15 parked-app launcher. Android Auto still decides whether either app appears in its launcher based on app category, host version, installation method, and allowlisting.

## Install and connect

1. Install Dextop from [Google Play](https://play.google.com/store/apps/details?id=moe.n4tsu.gpdextop), or install the signed Dextop APK from GitHub Releases.
2. Install **Dextop・Parking**, **Dextop・Driving**, or both. Dextop verifies each package and signing certificate through its protected relay.
3. Complete Dextop's initial setup on the phone. Dextop uses its built-in access on version 1.5.0 and later; if root or a compatible privileged service is already available, Dextop detects and uses it automatically.
4. Connect the phone to Android Auto. Make sure the vehicle is parked before opening Parking.
5. Open **Dextop・Parking** or **Dextop・Driving** from the Android Auto launcher.
6. Confirm that **Dextop installed** and **Secure relay verified** are shown, then select **Start**.

Dextop creates the Auto desktop at the size supplied by the head unit. Driving opens the phone-side trackpad and keyboard controller after the relay starts. Starting an Auto session does not start a second independent phone-side Dextop session.

Dextop itself is available from [Google Play](https://play.google.com/store/apps/details?id=moe.n4tsu.gpdextop). Car Companion packages and signed APK releases are provided under [GitHub Releases](https://github.com/NarYuki/Dextop/releases). Development builds can be downloaded from [GitHub Actions](https://github.com/NarYuki/Dextop/actions), but Nightly artifacts are beta builds and their Dextop and companion packages must still be used together.

## Display modes

Open **Dextop → Settings → Auto** on the phone to choose the Auto display behavior.

### Compatibility mode

This is the default and recommended mode. Dextop uses the Android overlay-display path with the widest device compatibility. Android may show the Auto-owned virtual-display overlay on the phone while the car session is active.

### Experimental hidden display

Enable **Experimental: hide the Auto display on the phone** to send an app-owned virtual display directly to the companion surface without showing the overlay window on the phone.

This path depends on hidden Android and OEM display APIs. If the car screen stays black, HOME cannot launch, or the connection becomes unavailable, disable the option and return to compatibility mode.

### Phone-mirror orientation matching

**Match the phone mirror orientation to Auto** applies only when the phone-side Dextop display is deliberately mirrored to Auto. It adjusts portrait or landscape orientation from the head unit's aspect ratio. It does not rotate the phone for an Auto-only session.

## Touch and gestures

### Parking

Parking uses direct head-unit touch input while the vehicle is parked.

- **Tap:** activates the item at that desktop position.
- **Drag:** drags or scrolls according to the application receiving the input.
- **Swipe right from the left edge:** opens the Auto control panel. Start inside the left-edge activation area and continue toward the center of the screen.

The Auto edge gesture is separate from Dextop's phone-side three-finger gesture. Three-finger phone gestures are not required to open the Auto controls.

### Driving

Driving accepts the cooked click events supplied by Android Auto. Gestures that the host does not forward, such as arbitrary drag, swipe, and continuous scrolling, are performed from the phone controller instead. The phone controller provides a software cursor, a normal trackpad area, and the system keyboard.

## Auto control panel

Swipe right from the left edge while the Auto desktop is visible. The panel contains only controls for the Auto session:

- **Close:** hides the panel and returns to the desktop.
- **Workspace:** expands the saved workspace list.
- **Reconnect video:** reconnects the current head-unit surface without creating another desktop session. Use it if the desktop remains active but the video freezes or disappears.
- **Stop:** ends the Auto session, removes its virtual display, and returns Dextop Car Companion to its start screen.

The panel is scrollable when the head unit does not have enough vertical space to display every action.

## Workspaces

The Auto workspace panel uses the same workspace data as Dextop on the phone.

- Select **Workspace** to expand the list.
- Saved entries show their names and application icons.
- Select a workspace to launch its applications and positions on the Auto desktop.
- Select **Add current app arrangement** to save the current Auto desktop arrangement.

Create, rename, reorder, import, or export more complex workspace definitions from the main Dextop application.

## Ending and recovering a session

Use **Stop** in the Auto control panel before disconnecting when possible. Dextop Car Companion releases its surface and Dextop removes the Auto-owned display.

If Android Auto, the companion, or the relay disconnects unexpectedly, Dextop records the interruption. Open Dextop on the phone and use the displayed Android recovery action if temporary display or System UI state still needs restoration.

## Current limitations

- Parking requires the parked state; Driving uses the driving-compatible Car App host flow.
- Android Auto controls whether each companion is visible and which head-unit input events are delivered.
- The installed companion package and signing certificate must be supported by the installed Dextop version.
- A phone-side Dextop session cannot currently be started while the independently owned Auto session is active. Stop Auto first.
- The hidden-display option is experimental; compatibility mode remains the default.
- Window behavior still depends on Samsung DeX, Android desktop mode, and each application's secondary-display and resize support.

## Troubleshooting

### Dextop connection is unavailable

1. Confirm Dextop is installed.
2. Confirm the companion version and Play/GitHub signing identity are supported by the installed Dextop version.
3. Open Dextop once on the phone and complete the in-app access setup if requested. If you already use root, Stellar, Shizuku, or another compatible privileged service, make sure that environment is active so Dextop can detect it.
4. Confirm that Dextop reports **Dextop is ready**.
5. Reconnect Android Auto and reopen the companion.

### The car screen is black

1. Open **Settings → Auto** in Dextop.
2. Disable the experimental hidden-display option.
3. Start the Auto session again in compatibility mode.
4. If the desktop is running but its video is missing, open the left-edge panel and select **Reconnect video**.

### The phone still shows an Auto overlay after stopping

Wait for Dextop's stopping state to finish. If the overlay remains, open Dextop and run its Android recovery action. Include the operation log in a bug report if the overlay returns after another session.

## DHU testing

Development profiles for standard, wide, tall, and Subaru-style parked displays are stored in [`docs/android-auto/dhu`](https://github.com/NarYuki/Dextop/tree/main/docs/android-auto/dhu). See its README for startup commands, parked-state simulation, supported DHU video modes, and profile details.
