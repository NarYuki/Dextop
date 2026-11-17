/*
 * Container escape probe for Dextop Linux containers (privileged and Android mode).
 * Static aarch64 binary, raw syscalls; run INSIDE the guest. Read-only/non-destructive:
 * it never kills, writes into or modifies Android processes or files.
 * Output: one line per check: "<ESCAPE|BLOCKED|EXPOSED|INFO> <id>: detail".
 *   ESCAPE  = reached Android-side state the guest must not reach
 *   EXPOSED = reachable by design/known exposure (reported, judged in the report)
 */
#define _GNU_SOURCE
#include <dirent.h>
#include <errno.h>
#include <fcntl.h>
#include <linux/io_uring.h>
#include <linux/android/binder.h>
#include <sched.h>
#include <signal.h>
#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/mman.h>
#include <sys/ptrace.h>
#include <sys/socket.h>
#include <sys/stat.h>
#include <sys/syscall.h>
#include <sys/uio.h>
#include <sys/un.h>
#include <sys/wait.h>
#include <unistd.h>

static void out(const char *v, const char *id, const char *fmt, ...) {
    va_list ap; va_start(ap, fmt);
    printf("%-7s %s: ", v, id); vprintf(fmt, ap); printf("\n"); fflush(stdout); va_end(ap);
}

/* Host-only files: a guest Debian rootfs has none of these. Content proves the host file. */
static const char *HOST_FILES[] = { "/system/build.prop", "/vendor/build.prop", "/system/bin/app_process64",
    "/apex/com.android.runtime/apex_manifest.pb", "/data/system/packages.list", "/proc/1/root/system/build.prop", NULL };

static int is_host_content(const char *buf, ssize_t n, const char *path) {
    if (n <= 0) return 0;
    if (strstr(path, "build.prop")) return memmem(buf, n, "ro.", 3) != NULL;
    if (strstr(path, "app_process")) return n >= 4 && !memcmp(buf, "\177ELF", 4);
    return 1;
}

static int try_open_host(const char *path, int dirfd) {
    int fd = (int)syscall(SYS_openat, dirfd, path, O_RDONLY | O_CLOEXEC);
    if (fd < 0) return -errno;
    char buf[256]; ssize_t n = read(fd, buf, sizeof buf); close(fd);
    return is_host_content(buf, n, path) ? 1 : 0;
}

static void check_path_views(void) {
    for (int i = 0; HOST_FILES[i]; i++) {
        int r = try_open_host(HOST_FILES[i], AT_FDCWD);
        if (r == 1) out("ESCAPE", "fs-host-file", "%s readable with host content", HOST_FILES[i]);
        else out("BLOCKED", "fs-host-file", "%s -> %s", HOST_FILES[i], r < 0 ? strerror(-r) : "guest file");
    }
    /* .. beyond the guest root, absolute and from an opened directory. */
    char deep[512] = "/"; for (int i = 0; i < 40; i++) strcat(deep, "../");
    strcat(deep, "system/build.prop");
    int r = try_open_host(deep, AT_FDCWD);
    out(r == 1 ? "ESCAPE" : "BLOCKED", "fs-dotdot-root", "%s", r == 1 ? "host build.prop via /../" : "clamped at guest root");
    int root = open("/", O_RDONLY | O_DIRECTORY);
    r = try_open_host(deep + 1, root); close(root);
    out(r == 1 ? "ESCAPE" : "BLOCKED", "fs-dotdot-dirfd", "%s", r == 1 ? "host build.prop via dirfd ../" : "clamped");
    /* Symlink created by the guest pointing at a host path. */
    char link[] = "/tmp/.dextop-escape-link"; unlink(link);
    if (!symlink("/../../../../system/build.prop", link)) {
        r = try_open_host(link, AT_FDCWD); unlink(link);
        out(r == 1 ? "ESCAPE" : "BLOCKED", "fs-symlink", "%s", r == 1 ? "symlink resolved on host" : "resolved in guest");
    }
    /* /proc/<pid>/root and /cwd of every visible process. */
    DIR *d = opendir("/proc"); struct dirent *e; int tried = 0, hits = 0; char first[64] = "";
    while (d && (e = readdir(d))) {
        if (e->d_name[0] < '0' || e->d_name[0] > '9') continue;
        char p[128];
        snprintf(p, sizeof p, "/proc/%s/root/system/build.prop", e->d_name); tried++;
        if (try_open_host(p, AT_FDCWD) == 1) { hits++; if (!*first) snprintf(first, sizeof first, "pid %s", e->d_name); }
        snprintf(p, sizeof p, "/proc/%s/cwd/../../../../system/build.prop", e->d_name);
        if (try_open_host(p, AT_FDCWD) == 1) { hits++; if (!*first) snprintf(first, sizeof first, "pid %s cwd", e->d_name); }
    }
    if (d) closedir(d);
    out(hits ? "ESCAPE" : "BLOCKED", "fs-proc-root", "%d of %d processes' /proc/<pid>/root|cwd reach host %s", hits, tried, first);
}

