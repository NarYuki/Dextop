/*
 * USB storage for Linux in Dextop: /dev/sdX and /dev/sdXN, served by Dextop (LinuxUsbDisk.kt).
 *
 * Android owns USB storage and no app may open a block device, so Dextop takes the stick and
 * runs its SCSI commands itself. The manifest /tmp/dextop-devices.conf lists each disk:
 *   disk <sdX> <Android device name> <bytes> <sector size> <read-only 0|1> <N:start:length,...|-> <name base64>
 * Opening /dev/sdX (or /dev/sdXN) connects to Dextop's device bridge ("B <device> <N>"); the
 * application gets that socket as its descriptor, and read, write, lseek, pread, pwrite, fsync
 * and the block-device ioctls on it become requests to Dextop. Dextop keeps the file position,
 * so duplicated descriptors and those inherited across fork and exec share it as on a real
 * device. Each socket is bound to an abstract name starting with "dextop-blk-": a process that
 * inherited one (shell redirections, exec) recognises it by that name.
 *
 * Mounting is not possible (no kernel); fdisk, sfdisk, parted, mkfs.*, dd, wipefs, badblocks,
 * fsck and other tools that work on the device file are.
 */
#define _GNU_SOURCE
#include <dlfcn.h>
#include <errno.h>
#include <fcntl.h>
#include <pthread.h>
#include <stddef.h>
#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/socket.h>
#include <sys/stat.h>
#include <sys/syscall.h>
#include <sys/sysmacros.h>
#include <sys/types.h>
#include <sys/uio.h>
#include <sys/un.h>
#include <unistd.h>

#define CONFIG "/tmp/dextop-devices.conf"
#define MAX_FDS 4096
#define PREFIX "dextop-blk-"
#define SCSI_DISK_MAJOR 8
#define CHUNK (1 << 20)

struct disk {
    int64_t size, sector, readonly, start, minor;
    pthread_mutex_t lock;
};

static struct disk *disks[MAX_FDS];
static pthread_mutex_t table = PTHREAD_MUTEX_INITIALIZER;

/* --- raw socket I/O (never through the hooks below) --- */

static int send_all(int fd, const void *data, size_t size) {
    const char *p = data;
    while (size > 0) {
        long n = syscall(SYS_sendto, fd, p, size, MSG_NOSIGNAL, NULL, 0);
        if (n < 0 && errno == EINTR) continue;
        if (n <= 0) return -1;
        p += n;
        size -= (size_t)n;
    }
    return 0;
}

static int recv_all(int fd, void *data, size_t size) {
    char *p = data;
    while (size > 0) {
        long n = syscall(SYS_recvfrom, fd, p, size, MSG_WAITALL, NULL, NULL);
        if (n < 0 && errno == EINTR) continue;
        if (n <= 0) return -1;
        p += n;
        size -= (size_t)n;
    }
    return 0;
}

/* One request; the answer's first long is returned (-errno on failure, set in errno too). */
static int64_t request(int fd, struct disk *disk, char op, int64_t a, int64_t b, const void *out, void *in) {
    unsigned char header[17];
    header[0] = (unsigned char)op;
    memcpy(header + 1, &a, 8);
    memcpy(header + 9, &b, 8);
    int64_t result = -EIO;
    pthread_mutex_lock(&disk->lock);
    if (send_all(fd, header, sizeof(header)) == 0 && (out == NULL || b == 0 || send_all(fd, out, (size_t)b) == 0) &&
        recv_all(fd, &result, 8) == 0) {
        if (in != NULL && result > 0 && recv_all(fd, in, (size_t)result) != 0) result = -EIO;
    } else {
        result = -EIO;
    }
    pthread_mutex_unlock(&disk->lock);
    if (result < 0) errno = (int)-result;
    return result;
}

static int read_info(int fd, struct disk *disk) {
    int64_t values[6];
    if (recv_all(fd, values, sizeof(values)) != 0 || values[0] != 0) return -1;
    disk->size = values[1];
    disk->sector = values[2] > 0 ? values[2] : 512;
    disk->readonly = values[3];
    disk->start = values[4];
    disk->minor = values[5];
    return 0;
}

