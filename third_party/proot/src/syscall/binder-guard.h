/* SPDX-License-Identifier: GPL-2.0-or-later
 * Dextop's mandatory Android guard, independent of PRoot's optional syscall
 * tracing/fast-path filter. Install before parsing guest-controlled paths. */
#ifndef DEXTOP_BINDER_GUARD_H
#define DEXTOP_BINDER_GUARD_H

#include <errno.h>
#include <stddef.h>
#include <linux/audit.h>
#include <linux/filter.h>
#include <linux/seccomp.h>
#include <sys/prctl.h>

#if defined(DEXTOP_BINDER_GUARD) && !defined(__aarch64__)
#error "Dextop binder guard requires an arm64 PRoot build"
#endif

/* Dextop ships PRoot only for arm64. AArch32 compat must also be covered:
 * ioctl truncates its request to unsigned int; inspect the low word only.
 * Deny the whole binder ('b') family, including both BINDER_WRITE_READ sizes,
 * rather than enumerating today's commands. No instruction-pointer exemption.
 * Unknown ABIs fail closed, even if the optional tracing filter is disabled. */
static const struct sock_filter dextop_binder_filter[] = {
	BPF_STMT(BPF_LD | BPF_W | BPF_ABS, offsetof(struct seccomp_data, arch)),
	BPF_JUMP(BPF_JMP | BPF_JEQ | BPF_K, AUDIT_ARCH_AARCH64, 0, 3),
	BPF_STMT(BPF_LD | BPF_W | BPF_ABS, offsetof(struct seccomp_data, nr)),
	BPF_JUMP(BPF_JMP | BPF_JEQ | BPF_K, 29 /* arm64 ioctl */, 6, 0),
	BPF_STMT(BPF_RET | BPF_K, SECCOMP_RET_ALLOW),
	BPF_JUMP(BPF_JMP | BPF_JEQ | BPF_K, AUDIT_ARCH_ARM, 0, 3),
	BPF_STMT(BPF_LD | BPF_W | BPF_ABS, offsetof(struct seccomp_data, nr)),
	BPF_JUMP(BPF_JMP | BPF_JEQ | BPF_K, 54 /* ARM ioctl */, 2, 0),
	BPF_STMT(BPF_RET | BPF_K, SECCOMP_RET_ALLOW),
	BPF_STMT(BPF_RET | BPF_K, SECCOMP_RET_KILL_PROCESS),
	BPF_STMT(BPF_LD | BPF_W | BPF_ABS, offsetof(struct seccomp_data, args[1])),
	BPF_STMT(BPF_ALU | BPF_AND | BPF_K, 0xff00),
	BPF_JUMP(BPF_JMP | BPF_JEQ | BPF_K, 'b' << 8, 0, 1),
	BPF_STMT(BPF_RET | BPF_K, SECCOMP_RET_ERRNO | EPERM),
	BPF_STMT(BPF_RET | BPF_K, SECCOMP_RET_ALLOW),
};

static inline int dextop_install_binder_guard(void)
{
	struct sock_fprog program = {
		.len = sizeof(dextop_binder_filter) / sizeof(dextop_binder_filter[0]),
		.filter = (struct sock_filter *)dextop_binder_filter,
	};
	if (prctl(PR_SET_NO_NEW_PRIVS, 1, 0, 0, 0) < 0)
		return -1;
	return prctl(PR_SET_SECCOMP, SECCOMP_MODE_FILTER, &program);
}
#endif
