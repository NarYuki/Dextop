# Landlock instability investigation, 2026-11-11

> Historical investigation: the exact-kernel guard described below was later
> removed at the user's explicit request. Current Fast mode offers protected
> (Landlock) and previous unprotected paths with no device blacklist. See
> [current implementation](linux-landlock-fastpath.md). The observed failures
> below have not been established as fixed.

**Current result:** the failure is independent of PRoot and the preload. The
strongest remaining hypothesis is a Samsung-specific interaction in Landlock
credential/domain/object lifetime management, particularly KDP credential
conversion. This is **not yet a confirmed root cause in the exact firmware**.
The tested vendor build is now rejected before any Landlock syscall, and its
experimental launches fall back to ordinary PRoot.

## What is established

Device: SM-S942Z / RFGL22LEFGF, firmware S942ZSCU1BZI9, Android **17** according
to `getprop ro.build.version.release` and the boot record. The `android16` in the
kernel release identifies its kernel branch, not the current Android OS version.
Kernel release:

```text
6.12.69-android16-6-pee899be-abogkiS942ZSCU1BZI9-4k
```

The native probe was expanded to fork an unconfined observer before applying the
policy. After a confined read failed, the observer opened that exact same file.
Both processes recorded device, inode, mode and owner. The confined child also
retried relative to a pinned directory descriptor.

| Case on Galaxy | First denied read | Unconfined observer |
| --- | ---: | --- |
| Production policy, existing rootfs libc | 164,593 | succeeded |
| Independent official-UAPI policy, same file | 345,253 | succeeded |
| Independent READ_FILE-only policy, same file | 192,610 | succeeded |
| Production policy, disposable nested fixture | 378,485 | succeeded |
| READ_FILE-only policy, disposable nested fixture | 576,826 | succeeded |
| Rule directly on the fixture's parent directory | 137,909 | succeeded |
| Rule directly on the fixture file | 497,129 | succeeded |

In each failing case, root and file metadata stayed unchanged; retrying through
an already-open root descriptor also failed. Immediate retries remained denied.
The independent implementation uses the NDK's official Landlock UAPI structs and
syscall constants, rather than the production implementation's local layouts.

This rules out PRoot path rewriting, syscall exemptions, preload wrappers,
ordinary chmod/ownership changes, an overly broad handled-rights mask, and a
problem limited to the original libc file or a deep ancestor rule. It does not
by itself distinguish the vendor Landlock code, another LSM, credential copying,
filesystem changes or memory corruption inside the kernel.

## Ruleset descriptor lifetime

Keeping the source ruleset FD open completed two alternating **3,000,000-open**
trials; the corresponding closed-FD trials failed at 1,368,301, 2,170,739 and
2,152 opens. A prior retained-FD trial completed 2,000,000 opens.

This is a useful clue, **not a validated workaround**. A closed-FD trial with a
three-second pause also completed 2,000,000 opens. The third retained-FD trial
was interrupted by loss of ADB and a device restart. We did not complete an
on-device close-mid-run experiment. Keeping a root O_PATH descriptor alone did
not reliably help in the earlier investigation.

