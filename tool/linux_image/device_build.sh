#!/system/bin/sh
# Runs on the Galaxy as the adb shell user. Usage: device_build.sh <native-lib-dir> <version> <tag>
W=/data/local/tmp/dextop-image-build
LIB="$1"; VERSION="$2"; TAG="$3"
cd "$W" || exit 1
mkdir -p tmp shm empty sources rootfs
: > status

guest() {
    PROOT_LOADER="$LIB/libproot_loader.so" PROOT_TMP_DIR="$W/tmp" "$LIB/libproot.so" \
        --kill-on-exit --link2symlink -0 -r "$W/rootfs" -w /root \
        -b /dev -b /proc -b /sys -b /dev/urandom:/dev/random -b /proc/self/fd:/dev/fd \
        -b "$W/shm:/dev/shm" -b "$W/empty:/sys/fs/selinux" -b "$W/empty:/sys/bus/pci/devices" \
        -b "$LIB:/opt/dextop/lib" -b "$W/input:/opt/dextop-image-input" -b "$W/sources:/var/tmp/dextop-sources" \
        /usr/bin/env -i HOME=/root USER=root LOGNAME=root TERM=xterm-256color LANG=C.UTF-8 TMPDIR=/tmp \
        PATH=/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin DEXTOP_HARDLINK_COPY=1 NO_AT_BRIDGE=1 \
        LD_PRELOAD="/opt/dextop/lib/libdextop_androidproc.so:/opt/dextop/lib/libdextop_unixsock.so" \
        DEXTOP_SOCKET_NS=dextop-imagebuild \
        /bin/sh "$@"
}

if [ ! -e rootfs/bin ] && [ ! -e rootfs/usr/bin/sh ]; then
    # /data/local/tmp refuses hard links: hardlinks.txt (made from base.tar) lists them to copy instead.
    tar -xf base.tar -C rootfs 2>extract.err; echo "extract $?" >> status
    (cd rootfs && while IFS="$(printf '\t')" read -r link target; do
        [ -e "$link" ] || cp -p "$target" "$link"
    done < ../hardlinks.txt)
fi
guest /opt/dextop-image-input/guest_build.sh "$VERSION"
code=$?; echo "build $code" >> status
[ "$code" = 0 ] || { echo failed >> status; exit "$code"; }
guest /opt/dextop-image-input/guest_finish.sh "$VERSION" "$TAG"
code=$?; echo "finish $code" >> status
[ "$code" = 0 ] || { echo failed >> status; exit "$code"; }
# proot's link emulation leaves symlinks to this directory; the image must not depend on it.
(cd rootfs && find . -type l | while read -r link; do
    case "$(readlink "$link")" in
        "$W"/*) real="$(readlink -f "$link")"; cp -p "$real" "$link.dextop-new" && mv -f "$link.dextop-new" "$link" ;;
    esac
done; find . -name '.l2s.*' -delete)
echo done >> status
