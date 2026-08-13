# 添加设备支持

[English](ADDING_DEVICE_SUPPORT.en.md) | [日本語](ADDING_DEVICE_SUPPORT.md) | [简体中文](ADDING_DEVICE_SUPPORT.zh-CN.md)

本指南说明如何通过 Pull Request 为新的 Android 设备或 OEM 实现添加支持。首要原则是：针对某一设备的修复不得改变无关设备的行为。

## 首先收集诊断信息

1. 打开**设置 → 应用信息 → 运行日志与设备诊断**。
2. 在 Dextop 停止时保存一份报告，并在重现问题后立即再保存一份。
3. 检查 `manufacturer`、`model`、`device`、`fingerprint`、`sdk` 和 `environmentId`。
4. 使用 `probe.*` 以及 `strategy=... success=...` 日志定位失败的实现。
5. 附加报告前，删除不希望公开的标识符。

也可使用 ADB 收集基本属性：

```sh
adb shell getprop ro.product.manufacturer
adb shell getprop ro.product.model
adb shell getprop ro.product.device
adb shell getprop ro.build.version.sdk
adb shell getprop ro.build.fingerprint
```

## 代码结构

| 文件 | 职责 |
| --- | --- |
| `DeviceProfiles.kt` | 设备匹配与有序策略 |
| `DesktopEnvironment.kt` | `DeviceIdentity`、`DeviceMatch` 和能力策略 |
| `CapabilityProbe.kt` | 只读的运行时能力探测 |
| `DisplayMirrorBackend.kt` | 虚拟显示与镜像实现 |
| `DesktopModeConfigurator.kt` | 自由窗口设置与窗口策略 |
| `SessionJournal.kt` | 恢复临时 Android 设置 |
| `DeviceMatchTest.kt` | 防止规则影响其他设备的测试 |

## 使用现有后端添加设备

如果现有后端可以工作，仅需添加匹配或策略规则，请在 `DeviceProfiles.rules` 中添加规则。由于采用首个匹配规则，具体型号规则必须位于宽泛厂商规则之前。

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

匹配范围必须限制在解决方法实际需要的范围内：

- 针对单一型号的解决方法不得只使用 `manufacturers`。
- 对特定系统版本的问题设置 `minSdk` 和 `maxSdk`。
- 不同地区型号名称不同时，优先使用稳定的设备代号或指纹前缀。
- 不要匹配完整指纹，系统更新会改变其后缀。
- 不得改变无关设备的默认策略顺序。

## 添加镜像后端

如果 `window_manager` 和 `surface_control` 都无法工作，请实现 `MirrorAttachBackend`：

```kotlin
private class ExampleMirrorBackend : MirrorAttachBackend {
    override val id = "example_mirror"

    override fun isSupported(): Boolean = runCatching {
        // 只检查 API 是否存在。不得更改设置或创建设备。
        Class.forName("example.hidden.MirrorApi")
    }.isSuccess

    override fun createLayer(displayId: Int): SurfaceControl {
        // 尝试失败时抛出有用的异常。
        return createExampleMirror(displayId)
    }
}
```

在 `DisplayMirrorBackend.attachBackends` 中注册：

```kotlin
listOf(
    WindowManagerMirrorBackend(privilegedAccess),
    SurfaceControlMirrorBackend(),
    ExampleMirrorBackend()
).associateBy { it.id }
```

仅在目标设备规则中选择该后端，同时保留现有回退：

```kotlin
mirrorStrategies = listOf(
    "example_mirror",
    "window_manager",
    "surface_control"
)
```

后端必须：

- 保持 `isSupported()` 只读；
- 失败后允许继续尝试下一策略；
- 释放其创建的所有 `SurfaceControl`、文件描述符和进程；
- 抛出能够标明失败操作的错误；
- 更改临时设置前在 `SessionJournal` 中记录旧值；
- 在成功、失败、超时和强制终止后恢复状态。

## 添加能力探测

不要仅根据 SDK 版本或厂商推断支持情况。请向 `CapabilityProbe.run()` 添加只读探测：

```kotlin
"exampleMirror" to probe("Example mirror API") {
    Class.forName("example.hidden.MirrorApi")
        .getDeclaredMethod("mirror", Int::class.javaPrimitiveType)
}
```

该结果会以 `probe.exampleMirror` 出现在诊断报告中。探测不得修改系统设置、创建显示器或启动应用。

## 必需测试

测试预期匹配项，以及相似设备和无关设备的负向用例：

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

运行：

```sh
flutter analyze
flutter test
cd android
./gradlew testDebugUnitTest assembleDebug
```

## 真实设备检查

- 首次启动、Shizuku 权限、会话启动与停止
- 虚拟显示创建、镜像和桌面 HOME 启动
- 屏幕旋转、分辨率和 DPI 更改
- 安全显示开关及截图行为
- 锁屏、解锁和应用重启
- 触摸、光标和基本手势
- 强制第一策略失败并回退至第二策略
- 恢复 `overlay_display_devices` 等临时设置
- 至少在一台无关设备上确认策略选择没有变化

## Pull Request 内容

- 厂商、准确型号、代号、Android 版本和 SDK
- 已移除不必要标识符的诊断报告
- 修复前后的日志
- 新增或更改的策略及其排序原因
- 目标设备的正向测试和其他设备的负向测试
- 真实设备测试结果

如果 Pull Request 将单一型号的解决方法放入厂商级规则、移除现有回退、静默丢弃失败，或引入无法恢复的系统设置，将不会被接受。
