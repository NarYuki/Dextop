#!/usr/bin/env python3
"""Probe host mappings needed by EAC without modifying a game or its checks."""
import ctypes
import errno
import os


def main():
    libc = ctypes.CDLL(None, use_errno=True)
    libc.mmap.argtypes = [ctypes.c_void_p, ctypes.c_size_t, ctypes.c_int,
                          ctypes.c_int, ctypes.c_int, ctypes.c_long]
    libc.mmap.restype = ctypes.c_void_p
    libc.munmap.argtypes = [ctypes.c_void_p, ctypes.c_size_t]
    page = os.sysconf('SC_PAGE_SIZE')
    failed = ctypes.c_void_p(-1).value
    # MAP_FIXED_NOREPLACE never replaces an existing mapping. Only one page is
    # held at a time; PROT_NONE separates address availability from W^X policy.
    for address in [0x4000000000, 0x8000000000, 0x400000000000] + [
            0x700100000000 + (n << 40) for n in range(10)]:
        ctypes.set_errno(0)
        result = libc.mmap(address, page, 0, 0x100022, -1, 0)
        error = ctypes.get_errno()
        if result == failed:
            print(f'{address:#014x}: FAILED {errno.errorcode.get(error, error)} '
                  f'({os.strerror(error)})')
        else:
            print(f'{address:#014x}: mapped {result:#014x}' +
                  (' (hint only; unsupported NOREPLACE)' if result != address else ''))
            libc.munmap(result, page)


if __name__ == '__main__':
    main()
