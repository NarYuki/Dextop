#!/usr/bin/env python3
"""Fast-path bypass probe (run INSIDE a guest started with the PRoot fast path).

The fast path exempts syscalls issued from the preloaded translator library's own
`svc #0` instruction from ptrace. A guest program can branch to that instruction with
arbitrary registers, so the syscall reaches the kernel untranslated: only Landlock
(protected mode) stands between it and the host filesystem. Read-only checks.
"""
import ctypes, mmap, os, struct, sys

def out(verdict, ident, detail):
    print("%-7s %s: %s" % (verdict, ident, detail), flush=True)

# Candidate exempt code: the protected-mode translator library and the unprotected-mode stub page
# (PRoot exempts syscalls whose instruction pointer lies in 0xffff00000-0xffff01000).
regions = []
for line in open("/proc/self/maps"):
    f = line.split()
    if "x" not in f[1]: continue
    start, end = (int(x, 16) for x in f[0].split("-"))
    if "libblfastpath" in line or start == 0xFFFF00000:
        regions.append((start, end, f[-1] if len(f) > 5 else "[fast-path stub page]"))
if not regions:
    out("INFO", "fastpath-gadget", "no fast-path code mapped (fast path off for this process)")
    sys.exit(0)
gadget = None
for start, end, name in regions:
    code = ctypes.string_at(start, end - start)
    words = [struct.unpack_from("<I", code, i)[0] for i in range(0, len(code) - 3, 4)]
    svcs = [i for i, w in enumerate(words) if w == 0xD4000001]
    out("INFO", "fastpath-gadget", "%s %#x-%#x: %d svc, next insns %s" % (name, start, end, len(svcs),
        " ".join("%08x" % words[i + 1] for i in svcs[:4] if i + 1 < len(words))))
    for i in svcs:
        if i + 1 < len(words) and words[i + 1] == 0xD65F03C0:   # svc #0 ; ret
            gadget = start + 4 * i; lib = name; break
    if gadget: break
if gadget is None:
    out("INFO", "fastpath-gadget", "no svc;ret gadget found"); sys.exit(0)
out("INFO", "fastpath-gadget", "svc;ret at %#x in %s" % (gadget, lib))

# Trampoline: mov x8, x6 ; br x7   (x0-x5 syscall args, x6 = number, x7 = gadget; ret returns to us)
page = mmap.mmap(-1, 4096, prot=mmap.PROT_READ | mmap.PROT_WRITE | mmap.PROT_EXEC)
page.write(struct.pack("<II", 0xAA0603E8, 0xD61F00E0))
addr = ctypes.addressof(ctypes.c_char.from_buffer(page))
fn = ctypes.CFUNCTYPE(ctypes.c_long, *([ctypes.c_long] * 8))(addr)

def sys_gadget(nr, *args):
    a = list(args) + [0] * (6 - len(args))
    return fn(*(a[:6] + [nr, gadget]))

SYS_openat, SYS_read, SYS_close, SYS_getdents64 = 56, 63, 57, 61
AT_FDCWD = -100

def gopen(path, flags=0):
    b = ctypes.create_string_buffer(path.encode())
    return sys_gadget(SYS_openat, AT_FDCWD, ctypes.addressof(b), flags | 0o2000000, 0)

def gread(fd, n=256):
    buf = ctypes.create_string_buffer(n)
    r = sys_gadget(SYS_read, fd, ctypes.addressof(buf), n)
    return buf.raw[:r] if r > 0 else b""

def glist(path):
    fd = gopen(path, 0o40000)
    if fd < 0: return None, fd
    buf = ctypes.create_string_buffer(8192); n = sys_gadget(SYS_getdents64, fd, ctypes.addressof(buf), 8192)
    names, i = [], 0
    while 0 <= i < n:
        reclen = struct.unpack_from("<H", buf.raw, i + 16)[0]
        names.append(buf.raw[i + 19:i + reclen].split(b"\0")[0].decode(errors="replace")); i += reclen
    sys_gadget(SYS_close, fd)
    return [x for x in names if x not in (".", "..")], 0

# Definitive: is the gadget syscall translated? Android's / has system/vendor/apex, the guest's has usr/etc.
root, err = glist("/")
if root is None: out("BLOCKED", "fastpath-root-view", "openat / -> %s" % os.strerror(-err))
else:
    android = {"system", "vendor", "apex"} <= set(root)
    out("ESCAPE" if android else "BLOCKED", "fastpath-root-view", "%s view of /: %s" % ("ANDROID (untranslated)" if android else "guest (translated)", " ".join(sorted(root)[:12])))
probe = {
    "/system/build.prop": b"ro.",
    "/vendor/build.prop": b"ro.",
    "/data/system/packages.list": b"",
    "/data/local/tmp": None,                         # privileged host scratch directory
    "/data/user/0/moe.n4tsu.gpdextop/shared_prefs": None,
    "/data/user/0/moe.n4tsu.dextop/shared_prefs": None,
    "/sdcard/Download": None,
    "/storage/emulated/0": None,
    "/proc/1/root/system/build.prop": b"ro.",
}
for path, marker in probe.items():
    directory = marker is None
    fd = gopen(path, 0o40000 if directory else 0)      # O_DIRECTORY (aarch64)
    if fd < 0:
        out("BLOCKED", "fastpath-host-open", "%s -> %s" % (path, os.strerror(-fd)))
        continue
    if directory:
        buf = ctypes.create_string_buffer(4096)
        n = sys_gadget(SYS_getdents64, fd, ctypes.addressof(buf), 4096)
        names, i = [], 0
        while 0 <= i < n:
            reclen = struct.unpack_from("<H", buf.raw, i + 16)[0]
            names.append(buf.raw[i + 19:i + reclen].split(b"\0")[0].decode(errors="replace"))
            i += reclen
        sys_gadget(SYS_close, fd)
        names = [x for x in names if x not in (".", "..")]
        out("ESCAPE" if n > 0 else "BLOCKED", "fastpath-host-open", "%s listed untranslated: %s" % (path, " ".join(names[:6])))
    else:
        data = gread(fd); sys_gadget(SYS_close, fd)
        host = marker in data if marker else bool(data)
        out("ESCAPE" if host else "INFO", "fastpath-host-open", "%s opened untranslated (%s)" % (path, "HOST content" if host else "no host marker"))
