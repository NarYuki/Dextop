#!/bin/sh
# Runs inside the proot on the Galaxy. Bakes what every installation needs; language, fonts,
# IME, applications and the account stay in LinuxSetupPlan and run on each user's device.
# Keep in step with LinuxCatalog (configureScript, provisionScript, turnipScript) and
# LinuxSetupPlan (the wayland "graphics", "desktop" and "graphics" steps).
set -e
export DEBIAN_FRONTEND=noninteractive
IMAGE_VERSION="$1"
install_packages() { apt-get install -y --no-install-recommends "$@"; }

# LinuxCatalog.configureScript
rm -f /etc/resolv.conf
printf 'nameserver 8.8.8.8\nnameserver 1.1.1.1\n' > /etc/resolv.conf
printf '127.0.0.1 localhost\n::1 localhost ip6-localhost\n' > /etc/hosts
mkdir -p /tmp /root && chmod 1777 /tmp
mkdir -p /etc/apt/apt.conf.d
echo 'APT::Sandbox::User "root";' > /etc/apt/apt.conf.d/01-dextop-sandbox
echo 'APT::Sandbox::User "root";' > /etc/apt/apt.conf.d/00dextop-android

# LinuxCatalog.refreshScript and provisionScript
apt-get update
install_packages \
    xvfb x11-xserver-utils xauth icewm xterm dbus-x11 pulseaudio pulseaudio-utils \
    fonts-dejavu-core xdg-utils ca-certificates fakeroot adwaita-icon-theme xz-utils procps \
    python3 passwd sudo libcrypt1 gnome-keyring libpam-gnome-keyring libsecret-tools gnome-themes-extra libgtk-3-bin libglib2.0-bin qt5ct qt6ct
if apt-cache show adwaita-icon-theme-legacy >/dev/null 2>&1; then
    install_packages adwaita-icon-theme-legacy
fi

# LinuxSetupPlan: wayland "graphics"
install_packages xwayland libwayland-client0 libwayland-egl1 qtwayland5 qt6-wayland libegl-mesa0 libgl1-mesa-dri

# LinuxSetupPlan: "desktop"
install_packages icewm-common adwaita-icon-theme gnome-themes-extra gtk2-engines-pixbuf bzip2
mkdir -p /root/.icewm /root/.config/gtk-3.0 /usr/share/icewm/themes
printf '[Settings]\ngtk-theme-name=Adwaita\ngtk-icon-theme-name=Adwaita\ngtk-font-name=Noto Sans 10\n' >/root/.config/gtk-3.0/settings.ini
tar -xjf /opt/dextop-image-input/materia.tar.bz2 -C /usr/share/icewm/themes
cp /usr/share/common-licenses/GPL-3 /usr/share/icewm/themes/Materia-Manjaro-Dark/COPYING
printf 'Theme="Materia-Manjaro-Dark/default.theme"\n' >/root/.icewm/theme
: >/root/.icewm/.dextop-theme

# LinuxSetupPlan: "graphics"
install_packages libgl1-mesa-dri libegl-mesa0 libglx-mesa0 mesa-utils mesa-vulkan-drivers vulkan-tools

# LinuxCatalog.turnipScript with GPU_PACKAGE
GPU_PREFIX=/opt/dextop-mesa
GPU_NAME=DD-Turnip-v0.2.0
TURNIP_ICD=$GPU_PREFIX/share/vulkan/icd.d/freedreno_icd.aarch64.json
install_packages wget ca-certificates unzip libvulkan1 libgl1-mesa-dri \
    libdrm2 libzstd1 zlib1g libwayland-client0 libx11-xcb1 libxshmfence1 libxcb1 libxcb-dri3-0 \
    libxcb-present0 libxcb-xfixes0 libxcb-sync1 libxcb-randr0 libxcb-shm0 libxcb-keysyms1
work=/tmp/dextop-gpu-package
rm -rf "$work" && mkdir -p "$work" && cd "$work"
wget -q -O package.zip "https://github.com/Droid-Deck/Drivers-CI/releases/download/$GPU_NAME/$GPU_NAME.zip"
echo "fc16658953062cfaabda8a5663c498667d78bb4c9c74c819ff580753270a147d  package.zip" | sha256sum -c -
unzip -q package.zip
rm -rf "$GPU_PREFIX"
mkdir -p "$GPU_PREFIX/lib" "$GPU_PREFIX/share/doc/$GPU_NAME" "$(dirname "$TURNIP_ICD")"
cp linux/libvulkan_freedreno.so "$GPU_PREFIX/lib/libvulkan_freedreno.so"
cp -r NOTICE LICENSES manifest.json "$GPU_PREFIX/share/doc/$GPU_NAME/"
printf '{"file_format_version": "1.0.0", "ICD": {"library_path": "%s/lib/libvulkan_freedreno.so", "api_version": "1.4.0"}}\n' "$GPU_PREFIX" > "$TURNIP_ICD"
cd / && rm -rf "$work"
. /etc/os-release
echo "deb http://deb.debian.org/debian $VERSION_CODENAME-backports main" > /etc/apt/sources.list.d/dextop-backports.list
apt-get update
apt-get install -y --no-install-recommends -t "$VERSION_CODENAME-backports" \
    mesa-libgallium libgl1-mesa-dri libglx-mesa0 libegl-mesa0 libgbm1 mesa-vulkan-drivers

mkdir -p /etc/dextop
printf '{"version":"%s","distro":"debian","release":"%s"}\n' "$IMAGE_VERSION" "$VERSION_CODENAME" > /etc/dextop/prebuilt-image.json
echo guest-build-complete