/* The disk behind [fd]: opened here, or inherited (recognised by its socket's name). */
static struct disk *disk_of(int fd) {
    if (fd < 0 || fd >= MAX_FDS) return NULL;
    /* Every call checks the descriptor itself: a number seen before may have been closed
     * where this library does not see it (inside libc) and reused for something else. */
    int saved = errno;
    struct sockaddr_un address;
    socklen_t length = sizeof(address);
    if (syscall(SYS_getsockname, fd, &address, &length) != 0 || address.sun_family != AF_UNIX ||
        length <= offsetof(struct sockaddr_un, sun_path) + sizeof(PREFIX) || address.sun_path[0] != '\0' ||
        memcmp(address.sun_path + 1, PREFIX, sizeof(PREFIX) - 1) != 0) {
        errno = saved;
        if (disks[fd] != NULL) {
            pthread_mutex_lock(&table);
            disks[fd] = NULL; /* closed where this library did not see it; the record is small, keep it */
            pthread_mutex_unlock(&table);
        }
        return NULL;
    }
    struct disk *disk = disks[fd];
    if (disk != NULL) return disk;
    disk = calloc(1, sizeof(*disk));
    if (disk == NULL) return NULL;
    pthread_mutex_init(&disk->lock, NULL);
    unsigned char header[17] = {'i'};
    if (send_all(fd, header, sizeof(header)) != 0 || read_info(fd, disk) != 0) { free(disk); errno = saved; return NULL; }
    pthread_mutex_lock(&table);
    if (disks[fd] == NULL) disks[fd] = disk; else { free(disk); disk = disks[fd]; }
    pthread_mutex_unlock(&table);
    errno = saved;
    return disk;
}

/* --- manifest --- */

struct entry {
    char node[16], device[256];
    int index;          /* sda 0, sdb 1... */
    int partition;      /* 0: the whole disk */
    int readonly;
};

/* Finds [path] ("/dev/sda", "/dev/sda1") in the manifest; 0 when listed. */
static int lookup(const char *path, struct entry *entry, char *socket_name, size_t socket_size) {
    if (path == NULL || strncmp(path, "/dev/sd", 7) != 0 || path[7] < 'a' || path[7] > 'z') return -1;
    const char *rest = path + 8;
    int partition = 0;
    if (*rest) {
        char *end;
        long n = strtol(rest, &end, 10);
        if (*end || n < 1 || n > 15) return -1;
        partition = (int)n;
    }
    int fd = (int)syscall(SYS_openat, AT_FDCWD, CONFIG, O_RDONLY | O_CLOEXEC);
    if (fd < 0) return -1;
    char *text = malloc(32768);
    ssize_t size = text ? syscall(SYS_read, fd, text, 32767) : -1;
    syscall(SYS_close, fd);
    if (size <= 0) { free(text); return -1; }
    text[size] = '\0';
    int found = -1;
    if (socket_name) socket_name[0] = '\0';
    char *cursor = NULL;
    for (char *line = strtok_r(text, "\n", &cursor); line != NULL; line = strtok_r(NULL, "\n", &cursor)) {
        if (strncmp(line, "socket ", 7) == 0) {
            if (socket_name) snprintf(socket_name, socket_size, "%s", line + 7);
            continue;
        }
        if (strncmp(line, "disk ", 5) != 0) continue;
        char node[16], device[256], parts[2048];
        long long bytes, sector;
        int readonly;
        if (sscanf(line + 5, "%15s %255s %lld %lld %d %2047s", node, device, &bytes, &sector, &readonly, parts) != 6) continue;
        if (strncmp(node, path + 5, 3) != 0 || strlen(node) != 3) continue;
        if (partition > 0) {
            /* The partition must be in the table Dextop read. */
            int listed = 0;
            for (char *p = parts; *p && *p != '-';) {
                if (atoi(p) == partition) { listed = 1; break; }
                p = strchr(p, ',');
                if (p == NULL) break;
                p++;
            }
            if (!listed) continue;
        }
        snprintf(entry->node, sizeof(entry->node), "%s", path + 5);
        snprintf(entry->device, sizeof(entry->device), "%s", device);
        entry->index = node[2] - 'a';
        entry->partition = partition;
        entry->readonly = readonly;
        found = 0;
    }
    free(text);
    return found;
}

static void fill_stat(struct stat *st, int minor) {
    memset(st, 0, sizeof(*st));
    st->st_mode = S_IFBLK | 0660;
    st->st_rdev = makedev(SCSI_DISK_MAJOR, minor);
    st->st_nlink = 1;
    st->st_uid = getuid();
    st->st_gid = getgid();
    st->st_blksize = 4096;
}

int dextop_disk_stat(const char *path, struct stat *st) {
    struct entry entry;
    if (lookup(path, &entry, NULL, 0) != 0) return -1;
    fill_stat(st, entry.index * 16 + entry.partition);
    return 0;
}

