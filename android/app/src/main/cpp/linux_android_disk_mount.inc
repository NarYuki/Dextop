/* Included by linux_android_disk.c.
 *
 * Mounting through Android (LinuxUsbDisk.mount): there is no kernel to mount a file system in,
 * so "mount /dev/sdX1 /mnt/usb" asks Dextop. The stick goes back to Android, whose kernel mounts
 * it (FAT, exFAT, NTFS) at /storage/<UUID>, seen in Linux at /media/storage/<UUID>; the mount
 * point becomes a link to it. "umount" asks Android to unmount and turns the link back into an
 * empty directory. /tmp/dextop-mounts records the links: "<UUID> <mount point>".
 */
#include <strings.h>

#define MOUNTS "/tmp/dextop-mounts"
#define VOLUMES "/media/storage/"

static void say(const char *message) {
    char line[600];
    int n = snprintf(line, sizeof(line), "dextop: %s\n", message);
    if (n >= (int)sizeof(line)) n = (int)sizeof(line) - 1;
    if (n > 0) syscall(SYS_write, 2, line, (size_t)n);
}

/* The bridge's socket name from the manifest; 0 when found. */
static int bridge_name(char *socket_name, size_t size) {
    int config = (int)syscall(SYS_openat, AT_FDCWD, CONFIG, O_RDONLY | O_CLOEXEC);
    if (config < 0) return -1;
    char *manifest = malloc(32768);
    ssize_t length = manifest ? syscall(SYS_read, config, manifest, 32767) : -1;
    syscall(SYS_close, config);
    socket_name[0] = '\0';
    if (length > 0) {
        manifest[length] = '\0';
        char *cursor = NULL;
        for (char *line = strtok_r(manifest, "\n", &cursor); line != NULL; line = strtok_r(NULL, "\n", &cursor))
            if (strncmp(line, "socket ", 7) == 0) snprintf(socket_name, size, "%s", line + 7);
    }
    free(manifest);
    return socket_name[0] ? 0 : -1;
}

/* Sends [request] to Dextop; returns the answer line's errno, its text in [text]. */
static int ask(const char *request, char *text, size_t size) {
    char socket_name[108];
    if (bridge_name(socket_name, sizeof(socket_name)) != 0) return EIO;
    int fd = (int)syscall(SYS_socket, AF_UNIX, SOCK_STREAM | SOCK_CLOEXEC, 0);
    if (fd < 0) return EIO;
    struct sockaddr_un address = {.sun_family = AF_UNIX};
    size_t name = strlen(socket_name);
    memcpy(address.sun_path + 1, socket_name, name);
    char line[600];
    int n = snprintf(line, sizeof(line), "%s\n", request);
    int status = EIO;
    if (syscall(SYS_connect, fd, &address, (socklen_t)(offsetof(struct sockaddr_un, sun_path) + 1 + name)) == 0 &&
        send_all(fd, line, (size_t)n) == 0) {
        size_t used = 0;
        char c;
        while (used + 1 < sizeof(line) && recv_all(fd, &c, 1) == 0 && c != '\n') line[used++] = c;
        line[used] = '\0';
        if (used > 0) {
            char *space = strchr(line, ' ');
            status = atoi(line);
            snprintf(text, size, "%s", space ? space + 1 : "");
        }
    }
    syscall(SYS_close, fd);
    return status;
}

static void record(const char *uuid, const char *target) {
    int fd = (int)syscall(SYS_openat, AT_FDCWD, MOUNTS, O_WRONLY | O_CREAT | O_APPEND | O_CLOEXEC, 0644);
    if (fd < 0) return;
    char line[700];
    int n = snprintf(line, sizeof(line), "%s %s\n", uuid, target);
    if (n > 0 && n < (int)sizeof(line)) syscall(SYS_write, fd, line, (size_t)n);
    syscall(SYS_close, fd);
}

/* Turns every link to volume [uuid] back into an empty directory and forgets it. */
static void unlink_volume(const char *uuid) {
    int fd = (int)syscall(SYS_openat, AT_FDCWD, MOUNTS, O_RDONLY | O_CLOEXEC);
    if (fd < 0) return;
    char *text = malloc(65536);
    ssize_t length = text ? syscall(SYS_read, fd, text, 65535) : -1;
    syscall(SYS_close, fd);
    if (length <= 0) { free(text); return; }
    text[length] = '\0';
    char *kept = malloc((size_t)length + 1);
    size_t used = 0;
    char *cursor = NULL;
    for (char *line = strtok_r(text, "\n", &cursor); line != NULL; line = strtok_r(NULL, "\n", &cursor)) {
        char id[128], target[512];
        if (sscanf(line, "%127s %511[^\n]", id, target) != 2) continue;
        if (strcasecmp(id, uuid) == 0) {
            char link[512];
            if (syscall(SYS_readlinkat, AT_FDCWD, target, link, sizeof(link) - 1) > 0) {
                syscall(SYS_unlinkat, AT_FDCWD, target, 0);
                syscall(SYS_mkdirat, AT_FDCWD, target, 0755);
            }
        } else if (kept) {
            used += (size_t)snprintf(kept + used, (size_t)length + 1 - used, "%s\n", line);
        }
    }
    fd = (int)syscall(SYS_openat, AT_FDCWD, MOUNTS, O_WRONLY | O_TRUNC | O_CLOEXEC);
    if (fd >= 0) {
        if (kept && used) syscall(SYS_write, fd, kept, used);
        syscall(SYS_close, fd);
    }
    free(kept);
    free(text);
}

