# proot patches carried by the app

Built by `tools/proot/build.sh` with the NDK on top of termux/proot `4dba3afb` (Termux's
5.1.107-70 package, the proot the Linux runtime ships at `opt/android-host/proot`; every
`file.c:line` site in that binary matches this commit). talloc is linked in statically from
Samba's release tarball, so the apk carries one self-contained `libproot.so` plus
`libproot-loader.so`, and `LinuxRuntime` prefers them over the runtime's copy.

Ported from WinNative (`feature/proot-enhancements`, e7af0f24):

- `0001-tracee-lookup-by-pid.patch` - tracees are hashed by pid, so each ptrace stop finds its
  tracee without walking one list entry per thread, and terminated tracees are swept only after a
  termination.
- `0002-canon-resolve-parent-at-once.patch` - a clean absolute guest path at least three
  directories deep under the rootfs binding alone has its parent opened once with `O_PATH` and
  taken as canonical when `/proc/self/fd` names the path proot would build, replacing an `lstat`
  per component. The final component of a call that does not follow it is no longer `lstat`ed.
  Extensions still see the parent's host path, and the fast path stays off while the f2fs
  workaround is active.
- `0003-clone3-flags-read-guard.patch` - a thread created with `clone3` keeps the flags it
  inherited when `struct clone_args` cannot be read, instead of being tracked as a fork.

Ported from WinNative (`main`, 53836ca9, "Fix/performance and vac"):

- `0004-seccomp-filter-by-argument.patch` - the seccomp filter traces `prctl` only for
  `PR_SET_DUMPABLE`, `setrlimit` only for `RLIMIT_STACK` and `prlimit64` only when it sets a new
  `RLIMIT_STACK`, the only cases proot acts on; every other call runs without a stop. `uname` is
  traced by the core only on x86_64, the one arch it rewrites; kompat still adds it for
  `--kernel-release`.
- `0005-seccomp-skip-unneeded-sysexit.patch` - a seccomp stop fetches the registers once and
  looks the filter flags up by syscall number, instead of a `PTRACE_GETEVENTMSG` and a second
  register fetch. The flags table is built from proot's list merged with the enabled extensions'
  (kompat and fake_id0 here), so it is exactly what the filter reports. `wait4`/`waitpid` left to
  the kernel and `accept`/`accept4` without a sockaddr skip their exit stop, unless an extension
  replaced the syscall (kompat turning `accept4` into `accept`).
- `0006-clone3-exit-signal.patch` - `clone3` flags and exit signal are read as the 64-bit fields
  of `struct clone_args`, so a `clone3` fork is told apart from a thread when proot decides how the
  child is traced.
- `0007-proc-self-thread-group.patch` - tracees track their thread group, `/proc/self` names it
  instead of the calling thread, and `/proc/thread-self` resolves to `/proc/<tgid>/task/<tid>`.
- `0008-fchmodat2-openat2.patch` - `fchmodat2` paths are translated (honouring
  `AT_SYMLINK_NOFOLLOW`), `openat2` answers `ENOSYS` so callers fall back to the translated
  `openat`, and syscall numbers past the end of a table are rejected instead of read.
- `0009-tracee-relatives-sweep.patch` - tracees count their children, so a terminating thread
  with no children or ptracees no longer walks every tracee; the per-stop memory collector is
  emptied instead of freed and reallocated.

Added for Flatpak:

- `0010-new-mount-api-enosys.patch` - `open_tree`, `move_mount`, `fspick` and `mount_setattr`
  answer `ENOSYS`. They take paths proot never translated, so libglnx's `open_tree(AT_FDCWD, "/")`
  handed Flatpak a descriptor for the host's root and it tried to create directories there
  (`mkdirat(root): Operation not permitted`); unsupported, libglnx falls back to `openat`.
- `0012-android-hardlink-denial.patch` - Android's SELinux policy denies apps hard links, so
  `linkat` fails with `EACCES` and Flatpak could not create its repo (`Creating repo: linkat:
  Permission denied`). `O_TMPFILE` answers `EOPNOTSUPP`, so libglnx writes a named temporary file
  and renames it, and a denied link answers `EPERM`, on which ostree's checkout copies instead.
Added by DroidDeck:

