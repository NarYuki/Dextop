# Device Compatibility

Compatibility is recorded per model **and firmware build**. A successful result on one device does not imply support for every model from the same vendor or for future OS updates.

| Status | Meaning |
| --- | --- |
| ✅ Confirmed working | Dextop was launched and used successfully on the listed build. |
| 🟡 Partial | Dextop starts, but one or more important functions are unavailable or unstable. |
| 🧪 Experimental | A device profile or generic backend exists, but the exact model/build has not been verified. |
| ❌ Not working | The listed build has a known blocking issue. |

<details>
<summary><strong>Samsung</strong></summary>

Samsung DeX is mostly supported and is currently the most complete environment. Dextop avoids generic freeform changes that could conflict with Samsung's platform-managed desktop implementation.

<details>
<summary><strong>Galaxy S26 — SM-S942Z / Android 16 / One UI 8.5 — ✅ Confirmed working</strong></summary>

| Item | Tested value |
| --- | --- |
| Marketing name | Samsung Galaxy S26 |
| Model | `SM-S942Z` |
| Device codename | `m1q` |
| Android | Android 16 (API 36) |
| One UI | One UI 8.5 (`80500`) |
| Firmware / incremental build | `S942ZSCS1AZF2` |
| Build ID | `BP4A.251205.006.S942ZSCS1AZF2` |
| Build fingerprint | `samsung/m1qsbmx/m1q:16/BP4A.251205.006/S942ZSCS1AZF2_QBM1AZF2:user/release-keys` |
| Last verified | 2026-08-13 |
| Overall status | ✅ Confirmed working |

#### Verification results

| Area | Result | Notes |
| --- | --- | --- |
| App startup and device detection | ✅ Confirmed | Detected as Samsung `SM-S942Z` / `m1q`. |
| Dextop session startup | ✅ Confirmed | Dextop operates on the firmware listed above. |
| Individual optional features | Not separately recorded | Add separate results when each feature is retested on this build. |

</details>

<!-- DEXTOP-REPORT-CONSOLE:SAMSUNG:BEGIN -->
<details>
<summary><strong>Galaxy Z TriFold — SM-F968N</strong> / q7mq / F968NKSS6BZG3 — 🟡 Partial</summary>

> **Report source:** This compatibility status is based on a device report submitted by a community member and reviewed before publication.

## Device and software

| Item | Reported value |
| --- | --- |
| Manufacturer | Samsung |
| Brand | Samsung |
| Marketing name | Galaxy Z TriFold |
| Model | `SM-F968N` |
| Device codename | `q7mq` |
| Product | `q7mqksx` |
| Android | Android 16 (API 36) |
| Firmware / incremental build | `F968NKSS6BZG3` |
| Build ID | `BP4A.251205.006` |
| Build fingerprint | `samsung/q7mqksx/q7mq:16/BP4A.251205.006/F968NKSS6BZG3_OKR6BZG3:user/release-keys` |
| Security patch | `2026-07-05` |
| Display build | `BP4A.251205.006.F968NKSS6BZG3` |
| Dextop version | `1.1.1+6` |
| Last verified | 2026-08-13 |
| Overall status | 🟡 Partial |

## Feature verification

| Feature | Result |
| --- | --- |
| Overall status | 🟡 Partial |
| App startup and device detection | ✅ Working |
| Dextop session startup | ✅ Working |
| VirtualDisplay mirroring | ✅ Working |
| WindowManager mirroring | ✅ Working |
| SurfaceControl mirroring | ❌ Not working at this time |
| Landscape mode | 🟡 Partial |
| Portrait mode | ✅ Working |
| Secure display | ❌ Not working at this time |
| App launcher and freeform windows | ✅ Working |
| Workspace save and restore | ❌ Not working at this time |
| Cursor and touchpad input | 🟡 Partial |
| Direct touch input | 🟡 Partial |
| Multi-touch scrolling and pinch-to-zoom | 🟡 Partial |
| Three-finger overlay gesture | ✅ Working |
| Physical mouse | ⬜ Not tested |
| Physical keyboard | ⬜ Not tested |
| Physical mouse and keyboard display routing | ⬜ Not tested |
| Automatic foldable-device resolution | ✅ Working |
| Performance overlay | ✅ Working |
| Session shutdown and Android state restoration | ✅ Working |


