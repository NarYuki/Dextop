/* SPDX-License-Identifier: GPL-2.0-or-later
 * Read-only Landlock lifetime diagnostic for isolated Linux test environments.
 * Usage: check MODE ALLOWED_ROOT EXISTING_FILE ITERATIONS
 * MODE: production | independent | minimal | keep | drop | plain
 * Link tracee/landlock.c. Production mode respects its vendor-build guard.
 * Independent modes are diagnostic only: do not run on a known-unstable device.
 * Optional LL_DIAG_OUTSIDE verifies denial of an existing outside fixture.
 * Optional LL_DIAG_SETTLE waits 3 seconds after enforcement and FD release.
 * Optional LL_DIAG_DROP_AFTER chooses the drop-mode release iteration.
 */
#define _GNU_SOURCE
#include <errno.h>
#include <fcntl.h>
#include <inttypes.h>
#include <linux/landlock.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/prctl.h>
#include <sys/stat.h>
#include <sys/syscall.h>
#include <sys/wait.h>
#include <unistd.h>
#include "tracee/landlock.h"

static int retained = -1;

static void metadata(const char *tag, const char *path)
{
    struct stat st;
    if (stat(path, &st)) {
        printf("%s stat_errno=%d\n", tag, errno);
        return;
    }
    printf("%s dev=%ju ino=%ju mode=%o uid=%u\n", tag,
           (uintmax_t)st.st_dev, (uintmax_t)st.st_ino, st.st_mode, st.st_uid);
}

static int independent_policy(const char *path, int minimal, int keep)
{
    /* Use official UAPI declarations, independently of the production setup. */
    struct landlock_ruleset_attr attr = {
        .handled_access_fs = minimal ? LANDLOCK_ACCESS_FS_READ_FILE : (1ULL << 16) - 1
    };
    int fd = syscall(SYS_landlock_create_ruleset, &attr, sizeof(attr), 0);
    if (fd < 0) return -errno;
    int anchor = open(path, O_PATH | O_CLOEXEC);
    if (anchor < 0) { int error = errno; close(fd); return -error; }
    struct stat st;
    int result = -EINVAL;
    if (fstat(anchor, &st)) { result = -errno; goto out; }
    struct landlock_path_beneath_attr rule = {
        .parent_fd = anchor, .allowed_access = attr.handled_access_fs
    };
    if (!S_ISDIR(st.st_mode))
        rule.allowed_access &= (1ULL << 0) | (1ULL << 1) | (1ULL << 2) |
                               (1ULL << 14) | (1ULL << 15);
    if (syscall(SYS_landlock_add_rule, fd, LANDLOCK_RULE_PATH_BENEATH, &rule, 0) ||
        prctl(PR_SET_NO_NEW_PRIVS, 1, 0, 0, 0) ||
        syscall(SYS_landlock_restrict_self, fd, 0)) {
        result = -errno;
        goto out;
    }
    result = 0;
    if (keep) { retained = fd; fd = -1; }
out:
    close(anchor);
    if (fd >= 0) close(fd);
    return result;
}

int main(int argc, char **argv)
{
    if (argc != 5) return 2;
    char *end;
    long limit = strtol(argv[4], &end, 10);
    if (*end || limit <= 0 || limit > 10000000) return 2;
    const char *mode = argv[1], *root = argv[2], *file = argv[3];
    int rootfd = open(root, O_PATH | O_CLOEXEC);
    if (rootfd < 0) return 3;
    int ready[2];
    if (pipe(ready)) return 4;
    setvbuf(stdout, NULL, _IOLBF, 0);
    pid_t pid = fork();
    if (pid < 0) return 5;
    if (pid) {
        close(ready[1]);
        char event;
        ssize_t got;
        do { got = read(ready[0], &event, 1); } while (got < 0 && errno == EINTR);
        if (got == 1) {
            int fd = open(file, O_RDONLY | O_CLOEXEC);
            printf("OBSERVER open=%d errno=%d\n", fd, fd < 0 ? errno : 0);
            if (fd >= 0) close(fd);
            metadata("observer_root", root);
            metadata("observer_file", file);
        }
        close(ready[0]); close(rootfd);
        int status;
        pid_t waited;
        do { waited = waitpid(pid, &status, 0); } while (waited < 0 && errno == EINTR);
        return waited == pid && WIFEXITED(status) ? WEXITSTATUS(status) : 6;
    }
    close(ready[0]);
    printf("mode=%s rule=%s file=%s\n", mode, root, file);
    metadata("root_before", root); metadata("file_before", file);
    int status;
    if (!strcmp(mode, "production")) {
        setenv("PROOT_LANDLOCK_PATHS", root, 1);
        status = proot_landlock_apply();
    } else if (!strcmp(mode, "minimal")) status = independent_policy(root, 1, 0);
    else if (!strcmp(mode, "independent")) status = independent_policy(root, 0, 0);
    else if (!strcmp(mode, "keep") || !strcmp(mode, "drop")) status = independent_policy(root, 0, 1);
    else if (!strcmp(mode, "plain")) status = 0;
    else _exit(2);
    if (status) { printf("SETUP=%d\n", status); _exit(77); }
    const char *outside = getenv("LL_DIAG_OUTSIDE");
    if (outside && strcmp(mode, "plain")) {
        int fd = open(outside, O_RDONLY | O_CLOEXEC), error = errno;
        printf("outside_open=%d errno=%d\n", fd, fd < 0 ? error : 0);
        if (fd >= 0) { close(fd); _exit(8); }
        if (error != EACCES) _exit(9);
    }
    if (getenv("LL_DIAG_SETTLE")) sleep(3);
    const char *relative = !strncmp(file, root, strlen(root)) && file[strlen(root)] == '/'
                         ? file + strlen(root) + 1 : NULL;
    long drop = limit / 2;
    if (getenv("LL_DIAG_DROP_AFTER")) drop = strtol(getenv("LL_DIAG_DROP_AFTER"), NULL, 10);
    for (long i = 0; i < limit; i++) {
        if (!strcmp(mode, "drop") && i == drop) {
            printf("CLOSE_RULESET after=%ld fd=%d\n", i, retained);
            close(retained); retained = -1;
            if (getenv("LL_DIAG_SETTLE")) sleep(3);
        }
        int fd = open(file, O_RDONLY | O_CLOEXEC);
        if (fd >= 0) { close(fd); continue; }
        printf("FAIL i=%ld errno=%d\n", i, errno);
        metadata("root_after", root); metadata("file_after", file);
        struct stat st;
        if (!fstat(rootfd, &st))
            printf("anchor ino=%ju dev=%ju\n", (uintmax_t)st.st_ino, (uintmax_t)st.st_dev);
        if (relative) {
            fd = openat(rootfd, relative, O_RDONLY | O_CLOEXEC);
            printf("anchored_open=%d errno=%d\n", fd, fd < 0 ? errno : 0);
            if (fd >= 0) close(fd);
        }
        for (int retry = 0; retry < 3; retry++) {
            fd = open(file, O_RDONLY | O_CLOEXEC);
            printf("retry=%d errno=%d\n", fd, fd < 0 ? errno : 0);
            if (fd >= 0) close(fd);
        }
        (void)write(ready[1], "x", 1);
        _exit(1);
    }
    printf("PASS opens=%ld\n", limit);
    _exit(0);
}