/* Files held open by processes outside the guest (e.g. Android services), via /proc/<pid>/fd. */
static void check_proc_fds(void) {
    DIR *d = opendir("/proc"); struct dirent *e; int opened = 0, seen = 0; char ex[256] = "";
    while (d && (e = readdir(d))) {
        if (e->d_name[0] < '0' || e->d_name[0] > '9') continue;
        char dir[64]; snprintf(dir, sizeof dir, "/proc/%s/fd", e->d_name);
        DIR *f = opendir(dir); struct dirent *g;
        while (f && (g = readdir(f))) {
            if (g->d_name[0] == '.') continue;
            char p[128], target[256]; snprintf(p, sizeof p, "%s/%s", dir, g->d_name);
            ssize_t n = readlink(p, target, sizeof target - 1); if (n <= 0) continue; target[n] = 0;
            /* Only Android-side paths matter: guest paths are translated (start in the guest tree). */
            if (strncmp(target, "/data/", 6) && strncmp(target, "/system/", 8) && strncmp(target, "/apex/", 6) &&
                strncmp(target, "/vendor/", 8) && strncmp(target, "/storage/", 9)) continue;
            seen++;
            int fd = open(p, O_RDONLY | O_CLOEXEC | O_NONBLOCK);
            if (fd >= 0) { opened++; if (!*ex) snprintf(ex, sizeof ex, "pid %s fd %s -> %s", e->d_name, g->d_name, target); close(fd); }
        }
        if (f) closedir(f);
    }
    if (d) closedir(d);
    out(opened ? "ESCAPE" : "BLOCKED", "fs-proc-fd", "%d of %d Android-path descriptors of other processes opened %s", opened, seen, ex);
}

/* clone(CLONE_UNTRACED): a child the tracer does not follow would see the real filesystem. */
static void check_untraced_clone(void) {
    int pfd[2]; if (pipe(pfd)) return;
    pid_t pid = (pid_t)syscall(SYS_clone, CLONE_UNTRACED | SIGCHLD, 0, 0, 0, 0);
    if (pid == 0) {
        char res = (char)('0' + (try_open_host("/system/build.prop", AT_FDCWD) == 1));
        char tracer[16] = "?"; FILE *s = fopen("/proc/self/status", "r"); char line[128];
        while (s && fgets(line, sizeof line, s)) if (!strncmp(line, "TracerPid:", 10)) { sscanf(line + 10, "%15s", tracer); }
        if (s) fclose(s);
        write(pfd[1], &res, 1); write(pfd[1], tracer, strlen(tracer)); _exit(0);
    }
    close(pfd[1]);
    char buf[32] = {0}; read(pfd[0], buf, sizeof buf - 1); close(pfd[0]);
    if (pid > 0) waitpid(pid, NULL, 0);
    if (pid < 0) { out("BLOCKED", "ptrace-clone-untraced", "clone failed: %s", strerror(errno)); return; }
    out(buf[0] == '1' ? "ESCAPE" : "BLOCKED", "ptrace-clone-untraced", "child TracerPid=%s host file %s",
        buf + 1, buf[0] == '1' ? "READ" : "not reachable");
}

