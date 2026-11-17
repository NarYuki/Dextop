#!/bin/bash
# Dextop Linux container -> Android escape suite. Run INSIDE a guest (privileged or Android mode).
#   bash linux_escape_suite.sh <escape_probe binary> [label=pid ...]
# label=pid names Android-side processes (outside the guest) to probe with ptrace/memory reads.
# Non-destructive: no kills, no writes to Android files/processes. The only Android-side write
# attempted is a harmless debug property (debug.dextop.sectest), reported if it succeeds.
# Output lines: ESCAPE | BLOCKED | EXPOSED | INFO  <id>: detail
PROBE=${1:?usage: $0 <escape_probe> [label=pid ...]}; shift
export LANG=C
r() { printf '%-7s %s: %s\n' "$1" "$2" "$3"; }

echo "== identity"
r INFO id "$(id)"
r INFO kernel "$(uname -srm)"
r INFO selinux "$(cat /proc/self/attr/current 2>/dev/null | tr -d '\0')"
grep -E '^(CapInh|CapPrm|CapEff|CapBnd|CapAmb|NoNewPrivs|Seccomp|Seccomp_filters|TracerPid)' /proc/self/status | while read -r k v; do r INFO status "$k $v"; done

echo "== native probe"
chmod +x "$PROBE" 2>/dev/null
"$PROBE" "$@" 2>&1

echo "== Android storage and app data (guest view)"
for p in /sdcard /storage/emulated/0 /storage/self/primary /mnt/user/0 /data/media/0 /data/data /data/user/0 /data/user_de/0 \
         /data/app /data/local/tmp /data/system /data/misc /data/adb /mnt/sdcard /system /vendor /apex /odm /product; do
  if [ -e "$p" ]; then
    n=$(ls -A "$p" 2>/dev/null | head -50 | wc -l)
    if [ "$n" -gt 0 ]; then r EXPOSED fs-android-dir "$p visible ($n entries: $(ls -A "$p" 2>/dev/null | head -5 | tr '\n' ' '))"
    else r BLOCKED fs-android-dir "$p exists but empty/unlistable"; fi
  else r BLOCKED fs-android-dir "$p absent"; fi
done
# Host paths of both modes' private data: must not be reachable by their host spelling.
# The container's own rootfs subtrees are also bound at their host spelling (usr, opt, root, home),
# so only paths outside them count.
for p in /data/user/0/moe.n4tsu.dextop/shared_prefs /data/user/0/moe.n4tsu.gpdextop/shared_prefs \
         /data/data/moe.n4tsu.gpdextop/files /data/local/tmp/dextop/linux/sys-standins /data/local/tmp/dextop/linux/folders; do
  if ls "$p" >/dev/null 2>&1; then r ESCAPE fs-host-private "$p listable: $(ls "$p" 2>/dev/null | head -3 | tr '\n' ' ')"
  else r BLOCKED fs-host-private "$p not reachable"; fi
done

echo "== bound host directories (write test)"
grep -hE ' /opt/dextop| /dev | /sys | /proc ' /proc/self/mountinfo 2>/dev/null | head -0
# Only /opt/dextop/lib is a host bind (the app's native library directory); /opt/dextop-gpu and
# /opt/dextop-mesa live in the guest's own rootfs.
for d in /opt/dextop/lib; do
  [ -d "$d" ] || continue
  t="$d/.dextop-escape-write-$$"
  if (echo x > "$t") 2>/dev/null; then rm -f "$t"; r ESCAPE fs-bind-write "$d writable (host app/native dir)"; else r BLOCKED fs-bind-write "$d not writable"; fi
done

