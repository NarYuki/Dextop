# Restore Android with ADB

Use this page if Android navigation, Circle to Search, rotation, or display behavior remains abnormal after Dextop has stopped and the in-app recovery action cannot fix it.

## 1. Try Dextop recovery first

Open Dextop. If the Home screen shows a recovery card, run **Restore Android**. This is the safest method because Dextop's session journal remembers the original values and restores them instead of assuming defaults.

If the app cannot be opened or recovery fails, continue with ADB.

## 2. Connect and select the device

```sh
adb devices -l
```

If more than one device is listed, add `-s SERIAL` to every command below:

```sh
adb -s SERIAL shell getprop ro.product.model
```

The examples below use `adb shell`. Replace it with `adb -s SERIAL shell` when required.

## 3. Record the current state

Do this before changing anything:

```sh
adb shell settings get global overlay_display_devices
adb shell settings get global enable_freeform_support
adb shell settings get global force_resizable_activities
adb shell settings get global force_desktop_mode_on_external_displays
adb shell settings get secure enabled_accessibility_services
adb shell settings get secure accessibility_enabled
adb shell dumpsys display > dextop-display-before.txt
```

Keep this output with the Dextop diagnostic report. A value of `null` means that the setting is not currently present.

## 4. Stop Dextop and release temporary UI state

```sh
adb shell am force-stop moe.n4tsu.dextop
adb shell cmd statusbar send-disable-flag none
adb shell wm user-rotation free
```

Force-stopping Dextop releases its binder token. The next two commands clear residual System UI disable flags and release the default display's forced rotation.

## 5. Remove a residual Dextop virtual display

Check the value first:

```sh
adb shell settings get global overlay_display_devices
```

If a Dextop virtual-display specification remains and you did not configure an overlay display yourself, remove it:

```sh
adb shell settings delete global overlay_display_devices
```

Do not run this command when the displayed value belongs to another app or to your own developer configuration.

## 6. Remove only Dextop from accessibility services

First open Android's accessibility settings and turn Dextop off manually:

```sh
adb shell am start -a android.settings.ACCESSIBILITY_SETTINGS
```

If the entry cannot be disabled from the UI, inspect the enabled service list:

```sh
adb shell settings get secure enabled_accessibility_services
```

Remove only the component belonging to `moe.n4tsu.dextop` from the colon-separated value, preserve every other service, and write the resulting complete value back:

```sh
adb shell settings put secure enabled_accessibility_services 'PRESERVED_COMPONENTS_WITHOUT_DEXTOP'
```

If the resulting list is empty, also run:

```sh
adb shell settings put secure accessibility_enabled 0
```

If other accessibility services remain, keep `accessibility_enabled` set to `1`.

## 7. Freeform and desktop-mode settings

Dextop may temporarily change these global keys on non-platform-managed devices:

- `enable_freeform_support`
- `force_resizable_activities`
- `force_desktop_mode_on_external_displays`

Do not blindly delete them: another desktop tool or a developer setting may own the current value. Restore the values recorded before the Dextop session. If you have confirmed that the keys did not exist before Dextop and now contain Dextop's temporary values, remove them with:

```sh
adb shell settings delete global enable_freeform_support
adb shell settings delete global force_resizable_activities
adb shell settings delete global force_desktop_mode_on_external_displays
```

## 8. Restart Android and verify

```sh
adb reboot
```

After reboot, verify Back/Home gestures, Circle to Search, automatic rotation, the status and navigation bars, and **Settings → Accessibility → Installed apps**. Then reopen Dextop and collect **Settings → App information → Operation log and device diagnostics** if the problem returns.

## One-block emergency sequence

This sequence does not touch accessibility lists or freeform settings. It is suitable when navigation or rotation is stuck and you need the least invasive ADB recovery first:

```sh
adb shell am force-stop moe.n4tsu.dextop
adb shell cmd statusbar send-disable-flag none
adb shell wm user-rotation free
adb reboot
```