/* io_uring operations are not seen by a ptrace-based tracer. */
static void check_io_uring(void) {
    struct io_uring_params p; memset(&p, 0, sizeof p);
    int ring = (int)syscall(SYS_io_uring_setup, 4, &p);
    if (ring < 0) { out("BLOCKED", "io_uring", "io_uring_setup: %s", strerror(errno)); return; }
    size_t sqsz = p.sq_off.array + p.sq_entries * sizeof(unsigned), cqsz = p.cq_off.cqes + p.cq_entries * sizeof(struct io_uring_cqe);
    char *sq = mmap(0, sqsz, PROT_READ | PROT_WRITE, MAP_SHARED | MAP_POPULATE, ring, IORING_OFF_SQ_RING);
    char *cq = (p.features & IORING_FEAT_SINGLE_MMAP) ? sq : mmap(0, cqsz, PROT_READ | PROT_WRITE, MAP_SHARED | MAP_POPULATE, ring, IORING_OFF_CQ_RING);
    struct io_uring_sqe *sqes = mmap(0, p.sq_entries * sizeof *sqes, PROT_READ | PROT_WRITE, MAP_SHARED | MAP_POPULATE, ring, IORING_OFF_SQES);
    if (sq == MAP_FAILED || cq == MAP_FAILED || sqes == MAP_FAILED) { out("BLOCKED", "io_uring", "mmap failed"); close(ring); return; }
    memset(&sqes[0], 0, sizeof sqes[0]);
    sqes[0].opcode = IORING_OP_OPENAT; sqes[0].fd = AT_FDCWD; sqes[0].addr = (uint64_t)(uintptr_t)"/system/build.prop";
    sqes[0].open_flags = O_RDONLY;
    unsigned *tail = (unsigned *)(sq + p.sq_off.tail), *arr = (unsigned *)(sq + p.sq_off.array), mask = *(unsigned *)(sq + p.sq_off.ring_mask);
    arr[*tail & mask] = 0; __atomic_store_n(tail, *tail + 1, __ATOMIC_RELEASE);
    int n = (int)syscall(SYS_io_uring_enter, ring, 1, 1, IORING_ENTER_GETEVENTS, NULL, 0);
    struct io_uring_cqe *cqe = (struct io_uring_cqe *)(cq + p.cq_off.cqes);
    int res = n < 0 ? -errno : cqe[*(unsigned *)(cq + p.cq_off.head) & *(unsigned *)(cq + p.cq_off.ring_mask)].res;
    if (res >= 0) {
        char buf[256]; ssize_t r = read(res, buf, sizeof buf); close(res);
        out(is_host_content(buf, r, "build.prop") ? "ESCAPE" : "BLOCKED", "io_uring", "IORING_OP_OPENAT /system/build.prop %s",
            is_host_content(buf, r, "build.prop") ? "opened HOST file" : "opened guest file");
    } else out("BLOCKED", "io_uring", "IORING_OP_OPENAT -> %s", strerror(-res));
    close(ring);
}

static int tracer_pid(void) {
    FILE *s = fopen("/proc/self/status", "r"); char line[128]; int t = 0;
    while (s && fgets(line, sizeof line, s)) if (!strncmp(line, "TracerPid:", 10)) t = atoi(line + 10);
    if (s) fclose(s); return t;
}

/* Attach to (then immediately detach from) a process; read its memory. Never writes or kills. */
static void probe_process(const char *id, int pid, const char *label) {
    char p[64]; snprintf(p, sizeof p, "/proc/%d/mem", pid);
    long a = ptrace(PTRACE_SEIZE, pid, 0, 0); int ae = errno;
    if (a == 0) ptrace(PTRACE_DETACH, pid, 0, 0);
    char maps[64]; snprintf(maps, sizeof maps, "/proc/%d/maps", pid);
    unsigned long start = 0; FILE *m = fopen(maps, "r"); int me = errno;
    if (m) { fscanf(m, "%lx", &start); fclose(m); }
    char buf[16]; struct iovec l = { buf, sizeof buf }, r = { (void *)start, sizeof buf };
    ssize_t v = start ? process_vm_readv(pid, &l, 1, &r, 1, 0) : -1; int ve = errno;
    int fd = open(p, O_RDONLY); ssize_t mr = -1; int mre = errno;
    if (fd >= 0) { mr = pread(fd, buf, sizeof buf, (off_t)start); mre = errno; close(fd); }
    int bad = a == 0 || v > 0 || mr > 0;
    out(bad ? "ESCAPE" : "BLOCKED", id, "%s pid %d: ptrace %s, maps %s, process_vm_readv %s, /proc/mem %s", label, pid,
        a == 0 ? "ATTACHED" : strerror(ae), m ? "readable" : strerror(me), v > 0 ? "READ" : strerror(ve), mr > 0 ? "READ" : strerror(mre));
}

static void check_tracer_and_host_processes(char **targets) {
    int t = tracer_pid();
    out(t ? "INFO" : "ESCAPE", "ptrace-traced", "TracerPid=%d%s", t, t ? "" : " (guest runs untraced: no path translation)");
    if (t) probe_process("ptrace-tracer", t, "PRoot tracer");
    for (int i = 0; targets && targets[i]; i++) {
        char *eq = strchr(targets[i], '='); if (!eq) continue;
        *eq = 0; probe_process("ptrace-host", atoi(eq + 1), targets[i]); *eq = '=';
    }
}