</details>

<details>
<summary><strong>Galaxy Z Fold8 — SM-F971Q</strong> / h8q / F971QOPU1AZGI — 🟡 Partial</summary>

> **Report source:** This compatibility status is based on a device report submitted by a community member and reviewed before publication.

## Device and software

| Item | Reported value |
| --- | --- |
| Manufacturer | Samsung |
| Brand | Samsung |
| Marketing name | Galaxy Z Fold8 |
| Model | `SM-F971Q` |
| Device codename | `h8q` |
| Product | `h8qjpnw` |
| Android | Android 17 (API 37) |
| Firmware / incremental build | `F971QOPU1AZGI` |
| Build ID | `CP2A.260605.016` |
| Build fingerprint | `samsung/h8qjpnw/h8q:17/CP2A.260605.016/F971QOPU1AZGI_SJP1AZGI:user/release-keys` |
| Security patch | `2026-06-05` |
| Display build | `CP2A.260605.016.F971QOPU1AZGI` |
| Dextop version | `1.1.1+6` |
| Last verified | 2026-08-13 |
| Overall status | 🟡 Partial |

## Feature verification

| Feature | Result |
| --- | --- |
| Overall status | 🟡 Partial |
| App startup and device detection | ✅ Working |
| Dextop session startup | ✅ Working |
| VirtualDisplay mirroring | ✅ Working |
| WindowManager mirroring | ⬜ Not tested |
| SurfaceControl mirroring | ⬜ Not tested |
| Landscape mode | 🧪 Experimental |
| Portrait mode | 🧪 Experimental |
| Secure display | ⬜ Not tested |
| App launcher and freeform windows | ⬜ Not tested |
| Workspace save and restore | ⬜ Not tested |
| Cursor and touchpad input | 🧪 Experimental |
| Direct touch input | 🧪 Experimental |
| Multi-touch scrolling and pinch-to-zoom | ✅ Working |
| Three-finger overlay gesture | ✅ Working |
| Physical mouse | ✅ Working |
| Physical keyboard | ✅ Working |
| Physical mouse and keyboard display routing | ⬜ Not tested |
| Automatic foldable-device resolution | ❌ Not working at this time |
| Performance overlay | ⬜ Not tested |
| Session shutdown and Android state restoration | ✅ Working |


</details>

<details>
<summary><strong>Galaxy Z Fold7 — SM-F966Q</strong> / q7q / F966QOPU1BZF1 — ✅ Confirmed working</summary>

> **Report source:** This compatibility status is based on a device report submitted by a community member and reviewed before publication.

## Device and software

| Item | Reported value |
| --- | --- |
| Manufacturer | Samsung |
| Brand | Samsung |
| Marketing name | Galaxy Z Fold7 |
| Model | `SM-F966Q` |
| Device codename | `q7q` |
| Product | `q7qjpnw` |
| Android | Android 16 (API 36) |
| Firmware / incremental build | `F966QOPU1BZF1` |
| Build ID | `BP4A.251205.006` |
| Build fingerprint | `samsung/q7qjpnw/q7q:16/BP4A.251205.006/F966QOPU1BZF1_SJP1BZF1:user/release-keys` |
| Security patch | `2026-06-05` |
| Display build | `BP4A.251205.006.F966QOPU1BZF1` |
| Dextop version | `1.1.1+6` |
| Last verified | 2026-08-13 |
| Overall status | ✅ Confirmed working |

## Feature verification

| Feature | Result |
| --- | --- |
| Overall status | ✅ Working |
| App startup and device detection | ✅ Working |
| Dextop session startup | ✅ Working |
| VirtualDisplay mirroring | ✅ Working |
| WindowManager mirroring | ✅ Working |
| SurfaceControl mirroring | ✅ Working |
| Landscape mode | ✅ Working |
| Portrait mode | ✅ Working |
| Secure display | ✅ Working |
| App launcher and freeform windows | ⬜ Not tested |
| Workspace save and restore | ⬜ Not tested |
| Cursor and touchpad input | ✅ Working |
| Direct touch input | ✅ Working |
| Multi-touch scrolling and pinch-to-zoom | ⬜ Not tested |
| Three-finger overlay gesture | ✅ Working |
| Physical mouse | ⬜ Not tested |
| Physical keyboard | ⬜ Not tested |
| Physical mouse and keyboard display routing | ⬜ Not tested |
| Automatic foldable-device resolution | ✅ Working |
| Performance overlay | ✅ Working |
| Session shutdown and Android state restoration | ✅ Working |


