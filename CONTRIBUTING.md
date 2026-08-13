# Contributing to Dextop

Use English for pull-request titles, commit messages, and the primary technical description. Japanese may be added as a translation.

## Before opening an issue

- Search open and closed issues.
- Test the latest release or current `main`.
- Use the appropriate issue form.
- For device problems, attach a sanitized diagnostic report.
- Do not publish credentials, signing files, private keys, account data, or logs you do not want public.

## Pull requests

1. Create a focused branch from current `main`.
2. Keep unrelated formatting and generated-file changes out of the PR.
3. Add tests for behavior changes.
4. Run all checks listed in the PR template.
5. Explain user-visible behavior and compatibility impact.

Device-support changes must follow [the device support guide](docs/ADDING_DEVICE_SUPPORT.en.md). A workaround for one model must not change manufacturer-wide behavior.

## Licensing

All contributions are submitted under GPL-3.0-or-later. By opening a pull request, you confirm that you have the right to contribute the code and assets under that license.
