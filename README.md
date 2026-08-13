<p align="center">
  <img src="assets/dextop-readme-icon.png" alt="Dextop" width="192">
</p>

<h1 align="center">Dextop</h1>

<p align="center">
  <a href="README.md">English</a> | <a href="README.ja.md">日本語</a> | <a href="README.zh-CN.md">简体中文</a>
</p>

Dextop is an open-source Android app that creates a virtual display and provides a desktop-like workspace using only a smartphone. It uses Stellar or Shizuku and Android system services to control app launching, window placement, touch input, orientation, and related desktop behavior.

## Screenshots and demo

<table>
  <tr>
    <td width="20%" align="center"><img src="docs/media/home.jpg" alt="Dextop home screen"><br><sub>Home and workspaces</sub></td>
    <td width="20%" align="center"><img src="docs/media/desktop.jpg" alt="Dextop desktop"><br><sub>Desktop</sub></td>
    <td width="20%" align="center"><img src="docs/media/control-overlay.jpg" alt="Dextop control overlay"><br><sub>Control overlay</sub></td>
    <td width="20%" align="center"><img src="docs/media/multi-window.jpg" alt="Dextop multi-window workspace"><br><sub>Multi-window workspace</sub></td>
    <td width="20%" align="center"><a href="docs/media/dextop-demo.mp4"><img src="docs/media/demo-poster.jpg" alt="Play the Dextop demo video"></a><br><sub>▶ Demo video</sub></td>
  </tr>
</table>

## Features

- [x] Virtual displays with configurable resolution, density, and portrait or landscape orientation
- [x] Secure-display and Android system-decoration controls
- [x] Desktop app launcher
- [x] Workspaces that save and restore the placement of multiple apps
- [x] Two-pane, three-pane, four-pane, and other window layouts
- [x] Workspace import and export as JSON
- [x] Cursor and direct-touch input modes
- [x] Tap, long-press, drag, right-click, two-finger, and three-finger gestures
- [x] Multi-touch input, including scrolling and pinch-to-zoom
- [x] Physical mouse support
- [x] Physical keyboard support
- [x] Mouse and keyboard input routing between Dextop and the external display on supported devices
- [x] Automatic resolution switching for foldable open and closed states
- [x] Performance overlay for FPS, refresh rate, memory, battery, and estimated power usage
- [x] Quick Settings tile launch
- [x] Interrupted-session recovery and restoration of temporary Android settings
- [x] Detailed diagnostic reports containing app logs, capability probes, fallback results, and device specifications
- [x] Localized device compatibility reports with per-feature results and automatic email composition
- [x] Japanese, English, Chinese, Korean, and Russian interfaces

## Compatibility

| Environment | Status | Notes |
| --- | --- | --- |
| Samsung DeX | Mostly supported | Currently the most complete environment. Features managed by DeX use Samsung's platform implementation. |
| Google Pixel | Limited and incomplete | Depends on Android's freeform/desktop implementation and hidden API availability. Some features may not work. |
| Other Android devices | Experimental | Virtual-display, mirroring, and freeform support varies by manufacturer, model, and OS update. |

Dextop probes device capabilities at runtime and tries compatible backends in order. It still depends on Android hidden APIs and OEM behavior, so results can differ between models and OS versions from the same manufacturer.

<details>
<summary><strong>Supported devices</strong></summary>