int dextop_disk_fstat(int fd, struct stat *st) {
    struct disk *disk = disk_of(fd);
    if (disk == NULL) return -1;
    fill_stat(st, (int)disk->minor);
    return 0;
}

/* access() and friends: libblkid's cache (blkid, findfs, mount helpers) checks a disk exists so. */
typedef int (*access_fn)(const char *, int);
typedef int (*faccessat_fn)(int, const char *, int, int);
static int disk_access(const char *path, int mode) {
    struct entry entry;
    if (path == NULL || strncmp(path, "/dev/sd", 7) != 0 || lookup(path, &entry, NULL, 0) != 0) return 1;
    if ((mode & W_OK) && entry.readonly) { errno = EROFS; return -1; }
    return 0;
}

int access(const char *path, int mode) {
    int listed = disk_access(path, mode);
    if (listed <= 0) return listed;
    static access_fn next;
    if (!next) next = (access_fn)dlsym(RTLD_NEXT, "access");
    return next(path, mode);
}

int euidaccess(const char *path, int mode) {
    int listed = disk_access(path, mode);
    if (listed <= 0) return listed;
    static access_fn next;
    if (!next) next = (access_fn)dlsym(RTLD_NEXT, "euidaccess");
    return next(path, mode);
}

int eaccess(const char *path, int mode) { return euidaccess(path, mode); }

int faccessat(int dir, const char *path, int mode, int flags) {
    int listed = path && path[0] == '/' ? disk_access(path, mode) : 1;
    if (listed <= 0) return listed;
    static faccessat_fn next;
    if (!next) next = (faccessat_fn)dlsym(RTLD_NEXT, "faccessat");
    return next(dir, path, mode, flags);
}

/* Opens [path] when it is a listed disk; -2 when it is none. */
int dextop_disk_open(const char *path, int flags) {
    struct entry entry;
    char socket_name[108];
    if (lookup(path, &entry, socket_name, sizeof(socket_name)) != 0 || !socket_name[0]) return -2;
    if (entry.readonly && (flags & O_ACCMODE) != O_RDONLY) { errno = EROFS; return -1; }
    int fd = (int)syscall(SYS_socket, AF_UNIX, SOCK_STREAM | SOCK_CLOEXEC, 0);
    if (fd < 0) return -1;
    /* Named, so that processes inheriting the descriptor know it for a disk. */
    static int counter;
    struct sockaddr_un own = {.sun_family = AF_UNIX};
    int own_length = snprintf(own.sun_path + 1, sizeof(own.sun_path) - 1, PREFIX "%d-%d-%lx", (int)getpid(),
                              __atomic_add_fetch(&counter, 1, __ATOMIC_RELAXED), (unsigned long)random());
    struct sockaddr_un address = {.sun_family = AF_UNIX};
    size_t name = strlen(socket_name);
    if (name > sizeof(address.sun_path) - 2 ||
        syscall(SYS_bind, fd, &own, (socklen_t)(offsetof(struct sockaddr_un, sun_path) + 1 + own_length)) != 0) {
        syscall(SYS_close, fd);
        errno = EACCES;
        return -1;
    }
    memcpy(address.sun_path + 1, socket_name, name);
    char line[320];
    int size = snprintf(line, sizeof(line), "B %s %d\n", entry.device, entry.partition);
    char status = 1;
    struct disk *disk = calloc(1, sizeof(*disk));
    if (disk == NULL ||
        syscall(SYS_connect, fd, &address, (socklen_t)(offsetof(struct sockaddr_un, sun_path) + 1 + name)) != 0 ||
        send_all(fd, line, (size_t)size) != 0 || recv_all(fd, &status, 1) != 0 || status != 0 || read_info(fd, disk) != 0) {
        syscall(SYS_close, fd);
        free(disk);
        errno = status == 2 ? EBUSY : status == 1 ? ENXIO : EIO; /* 2: mounted through Android */
        return -1;
    }
    pthread_mutex_init(&disk->lock, NULL);
    if (fd >= MAX_FDS) { syscall(SYS_close, fd); free(disk); errno = EMFILE; return -1; }
    if (!(flags & O_CLOEXEC)) fcntl(fd, F_SETFD, 0);
    pthread_mutex_lock(&table);
    free(disks[fd]);
    disks[fd] = disk;
    pthread_mutex_unlock(&table);
    return fd;
}

