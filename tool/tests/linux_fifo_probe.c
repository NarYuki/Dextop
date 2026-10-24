/* Transport probe for a controlled SCM_RIGHTS server. This does not replace
 * the real Android endpoint's FIFO blocking/lifetime tests. */
#define _GNU_SOURCE
#include <sys/stat.h>
#include <unistd.h>
#include <fcntl.h>
#include <errno.h>
#include <stdio.h>
#include <string.h>

int main(void) {
    const char *path = "/tmp/dextop-fifo-transport-probe";
    struct stat info;
    if (mkfifo(path, 0600)) {perror("mkfifo"); return 2;}
    if (lstat(path, &info) || !S_ISFIFO(info.st_mode) || (info.st_mode & 0777) != 0600) return 3;
    if (stat(path, &info) || !S_ISFIFO(info.st_mode) || info.st_size) return 4;
    if (!mkfifo(path, 0600) || errno != EEXIST) return 5;
    const char *alias = "/tmp/dextop-fifo-transport-alias";
    if (symlink("dextop-fifo-transport-probe", alias)) return 14;
    if (lstat(alias, &info) || !S_ISLNK(info.st_mode)) return 15;
    if (stat(alias, &info) || !S_ISFIFO(info.st_mode)) return 16;
    int directory = open("/tmp", O_RDONLY | O_DIRECTORY);
    if (directory < 0) return 17;
    if (fstatat(directory, "dextop-fifo-transport-probe", &info, AT_SYMLINK_NOFOLLOW) || !S_ISFIFO(info.st_mode)) return 18;
    if (fstatat(directory, "dextop-fifo-transport-alias", &info, 0) || !S_ISFIFO(info.st_mode)) return 19;
    struct statx extended;
    if (statx(directory, "dextop-fifo-transport-alias", 0, STATX_BASIC_STATS, &extended) || !S_ISFIFO(extended.stx_mode)) return 20;
    if (open(alias, O_RDONLY | O_NONBLOCK | O_NOFOLLOW) != -1 || errno != ELOOP) return 21;
    close(directory);
    char bytes[8];
    if (readlink(path, bytes, sizeof(bytes)) != -1 || errno != EINVAL) return 6;
    for (int cloexec = 0; cloexec < 2; ++cloexec) {
        int fd = open(cloexec ? alias : path, O_RDONLY | O_NONBLOCK | (cloexec ? O_CLOEXEC : 0));
        if (fd < 0) {perror("open FIFO"); return 7;}
        if (fstat(fd, &info) || !S_ISFIFO(info.st_mode)) return 8;
        if (!!(fcntl(fd, F_GETFD) & FD_CLOEXEC) != cloexec) return 9;
        if (read(fd, bytes, sizeof(bytes)) != 4 || memcmp(bytes, "fifo", 4)) return 10;
        if (read(fd, bytes, sizeof(bytes)) != 0) return 11;
        close(fd);
    }
    int metadata_fd = open(path, O_PATH | O_NOFOLLOW | O_CLOEXEC);
    if (metadata_fd < 0 || fstat(metadata_fd, &info) || !S_ISFIFO(info.st_mode)) return 22;
    if (read(metadata_fd, bytes, sizeof(bytes)) != -1 || errno != EBADF) return 23;
    close(metadata_fd);
    if (unlink(alias)) return 24;
    if (unlink(path)) return 12;
    if (!lstat(path, &info) || errno != ENOENT) return 13;
    puts("FIFO_DESCRIPTOR_TRANSPORT_METADATA_EOF_CLOEXEC_OK");
    return 0;
}
