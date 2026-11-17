"""Run inside guarded PRoot with python3; reads only and sends no transaction."""
import ctypes
import errno
import os
import pathlib
import sys

libc = ctypes.CDLL(None, use_errno=True)
libc.syscall.restype = ctypes.c_long


def denied(label, result):
    error = ctypes.get_errno()
    assert result == -1 and error in (errno.EPERM, errno.EACCES), (label, result, error)
    print("PASS", label, flush=True)


def binder():
    empty = (ctypes.c_uint64 * 6)()
    for path in ("/dev/binder", "/dev/hwbinder", "/dev/null"):
        fd = os.open(path, os.O_RDWR)
        try:
            for command in (0xC0306201, 0xC0186201, 0x40046207, 0x40046210, 0x6200, 0xFFFFFFFFC0306201):
                ctypes.set_errno(0)
                # Only empty WRITE_READ calls use the real device. Test other
                # requests with an invalid fd so a broken guard has no effects.
                target = fd if command in (0xC0306201, 0xC0186201, 0xFFFFFFFFC0306201) else -1
                denied(f"{path} ioctl {command:x}", libc.syscall(29, target, ctypes.c_ulong(command), ctypes.byref(empty)))
        finally:
            os.close(fd)
    ctypes.set_errno(0)
    assert libc.syscall(29, -1, 0x5401, ctypes.byref(empty)) == -1
    assert ctypes.get_errno() == errno.EBADF
    print("PASS ordinary ioctl preserved", flush=True)


binder()
if len(sys.argv) == 1:
    # TracerPid identifies the real PRoot even with guest parent emulation.
    status = pathlib.Path("/proc/self/status").read_text()
    tracer = int(next(line.split()[1] for line in status.splitlines() if line.startswith("TracerPid:")))
    assert tracer > 0
    for name in ("mem", "maps", "environ"):
        try:
            fd = os.open(f"/proc/{tracer}/{name}", os.O_RDONLY)
        except OSError as error:
            assert error.errno in (errno.EPERM, errno.EACCES), (name, error)
            print("PASS tracer", name, "protected", flush=True)
        else:
            os.close(fd)
            raise AssertionError(f"tracer {name} accessible")

    class IOVec(ctypes.Structure):
        _fields_ = [("base", ctypes.c_void_p), ("length", ctypes.c_size_t)]

    buf = ctypes.create_string_buffer(1)
    local = IOVec(ctypes.cast(buf, ctypes.c_void_p), 1)
    remote = IOVec(1, 1)
    for nr, name in ((270, "process_vm_readv"), (271, "process_vm_writev")):
        ctypes.set_errno(0)
        denied(name, libc.syscall(nr, tracer, ctypes.byref(local), 1, ctypes.byref(remote), 1, 0))
    pid = os.fork()
    if pid == 0:
        os.execv(sys.executable, [sys.executable, __file__, "child"])
    assert os.waitstatus_to_exitcode(os.waitpid(pid, 0)[1]) == 0
    print("PASS guest fork + exec inheritance and tracer protection", flush=True)