</details>
<!-- DEXTOP-REPORT-CONSOLE:SAMSUNG:END -->

</details>

<details>
<summary><strong>Google</strong></summary>

Pixel support is limited and incomplete. No exact Pixel model and firmware combination has been confirmed yet.

<!-- DEXTOP-REPORT-CONSOLE:GOOGLE:BEGIN -->
<details>
<summary><strong>Pixel 9a</strong> / tegu / 15641320 — 🟡 Partial</summary>

> **Report source:** This compatibility status is based on a device report submitted by a community member and reviewed before publication.

## Device and software

| Item | Reported value |
| --- | --- |
| Manufacturer | Google |
| Brand | Google |
| Marketing name | Not available |
| Model | `Pixel 9a` |
| Device codename | `tegu` |
| Product | `tegu` |
| Android | Android 17 (API 37) |
| Firmware / incremental build | `15641320` |
| Build ID | `CP2A.260705.006` |
| Build fingerprint | `google/tegu/tegu:17/CP2A.260705.006/15641320:user/release-keys` |
| Security patch | `2026-07-05` |
| Display build | `CP2A.260705.006` |
| Dextop version | `1.1.1+6` |
| Last verified | 2026-08-13 |
| Overall status | 🟡 Partial |

## Feature verification

| Feature | Result |
| --- | --- |
| Overall status | 🟡 Partial |
| App startup and device detection | ✅ Working |
| Dextop session startup | ✅ Working |
| VirtualDisplay mirroring | ✅ Working |
| WindowManager mirroring | ✅ Working |
| SurfaceControl mirroring | ⬜ Not tested |
| Landscape mode | ✅ Working |
| Portrait mode | 🟡 Partial |
| Secure display | ⬜ Not tested |
| App launcher and freeform windows | 🟡 Partial |
| Workspace save and restore | ✅ Working |
| Cursor and touchpad input | ✅ Working |
| Direct touch input | ✅ Working |
| Multi-touch scrolling and pinch-to-zoom | ✅ Working |
| Three-finger overlay gesture | 🟡 Partial |
| Physical mouse | ⬜ Not tested |
| Physical keyboard | ⬜ Not tested |
| Physical mouse and keyboard display routing | ⬜ Not tested |
| Automatic foldable-device resolution | ⬜ Not tested |
| Performance overlay | ⬜ Not tested |
| Session shutdown and Android state restoration | ✅ Working |


</details>
<!-- DEXTOP-REPORT-CONSOLE:GOOGLE:END -->

</details>

<!-- DEXTOP-REPORT-CONSOLE:OTHER:BEGIN -->
<details>
<summary><strong>sony</strong></summary>

<br>

<details>
<summary><strong>Xperia 1 III — XQ-BC42</strong> / XQ-BC42 / 061002A0000472A1434898470 — ❌ Not working at this time</summary>

> **Report source:** This compatibility status is based on a device report submitted by a community member and reviewed before publication.

> **Status notice:** This device does not work at this time. Compatibility may improve in future Dextop updates.

## Device and software

| Item | Reported value |
| --- | --- |
| Manufacturer | Sony |
| Brand | Sony |
| Marketing name | Xperia 1 III |
| Model | `XQ-BC42` |
| Device codename | `XQ-BC42` |
| Product | `XQ-BC42` |
| Android | Android 13 (API 33) |
| Firmware / incremental build | `061002A0000472A1434898470` |
| Build ID | `61.2.A.0.472A` |
| Build fingerprint | `Sony/XQ-BC42/XQ-BC42:13/61.2.A.0.472A/061002A0000472A1434898470:user/release-keys` |
| Security patch | `2023-07-01` |
| Display build | `61.2.A.0.472A release-keys` |
| Dextop version | `1.1.1+6` |
| Last verified | 2026-08-13 |
| Overall status | ❌ Not working at this time |

## Feature verification

