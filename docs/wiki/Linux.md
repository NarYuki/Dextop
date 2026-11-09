# Linux

Dextop 2.0.0 runs Debian 13 applications on the phone. They open as Android windows or on the Dextop desktop.

## Requirements

- An arm64 device
- An internet connection during setup
- Several GB of free storage. The amount depends on the applications you choose.

## Set up Linux

1. Open the **Linux** tab and start the setup.
2. Choose a name and the Linux environment.
3. Choose the Store sources, language, input method, and applications.
4. Create the Linux account.
5. Review your choices and start the setup.

If you only want Linux, select **Go to Linux containers** on the first page of Dextop's setup. Dextop's desktop features stay locked until you finish that setup, but Linux works with ordinary app permissions.

Dextop downloads a signed, prebuilt Debian image that already contains the desktop components, Mesa, and GPU drivers. It verifies the image's signature and SHA-256 before using it. Fonts, the input method, and applications are installed afterward. An interrupted download continues from where it stopped.

The images and their complete corresponding source are published in [dextop-linux-images](https://github.com/NarYuki/dextop-linux-images/releases).

## Execution modes

| Mode | Availability | Behavior |
| --- | --- | --- |
| Standard permissions | All builds, default | Runs with Dextop's ordinary app permissions |
| Privileged Shell (advanced) | GitHub build, after Dextop's setup | Runs containers with elevated permissions |

The Google Play build offers Standard permissions only.

## Store

APT is always available. deb-get and Flathub are optional. Turn them on during the first setup or in Linux settings. Each container prepares their tools separately.

## Steam

Select Steam during setup, or add it later by setting up the container again. Dextop downloads Valve's ARM64 client and the compatibility components it needs.

## Maintenance

- **Set up again** adds features to a container and keeps its files and applications.
- **Repair** reinstalls the system components that Dextop manages. Your files and applications are kept, but some settings return to their defaults, including the input method profile and the browser launchers.
- **Delete all Linux data** in Linux settings removes every container and setting.