Closing the ruleset after successful restriction is supported usage: the
[Linux 6.12 documentation](https://www.kernel.org/doc/html/v6.12/userspace-api/landlock.html)
does exactly that in its example. The policy should survive without the source
FD. We did not change production code to retain that FD or to retry denied
operations.

## Independent Linux controls

### Linux 6.8.0-100-generic, x86-64

The same independent READ_FILE-only implementation ran as UID 65534 in a
capability-free container. Its default seccomp profile was disabled only inside
that disposable test container so that Landlock syscalls could run. Three trials
of **5,000,000 opens each passed** after closing the ruleset FD.

### Linux 6.12.69, ARM64, tmpfs and F2FS

To reduce kernel-version and architecture differences, an isolated QEMU ARM64
VM booted Debian's `linux-image-6.12.69+deb13-arm64-unsigned`, version
`6.12.69-1`, from the [official Debian snapshot metadata](https://snapshot.debian.org/mr/binary/linux-image-6.12.69+deb13-arm64-unsigned/6.12.69-1/binfiles).
The package SHA-1 is `983ae7615fdc222a4051d5da40a879a04dbd4cec`.
It was not installed on the phone or host. The VM had no host filesystem shares.

The test process dropped to UID/GID 65534. Each case checked that a separate,
readable outside fixture was denied with EACCES. Policy enforcement and FD
release were followed by three-second pauses to allow deferred work to run.

| Case | tmpfs | F2FS |
| --- | --- | --- |
| Production full-filesystem policy, FD closed | 50,000 passed | 50,000 passed |
| Independent READ_FILE-only policy, FD closed | 50,000 passed | 50,000 passed |
| Source ruleset FD retained | 50,000 passed | 50,000 passed |
| Source ruleset FD closed midway through reads | 100,000 passed; closed at 50,000 | 50,000 passed; closed at 25,000 |
| Outside fixture | denied in all four cases | denied in all four cases |

F2FS used a separate 128 MiB test image and the matching Debian F2FS module.
These shorter software-emulated runs do not exclude a rare generic bug, nor do
they reproduce Samsung's F2FS patches/options or security stack. They show that
Linux **6.12.69 + ARM64 + F2FS does not inherently require retaining the ruleset
FD**, and that the production policy works on a non-Samsung kernel of the same
version. An earlier million-iteration VM run was stopped to bound emulation
time; it is not counted as a passed test.

## KDP hypothesis and source evidence

The captured device config has `CONFIG_KDP=y`, `CONFIG_RKP=y`,
`CONFIG_SECURITY_DEFEX=y`, `CONFIG_SECURITY_LANDLOCK=y` and `CONFIG_BPF_LSM=y`.
This establishes compiled support, not an independent measurement of each
runtime enable flag.

Exact source for S942ZSCU1BZI9 was not obtained: Samsung's official source search
returned HTTP 403. A **different-device, non-official public Samsung-source
mirror**, SM8750 kernel 6.6.98, was inspected at immutable revision
`aadec03b89aa554a6b77c5e5a2b5822b023520e6`. Its code provides a specific review
lead, but is not proof of what executes in SM-S942Z's 6.12.69 firmware:

1. The KDP branch of
   [`commit_creds()`](https://github.com/YuzakiKokuban/android_kernel_samsung_sm8750/blob/aadec03b89aa554a6b77c5e5a2b5822b023520e6/kernel/cred.c#L510)
   creates a protected credential copy, installs it, then drops the original new
   credential's two references.
2. [`prepare_ro_creds()`](https://github.com/YuzakiKokuban/android_kernel_samsung_sm8750/blob/aadec03b89aa554a6b77c5e5a2b5822b023520e6/drivers/uh/kdp.c#L255)
   makes a hypervisor-mediated copy. It explicitly acquires group/user/key
   references, but contains no matching generic LSM credential-transfer call.
   Its security allocation is sized to a SELinux-specific structure, another
   point to review with stacked LSMs. The hypervisor's full copy behavior was
   not available for inspection.
3. Landlock's
   [`cred_transfer` / `cred_free` hooks](https://github.com/YuzakiKokuban/android_kernel_samsung_sm8750/blob/aadec03b89aa554a6b77c5e5a2b5822b023520e6/security/landlock/cred.c#L18)
   acquire and release domain references. Copying a domain pointer to a protected
   credential without preserving its ownership, or copying an incomplete LSM
   blob, could leave an invalid domain/object reference after the original is
   released.

That ownership/blob mismatch is consistent with delayed, persistent failures and
the FD-retention sensitivity. It is a **hypothesis**, not a demonstrated
use-after-free, exploit, or proof against KDP in the exact build. Matching vendor
source or a full kernel crash trace is still needed to identify the exact
faulting operation and validate a kernel-side fix.

## Restart evidence and investigation limits

During the third retained-FD run, ADB disconnected. After reconnection, uptime
was about 100 seconds. The user could not determine whether the restart was
automatic. Android DropBox contained a contemporaneous
`SYSTEM_LAST_KMSG_13_20261111_183849_KP` entry, but it was truncated and contained
bootloader output without a usable panic backtrace. Both boot-reason properties
reported `reboot`.

Consequently we have **observed a restart, not established its cause**. Direct
pstore, reset-summary and kallsyms access were denied to the shell user. We did
not change SELinux, load modules on the phone, root it, flash firmware, or repeat
long independent Landlock stress after reconnection. Module loading and F2FS
mounting occurred only in the disposable VM.

## Product mitigation and verification

`proot_landlock_apply()` now checks the exact known-unstable release above before
any Landlock syscall and returns `-EOPNOTSUPP`. This reuses the existing normal
PRoot re-exec fallback. It does not blacklist all Samsung devices, all KDP
kernels, or all Linux 6.12.69 builds. ABI 6 availability alone is insufficient to
consider this specific build usable.

Verification:

- Native PRoot rebuilt successfully.
- `linux_landlock_kernel_guard_check.c`: exact release rejected with **zero**
  Landlock syscall attempts; a different release reaches the mocked ABI query;
  uname failure leaves acceleration inactive. Built with warnings as errors.
- Actual phone: the production probe returned setup `-95` immediately.
- Actual phone: explicit fast-path request re-execed normal PRoot, the guest
  activation probe stayed inactive, and **all 16 guest regressions passed**.
- New independent lifetime harness compiled for Android ARM64 and Linux x86-64;
  its READ_FILE-only/outside-denial control passed 10,000 reads on Linux.
- The earlier seven-locale generation check and final diff whitespace check
  passed. No APK was installed and no setting was enabled.

## Artifacts

Maintained checks:

- `tool/tests/linux_landlock_read_stress.c`
- `tool/tests/linux_landlock_lifetime_check.c`
- `tool/tests/linux_landlock_kernel_guard_check.c`

Do not run the independent stress modes on the known-unstable phone as a routine
check. Production mode respects the guard; the independent modes exist for
isolated VMs or deliberate kernel diagnostics.

Detailed evidence and VM build inputs remain in ignored
`temp/landlock-investigation/`: `matrix-root.log`, `matrix-lifetime.log`,
`matrix-keep-repeat.log`, `linux-control.log`, `vm-tmpfs-final.log`,
`vm-f2fs-final.log`, `kernel-guard-unit.log`, `guarded-integration.log`,
`last-kmsg.txt`, `reset-summary.txt` and the pinned vendor-source copies.
Disposable phone fixtures were removed after evidence collection. No report was
sent externally and no commit was made.

## Guarded 109-case re-test

The maintained CLI/TUI suite was rerun on the Galaxy with an explicit fast-path
request and the exact-build guard in the rebuilt PRoot. The guard re-executed
ordinary PRoot; the guest library reported an inactive activation probe. This
validates the fallback, not accelerated operation on the affected firmware.

| Classification from the existing suite | Count |
| --- | ---: |
| OK | 91 |
| NG | 17 |
| Not installed | 1 |
| Total | 109 |

Relative to the earlier normal-mode run (94/14/1), the three changed entries were
atop, nc and wget. Additional checks isolated those differences:

- Atop passed three isolated runs without a fast-path request and three with the
  guarded request, with diagnostic statistics disabled. Its original exit 137
  was not reproduced and is consistent with the suite's outer PTY deadline.
- nc failed its direct TCP connection to 1.1.1.1:443 in both normal and guarded
  modes. This result is independent of hostname lookup.
- wget failed DNS resolution in both modes. The existing resolver was
  `nameserver 192.0.2.1`; a strict `dig +time=2 +tries=1` check also failed with
  exit 9 in both modes. No DNS or network setting was changed.

The suite's original dig pipeline had classified that timeout as OK. As noted
above, counts from this legacy suite must not be interpreted as 91 fully
verified functional applications; some pipelines hide an upstream failure.
No cascading file-access denials, lost ADB session or device restart were
observed during this guarded run. The other NG cases match the earlier baseline.

Evidence: `temp/landlock-retest/apps-guarded.log`, `results.json`,
`atop-controls.log`, and `network-controls.log`. Test homes and fixtures were
isolated and removed afterward. No production code or app settings were changed
for this re-test, and no APK was installed.

## wget follow-up with VPN preserved

The previous PRoot from HEAD 9485910 was rebuilt independently and compared
against the current binary with the same Galaxy rootfs and with preloads both
present and removed. Both fail DNS. A separate regular Android app also fails
DNS and VPN-bound TCP without using PRoot. Mullvad's own UI then confirmed
**device invalid / device removed**, with Internet blocked pending login.
The user requires keeping the VPN; the login button, which says it releases the
block, was not pressed. See the [wget investigation](linux-wget-investigation-2026-11-11.md)
for the controlled comparisons and authentication dependency.
