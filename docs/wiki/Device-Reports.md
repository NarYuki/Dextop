# Device Reports

Device reports collect consistent compatibility results for a particular device and firmware build. They provide enough information to update the compatibility table without requiring the reporter to manually copy Android build properties.

## Send a report

1. Open **Settings → Device report**.
2. Select **Working**, **Not working**, or **Not tested** for the overall status.
3. Select one of the same three results for every feature. Use **Not tested** when you have not verified a feature; do not treat it as a failure.
4. Add relevant details to **Other notes**, such as symptoms, required workarounds, external-display hardware, or whether the problem is reproducible.
5. Tap **Send report by email**.
6. Review the recipient, subject, generated Markdown body, and notes in the email application, then send it.

The recipient is `dextop-device@n4t.su`. The subject uses this stable machine-readable format:

```text
DEXTOP_DEVICE_REPORT|v=1|app=<version+build>|manufacturer=<manufacturer>|model=<model>|sdk=<API level>
```

Do not manually change the structured subject unless the email application cannot preserve it. It lets reports be grouped by schema version, Dextop version, model, and Android API level.

## Result choices

| Result | Meaning |
| --- | --- |
| Working | The feature was exercised and behaved as expected. |
| Not working | The feature was exercised but failed or behaved incorrectly. Explain the symptom in Other notes. |
| Not tested | The feature was not verified on this configuration. |

The report covers session startup, every mirroring backend, orientation, secure display, app and workspace handling, cursor/direct-touch/multi-touch input, overlay gestures, physical mouse and keyboard support, input routing, foldable resolution handling, the performance overlay, and Android state restoration.

## Automatically collected data

The generated Markdown body contains:

- Manufacturer, brand, model, device codename, and product name
- Android version and API level
- Firmware incremental version, build ID, display build, and full build fingerprint
- Android security patch level
- Installed Dextop version and build number
- Date of verification
- The overall and per-feature results selected in the form
- Dextop's runtime capability diagnostics
- Text entered in Other notes

The report is composed locally and handed to an installed email application. Dextop does not send the message automatically. Review and edit the generated message before sending it if it contains information you do not want to share.

## Report versus diagnostic log

Use **Device report** for a compatibility submission that can be added to the supported-device documentation. Use **Settings → App information → Operation log and device diagnostics** when investigating a malfunction that needs chronological app logs and more detailed troubleshooting information. A maintainer may request both for a difficult device-specific issue.

## How reports are used

Reports are compared per model and firmware build. Confirmation on one firmware does not automatically establish compatibility for later updates or other models from the same vendor. Maintainers use sufficiently complete reports to update [Device Compatibility](Compatibility) and identify features requiring a device-specific implementation.