void dextop_disk_close(int fd) {
    if (fd < 0 || fd >= MAX_FDS || disks[fd] == NULL) return;
    pthread_mutex_lock(&table);
    struct disk *disk = disks[fd];
    disks[fd] = NULL;
    pthread_mutex_unlock(&table);
    /* Another thread may be inside a request on it; a disk record is small, keep it. */
    (void)disk;
}

/* --- I/O --- */

static ssize_t disk_pread(int fd, struct disk *disk, void *buffer, size_t count, int64_t offset, int positioned) {
    size_t done = 0;
    while (done < count) {
        size_t chunk = count - done < CHUNK ? count - done : CHUNK;
        int64_t n = request(fd, disk, positioned ? 'p' : 'r', offset + (int64_t)done, (int64_t)chunk, NULL, (char *)buffer + done);
        if (n < 0) return done > 0 ? (ssize_t)done : -1;
        done += (size_t)n;
        if ((size_t)n < chunk) break;
    }
    return (ssize_t)done;
}

static ssize_t disk_pwrite(int fd, struct disk *disk, const void *buffer, size_t count, int64_t offset, int positioned) {
    size_t done = 0;
    while (done < count) {
        size_t chunk = count - done < CHUNK ? count - done : CHUNK;
        int64_t n = request(fd, disk, positioned ? 'w' : 'W', offset + (int64_t)done, (int64_t)chunk, (const char *)buffer + done, NULL);
        if (n < 0) return done > 0 ? (ssize_t)done : -1;
        done += (size_t)n;
        if ((size_t)n < chunk) break;
    }
    if (done == 0 && count > 0) { errno = ENOSPC; return -1; }
    return (ssize_t)done;
}

#define NEXT(name, type) static type next_##name; if (!next_##name) next_##name = (type)dlsym(RTLD_NEXT, #name)

typedef ssize_t (*read_fn)(int, void *, size_t);
ssize_t read(int fd, void *buffer, size_t count) {
    struct disk *disk = disk_of(fd);
    if (disk) return disk_pread(fd, disk, buffer, count, 0, 0);
    NEXT(read, read_fn);
    return next_read(fd, buffer, count);
}

ssize_t __read_chk(int fd, void *buffer, size_t count, size_t size) { (void)size; return read(fd, buffer, count); }

/* write() is defined in linux_android_pads.c (controllers), which asks here first. */
ssize_t dextop_disk_write(int fd, const void *buffer, size_t count, int *handled) {
    struct disk *disk = disk_of(fd);
    *handled = disk != NULL;
    return disk ? disk_pwrite(fd, disk, buffer, count, 0, 0) : 0;
}

typedef ssize_t (*pread_fn)(int, void *, size_t, off_t);
typedef ssize_t (*pwrite_fn)(int, const void *, size_t, off_t);
ssize_t pread(int fd, void *buffer, size_t count, off_t offset) {
    struct disk *disk = disk_of(fd);
    if (disk) return disk_pread(fd, disk, buffer, count, offset, 1);
    NEXT(pread, pread_fn);
    return next_pread(fd, buffer, count, offset);
}

ssize_t pwrite(int fd, const void *buffer, size_t count, off_t offset) {
    struct disk *disk = disk_of(fd);
    if (disk) return disk_pwrite(fd, disk, buffer, count, offset, 1);
    NEXT(pwrite, pwrite_fn);
    return next_pwrite(fd, buffer, count, offset);
}

typedef ssize_t (*pread64_fn)(int, void *, size_t, off64_t);
typedef ssize_t (*pwrite64_fn)(int, const void *, size_t, off64_t);
ssize_t pread64(int fd, void *buffer, size_t count, off64_t offset) {
    struct disk *disk = disk_of(fd);
    if (disk) return disk_pread(fd, disk, buffer, count, offset, 1);
    NEXT(pread64, pread64_fn);
    return next_pread64(fd, buffer, count, offset);
}

ssize_t pwrite64(int fd, const void *buffer, size_t count, off64_t offset) {
    struct disk *disk = disk_of(fd);
    if (disk) return disk_pwrite(fd, disk, buffer, count, offset, 1);
    NEXT(pwrite64, pwrite64_fn);
    return next_pwrite64(fd, buffer, count, offset);
}

ssize_t __pread_chk(int fd, void *buffer, size_t count, off_t offset, size_t size) { (void)size; return pread(fd, buffer, count, offset); }
ssize_t __pread64_chk(int fd, void *buffer, size_t count, off64_t offset, size_t size) { (void)size; return pread64(fd, buffer, count, offset); }