- `0011-kompat-utsname-only.patch` - `--kernel-release` (the guest's `DroidDeck` hostname) loads
  kompat, whose filter traps `futex`, `fcntl`, `epoll_pwait`, `pselect6`, `pipe2`, `eventfd2`,
  `socket` and more, and which strips `AT_SYSINFO_EHDR` on every `execve`, so glibc runs without
  the vDSO. When the virtual release is not older than the real kernel and the hwcap is left
  alone, every one of those handlers is a no-op: kompat now traces only `uname`, `sethostname`
  and `setdomainname` and leaves the auxv as the kernel wrote it. On an SD 8 Gen 2 guest this
  took a futex ping-pong from 467 to 97 us, `epoll_pwait` from 60 to 0.8 us and `fcntl` from
  40-107 to 0.4 us, and `clock_gettime` back to the vDSO.
- `0012-fake_id0-identity-only.patch` - `-i uid:gid` (for Xwayland's setgid/setuid before it runs
  xkbcomp) loads fake_id0, whose filter traps every `fstat`/`newfstatat`/`stat` (entry and exit),
  every `sendmsg` (all Wayland, X11, Chromium and PulseAudio traffic), `socket`, `getsockopt`, the
  `get*id` family and the chown/chmod family. When the ids given are the ones proot really has and
  are not 0, every one of those handlers is a no-op; fake_id0 now traces only the `set*id` family
  and the xattr permission fixups, and leaves set-user-ID bits alone on `execve` (Android mounts the
  app's data `nosuid`, so the kernel would not honour them either), which keeps the ids fixed. On an
  x86_64 host build under the same options: `fstat` 41.7 -> 1.3 us, `sendmsg` 18.0 -> 2.5 us.
- `0013-seccomp-ioctl-by-request-and-kernel-exit-stops.patch` - every `ioctl` stopped on entry and
  exit, which is every GPU submit and wait. The filter now traces only the requests enter.c and
  exit.c act on (`TCSETSF`, the four termios2 requests, `FICLONE`) and allows the rest at once, and
  the ioctl block is emitted first (after WinNative 53836ca9, which traces only the termios2 ones).
  The `faccessat2` exit stop (termux 5ba8b95, for glibc's ENOSYS fallback on kernels before 5.8) and
  the `statx` one (emulation on kernels before 4.11) are dropped on kernels 5.8 and newer; patch
  `0015` keeps it on 4.11–5.7 to return the mount ID added in 5.8.
  Host build: `ioctl` 31.6 -> 0.5 us; `stat`/`statx`/`faccessat2` about 44 -> 28 us (one stop).
- `0015-statx-mnt-id-fdinfo.patch` - kernels before 5.8 can complete `statx` without the mount ID
  that libglnx requires while chasing an open descriptor. PRoot keeps the exit stop on those
  kernels and fills only a missing ordinary `STATX_MNT_ID` for `AT_EMPTY_PATH` after pinning the
  tracee descriptor, matching its device and inode to the successful statx result, and reading
  `mnt_id` from the pin's fdinfo. A later close/reuse of the tracee fd cannot redirect that read;
  a concurrent reuse for the same inode through a different bind mount is indistinguishable at
  the exit stop. It does not synthesize `STATX_MNT_ID_UNIQUE`.

Prototype (inert unless `PROOT_FASTPATH` is set; see `docs/development/proot-performance.md`):

- `0014-fastpath-trampoline.patch` - with `PROOT_FASTPATH`, every `SECCOMP_RET_TRACE` in the filter
  is preceded by a check of the caller's address, and a syscall made from the fast path's trampoline
  page (`fastpath/fastpath.c`, `0xffff00000`) runs without a stop: the tracee already translated it.
  The check comes after the syscall-number dispatch, so untraced syscalls stay constant-ALLOW and keep
  the kernel's seccomp action cache (checking first cost every syscall the full filter: +0.5 ms an
  exec). `chdir`/`fchdir`, still emulated, also move the kernel's cwd to the host directory, and the
  first tracee starts at `-w`'s, so relative lookups inside the tracee resolve where proot would.
  SM8850 (adb shell): `stat` 25.3 -> 0.6 us, `open+close` 25.3 -> 0.9 us, ENOENT 22.9 -> 1.6 us,
  8 threads `stat`ing 89k -> 3.6M/s; equivalence suite (`bench/equiv.py`) byte-identical to proot.

Old kernel fixes:

- `0015-lost-fork-events.patch` - works around a problem reported on Xiaomi's msm 4.14 kernel:
  the kernel sometimes reports a new process or thread but gives its ID as zero. Without that ID,
  PRoot cannot finish setting it up, which can leave the session stuck. The patch searches `/proc`
  for the child. If exactly one matches, PRoot uses its existing setup code. If several threads
  match, it waits for the creating thread's `clone` call to return and uses the child ID returned
  by the kernel instead. The original clone flags are preserved. When the kernel supplies a valid
  ID in the notification, normal setup is unchanged. No timers or background polling are added.
  Completely missing notifications and ambiguous matches for separate processes are not recovered.
