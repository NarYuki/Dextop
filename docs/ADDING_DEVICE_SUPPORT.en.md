# Adding device support

[English](ADDING_DEVICE_SUPPORT.en.md) | [日本語](ADDING_DEVICE_SUPPORT.md) | [简体中文](ADDING_DEVICE_SUPPORT.zh-CN.md)

This guide explains how to add support for a new Android device or OEM implementation through a pull request. The primary rule is that a fix for one device must not change behavior on unrelated devices.

## Collect diagnostics first

1. Open **Settings → App information → Operation log and device diagnostics**.
2. Save a report while stopped and another immediately after reproducing the problem.
3. Check `manufacturer`, `model`, `device`, `fingerprint`, `sdk`, and `environmentId`.
4. Use `probe.*` and `strategy=... success=...` log entries to identify the failing implementation.
5. Remove identifiers you do not want to publish before attaching the report.

Basic properties can also be collected with ADB:

```sh
adb shell getprop ro.product.manufacturer
adb shell getprop ro.product.model
adb shell getprop ro.product.device
adb shell getprop ro.build.version.sdk
adb shell getprop ro.build.fingerprint
```

## Code map

| File | Responsibility |
| --- | --- |
| `DeviceProfiles.kt` | Device matching and ordered strategies |
| `DesktopEnvironment.kt` | `DeviceIdentity`, `DeviceMatch`, and capability policy |
| `CapabilityProbe.kt` | Read-only runtime capability probes |
| `DisplayMirrorBackend.kt` | Virtual-display and mirror implementations |
| `DesktopModeConfigurator.kt` | Freeform settings and windowing strategies |
| `SessionJournal.kt` | Restoration of temporary Android settings |
| `DeviceMatchTest.kt` | Tests preventing rules from leaking to other devices |

## Add a device using existing backends

Add a rule to `DeviceProfiles.rules` when existing backends work and only matching or strategy policy is required. Specific model rules must appear before broad vendor rules because the first matching rule wins.

```kotlin
DesktopEnvironmentRule(
    id = "example_phone_2_android_16",
    match = DeviceMatch(
        manufacturers = setOf("example"),
        models = setOf("example phone 2"),
        devices = setOf("example2"),
        fingerprintPrefixes = setOf("example/example2/"),
        minSdk = 36,
        maxSdk = 36
    )
) { identity ->
    DesktopEnvironmentRegistry.aospFreeform(identity).copy(
        id = "example_phone_2_android_16",
        displayName = "Example Desktop",
        mirrorStrategies = listOf("window_manager", "surface_control"),
        windowingStrategies = listOf("wm", "activity_task_manager")
    )
}
```

Keep matching as narrow as the workaround requires:

- Never use only `manufacturers` for a one-model workaround.
- Set `minSdk` and `maxSdk` for an OS-version-specific problem.
- Prefer a stable device codename or fingerprint prefix when regional model names differ.
- Do not match an entire fingerprint; build updates change its suffix.
- Never change the default strategy order for unrelated devices.

## Add a mirror backend

If neither `window_manager` nor `surface_control` works, implement `MirrorAttachBackend`:

```kotlin
private class ExampleMirrorBackend : MirrorAttachBackend {
    override val id = "example_mirror"

    override fun isSupported(): Boolean = runCatching {
        // Check API presence only. Do not change settings or create a display.
        Class.forName("example.hidden.MirrorApi")
    }.isSuccess

    override fun createLayer(displayId: Int): SurfaceControl {
        // Throw a useful exception when the attempt fails.
        return createExampleMirror(displayId)
    }
}
```

Register it in `DisplayMirrorBackend.attachBackends`:

```kotlin
listOf(
    WindowManagerMirrorBackend(privilegedAccess),
    SurfaceControlMirrorBackend(),
    ExampleMirrorBackend()
).associateBy { it.id }
```

Select it only from the target device rule while retaining existing fallbacks:

```kotlin
mirrorStrategies = listOf(
    "example_mirror",
    "window_manager",
    "surface_control"
)
```

A backend must:

- keep `isSupported()` read-only;
- allow the next strategy to run after failure;
- release every `SurfaceControl`, file descriptor, and process it creates;
- throw errors that identify the failed operation;
- record old values in `SessionJournal` before changing temporary settings;
- restore state after success, failure, timeout, and forced termination.

## Add a capability probe

Do not infer support only from an SDK number or manufacturer. Add a read-only probe to `CapabilityProbe.run()`:

```kotlin
"exampleMirror" to probe("Example mirror API") {
    Class.forName("example.hidden.MirrorApi")
        .getDeclaredMethod("mirror", Int::class.javaPrimitiveType)
}
```

It will appear as `probe.exampleMirror` in diagnostic reports. A probe must not modify system settings, create a display, or launch an app.

## Required tests

Test the intended match and negative cases for similar and unrelated devices:

```kotlin
@Test
fun exampleRuleIsLimitedToTargetDevice() {
    val match = DeviceMatch(
        manufacturers = setOf("example"),
        devices = setOf("example2"),
        minSdk = 36,
        maxSdk = 36
    )
    assertTrue(match.matches(targetIdentity))
    assertFalse(match.matches(targetIdentity.copy(device = "example1")))
    assertFalse(match.matches(targetIdentity.copy(manufacturer = "other")))
    assertFalse(match.matches(targetIdentity.copy(sdk = 35)))
}
```

Run:

```sh
flutter analyze
flutter test
cd android
./gradlew testDebugUnitTest assembleDebug
```

## Physical-device checks

- First launch, Stellar or Shizuku permission, session start, and session stop
- Virtual-display creation, mirroring, and desktop HOME launch
- Rotation, resolution, and density changes
- Secure display on/off and screenshot behavior
- Screen lock, unlock, and application restart
- Touch, cursor, and basic gestures
- Forced failure of the first strategy and fallback to the second
- Restoration of temporary settings such as `overlay_display_devices`
- Unchanged strategy selection on at least one unrelated device

## Pull request contents

- Manufacturer, exact model, codename, Android version, and SDK
- A diagnostic report with unwanted identifiers removed
- Logs from before and after the fix
- The added or changed strategy and the reason for its order
- Positive tests for the target and negative tests for other devices
- Physical-device test results

Pull requests will not be accepted if they place a model workaround in a vendor-wide rule, remove existing fallbacks, silently discard failures, or introduce system settings that are not restored.