static off64_t disk_seek(int fd, struct disk *disk, off64_t offset, int whence) {
    if (whence != SEEK_SET && whence != SEEK_CUR && whence != SEEK_END) { errno = EINVAL; return -1; }
    int64_t n = request(fd, disk, 's', offset, whence, NULL, NULL);
    return n < 0 ? -1 : n;
}

typedef off_t (*lseek_fn)(int, off_t, int);
typedef off64_t (*lseek64_fn)(int, off64_t, int);
off_t lseek(int fd, off_t offset, int whence) {
    struct disk *disk = disk_of(fd);
    if (disk) return (off_t)disk_seek(fd, disk, offset, whence);
    NEXT(lseek, lseek_fn);
    return next_lseek(fd, offset, whence);
}

off64_t lseek64(int fd, off64_t offset, int whence) {
    struct disk *disk = disk_of(fd);
    if (disk) return disk_seek(fd, disk, offset, whence);
    NEXT(lseek64, lseek64_fn);
    return next_lseek64(fd, offset, whence);
}

static ssize_t vector(int fd, struct disk *disk, const struct iovec *iov, int count, int64_t offset, int positioned, int writing) {
    ssize_t total = 0;
    for (int i = 0; i < count; i++) {
        if (iov[i].iov_len == 0) continue;
        ssize_t n = writing
            ? disk_pwrite(fd, disk, iov[i].iov_base, iov[i].iov_len, offset + total, positioned)
            : disk_pread(fd, disk, iov[i].iov_base, iov[i].iov_len, offset + total, positioned);
        if (n < 0) return total > 0 ? total : -1;
        total += n;
        if ((size_t)n < iov[i].iov_len) break;
    }
    return total;
}

typedef ssize_t (*readv_fn)(int, const struct iovec *, int);
typedef ssize_t (*preadv_fn)(int, const struct iovec *, int, off_t);
typedef ssize_t (*preadv64_fn)(int, const struct iovec *, int, off64_t);
ssize_t readv(int fd, const struct iovec *iov, int count) {
    struct disk *disk = disk_of(fd);
    if (disk) return vector(fd, disk, iov, count, 0, 0, 0);
    NEXT(readv, readv_fn);
    return next_readv(fd, iov, count);
}

ssize_t writev(int fd, const struct iovec *iov, int count) {
    struct disk *disk = disk_of(fd);
    if (disk) return vector(fd, disk, iov, count, 0, 0, 1);
    NEXT(writev, readv_fn);
    return next_writev(fd, iov, count);
}

ssize_t preadv(int fd, const struct iovec *iov, int count, off_t offset) {
    struct disk *disk = disk_of(fd);
    if (disk) return vector(fd, disk, iov, count, offset, 1, 0);
    NEXT(preadv, preadv_fn);
    return next_preadv(fd, iov, count, offset);
}

ssize_t pwritev(int fd, const struct iovec *iov, int count, off_t offset) {
    struct disk *disk = disk_of(fd);
    if (disk) return vector(fd, disk, iov, count, offset, 1, 1);
    NEXT(pwritev, preadv_fn);
    return next_pwritev(fd, iov, count, offset);
}

ssize_t preadv64(int fd, const struct iovec *iov, int count, off64_t offset) {
    struct disk *disk = disk_of(fd);
    if (disk) return vector(fd, disk, iov, count, offset, 1, 0);
    NEXT(preadv64, preadv64_fn);
    return next_preadv64(fd, iov, count, offset);
}

ssize_t pwritev64(int fd, const struct iovec *iov, int count, off64_t offset) {
    struct disk *disk = disk_of(fd);
    if (disk) return vector(fd, disk, iov, count, offset, 1, 1);
    NEXT(pwritev64, preadv64_fn);
    return next_pwritev64(fd, iov, count, offset);
}

typedef int (*fsync_fn)(int);
int fsync(int fd) {
    struct disk *disk = disk_of(fd);
    if (disk) return request(fd, disk, 'f', 0, 0, NULL, NULL) < 0 ? -1 : 0;
    NEXT(fsync, fsync_fn);
    return next_fsync(fd);
}

int fdatasync(int fd) {
    struct disk *disk = disk_of(fd);
    if (disk) return request(fd, disk, 'f', 0, 0, NULL, NULL) < 0 ? -1 : 0;
    NEXT(fdatasync, fsync_fn);
    return next_fdatasync(fd);
}

