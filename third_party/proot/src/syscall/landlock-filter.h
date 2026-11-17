/* Shared with the offline BPF interpreter regression test. GPL-2.0-or-later. */
#ifndef PROOT_LANDLOCK_FILTER_H
#define PROOT_LANDLOCK_FILTER_H
#include <stddef.h>
#include <fcntl.h>
#include <linux/filter.h>
#include <linux/seccomp.h>
static const struct sock_filter landlock_open_filter[] = {
		BPF_STMT(BPF_LD | BPF_W | BPF_ABS, offsetof(struct seccomp_data, arch)),
		BPF_JUMP(BPF_JMP | BPF_JEQ | BPF_K, 0xc00000b7, 0, 15),
		BPF_STMT(BPF_LD | BPF_W | BPF_ABS, offsetof(struct seccomp_data, instruction_pointer) + 4),
		BPF_JUMP(BPF_JMP | BPF_JEQ | BPF_K, 0xf, 0, 13),
		BPF_STMT(BPF_LD | BPF_W | BPF_ABS, offsetof(struct seccomp_data, instruction_pointer)),
		BPF_JUMP(BPF_JMP | BPF_JGE | BPF_K, 0xfff00000, 0, 11),
		BPF_JUMP(BPF_JMP | BPF_JGE | BPF_K, 0xfff01000, 10, 0),
		BPF_STMT(BPF_LD | BPF_W | BPF_ABS, offsetof(struct seccomp_data, nr)),
		/* Harmless activation probe: Landlock prevents a confined process from
		 * reading its unconfined tracer's /proc/PID/environ. Never exempt prctl. */
		BPF_JUMP(BPF_JMP | BPF_JEQ | BPF_K, 167, 0, 3),
		BPF_STMT(BPF_LD | BPF_W | BPF_ABS, offsetof(struct seccomp_data, args[0])),
		BPF_JUMP(BPF_JMP | BPF_JEQ | BPF_K, 0x44584c4c, 0, 6),
		BPF_STMT(BPF_RET | BPF_K, SECCOMP_RET_ERRNO | 117),
		BPF_STMT(BPF_LD | BPF_W | BPF_ABS, offsetof(struct seccomp_data, nr)),
		BPF_JUMP(BPF_JMP | BPF_JEQ | BPF_K, 56, 0, 3),
		BPF_STMT(BPF_LD | BPF_W | BPF_ABS, offsetof(struct seccomp_data, args[2])),
		/* O_PATH is outside Landlock file access checks. Keep it traced. */
		BPF_JUMP(BPF_JMP | BPF_JSET | BPF_K, O_PATH, 1, 0),
		BPF_STMT(BPF_RET | BPF_K, SECCOMP_RET_ALLOW),
	};

/* Unprotected-mode ("broad") fast path, also confined by Landlock. Only file
 * operations Landlock restricts, plus metadata-only lookups, are exempted from
 * the AArch64 trampoline range. Everything else, notably connect/bind/accept,
 * execve, ptrace, signals, chmod/chown/utimes, inotify and prctl, stays traced,
 * so code that jumps into the trampoline gains no access PRoot would refuse.
 * The address is a dispatch optimization, never an authentication boundary. */