typedef int (*mount_fn)(const char *, const char *, const char *, unsigned long, const void *);
int mount(const char *source, const char *target, const char *type, unsigned long flags, const void *data) {
    struct entry entry;
    /* MS_REMOUNT, MS_BIND, MS_MOVE and propagation changes are not about a listed disk. */
    const unsigned long other = 0x20 | 0x1000 | 0x2000 | 0x20000 | 0x40000 | 0x80000 | 0x100000;
    if (source == NULL || target == NULL || (flags & other) || strncmp(source, "/dev/sd", 7) != 0 ||
        lookup(source, &entry, NULL, 0) != 0) {
        static mount_fn next;
        if (!next) next = (mount_fn)dlsym(RTLD_NEXT, "mount");
        return next(source, target, type, flags, data);
    }
    struct stat st;
    if (fstatat(AT_FDCWD, target, &st, AT_SYMLINK_NOFOLLOW) != 0) return -1;
    if (!S_ISDIR(st.st_mode) && !S_ISLNK(st.st_mode)) { errno = ENOTDIR; return -1; }
    /* A real mount hides what the directory holds; a link cannot, so the directory must be empty. */
    if (S_ISDIR(st.st_mode) && syscall(SYS_unlinkat, AT_FDCWD, target, AT_REMOVEDIR) != 0) {
        if (errno == ENOTEMPTY || errno == EEXIST) say("the mount point must be an empty directory");
        return -1;
    }
    char request[400], answer[512] = "";
    snprintf(request, sizeof(request), "M %s %d", entry.device, entry.partition);
    int status = ask(request, answer, sizeof(answer));
    if (status != 0 || strncmp(answer, VOLUMES, sizeof(VOLUMES) - 1) != 0) {
        if (S_ISDIR(st.st_mode)) syscall(SYS_mkdirat, AT_FDCWD, target, st.st_mode & 07777);
        if (answer[0]) say(answer);
        errno = status ? status : EIO;
        return -1;
    }
    if (S_ISLNK(st.st_mode)) syscall(SYS_unlinkat, AT_FDCWD, target, 0);
    if (syscall(SYS_symlinkat, answer, AT_FDCWD, target) != 0) return -1;
    record(answer + sizeof(VOLUMES) - 1, target);
    return 0;
}

/* The volume behind [target]: /media/storage/<UUID> itself (umount resolves links) or a link to it. */
static int volume_of(const char *target, char *uuid, size_t size) {
    char link[512];
    const char *path = target;
    if (strncmp(target, VOLUMES, sizeof(VOLUMES) - 1) != 0) {
        ssize_t n = syscall(SYS_readlinkat, AT_FDCWD, target, link, sizeof(link) - 1);
        if (n <= 0) return -1;
        link[n] = '\0';
        if (strncmp(link, VOLUMES, sizeof(VOLUMES) - 1) != 0) return -1;
        path = link;
    }
    const char *id = path + sizeof(VOLUMES) - 1;
    size_t length = strcspn(id, "/");
    if (length == 0 || length >= size || id[length] != '\0' || strcmp(id, "emulated") == 0 || strcmp(id, "self") == 0) return -1;
    memcpy(uuid, id, length);
    uuid[length] = '\0';
    return 0;
}

/* 1 when [target] is no volume Linux mounted through Android. */
static int dextop_umount(const char *target) {
    char uuid[128], request[200], answer[512] = "";
    if (target == NULL || volume_of(target, uuid, sizeof(uuid)) != 0) return 1;
    snprintf(request, sizeof(request), "N %s", uuid);
    int status = ask(request, answer, sizeof(answer));
    if (status != 0) {
        if (answer[0]) say(answer);
        errno = status;
        return -1;
    }
    unlink_volume(uuid);
    return 0;
}

typedef int (*umount_fn)(const char *);
typedef int (*umount2_fn)(const char *, int);
int umount(const char *target) {
    int result = dextop_umount(target);
    if (result <= 0) return result;
    static umount_fn next;
    if (!next) next = (umount_fn)dlsym(RTLD_NEXT, "umount");
    return next(target);
}

int umount2(const char *target, int flags) {
    int result = dextop_umount(target);
    if (result <= 0) return result;
    static umount2_fn next;
    if (!next) next = (umount2_fn)dlsym(RTLD_NEXT, "umount2");
    return next(target, flags);
}

/*
 * The new mount API (fsopen, fsmount, move_mount, open_tree...) cannot work in the container,
 * and libmount (util-linux 2.39+) tries it first, failing with "permission denied" before it
 * would call mount(): reporting it missing makes libmount use mount(), which Dextop answers.
 */
static int no_new_mount_api(void) { errno = ENOSYS; return -1; }
int fsopen(const char *name, unsigned int flags) { (void)name; (void)flags; return no_new_mount_api(); }
int fsmount(int fd, unsigned int flags, unsigned int attributes) { (void)fd; (void)flags; (void)attributes; return no_new_mount_api(); }
int fspick(int dir, const char *path, unsigned int flags) { (void)dir; (void)path; (void)flags; return no_new_mount_api(); }
int fsconfig(int fd, unsigned int command, const char *key, const void *value, int aux) {
    (void)fd; (void)command; (void)key; (void)value; (void)aux; return no_new_mount_api();
}
int open_tree(int dir, const char *path, unsigned int flags) { (void)dir; (void)path; (void)flags; return no_new_mount_api(); }
int move_mount(int from_dir, const char *from, int to_dir, const char *to, unsigned int flags) {
    (void)from_dir; (void)from; (void)to_dir; (void)to; (void)flags; return no_new_mount_api();
}
int mount_setattr(int dir, const char *path, unsigned int flags, void *attributes, size_t size) {
    (void)dir; (void)path; (void)flags; (void)attributes; (void)size; return no_new_mount_api();
}