The status below applies only to firmware versions that were actually tested. Open a vendor to see its devices. For feature-by-feature results, see the [Device compatibility wiki](https://github.com/NarYuki/Dextop/wiki/Device-Compatibility).

<details>
<summary><strong>Samsung</strong></summary>

| Device | Model | Tested software | Status |
| --- | --- | --- | --- |
| Galaxy S26 | SM-S942Z (`m1q`) | Android 16 / One UI 8.5 / `S942ZSCS1AZF2` | ✅ Confirmed working |
| Galaxy Z TriFold | SM-F968N (`q7mq`) | Android 16 (API 36) / `F968NKSS6BZG3` | 🧪 Experimental |
| Galaxy Z Fold8 | SM-F971Q (`h8q`) | Android 17 (API 37) / `F971QOPU1AZGI` | 🟡 Partial |
| Galaxy Z Fold7 | SM-F966Q (`q7q`) | Android 16 (API 36) / `F966QOPU1BZF1` | ✅ Confirmed working |

_Community-submitted and reviewed device report_

</details>

<details>
<summary><strong>Google</strong></summary>

| Device | Model | Tested software | Status |
| --- | --- | --- | --- |
| Pixel 9a | Pixel 9a (`tegu`) | Android 17 (API 37) / `15641320` | 🟡 Partial |

_Community-submitted and reviewed device report_

</details>

<details>
<summary><strong>HONOR</strong></summary>

| Device | Model | Tested software | Status |
| --- | --- | --- | --- |
| HONOR Magic 8 Pro | BKQ-AN10 (`HNBKQ`) | Android 16 (API 36) / `10DLDLD170SP5C00E167` | 🧪 Experimental |

_Community-submitted and reviewed device report_

</details>

<details>
<summary><strong>sony</strong></summary>

| Device | Model | Tested software | Status |
| --- | --- | --- | --- |
| Xperia 1 III | XQ-BC42 (`XQ-BC42`) | Android 13 (API 33) / `061002A0000472A1434898470` | ❌ Not working at this time |

_Community-submitted and reviewed device report_

</details>

</details>

## System requirements

- Android 10 or later
- [Stellar](https://github.com/roro2239/Stellar/releases) (default and recommended, especially on Android 16 or later) or [Shizuku](https://github.com/RikkaApps/Shizuku/releases)
- The selected service started through wireless debugging, ADB, or root
- Stellar or Shizuku permission granted to Dextop

Download Stellar with the **Download from GitHub** button in Dextop. On Android 16 or later, Stellar is recommended. If you use Shizuku on Android 16 or later, install its [GitHub Releases build](https://github.com/RikkaApps/Shizuku/releases), not the Play Store build. On Android 15 or earlier, the Play Store build can also be used. If both managers are installed, Dextop asks which one to use and preserves that selection until either manager is uninstalled.

If any part of wireless-debugging setup is unclear, follow **Start via wireless debugging** in the [official Shizuku setup guide](https://shizuku.rikka.app/guide/setup/); the same Android pairing flow also applies when starting Stellar wirelessly.

## Installation

The Google Play release is currently under review.

Download the latest APK from [GitHub Releases](https://github.com/NarYuki/Dextop/releases/latest) and install it.

## Development

```sh
git clone https://github.com/NarYuki/Dextop.git
cd Dextop
flutter pub get
flutter analyze
flutter test
flutter build apk --debug
```

To contribute support for another device, read [Adding support for a device](docs/ADDING_DEVICE_SUPPORT.en.md). The Japanese guide is [available here](docs/ADDING_DEVICE_SUPPORT.md).

## Diagnostics

Open **Settings → App information → Operation log and device diagnostics** to view, copy, or share device specifications, capability probes, fallback results, and Dextop operation logs. Remove any personal information you do not want to publish before attaching a report to an issue.

## Device reports

Open **Settings → Device report** to report how Dextop works on a specific device and firmware. Choose **Working**, **Not working**, or **Not tested** for the overall result and every listed feature, add optional notes, then tap **Send report by email**. Dextop prepares a structured Markdown report and opens your email app with `dextop-device@n4t.su` as the recipient.

The report includes the device model, codename, Android/API version, firmware identifiers, security patch, Dextop version, detected capabilities, and the results you selected. Review the generated email before sending it. See [Device reports](https://github.com/NarYuki/Dextop/wiki/Device-Reports) for the complete field list and procedure.

This project is under active development. Available features and behavior may change with device firmware and Android updates.

## License

Licensed under GPL-3.0-or-later. See [LICENSE](LICENSE).
