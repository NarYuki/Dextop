# Diagnostics and recovery

## Diagnostic report

Open **Settings → App information → Operation log and device diagnostics**.

The report includes:

- App version and process information
- Manufacturer, brand, model, product, board, hardware, build, fingerprint, ABI, Android version, SDK, and security patch
- Selected Dextop environment and ordered backend strategies
- Read-only capability probe results
- CPU, memory, heap, and storage information
- Display sizes, densities, modes, refresh rates, and rotation
- Battery and input-device information
- Android system features and relevant global settings
- Rolling Dextop operation log
- Current-process Android logcat entries

The report can be copied or shared. Remove identifiers you do not want public before attaching it to an issue.

## Recoverable sessions

If Dextop or Android stops during an open transaction, the Home screen may show a recovery warning. Use the recovery action to restore temporary settings before starting another session.

## Safe restoration

Dextop records settings it owns before changing them. Stop and recovery paths restore those original values. It does not intentionally delete an overlay configured by another application.

If Android remains in an abnormal state and in-app recovery is unavailable, follow [Restore Android with ADB](ADB-System-Recovery). The page includes a minimal emergency sequence and careful recovery of display, navigation, rotation, accessibility, and desktop-mode settings.

## Reporting a bug

Use the repository bug form and include exact reproduction steps, device/build information, expected and actual behavior, and a sanitized diagnostic report.
