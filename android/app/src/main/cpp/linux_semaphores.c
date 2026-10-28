/* glibc publishes named semaphores with link(), denied to Android's shell.
 * Publish an initialized inode atomically with renameat2(NOREPLACE), then let
 * glibc own mapping/refcount/close/unlink as usual. No symlink emulation. */
#define _GNU_SOURCE
#include <semaphore.h>
#include <stdarg.h>
#include <dlfcn.h>
#include <fcntl.h>
#include <unistd.h>
#include <sys/mman.h>
#include <sys/stat.h>
#include <errno.h>
#include <stdio.h>
#include <string.h>
#include <stdatomic.h>
#include <limits.h>

sem_t *sem_open(const char *name, int flags, ...) {
    static sem_t *(*next)(const char *, int, ...);
    if (!next) next = dlsym(RTLD_NEXT, "sem_open");
    mode_t mode = 0;
    unsigned value = 0;
    if (flags & O_CREAT) {
        va_list args; va_start(args, flags);
        mode = va_arg(args, unsigned); value = va_arg(args, unsigned);
        va_end(args);
    }
    sem_t *result = next(name, flags, mode, value);
    if (result != SEM_FAILED || !(flags & O_CREAT) ||
        (errno != EPERM && errno != EACCES)) return result;
    const char *base = name;
    while (*base == '/') base++;
    if (!*base || strchr(base, '/') || strlen(base) > NAME_MAX - 4 || value > SEM_VALUE_MAX) return result;
    char target[PATH_MAX], temporary[PATH_MAX];
    snprintf(target, sizeof(target), "/dev/shm/sem.%s", base);
    static _Atomic unsigned counter;
    int fd = -1;
    for (unsigned attempt = 0; attempt < 32; attempt++) {
        snprintf(temporary, sizeof(temporary), "/dev/shm/.dextop-sem-%ld-%u",
            (long)getpid(), atomic_fetch_add(&counter, 1));
        fd = open(temporary, O_RDWR | O_CREAT | O_EXCL | O_CLOEXEC, mode);
        if (fd >= 0 || errno != EEXIST) break;
    }
    if (fd < 0) return SEM_FAILED;
    int saved = 0;
    if (ftruncate(fd, sizeof(sem_t))) saved = errno;
    sem_t *mapping = MAP_FAILED;
    if (!saved) {
        mapping = mmap(NULL, sizeof(sem_t), PROT_READ | PROT_WRITE, MAP_SHARED, fd, 0);
        if (mapping == MAP_FAILED) saved = errno;
        else if (sem_init(mapping, 1, value)) saved = errno;
    }
    if (mapping != MAP_FAILED) munmap(mapping, sizeof(sem_t));
    close(fd);
    if (!saved && renameat2(AT_FDCWD, temporary, AT_FDCWD, target, RENAME_NOREPLACE)) saved = errno;
    unlink(temporary);
    if (saved && !(saved == EEXIST && !(flags & O_EXCL))) { errno = saved; return SEM_FAILED; }
    return next(name, 0);
}
