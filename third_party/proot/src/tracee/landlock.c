#ifndef _GNU_SOURCE
#define _GNU_SOURCE
#endif
#include "tracee/landlock.h"
#include <errno.h>
#include <fcntl.h>
#include <limits.h>
#include <stdint.h>
#include <stdlib.h>
#include <string.h>
#include <sys/prctl.h>
#include <sys/stat.h>
#include <sys/syscall.h>
#include <unistd.h>

static bool active;
bool proot_landlock_active(void) { return active; }

int proot_landlock_apply(void)
{
#if defined(__aarch64__) && defined(__NR_landlock_create_ruleset) && defined(__NR_landlock_add_rule) && defined(__NR_landlock_restrict_self)
	/* Stable UAPI layouts; do not require recent NDK linux/landlock.h. ABI 6
	 * includes REFER, TRUNCATE and IOCTL_DEV. Never silently drop rights. */
	struct { uint64_t handled_access_fs; } rules = { (1ULL << 16) - 1 };
	struct __attribute__((packed)) { uint64_t allowed_access; int32_t parent_fd; } rule;
	const uint64_t file_rights = (1ULL << 0) | (1ULL << 1) | (1ULL << 2) |
		(1ULL << 14) | (1ULL << 15);
	const char *paths = getenv("PROOT_LANDLOCK_PATHS");
	char *copy, *cursor, *path;
	int fd, result = -EINVAL, count = 0;
	if (!paths || !*paths || syscall(__NR_landlock_create_ruleset, NULL, 0, 1) < 6)
		return -ENOSYS;
	copy = strdup(paths);
	if (!copy) return -ENOMEM;
	fd = syscall(__NR_landlock_create_ruleset, &rules, sizeof rules, 0);
	if (fd < 0) { result = -errno; goto out; }
	cursor = copy;
	while ((path = strsep(&cursor, "\n")) != NULL) {
		struct stat st;
		char canonical[PATH_MAX];
		int parent;
		/* Reject empty entries, '/' and unreasonable policies. No broad fallback. */
		if (path[0] != '/' || !path[1] || ++count > 260) goto close_rules;
		if (!realpath(path, canonical) || !strcmp(canonical, "/")) goto close_rules;
		parent = open(canonical, O_PATH | O_CLOEXEC);
		if (parent < 0) { result = -errno; goto close_rules; }
		if (fstat(parent, &st) < 0) {
			result = -errno; close(parent); goto close_rules;
		}
		rule.parent_fd = parent;
		rule.allowed_access = S_ISDIR(st.st_mode) ? rules.handled_access_fs : file_rights;
		if (syscall(__NR_landlock_add_rule, fd, 1, &rule, 0) < 0) {
			result = -errno; close(parent); goto close_rules;
		}
		close(parent);
	}
	if (prctl(PR_SET_NO_NEW_PRIVS, 1, 0, 0, 0) < 0 ||
	    syscall(__NR_landlock_restrict_self, fd, 0) < 0) {
		result = -errno; goto close_rules;
	}
	active = true;
	result = 0;
close_rules:
	close(fd);
out:
	free(copy);
	return result;
#else
	return -ENOSYS;
#endif
}