| Feature | Result |
| --- | --- |
| Overall status | ❌ Not working at this time |
| App startup and device detection | ✅ Working |
| Dextop session startup | ❌ Not working at this time |
| VirtualDisplay mirroring | ❌ Not working at this time |
| WindowManager mirroring | ❌ Not working at this time |
| SurfaceControl mirroring | ❌ Not working at this time |
| Landscape mode | ❌ Not working at this time |
| Portrait mode | ❌ Not working at this time |
| Secure display | ❌ Not working at this time |
| App launcher and freeform windows | ❌ Not working at this time |
| Workspace save and restore | ❌ Not working at this time |
| Cursor and touchpad input | ❌ Not working at this time |
| Direct touch input | ❌ Not working at this time |
| Multi-touch scrolling and pinch-to-zoom | ❌ Not working at this time |
| Three-finger overlay gesture | ❌ Not working at this time |
| Physical mouse | ❌ Not working at this time |
| Physical keyboard | ❌ Not working at this time |
| Physical mouse and keyboard display routing | ❌ Not working at this time |
| Automatic foldable-device resolution | ❌ Not working at this time |
| Performance overlay | ❌ Not working at this time |
| Session shutdown and Android state restoration | ❌ Not working at this time |


</details>

</details>

<details>
<summary><strong>HONOR</strong></summary>

<br>

<details>
<summary><strong>HONOR Magic 8 Pro — BKQ-AN10</strong> / HNBKQ / 10DLDLD170SP5C00E167 — 🧪 Experimental</summary>

> **Report source:** This compatibility status is based on a device report submitted by a community member and reviewed before publication.

## Device and software

| Item | Reported value |
| --- | --- |
| Manufacturer | HONOR |
| Brand | HONOR |
| Marketing name | HONOR Magic 8 Pro |
| Model | `BKQ-AN10` |
| Device codename | `HNBKQ` |
| Product | `BKQ-AN10` |
| Android | Android 16 (API 36) |
| Firmware / incremental build | `10DLDLD170SP5C00E167` |
| Build ID | `HONORBKQ-ANXX` |
| Build fingerprint | `HONOR/BKQ-AN10/HNBKQ:16/HONORBKQ-ANXX/10DLDLD170SP5C00E167:user/release-keys` |
| Security patch | `2026-07-01` |
| Display build | `BKQ-AN10 10.0.0.170(SP5C00E167R101P7)` |
| Dextop version | `1.1.1+6` |
| Last verified | 2026-08-13 |
| Overall status | 🧪 Experimental |

## Feature verification

| Feature | Result |
| --- | --- |
| Overall status | 🧪 Experimental |
| App startup and device detection | ✅ Working |
| Dextop session startup | ✅ Working |
| VirtualDisplay mirroring | ⬜ Not tested |
| WindowManager mirroring | ⬜ Not tested |
| SurfaceControl mirroring | ⬜ Not tested |
| Landscape mode | ❌ Not working at this time |
| Portrait mode | ❌ Not working at this time |
| Secure display | ⬜ Not tested |
| App launcher and freeform windows | ❌ Not working at this time |
| Workspace save and restore | ❌ Not working at this time |
| Cursor and touchpad input | ✅ Working |
| Direct touch input | ✅ Working |
| Multi-touch scrolling and pinch-to-zoom | ❌ Not working at this time |
| Three-finger overlay gesture | ✅ Working |
| Physical mouse | ⬜ Not tested |
| Physical keyboard | ⬜ Not tested |
| Physical mouse and keyboard display routing | ⬜ Not tested |
| Automatic foldable-device resolution | ⬜ Not tested |
| Performance overlay | ⬜ Not tested |
| Session shutdown and Android state restoration | ✅ Working |


</details>

</details>
<!-- DEXTOP-REPORT-CONSOLE:OTHER:END -->

<details>
<summary><strong>Other vendors</strong></summary>

Support is experimental. No additional model and firmware combination has been confirmed yet.

</details>

## Reporting another device

Attach the report from **Settings → App information → Operation log and device diagnostics** to a [device support issue](https://github.com/NarYuki/Dextop/issues/new?template=device_support.yml). Include the model, codename, Android version, vendor UI version, firmware build, and feature-by-feature results.

Model-specific changes must be isolated by manufacturer, model, codename, fingerprint prefix, and SDK range. See [Adding device support](https://github.com/NarYuki/Dextop/blob/main/docs/ADDING_DEVICE_SUPPORT.en.md) before opening a pull request.

