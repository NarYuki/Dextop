# Landlock experiment: device validation, 2026-11-11

> Historical validation results. The later exact-build fallback guard was
> removed at the user's request; the current Fast mode UI offers protected and
> previous unprotected implementations without device-specific restrictions.
> See [current implementation and tests](linux-landlock-fastpath.md).

**Result: FAIL / not ready to enable on the tested Galaxy.**
The subsequent [investigation](linux-landlock-fastpath-investigation-2026-11-11.md)
adds independent kernel/filesystem controls and an exact-build fallback guard.
The setting remains off by default. No APK was installed and no application
preference was changed. Tests used separate PRoot binaries and isolated homes,
caches, runtime directories and temporary files. Existing desktop sessions were
left running.

Device: Galaxy RFGL22LEFGF, Android 17, Linux 6.12.69, shell UID 2000.
Guest: Debian 13 (trixie), rootfs c40c883b9055e. Runs reused the 37 actual
shell-session binds, native libraries and compatibility environment from the
running session, with an extra disposable fixture bind.

## Application/command comparison

The existing suite was recovered from the device's rootfs `/tmp/apps_test.sh`.
It contains **109 named CLI/TUI checks**, not a verified 110-GUI-app suite.
The maintained copy is `tool/tests/linux_apps_smoke.sh`. The `at` check now reads
the version instead of registering a real job; its lock fixture is isolated.
The same changes and environment isolation were applied in both modes.

| Run | OK | NG | Missing/reported unavailable | Result |
| --- | ---: | ---: | ---: | --- |
| Normal PRoot | 94 | 14 | 1 | baseline |
| Initial fast-path run | 94 | 14 | 1 | same named statuses as baseline |
| Repeat with canonical bind fix | 2 | 2 | 105 | invalidated by cascading EACCES |

The last row does **not** mean 105 packages were removed. Permission failures
made the suite's `command -v` checks incorrectly report installed tools as
unavailable. It also failed to open `/dev/null`, execute helpers and remove its
temporary directory. These failures invalidate a claim of stable compatibility.

The 14 baseline NG entries were top, glances, nvtop, iotop, iftop, nload,
nethogs, vim, nvim, watch, gdb, sudo, chroot and unshare. Neofetch was absent.
Some are harness artifacts: TUI programs terminated by the outer deadline return
137, and gdb's requested `bt` returns failure after `true` has already exited
normally. Other results reflect GPU/capture/namespace/sudo restrictions. The
suite also contains pipelines that can hide an upstream error; the 94 OK entries
are the original suite's classifications, not full functional verification.
Full GUI application behavior has not been tested.

## Confirmed bug fixed during testing

A bind destination containing a symlink was canonicalized by PRoot but the guest
library received the original raw mapping. Reading the canonical destination
could therefore return the hidden underlying file instead of the bind source.

`tracee/event.c` now exports PRoot's **final initialized binding table**, including
canonical targets and induced bindings, in the explicit guest `env -i` assignments.
The root also comes from the initialized PRoot binding. Only environment
assignments are rewritten, never guest-command arguments. Unrepresentable maps
fall back to normal PRoot.

The regression fixture bound a parent directory at `/ll-bind`, made its `alias`
a symlink to `real`, then bound a different source at `/ll-bind/alias`. Before the
fix, `/ll-bind/real/data` returned `parent`; normal PRoot returned `child`.
After the fix, both alias and canonical names returned `child`. Nested, file and
duplicate-destination binds also returned the expected source.

## Outstanding stability failure

A Python shell/cat spawn loop passed **500 iterations without Landlock**. With
Landlock, it failed with EACCES after about 227–239 iterations. The failure also
occurred with `PROOT_FP_OFF=1`, so it does not require accelerated opens. Forcing
ordinary fork instead of Python's posix_spawn/vfork did not resolve it.

A separate native probe links only the production Landlock setup; it has **no
PRoot, preload, trampoline, ptrace or path translation**. It repeatedly opens the
same existing file beneath an explicitly allowed rootfs directory, then closes
it, without writing anything.

| Native probe mode | Result |
| --- | --- |
| Plain | 1,000,000 opens passed; additional repeated controls passed |
| `no_new_privs` only | 1,000,000 opens passed; additional repeated controls passed |
| Landlock, repeat 1 | EACCES at iteration 658,884 |
| Landlock, repeat 2 | EACCES at iteration 28,696 |
| Landlock, repeat 3 | EACCES at iteration 526,763 |
| Final maintained probe | EACCES at iteration 279,347 |

The final probe confirmed that the policy root still had the same device/inode
and that five immediate retries remained denied. Keeping an O_PATH descriptor
open for the root did not reliably resolve the problem (two of three runs still
failed). Native opens in a disposable test directory and 1,000 static native execs
passed, so the failure is not universal across paths/workloads.

This isolates a problem under Landlock in this device/filesystem configuration,
but **does not establish whether the root cause is the vendor kernel, filesystem,
policy implementation or another interaction**. No permission broadening,
unconfined fast path or error-masking retry was introduced as a workaround.

## Passing targeted checks

- Landlock containment: allowed read/write succeeds; outside read/truncate and an
  inside symlink to the outside file are denied; fork inherits the policy.
- Production BPF prologue: 70,000 interpreted cases plus ordinary-prctl fallthrough.
- Guest wrapper regressions: **16/16 in normal and fast modes**, and again with
  the final bind fix. Includes symlinks, dot components, duplicate separators,
  NOFOLLOW, ENOENT, creation/EXCL, relative dirfd, O_PATH, four threads/1,000 reads,
  fork, exec, descriptor leaks and a single remaining descriptor slot.
- Nested/file/duplicate binds and symlink-canonicalized binds passed with the fix.
- Invalid policy re-execs normal PRoot, leaves the activation probe inactive,
  and passes the same 16 guest regressions.
- Nine targeted JVM tests passed (3 fast-path, 6 session compatibility), settings
  Flutter analysis passed, shared seven-locale generation check passed, and the
  updated native PRoot build passed. The native stress probe builds with
  `-Wall -Wextra -Werror`.

## Reproduce the native failure

Build `tool/tests/linux_landlock_read_stress.c` together with
`third_party/proot/src/tracee/landlock.c`, using NDK aarch64-linux-android30-clang
and `-Ithird_party/proot/src`. Push the executable to a disposable directory.
Run it against an existing, readable file beneath the selected allowed directory:

```sh
check plain /absolute/root /absolute/root/path/to/file 1000000
check nnp /absolute/root /absolute/root/path/to/file 1000000
check landlock /absolute/root /absolute/root/path/to/file 1000000
```

Exit 0 means all opens succeeded, 1 records a failed open with retry/inode
information, and 77 means policy setup was unavailable. This test reads only;
it does not change the selected file or root. The device reproducer used the
rootfs directory and `usr/lib/aarch64-linux-gnu/libc.so.6` beneath it.

Detailed local evidence is in ignored `temp/landlock/`: `apps-baseline.log`,
`apps-fast.log`, `apps-fast-fixed.log`, `native-read-repeat.log`,
`native-read-final.log`, `native-read-control.log`, `retest-final-positive.log`,
`retest-native.log` and `retest-jvm.log`. Disposable device fixtures were removed
after evidence collection.
