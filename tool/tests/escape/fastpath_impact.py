#!/usr/bin/env python3
"""Impact of the unprotected fast-path stub (run INSIDE a guest). Writes only a marker it removes."""
import ctypes, mmap, os, struct, sys
G = 0xFFFF0001C
for line in open("/proc/self/maps"):
    if line.startswith("ffff00000-"): break
else: print("INFO    impact: stub page absent"); sys.exit(0)
code = ctypes.string_at(0xFFFF00000, 0x1000)
G = 0xFFFF00000 + code.index(struct.pack("<II", 0xD4000001, 0xD65F03C0))
page = mmap.mmap(-1, 4096, prot=mmap.PROT_READ | mmap.PROT_WRITE | mmap.PROT_EXEC)
page.write(struct.pack("<II", 0xAA0603E8, 0xD61F00E0))
fn = ctypes.CFUNCTYPE(ctypes.c_long, *([ctypes.c_long] * 8))(ctypes.addressof(ctypes.c_char.from_buffer(page)))
def sc(nr, *a): a = list(a) + [0] * (6 - len(a)); return fn(*(a[:6] + [nr, G]))
def cstr(s): b = ctypes.create_string_buffer(s.encode()); keep.append(b); return ctypes.addressof(b)
keep = []
out = lambda v, i, d: print("%-7s %s: %s" % (v, i, d), flush=True)
# 1. Write on the Android side (marker in the shell scratch directory, removed at once).
marker = "/data/local/tmp/.dextop-escape-marker-%d" % os.getpid()
fd = sc(56, -100, cstr(marker), 0o1 | 0o100 | 0o2000000, 0o600)
if fd >= 0:
    sc(64, fd, cstr("escape\n"), 7); sc(57, fd)
    st = os.path.exists(marker)  # translated view cannot see it
    out("ESCAPE", "impact-write", "created %s on Android (translated guest view sees it: %s)" % (marker, st))
    out("INFO", "impact-cleanup", "unlink -> %d" % sc(35, -100, cstr(marker), 0))
else: out("BLOCKED", "impact-write", "%s -> %s" % (marker, os.strerror(-fd)))
fd = sc(56, -100, cstr("/sdcard/Download"), 0o40000 | 0o2000000, 0)
out("ESCAPE" if fd >= 0 else "BLOCKED", "impact-shared-storage", "open /sdcard/Download -> %s" % (fd if fd >= 0 else os.strerror(-fd)))
if fd >= 0: sc(57, fd)
# 2. Run an Android binary untraced: the child execs /system/bin/sh via the stub (no PRoot translation).
r, w = os.pipe()
pid = os.fork()
if pid == 0:
    os.dup2(w, 1); os.dup2(w, 2)
    argv = (ctypes.c_char_p * 4)(b"/system/bin/sh", b"-c",
        b"echo uid=$(id -u) ctx=$(cat /proc/self/attr/current); ls /data/local/tmp | head -3; service check activity 2>&1 | head -1; getprop ro.product.model", None)
    envp = (ctypes.c_char_p * 2)(b"PATH=/system/bin", None)
    sc(221, cstr("/system/bin/sh"), ctypes.addressof(argv), ctypes.addressof(envp))
    os._exit(99)
os.close(w); data = os.read(r, 4096).decode(errors="replace"); os.waitpid(pid, 0)
out("ESCAPE" if "uid=" in data else "BLOCKED", "impact-exec-android", data.strip().replace("\n", " | ") or "exec failed")
# 3. Binder through the stub: the independent Binder guard (separate seccomp filter) must still refuse.
fd = sc(56, -100, cstr("/dev/binder"), 0o2 | 0o2000000, 0)
if fd >= 0:
    ver = ctypes.c_int32(0)
    r = sc(29, fd, 0xC0046209, ctypes.addressof(ver))  # BINDER_VERSION
    out("ESCAPE" if r == 0 else "BLOCKED", "impact-binder", "BINDER_VERSION via stub -> %s" % ("OK v%d" % ver.value if r == 0 else os.strerror(-r)))
    sc(57, fd)
else: out("BLOCKED", "impact-binder", "open /dev/binder -> %s" % os.strerror(-fd))
# 4. Shizuku starter and other shell-owned executables are writable by the shell UID.
for p in ("/data/local/tmp/shizuku_starter", "/data/local/tmp/shizuku"):
    # Real write access: open O_WRONLY|O_APPEND and close at once (nothing written, nothing truncated).
    # faccessat would only report permission bits, which Landlock does not change.
    fd = sc(56, -100, cstr(p), 0o1 | 0o2000 | 0o2000000, 0)
    if fd >= 0: sc(57, fd)
    out("ESCAPE" if fd >= 0 else "BLOCKED", "impact-shizuku", "%s open for writing -> %s" % (p, "OPENED" if fd >= 0 else os.strerror(-fd)))
