# Android Binder restriction for PRoot

## Assessment and implementation

Blocking Binder ioctls with seccomp is effective for preventing **direct Binder
driver IPC from the restricted process tree**. Hiding `/dev/binder` alone is
insufficient: paths, inherited descriptors and descriptor passing are not a
boundary. The Android 16 / Linux 6.12 Binder driver dispatches IPC through
`binder_ioctl`; its file operations expose ioctl/compat_ioctl, mmap and poll,
but no write or io_uring command handler providing an alternative IPC route.

The original proposal needed two corrections:

- PRoot's existing seccomp program is an optional tracing optimization. It can
  be disabled with `PROOT_NO_SECCOMP` and has experimental instruction-address
  exemptions. Security must not depend on that program being installed.
- One exact `BINDER_WRITE_READ` constant omits ABI variants and other Binder
  operations. The independent guard rejects the entire ioctl type `'b'`, using
  the low 32 bits consumed by the kernel. This includes 32/64-bit requests and
  requests with nonzero upper argument bits.

The Dextop arm64 CMake target now defines `DEXTOP_BINDER_GUARD`. At the beginning
of PRoot `main`, before argument/path processing, it sets `PR_SET_DUMPABLE=0`,
sets `PR_SET_NO_NEW_PRIVS=1`, and installs the mandatory Binder filter. Failure
terminates startup. The guard covers PRoot itself, its shared-memory helper and
every child, independently of the optional tracing filter and all fast paths.
Re-exec inherits the filter and startup reapplies memory protection. The filter
recognizes AArch64 and ARM compat syscall numbers and kills unknown ABIs.

After fork, only the guest child restores dumpability so PRoot can trace it and
read/write its memory normally. The parent tracer remains non-dumpable. Restoring
the child's dumpability does not remove its inherited seccomp filter. This avoids
breaking PRoot's `PTRACE_TRACEME` and `process_vm_*` operations.

This is not complete shell-UID isolation. Other accessible shell processes,
shared files, inherited resources, sockets and authorized brokers remain outside
this specific restriction. Same-UID signal delivery is not prevented. Kernel or
PRoot vulnerabilities are not eliminated; `dumpable=0` does not protect against
an actor with `CAP_SYS_PTRACE`, and setting it in `main` is not a claim of
protection during the dynamic linker's pre-main startup window. Existing
unrestricted processes are not retroactively restricted. Binder open/mmap/poll
remain possible, so the change does not remove all Binder driver attack surface.
Apps intentionally using Binder inside the Linux guest will no longer work.

## Device validation

Date: 2026-11-12 (Asia/Tokyo). Galaxy device, shell UID 2000, Android vendor kernel
`6.12.69-android16-6-pee899be-abogkiS942ZSCU1BZI9-4k`.
Tests use a disposable directory and isolated HOME/TMPDIR/XDG directories;
the installed APK and its running container services are not replaced.
The disposable device directory was removed after validation; local evidence is retained.

- The production BPF array passes 81,920 interpreted cases: AArch64, ARM compat,
  unknown ABIs, unrelated syscall numbers, all ioctl type bytes, request size/high
  bits and normal/fast-path instruction addresses.
- An empty native `BINDER_WRITE_READ` succeeds before restriction and fails with
  `EPERM` afterward, including a descriptor opened before filter installation.
- Native tests exercise `/dev/binder`, `/proc/self/root/dev/binder`, `/dev/hwbinder`
  and `/dev/null`; `/dev/vndbinder` is already denied by the device and is skipped.
  Empty WRITE_READ calls are the only real Binder operations attempted; tests
  for state-changing commands use an invalid descriptor.
- Fork + exec retain denial. Unrelated terminal ioctls retain their kernel
  behavior rather than returning the guard's `EPERM`.
- Actual PRoot guests pass Binder tests and tracer memory protection tests with
  normal tracing, `PROOT_NO_SECCOMP=1`, and failed-Landlock fallback/re-exec.
- Native guest probes issue ioctl from a mapped fast-path trampoline and remain
  denied in normal, unprotected fast-path and Landlock modes. These targeted
  tests do not claim to revalidate the entire experimental fast-path feature.
- An external same-UID process can open the baseline tracer's `/proc/PID/mem`.
  With protection it cannot open tracer mem/maps/environ/root, use
  `process_vm_readv/writev`, or `PTRACE_SEIZE` it. Guest probes also confirm denied
  mem/maps/environ and `process_vm_*`, while ordinary guest execution continues.
- A test-only preload injects failure into each startup protection operation
  (`PR_SET_DUMPABLE`, `PR_SET_NO_NEW_PRIVS`, `PR_SET_SECCOMP`). Each exits with
  status 1 before the guest echo marker can run.
- Android arm64 native PRoot and loader builds succeed. The device is arm64-only:
  AArch32 is covered by BPF interpretation, not by executing a 32-bit guest.

## Application comparison: completed

All **201/201** named checks ran on both builds with the same harness and binds.
Both produced **176 OK, 23 NG and 2 NG?**, with **zero per-check status changes**.
The 25 existing failures/uncertain checks include unavailable hardware/capabilities,
missing desktop session/password credentials, unsupported filesystem operations,
and two TUI output heuristics. They are not counted as passing tests. In
particular, the gdb command exits 1 because it requests a backtrace after its
inferior has exited normally on both builds. This comparison establishes no new
failure in this suite; it is not exhaustive GUI or security validation.

`temp/binder-guard/comparison.json` contains the machine-readable counts and
per-check status difference list (empty).

## Reproduction artifacts

Maintained probes:

- `tool/tests/linux_binder_guard_check.c`: NDK static native kernel/BPF tests;
  `guest` runs denial/trampoline checks inside PRoot; `inspect PID` checks an
  existing protected tracer from outside PRoot.
- `tool/tests/linux_binder_guest_check.py`: Linux Python guest tests.
- `tool/tests/linux_binder_guard_fail.c`: build as an NDK shared library, preload
  only for tests and set `DEXTOP_TEST_FAIL_PRCTL` to 4, 38 or 22.
- `tool/tests/linux_apps_smoke.sh`: existing 201 named CLI/TUI/runtime checks.

Example native build (substitute the installed NDK path):

```sh
$NDK/toolchains/llvm/prebuilt/darwin-x86_64/bin/aarch64-linux-android30-clang \
  -static -Wall -Wextra -Werror -Ithird_party/proot/src \
  tool/tests/linux_binder_guard_check.c -o check
```

Detailed local logs, exact launch scripts and the same-source baseline build
commands are in ignored `temp/binder-guard/`. The baseline differs only by omitting
the new compile definition for the two affected translation units, keeping all
pre-existing workspace changes in both builds. No unrelated changes are reverted.

## Primary references

- [Linux seccomp filter API](https://www.kernel.org/doc/html/next/userspace-api/seccomp_filter.html)
- [Android 16 / Linux 6.12 Binder implementation](https://android.googlesource.com/kernel/common/+/refs/heads/android16-6.12/drivers/android/binder.c)
- [Binder ioctl UAPI](https://github.com/torvalds/linux/blob/master/include/uapi/linux/android/binder.h)
- [PR_SET_DUMPABLE](https://man7.org/linux/man-pages/man2/PR_SET_DUMPABLE.2const.html)