static void check_binder(void) {
    const char *nodes[] = { "/dev/binder", "/dev/hwbinder", "/dev/vndbinder", "/dev/binderfs/binder", NULL };
    for (int i = 0; nodes[i]; i++) {
        int fd = open(nodes[i], O_RDWR | O_CLOEXEC);
        if (fd < 0) { out("BLOCKED", "binder-open", "%s: %s", nodes[i], strerror(errno)); continue; }
        struct binder_version v = {0}; int r = ioctl(fd, BINDER_VERSION, &v); int er = errno;
        uint32_t threads = 0; int r2 = ioctl(fd, BINDER_SET_MAX_THREADS, &threads); int er2 = errno;
        close(fd);
        out(r == 0 || r2 == 0 ? "ESCAPE" : "BLOCKED", "binder-ioctl", "%s opened; BINDER_VERSION %s, SET_MAX_THREADS %s", nodes[i],
            r == 0 ? "OK" : strerror(er), r2 == 0 ? "OK" : strerror(er2));
    }
    /* Inherited binder descriptors. */
    DIR *d = opendir("/proc/self/fd"); struct dirent *e; int found = 0;
    while (d && (e = readdir(d))) {
        char p[64], t[128]; snprintf(p, sizeof p, "/proc/self/fd/%s", e->d_name);
        ssize_t n = readlink(p, t, sizeof t - 1); if (n > 0) { t[n] = 0; if (strstr(t, "binder")) found++; }
    }
    if (d) closedir(d);
    out(found ? "ESCAPE" : "BLOCKED", "binder-inherited-fd", "%d binder descriptors inherited", found);
}

static int unix_connect(const char *path, int abstract) {
    int s = socket(AF_UNIX, SOCK_STREAM | SOCK_CLOEXEC, 0); if (s < 0) return -errno;
    struct sockaddr_un a = { .sun_family = AF_UNIX }; socklen_t len;
    if (abstract) { a.sun_path[0] = 0; strncpy(a.sun_path + 1, path, sizeof a.sun_path - 2); len = offsetof(struct sockaddr_un, sun_path) + 1 + strlen(path); }
    else { strncpy(a.sun_path, path, sizeof a.sun_path - 1); len = sizeof a; }
    if (connect(s, (struct sockaddr *)&a, len)) {
        int e = errno; close(s);
        if (e == EPROTOTYPE) { s = socket(AF_UNIX, SOCK_SEQPACKET | SOCK_CLOEXEC, 0); if (!connect(s, (struct sockaddr *)&a, len)) return s; e = errno; close(s);
            s = socket(AF_UNIX, SOCK_DGRAM | SOCK_CLOEXEC, 0); if (!connect(s, (struct sockaddr *)&a, len)) return s; e = errno; close(s); }
        return -e;
    }
    return s;
}

static void check_android_sockets(void) {
    const char *socks[] = { "property_service", "property_service_for_system", "logdr", "logdw", "logd", "statsdw", "dnsproxyd",
        "fwmarkd", "netd", "zygote", "zygote_secondary", "usap_pool_primary", "adbd", "lmkd", "tombstoned_crash",
        "tombstoned_intercept", "installd", "vold", "mdns", "traced_producer", "traced_consumer", "heapprofd", NULL };
    for (int i = 0; socks[i]; i++) {
        char p[96]; snprintf(p, sizeof p, "/dev/socket/%s", socks[i]);
        int s = unix_connect(p, 0);
        if (s < 0) { out("BLOCKED", "android-socket", "%s: %s", p, strerror(-s)); continue; }
        if (!strcmp(socks[i], "logdr")) {
            const char *q = "dumpAndClose tail=50 lids=0,2,3,4,5,6,7"; write(s, q, strlen(q));
            char buf[4096]; ssize_t n = read(s, buf, sizeof buf); close(s);
            out(n > 0 ? "EXPOSED" : "BLOCKED", "android-logd-read", "connected; read %zd bytes of the Android log", n);
            continue;
        }
        close(s);
        out(strcmp(socks[i], "dnsproxyd") && strcmp(socks[i], "fwmarkd") && strcmp(socks[i], "logdw") && strcmp(socks[i], "property_service") && strcmp(socks[i], "traced_producer")
            ? "ESCAPE" : "EXPOSED", "android-socket", "%s connected", p);
    }
}

int main(int argc, char **argv) {
    printf("== escape_probe uid=%d euid=%d pid=%d\n", getuid(), geteuid(), getpid());
    check_path_views();
    check_proc_fds();
    check_untraced_clone();
    check_io_uring();
    check_tracer_and_host_processes(argv + 1);   /* args: label=pid of Android-side processes */
    check_binder();
    check_android_sockets();
    return 0;
}
