# Android Auto and Dextop Car Companion

Dextop Car Companion connects a dedicated Dextop virtual display to a supported parked Android Auto screen. The car session owns its own display, uses the head unit's available surface size, and forwards touch input from the head unit to the desktop.

## Requirements

- Android 15 or later on the connected phone
- A head unit or Desktop Head Unit (DHU) that exposes parked apps
- Dextop and **Dextop Car Companion** from the same Dextop release
- Stellar or Shizuku running, with Dextop permission granted
- The vehicle in a parked state

Android Auto decides whether a sideloaded parked app appears in its launcher. Installing the APK does not guarantee that every production head unit or Android Auto release will expose it. If Dextop Car Companion is missing from the launcher, verify the Android Auto version, parked-app support, installation method, and host allowlisting first.

## Install and connect

1. Install the Dextop APK.
2. Install the matching **Dextop Car Companion** APK from the same release. The companion verifies Dextop through a signature-protected relay, so builds signed by different keys cannot connect.
3. Start Stellar or Shizuku and grant its permission to Dextop.
4. Complete Dextop's initial setup on the phone.
5. Connect the phone to Android Auto and make sure the vehicle is parked.
6. Open **Dextop Car Companion** from the Android Auto launcher.
7. Confirm that **Dextop installed** and **Secure relay verified** are shown, then select **Start**.

Dextop creates the Auto desktop at the size supplied by the head unit. Starting an Auto session does not start a second phone-side Dextop session.

Stable releases provide both APKs together under [GitHub Releases](https://github.com/NarYuki/Dextop/releases). Development builds can be downloaded from [GitHub Actions](https://github.com/NarYuki/Dextop/actions), but Nightly artifacts are beta builds and the Dextop and companion packages must still be a matching pair.

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

The Android Auto desktop uses direct head-unit touch input.

- **Tap:** activates the item at that desktop position.
- **Drag:** drags or scrolls according to the application receiving the input.
- **Swipe right from the left edge:** opens the Auto control panel. Start inside the left-edge activation area and continue toward the center of the screen.

The Auto edge gesture is separate from Dextop's phone-side three-finger gesture. Three-finger phone gestures are not required to open the Auto controls.

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

- Android Auto support is for parked use.
- Android Auto controls whether the companion is visible and accessible.
- Dextop and Dextop Car Companion must come from the same signed release.
- A phone-side Dextop session cannot currently be started while the independently owned Auto session is active. Stop Auto first.
- The hidden-display option is experimental; compatibility mode remains the default.
- Window behavior still depends on Samsung DeX, Android desktop mode, and each application's secondary-display and resize support.

## Troubleshooting

### Dextop connection is unavailable

1. Confirm Dextop is installed.
2. Confirm Dextop and Dextop Car Companion came from the same release.
3. Start Stellar or Shizuku again and grant Dextop permission.
4. Open Dextop once on the phone and confirm that it reports **Dextop is ready**.
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
