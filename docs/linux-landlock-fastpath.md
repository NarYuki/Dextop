# Experimental fast mode (2026-11-11)

**App information → Experimental features → Fast mode** uses a single right-hand dropdown with three choices:

- **Disabled** (`disabled` in the UI): ordinary PRoot, with neither acceleration
  library preloaded for the fast path.

- **Protected + fast** (`protected`): Landlock ABI >= 6 confines the child before
  the narrow openat fast path is installed. O_PATH, metadata, permission changes,
  sockets and watches remain traced. If Landlock cannot be applied, normal PRoot
  is used; it never silently switches to the unprotected mode.
- **Unprotected** (`unprotected`, "broad"): the previous DroidDeck `fastpath.c`
  library, now also confined by Landlock ABI >= 6 (since 2026-11-14). Its BPF
  prologue exempts only Landlock-restricted file operations and metadata-only
  lookups from the trampoline; everything else stays traced. If Landlock cannot
  be applied, normal PRoot is used. The name is kept for existing settings.

Fast mode defaults to Disabled. Existing
opt-ins retain their value via `experimental_linux_landlock_fast_path`; the new
mode key is `experimental_linux_fast_mode_kind`. Both are app-owned preferences,
published to a connected runtime and again on reconnect/mode changes. Changing
the settings alone does not start Linux. Existing apps must be restarted.
Android and shell execution modes can request acceleration. Package-management
fake-root sessions and host commands continue to use ordinary PRoot.

**There is no device/vendor/kernel-release blacklist.** The earlier exact Galaxy
kernel guard was removed at the user's explicit request. Protected mode tests
actual Landlock capability/application, not a device identity. The prior Galaxy
stress failures are recorded in the historical [investigation](linux-landlock-fastpath-investigation-2026-11-11.md);
removing the guard does not establish that those failures are fixed.

## Native routing

`PROOT_FASTPATH` is the per-launch activation key. `PROOT_FASTPATH_MODE` selects
`protected` or `unprotected`; an absent/unknown mode uses the protected path.
The child/parent readiness handshake gates both paths. Protected mode must
successfully apply its Landlock policy; unprotected mode skips Landlock. An
unavailable seccomp filter does not run the guest fast translator. Initialization
failure re-executes ordinary PRoot with activation and mode variables removed.

PRoot publishes its final canonical bind table into the explicit guest env -i
assignments, including induced/symlink-canonicalized binds. Library staging is
atomic and uses separate targets so switching modes does not overwrite a library
in an existing process:

| Mode | Guest library | Resource source |
| --- | --- | --- |
| Protected | `/usr/local/lib/libblfastpath.so` | `landlock_fastpath.c` |
| Unprotected | `/usr/local/lib/libblfastpath-unprotected.so` | original `fastpath.c` |

Protected mode uses the narrow BPF openat prologue and the synthetic prctl errno
activation probe. Only syscall 56, without O_PATH, from the AArch64 trampoline
range `[0xffff00000, 0xffff01000)` is exempted. The protected library walks
absolute symlink-free paths using directory descriptors, without a path cache.
The unprotected mode no longer rewrites TRACE statements into
trampoline-ALLOW: that rewriting let any guest code branch into the trampoline
and issue untranslated syscalls, reaching Android's filesystem as the shell
user (2026-11-14 escape test). It now uses `landlock_broad_filter` instead:
AArch64, IP in `[0xffff00000, 0xffff01000)` and one of openat 56, mkdirat 34,
unlinkat 35, symlinkat 36, linkat 37, renameat 38, renameat2 276, truncate 45
(Landlock-restricted) or newfstatat 79, statx 291, faccessat 48, readlinkat 78,
statfs 43 (metadata only). connect/bind/accept, execve, ptrace, signals,
chmod/chown/utimes, inotify and prctl are never exempted. It keeps the actual
chdir/fchdir handling and path cache. Its guest library activates through the
same synthetic prctl probe as the protected mode (Landlock hides the tracer's
environ), and routes chmod, utimes, inotify, accept and socket names to PRoot.
Residual: metadata of host paths (stat/readlink/access) remains observable
through the trampoline; file contents and changes are not.

Decision (2026-11-14): both fast-path modes stay Landlock-mandatory. A fast path
without Landlock is an escape route and must not be reintroduced. On Samsung KDP
kernels (the Galaxy test phone) Landlock use is suspected of delayed kernel
panics; that risk is accepted for the opt-in modes and is not a reason to drop
the confinement.
Neither mode installs a global fast-path preload or changes VPN/DNS settings.

## Current validation

- Nine widget tests: all three choices persist and synchronize, disabled
  migration, old enabled-preference migration, offline runtime handling,
  English/Japanese layouts at 320 pixels, and natural row growth when text is enlarged. There is no separate fast-mode switch.
- Experimental-feature rows share the original maximum-refresh-rate tile style:
  compact typography, horizontal padding and icon placement. Heights grow with
  their text; no fixed row height or title/summary truncation is imposed.
- Eleven targeted JVM tests: policy validation, mode-specific library staging,
  unknown mode defaults, and session compatibility.
- Production protected BPF interpreter: 70,000 cases plus ordinary prctl.
- Mocked Landlock syscall test: the former Galaxy-blocked release and another
  release both query capabilities; no uname/device-based rejection remains.
- Galaxy protected short test: cat reads GPL-3 successfully, SHA-256 matches an
  independent native read, counter reports one accelerated open. This is a short
  activation check, not a repeated-access stability result.
- Galaxy unprotected: 100 threaded reads, relative reads after chdir/fchdir,
  subprocess exec/content checks, actual positive acceleration counters, and
  VPN-preserving HTTPS wget (200, 1,198 bytes, matching SHA-256) pass.

Current logs are in ignored `temp/fast-mode/`. Historical measurements and their
limitations remain in the [initial validation](linux-landlock-fastpath-validation-2026-11-11.md)
and [VPN investigation](linux-wget-investigation-2026-11-11.md).

### Unprotected 109-case CLI/TUI run

With the restored unprotected path active on Galaxy and VPN retained, the
maintained suite classified **93 OK, 15 NG, 1 not installed** (109 total).
This is the existing suite's classification, not proof that all 93 applications
are fully functional: several checks use pipelines that can mask an upstream
failure. NG entries: top, glances, nvtop, iotop, iftop, nload, nethogs, tmux, vim,
nvim, watch, gdb, sudo, chroot and unshare. Neofetch was not installed.
Examples include Android capture permissions, no GPU exposed to nvtop,
PTY/deadline failures and password-required sudo; no automatic retry or setting
change was used to turn these into passes. `wget` and `nc` passed with the VPN
connected. The isolated positive-counter checks above independently establish
that the unprotected accelerator, not just ordinary PRoot, was active.
Full output and parsed counts: `temp/fast-mode/galaxy-unprotected-apps.log` and
`apps-results.json`. The initial incomplete run was stopped after identifying
that the previous real-cwd behavior also needed restoration; only the complete
run with that behavior restored is counted here.

The later UI correction removes the fixed row boxes and title/summary line
limits, preserving the three-choice selector and compact typography/padding.
Nine updated widget tests pass, including natural growth with enlarged text in
English/Japanese. Targeted analysis and the GitHub ARM64 release build pass.
The corrected APK was installed on Galaxy; its maximum-refresh-rate row remains
at its original natural size, while longer descriptions can increase other row
heights. The selector remains Disabled by default. Disposable device fixtures
were removed after verification.
