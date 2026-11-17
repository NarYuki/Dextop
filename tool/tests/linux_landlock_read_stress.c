/* Read-only Landlock stability probe; no PRoot, preload or trampoline.
 * Usage: check plain|nnp|landlock ALLOWED_DIRECTORY EXISTING_FILE [ITERATIONS]
 * Build with NDK clang and third_party/proot/src/tracee/landlock.c.
 */
#define _GNU_SOURCE
#include <errno.h>
#include <fcntl.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/prctl.h>
#include <sys/stat.h>
#include <unistd.h>
#include "tracee/landlock.h"

int main(int argc, char **argv)
{
    if (argc < 4 || argc > 5) return 2;
    const long iterations = argc == 5 ? strtol(argv[4], NULL, 10) : 1000000;
    if (iterations <= 0 || iterations > 10000000) return 2;
    struct stat before, after;
    if (stat(argv[2], &before)) { perror("stat policy root"); return 2; }
    int status;
    if (!strcmp(argv[1], "plain")) status = 0;
    else if (!strcmp(argv[1], "nnp")) status = prctl(PR_SET_NO_NEW_PRIVS, 1, 0, 0, 0);
    else if (!strcmp(argv[1], "landlock")) {
        setenv("PROOT_LANDLOCK_PATHS", argv[2], 1);
        status = proot_landlock_apply();
    } else return 2;
    if (status) { printf("SKIP policy setup: %d\n", status); return 77; }
    for (long i = 0; i < iterations; i++) {
        int fd = open(argv[3], O_RDONLY | O_CLOEXEC);
        if (fd < 0) {
            int error = errno;
            printf("FAIL mode=%s iteration=%ld errno=%d (%s)\n", argv[1], i, error, strerror(error));
            if (!stat(argv[2], &after))
                printf("policy_root_same_inode=%d\n", before.st_dev == after.st_dev && before.st_ino == after.st_ino);
            for (int retry = 0; retry < 5; retry++) {
                fd = open(argv[3], O_RDONLY | O_CLOEXEC);
                printf("retry=%d success=%d errno=%d\n", retry, fd >= 0, fd < 0 ? errno : 0);
                if (fd >= 0) close(fd);
            }
            return 1;
        }
        close(fd);
    }
    printf("PASS mode=%s opens=%ld\n", argv[1], iterations);
    return 0;
}
