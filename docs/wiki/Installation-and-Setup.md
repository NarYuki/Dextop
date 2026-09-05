# Installation and initial setup

## Requirements

- Android 10 or later
- Dextop 1.5.0 or later, including the latest release

Dextop 1.5.0 and later include the privileged access runtime needed for normal operation. No external app or separate privileged service is required, and setup can be completed with Dextop alone. Android may still show system permission or wireless-debugging pairing screens when required by the device.

## Automatic access selection

Dextop checks the available access environments when it starts and automatically chooses the compatible one:

- With no existing privileged environment, Dextop uses its built-in runtime.
- On a rooted device, or when Stellar, Shizuku, or another compatible privileged service is already running, Dextop detects and uses that environment automatically.
- Existing root or privileged-service setups remain supported; no migration or replacement is required.

## Setup

1. Install Dextop.
2. Open Dextop and follow its in-app setup instructions.
3. Grant the Android permissions requested by Dextop and complete the built-in access pairing if the device requests it.
4. Review the detected device, Android version, display size, and desktop environment.
5. Complete the gesture demonstration.

If root, Stellar, Shizuku, or another compatible privileged service is already available, Dextop uses it automatically. No separate provider installation or provider-specific setup is needed for the normal Dextop-only path.

The Home screen displays **Dextop is ready** when the active access environment, its permission, and required system access are available.

## Stable and Nightly builds

- **Stable:** download the latest signed APK from [GitHub Releases](https://github.com/NarYuki/Dextop/releases/latest).
- **Nightly / beta:** open [GitHub Actions](https://github.com/NarYuki/Dextop/actions), select the newest successful **Debug APK** workflow run, and download its Nightly artifact. It contains matching Dextop and Dextop Car Companion debug APKs from that run.

Nightly builds contain the latest committed changes before the next stable release. They may also include unfinished behavior, temporary diagnostics, or regressions, so use the stable release when reliability is more important than early access.

When a release includes Android Auto support, its assets contain both the Dextop APK and the matching **Dextop Car Companion** APK. Install the pair from the same release; a companion signed or built separately cannot use Dextop's signature-protected relay.
