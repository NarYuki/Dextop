# Installation and initial setup

## Requirements

- Android 10 or later
- [Stellar](https://github.com/roro2239/Stellar/releases) or [Shizuku](https://github.com/RikkaApps/Shizuku/releases)
- Wireless debugging, ADB, or root to start the selected service

Stellar is Dextop's default choice and is recommended on Android 16 or later. Stellar provides a Shizuku compatibility layer, so Dextop can use its existing privileged API integration.

## Download rules

- **Stellar:** use Dextop's **Download from GitHub** button, which opens [Stellar Releases](https://github.com/roro2239/Stellar/releases).
- **Shizuku on Android 16 or later:** do not use the Play Store build; use [Shizuku GitHub Releases](https://github.com/RikkaApps/Shizuku/releases).
- **Shizuku on Android 15 or earlier:** either the Play Store build or GitHub Releases build can be used.

If both Stellar and Shizuku are installed, Dextop asks which service to use. The explicit choice is saved and reused to avoid provider conflicts until either manager is uninstalled. If only one is installed, Dextop uses the available manager. The Home screen and diagnostics show the currently selected provider.

## Setup

1. Install Dextop.
2. Install and open Stellar, or Shizuku if specifically required.
3. Follow the manager's pairing procedure and start its service.
4. Return to Dextop and verify the selected service connection.
5. Grant Dextop permission in the selected manager.
6. Review the detected device, Android version, display size, and desktop environment.
7. Complete the gesture demonstration.

If any part of wireless-debugging setup is unclear, follow **Start via wireless debugging** in the [official Shizuku setup guide](https://shizuku.rikka.app/guide/setup/). Stellar uses the same Android wireless-debugging pairing flow.

The Home screen displays **Dextop is ready** only when the selected provider, its permission, and required system access are available.