typedef int (*fadvise_fn)(int, off_t, off_t, int);
int posix_fadvise(int fd, off_t offset, off_t length, int advice) {
    if (disk_of(fd)) return 0;
    NEXT(posix_fadvise, fadvise_fn);
    return next_posix_fadvise(fd, offset, length, advice);
}

typedef int (*fadvise64_fn)(int, off64_t, off64_t, int);
int posix_fadvise64(int fd, off64_t offset, off64_t length, int advice) {
    if (disk_of(fd)) return 0;
    NEXT(posix_fadvise64, fadvise64_fn);
    return next_posix_fadvise64(fd, offset, length, advice);
}

/* --- block-device ioctls (linux/fs.h, linux/hdreg.h) --- */

struct hd_geometry_compat {
    unsigned char heads;
    unsigned char sectors;
    unsigned short cylinders;
    unsigned long start;
};

/* 1 when [request] is a block-device ioctl on a disk; its result is in [result]. */
int dextop_disk_ioctl(int fd, unsigned long request_code, void *arg, int *result) {
    struct disk *disk = disk_of(fd);
    if (disk == NULL) return 0;
    unsigned int type = (request_code >> 8) & 0xff, nr = request_code & 0xff;
    *result = 0;
    if (type == 0x12) {
        switch (nr) {
            case 114: *(uint64_t *)arg = (uint64_t)disk->size; return 1;              /* BLKGETSIZE64 */
            case 96: *(unsigned long *)arg = (unsigned long)(disk->size / 512); return 1; /* BLKGETSIZE */
            case 104: *(int *)arg = (int)disk->sector; return 1;                       /* BLKSSZGET */
            case 123: *(unsigned int *)arg = (unsigned int)disk->sector; return 1;     /* BLKPBSZGET */
            case 120: *(unsigned int *)arg = (unsigned int)disk->sector; return 1;     /* BLKIOMIN */
            case 121: *(unsigned int *)arg = 0; return 1;                              /* BLKIOOPT */
            case 122: *(int *)arg = 0; return 1;                                       /* BLKALIGNOFF */
            case 94: *(int *)arg = (int)disk->readonly; return 1;                      /* BLKROGET */
            case 112: *(int *)arg = 4096; return 1;                                    /* BLKBSZGET */
            case 103: *(unsigned short *)arg = 240; return 1;                          /* BLKSECTGET */
            case 99: *(long *)arg = 256; return 1;                                     /* BLKRAGET */
            case 124: *(unsigned int *)arg = 0; return 1;                              /* BLKDISCARDZEROES */
            case 95: /* BLKRRPART: Dextop reads the table again */
                return request(fd, disk, 'P', 0, 0, NULL, NULL) < 0 ? (*result = -1, 1) : 1;
            case 97: case 113: case 98: case 93: /* BLKFLSBUF, BLKBSZSET, BLKRASET, BLKROSET */
                if (nr == 97) request(fd, disk, 'f', 0, 0, NULL, NULL);
                return 1;
            case 105: /* BLKPG: partitions added or removed by parted; Dextop rereads the table */
                request(fd, disk, 'P', 0, 0, NULL, NULL);
                return 1;
            default:
                errno = (nr == 119 || nr == 127) ? EOPNOTSUPP : ENOTTY; /* BLKDISCARD, BLKZEROOUT */
                *result = -1;
                return 1;
        }
    }
    /* SCSI_IOCTL_GET_IDLUN and SCSI_IOCTL_GET_BUS_NUMBER: parted identifies SCSI disks by them
     * (host 0, channel 0, target = disk number, LUN 0). Other SCSI/SG ioctls stay unsupported:
     * parted then names the disk from sysfs or as a generic SCSI disk. */
    if (request_code == 0x5382) {
        int *idlun = arg;
        idlun[0] = (int)(disk->minor / 16) & 0xff;
        idlun[1] = 0;
        return 1;
    }
    if (request_code == 0x5386) { *(int *)arg = 0; return 1; }
    if (type == 0x03 && nr == 0x01) { /* HDIO_GETGEO */
        struct hd_geometry_compat *geometry = arg;
        int64_t sectors = disk->size / 512;
        geometry->heads = 255;
        geometry->sectors = 63;
        geometry->cylinders = (unsigned short)(sectors / (255 * 63) > 65535 ? 65535 : sectors / (255 * 63));
        geometry->start = (unsigned long)disk->start;
        return 1;
    }
    errno = ENOTTY;
    *result = -1;
    return 1;
}

#include "linux_android_disk_mount.inc"