#define LANDLOCK_BROAD_OUT 26
#define LANDLOCK_BROAD_ALLOW 25
static const struct sock_filter landlock_broad_filter[] = {
		/* 0 */ BPF_STMT(BPF_LD | BPF_W | BPF_ABS, offsetof(struct seccomp_data, arch)),
		/* 1 */ BPF_JUMP(BPF_JMP | BPF_JEQ | BPF_K, 0xc00000b7, 0, LANDLOCK_BROAD_OUT - 2),
		/* 2 */ BPF_STMT(BPF_LD | BPF_W | BPF_ABS, offsetof(struct seccomp_data, instruction_pointer) + 4),
		/* 3 */ BPF_JUMP(BPF_JMP | BPF_JEQ | BPF_K, 0xf, 0, LANDLOCK_BROAD_OUT - 4),
		/* 4 */ BPF_STMT(BPF_LD | BPF_W | BPF_ABS, offsetof(struct seccomp_data, instruction_pointer)),
		/* 5 */ BPF_JUMP(BPF_JMP | BPF_JGE | BPF_K, 0xfff00000, 0, LANDLOCK_BROAD_OUT - 6),
		/* 6 */ BPF_JUMP(BPF_JMP | BPF_JGE | BPF_K, 0xfff01000, LANDLOCK_BROAD_OUT - 7, 0),
		/* 7 */ BPF_STMT(BPF_LD | BPF_W | BPF_ABS, offsetof(struct seccomp_data, nr)),
		/* Same harmless activation probe as the protected mode; never exempt prctl. */
		/* 8 */ BPF_JUMP(BPF_JMP | BPF_JEQ | BPF_K, 167, 0, 3),
		/* 9 */ BPF_STMT(BPF_LD | BPF_W | BPF_ABS, offsetof(struct seccomp_data, args[0])),
		/* 10 */ BPF_JUMP(BPF_JMP | BPF_JEQ | BPF_K, 0x44584c4c, 0, LANDLOCK_BROAD_OUT - 11),
		/* 11 */ BPF_STMT(BPF_RET | BPF_K, SECCOMP_RET_ERRNO | 117),
		/* Landlock-restricted: openat, mkdirat, unlinkat, symlinkat, linkat, renameat, renameat2, truncate. */
		/* 12 */ BPF_JUMP(BPF_JMP | BPF_JEQ | BPF_K, 56, LANDLOCK_BROAD_ALLOW - 13, 0),
		/* 13 */ BPF_JUMP(BPF_JMP | BPF_JEQ | BPF_K, 34, LANDLOCK_BROAD_ALLOW - 14, 0),
		/* 14 */ BPF_JUMP(BPF_JMP | BPF_JEQ | BPF_K, 35, LANDLOCK_BROAD_ALLOW - 15, 0),
		/* 15 */ BPF_JUMP(BPF_JMP | BPF_JEQ | BPF_K, 36, LANDLOCK_BROAD_ALLOW - 16, 0),
		/* 16 */ BPF_JUMP(BPF_JMP | BPF_JEQ | BPF_K, 37, LANDLOCK_BROAD_ALLOW - 17, 0),
		/* 17 */ BPF_JUMP(BPF_JMP | BPF_JEQ | BPF_K, 38, LANDLOCK_BROAD_ALLOW - 18, 0),
		/* 18 */ BPF_JUMP(BPF_JMP | BPF_JEQ | BPF_K, 276, LANDLOCK_BROAD_ALLOW - 19, 0),
		/* 19 */ BPF_JUMP(BPF_JMP | BPF_JEQ | BPF_K, 45, LANDLOCK_BROAD_ALLOW - 20, 0),
		/* Metadata only (no file content): newfstatat, statx, faccessat, readlinkat, statfs. */
		/* 20 */ BPF_JUMP(BPF_JMP | BPF_JEQ | BPF_K, 79, LANDLOCK_BROAD_ALLOW - 21, 0),
		/* 21 */ BPF_JUMP(BPF_JMP | BPF_JEQ | BPF_K, 291, LANDLOCK_BROAD_ALLOW - 22, 0),
		/* 22 */ BPF_JUMP(BPF_JMP | BPF_JEQ | BPF_K, 48, LANDLOCK_BROAD_ALLOW - 23, 0),
		/* 23 */ BPF_JUMP(BPF_JMP | BPF_JEQ | BPF_K, 78, LANDLOCK_BROAD_ALLOW - 24, 0),
		/* 24 */ BPF_JUMP(BPF_JMP | BPF_JEQ | BPF_K, 43, 0, LANDLOCK_BROAD_OUT - 25),
		/* 25 */ BPF_STMT(BPF_RET | BPF_K, SECCOMP_RET_ALLOW),
		/* 26: fall through to PRoot's ordinary filter. */
	};
#endif
