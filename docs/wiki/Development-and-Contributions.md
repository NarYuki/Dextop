# Development and contributions

## Build

```sh
git clone https://github.com/NarYuki/Dextop.git
cd Dextop
flutter pub get
flutter analyze
flutter test
flutter build apk --debug
```

## Issue rules

- Search before submitting.
- Use the bug, device support, or feature form.
- Test the latest version.
- Include a sanitized diagnostic report for device/runtime problems.
- Never publish credentials, signing keys, or private logs.

## Pull request rules

- Keep one focused change per PR.
- Complete the PR template and all checks.
- Add tests for behavior and matching changes.
- Document physical-device validation.
- Do not put a one-model workaround in a manufacturer-wide rule.
- Preserve existing fallbacks and restore every temporary setting.
- Contributions are licensed under GPL-3.0-or-later.

Read [CONTRIBUTING.md](https://github.com/NarYuki/Dextop/blob/main/CONTRIBUTING.md) and the [device support guide](https://github.com/NarYuki/Dextop/blob/main/docs/ADDING_DEVICE_SUPPORT.en.md).