echo "== device nodes"
dev() { # id path mode(r|w)
  local id=$1 p=$2 m=$3
  [ -e "$p" ] || { r BLOCKED "$id" "$p absent"; return; }
  if [ "$m" = r ]; then timeout 2 dd if="$p" of=/dev/null bs=1 count=1 iflag=nonblock 2>/dev/null; rc=$?
  else timeout 2 bash -c ": > '$p'" 2>/dev/null; rc=$?; fi
  case $rc in 0|124) r EXPOSED "$id" "$p opened for $m";; *) r BLOCKED "$id" "$p not openable for $m";; esac
}
for e in /dev/input/event*; do [ -e "$e" ] && dev dev-input "$e" r; done
dev dev-uhid /dev/uhid w
dev dev-kmsg /dev/kmsg r
dev dev-mem /dev/mem r
dev dev-kgsl /dev/kgsl-3d0 r
for b in /dev/block/sda /dev/block/by-name/userdata /dev/block/dm-0 /dev/block/mmcblk0; do dev dev-block "$b" r; done
for v in /dev/video0 /dev/snd/pcmC0D0c /dev/graphics/fb0 /dev/ion /dev/dma_heap/system /dev/tun /dev/fuse /dev/hw_random /dev/qseecom /dev/ashmem; do dev dev-other "$v" r; done
if [ -r /dev/__properties__/property_info ]; then r EXPOSED dev-properties "/dev/__properties__/property_info readable (Android system properties)"; else r BLOCKED dev-properties "Android property areas not readable"; fi

echo "== kernel and sysfs write surfaces"
for w in /proc/sys/kernel/hostname /proc/sys/vm/drop_caches /sys/power/wake_lock /sys/power/state /sys/class/leds/lcd-backlight/brightness \
         /sys/class/backlight/panel0-backlight/brightness /proc/sysrq-trigger /sys/kernel/debug/tracing/tracing_on; do
  if [ -w "$w" ]; then r EXPOSED sys-writable "$w writable (not written)"; else r BLOCKED sys-writable "$w"; fi
done
command -v python3 >/dev/null && python3 - <<'PY'
import ctypes, os, errno
libc = ctypes.CDLL(None, use_errno=True)
def sc(name, nr, *a):
    r = libc.syscall(nr, *a); e = ctypes.get_errno()
    print(("EXPOSED" if r >= 0 else "BLOCKED").ljust(7), "kernel-%s:" % name, "ok" if r >= 0 else os.strerror(e))
    if r >= 0 and name not in ("unshare-user",):
        try: os.close(r)
        except OSError: pass
sc("userfaultfd", 282, 0)
attr = ctypes.create_string_buffer(128); ctypes.memmove(attr, (1).to_bytes(4,'little') + (128).to_bytes(4,'little'), 8)
sc("perf_event_open", 241, attr, 0, -1, -1, 0)
sc("bpf-map", 280, 0, ctypes.create_string_buffer(64), 64)
sc("keyctl-join", 219, 1, 0)
pid = os.fork()
if pid == 0:
    r = libc.unshare(0x10000000); e = ctypes.get_errno()
    print(("EXPOSED" if r == 0 else "BLOCKED").ljust(7), "kernel-unshare-user:", "user namespace created" if r == 0 else os.strerror(e)); os._exit(0)
os.waitpid(pid, 0)
PY

echo "== abstract and filesystem unix sockets visible in /proc/net/unix"
if [ -r /proc/net/unix ]; then
  awk 'NR>1 && $8 ~ /^@/ {print substr($8,2)}' /proc/net/unix | sort -u > /tmp/.dextop-abstract.$$
  r INFO unix-abstract "$(wc -l < /tmp/.dextop-abstract.$$) abstract names: $(grep -vE '^[0-9a-f]{5,}$' /tmp/.dextop-abstract.$$ | head -40 | tr '\n' ' ')"
  command -v python3 >/dev/null && python3 - /tmp/.dextop-abstract.$$ <<'PY'
import socket, sys
for name in open(sys.argv[1]).read().split():
    if all(c in '0123456789abcdef' for c in name) and len(name) > 5: continue
    for kind in (socket.SOCK_STREAM, socket.SOCK_SEQPACKET):
        s = socket.socket(socket.AF_UNIX, kind); s.settimeout(1)
        try:
            s.connect('\0' + name); s.settimeout(0.5)
            try: banner = s.recv(64)
            except Exception: banner = b''
            print("INFO    unix-abstract-connect: @%s accepts (%s) banner=%r" % (name, 'stream' if kind == socket.SOCK_STREAM else 'seqpacket', banner[:40])); break
        except OSError as e:
            if e.errno == 91: continue
            print("BLOCKED unix-abstract-connect: @%s %s" % (name, e.strerror)); break
        finally: s.close()
PY
  rm -f /tmp/.dextop-abstract.$$
else r BLOCKED unix-abstract "/proc/net/unix unreadable"; fi

