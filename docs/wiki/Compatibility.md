# Device Compatibility

Compatibility is recorded per model **and firmware build**. A successful result on one device does not imply support for every model from the same vendor or for future OS updates.

Status definitions:

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
| App startup and device detection | ✅ Confirmed | The connected device is detected as Samsung `SM-S942Z` / `m1q`. |
| Physical mouse/keyboard display routing | ❌ Unsupported | Samsung's global pointer controller remains bound to the physical DeX display, so Dextop hides the routing controls and restores Android's normal routing. |
| Dextop session startup | ✅ Confirmed | Dextop is confirmed to operate on the firmware listed above. |
| Individual optional features | Not separately recorded | Add separate results when each feature is retested on this build. |

</details>
</details>

<details>
<summary><strong>Google</strong></summary>

Pixel support is limited and incomplete. Behavior depends on the Android release, freeform/desktop implementation, and hidden API availability. No exact Pixel model and firmware combination has been confirmed yet.

</details>

<details>
<summary><strong>Other vendors</strong></summary>

Support is experimental. A vendor profile does not guarantee that every model or firmware provides the required system services. No additional model and firmware combination has been confirmed yet.

</details>

## Reporting another device

Use **Settings → Device report** to prepare a structured compatibility report by email. See [Device reports](Device-Reports) for the complete procedure and collected fields. You may additionally attach **Settings → App information → Operation log and device diagnostics** to a [device support issue](https://github.com/NarYuki/Dextop/issues/new?template=device_support.yml) when detailed troubleshooting logs are needed.

Model-specific changes must be isolated by manufacturer, model, codename, fingerprint prefix, and SDK range. See [Adding device support](https://github.com/NarYuki/Dextop/blob/main/docs/ADDING_DEVICE_SUPPORT.en.md) before opening a pull request.