echo "== loopback TCP services (Android side included)"
# The full parallel scan once coincided with a kernel panic under the Landlock fast path;
# DEXTOP_ESCAPE_SCAN=quick probes only known service ports there.
export DEXTOP_ESCAPE_SCAN=${DEXTOP_ESCAPE_SCAN:-full}
command -v python3 >/dev/null && python3 - <<'PY'
import socket, concurrent.futures as cf
def probe(port):
    s = socket.socket(); s.settimeout(0.3)
    try:
        s.connect(("127.0.0.1", port)); s.settimeout(0.6)
        try: b = s.recv(48)
        except Exception: b = b""
        return port, b
    except Exception: return None
    finally: s.close()
import os
quick = os.environ.get("DEXTOP_ESCAPE_SCAN") == "quick"
ports = [2222, 2223, 5900, 8585, 8631, 5037, 4713, 9222, 61209] + list(range(30000, 30010)) if quick else range(1, 65536)
with cf.ThreadPoolExecutor(8 if quick else 256) as ex:
    open_ports = [r for r in ex.map(probe, ports) if r]
if quick: print("INFO    tcp-loopback: quick scan of known service ports only")
print("INFO    tcp-loopback: %d listening ports: %s" % (len(open_ports), " ".join(str(p) for p, _ in open_ports)))
for p, b in open_ports: print("INFO    tcp-port: %d banner=%r" % (p, b[:40]))
# ADB over TCP (wireless debugging): an unauthenticated CNXN must not be accepted.
import struct
def adb_msg(cmd, a0, a1, data):
    c = struct.unpack("<I", cmd)[0]
    return struct.pack("<6I", c, a0, a1, len(data), sum(data) & 0xffffffff, c ^ 0xffffffff) + data
for p, _ in open_ports:
    s = socket.socket(); s.settimeout(1.5)
    try:
        s.connect(("127.0.0.1", p)); s.sendall(adb_msg(b"CNXN", 0x01000001, 256 * 1024, b"host::\0")); h = s.recv(24)
        if len(h) == 24 and h[:4] in (b"AUTH", b"CNXN", b"STLS"):
            verdict = "ESCAPE" if h[:4] == b"CNXN" else "BLOCKED"
            print("%-7s adb-tcp: port %d answers ADB %s%s" % (verdict, p, h[:4].decode(), " (no authentication!)" if verdict == "ESCAPE" else " (authentication/TLS required)"))
    except Exception: pass
    finally: s.close()
PY

echo "== properties write (harmless debug property)"
if command -v python3 >/dev/null; then python3 - <<'PY'
import socket, struct
name, value = b"debug.dextop.sectest", b"1"
s = socket.socket(socket.AF_UNIX, socket.SOCK_STREAM)
try:
    s.connect("/dev/socket/property_service")
    s.sendall(struct.pack("<I", 0x20001) + struct.pack("<I", len(name)) + name + struct.pack("<I", len(value)) + value)
    s.settimeout(2); r = s.recv(4)
    code = struct.unpack("<I", r)[0] if len(r) == 4 else -1
    print(("EXPOSED" if code == 0 else "BLOCKED").ljust(7), "android-setprop: debug.dextop.sectest ->", "SET (verify with getprop)" if code == 0 else "result %#x" % code)
except OSError as e: print("BLOCKED android-setprop:", e.strerror)
PY
fi

echo "== Android executables reachable from the guest"
for x in /system/bin/sh /system/bin/app_process64 /system/bin/cmd /system/bin/am /system/bin/pm /system/bin/settings /system/bin/linker64 /system/xbin/su /sbin/su; do
  if [ -x "$x" ]; then r ESCAPE exec-android "$x executable from the guest"; else r BLOCKED exec-android "$x"; fi
done
for x in /opt/dextop/lib/*.so; do
  [ -f "$x" ] || continue
  case "$(head -c 20 "$x" | od -An -tx1 | tr -d ' \n')" in 7f454c46*) ;; *) continue;; esac
done
r INFO exec-app-libs "$(ls /opt/dextop/lib 2>/dev/null | wc -l) app native libraries visible read-only at /opt/dextop/lib"

echo "== privilege escalation inside the guest"
for b in sudo su pkexec; do command -v $b >/dev/null && r INFO "guest-$b" "$(command -v $b) present"; done
find / -xdev -perm -4000 -type f 2>/dev/null | head -5 | while read -r f; do r INFO guest-setuid "$f"; done
echo "== done"
