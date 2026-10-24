#define _GNU_SOURCE
#include <arpa/inet.h>
#include <spawn.h>
#include <dlfcn.h>
#include <fcntl.h>
#include <linux/netlink.h>
#include <netinet/in.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/socket.h>
#include <sys/stat.h>
#include <sys/types.h>
#include <errno.h>
#include <signal.h>
#include <time.h>
#include <unistd.h>
#include "socket_metadata.h"

static const char *net_dir(void) {
  static char dir[256];
  if (!dir[0]) {
    const char *base = getenv("BL_SYSV_DIR");
    snprintf(dir, sizeof(dir), "%s/net", base && *base ? base : "/dev/shm/wnsysv");
    char parent[256];
    snprintf(parent, sizeof(parent), "%s", dir);
    char *slash = strrchr(parent, '/');
    if (slash) {
      *slash = '\0';
      syscall(SYS_mkdirat, AT_FDCWD, parent, 0777);
    }
    syscall(SYS_mkdirat, AT_FDCWD, dir, 0777);
  }
  return dir;
}

static int loopback_family(const struct sockaddr *address, socklen_t length) {
  if (address && address->sa_family == AF_INET && length >= sizeof(struct sockaddr_in)) {
    return ((const struct sockaddr_in *)address)->sin_addr.s_addr == htonl(INADDR_LOOPBACK) ? AF_INET : 0;
  }
  if (address && address->sa_family == AF_INET6 && length >= sizeof(struct sockaddr_in6)) {
    const struct in6_addr *ip = &((const struct sockaddr_in6 *)address)->sin6_addr;
    if (IN6_IS_ADDR_LOOPBACK(ip)) return AF_INET6;
    if (IN6_IS_ADDR_V4MAPPED(ip) && ip->s6_addr[12] == 127 && ip->s6_addr[13] == 0 &&
        ip->s6_addr[14] == 0 && ip->s6_addr[15] == 1) return AF_INET;
  }
  return 0;
}

static void record_port(int fd, unsigned peer) {
  struct sockaddr_storage ss;
  socklen_t len = sizeof(ss);
  if (getsockname(fd, (struct sockaddr *)&ss, &len) != 0) {
    return;
  }
  int family = loopback_family((struct sockaddr *)&ss, len);
  if (!family) return;
  unsigned port;
  if (ss.ss_family == AF_INET) {
    struct sockaddr_in *in = (struct sockaddr_in *)&ss;
    port = ntohs(in->sin_port);
  } else if (ss.ss_family == AF_INET6) {
    port = ntohs(((struct sockaddr_in6 *)&ss)->sin6_port);
  } else {
    return;
  }
  if (port == 0) {
    return;
  }
  struct stat64 socket_stat;
  if (bl_socket_fstat(fd, &socket_stat) != 0) return;
  char path[300];
  snprintf(path, sizeof(path), "%s/p%u", net_dir(), port);
  char staged[360];
  snprintf(staged, sizeof(staged), "%s.tmp.%d.%d", path, (int)getpid(), fd);
  char line[128];
  int n = snprintf(line, sizeof(line), "%d %d %d %u %d %llu %d\n", (int)getpid(), (int)getppid(),
      (int)syscall(SYS_getuid), peer, fd, (unsigned long long)socket_stat.st_ino, family);
  int out = (int)syscall(SYS_openat, AT_FDCWD, staged, O_WRONLY | O_CREAT | O_TRUNC | O_NOFOLLOW, 0600);
  if (out >= 0) {
    int complete = syscall(SYS_write, out, line, n) == n;
    syscall(SYS_close, out);
    if (complete) syscall(SYS_renameat, AT_FDCWD, staged, AT_FDCWD, path);
    syscall(SYS_unlinkat, AT_FDCWD, staged, 0);
  }
}

static unsigned addr_port(const struct sockaddr *addr, socklen_t len) {
  if (addr && addr->sa_family == AF_INET && len >= sizeof(struct sockaddr_in)) {
    return ntohs(((const struct sockaddr_in *)addr)->sin_port);
  }
  if (addr && addr->sa_family == AF_INET6 && len >= sizeof(struct sockaddr_in6)) {
    return ntohs(((const struct sockaddr_in6 *)addr)->sin6_port);
  }
  return 0;
}

static unsigned peer_port(int fd) {
  struct sockaddr_storage ps;
  socklen_t plen = sizeof(ps);
  if (getpeername(fd, (struct sockaddr *)&ps, &plen) != 0) {
    return 0;
  }
  return addr_port((struct sockaddr *)&ps, plen);
}

typedef int (*bind_fn)(int, const struct sockaddr *, socklen_t);
typedef int (*listen_fn)(int, int);
typedef int (*connect_fn)(int, const struct sockaddr *, socklen_t);
typedef int (*accept4_fn)(int, struct sockaddr *, socklen_t *, int);
static unsigned guarded_port(void);

__attribute__((visibility("hidden"))) int bl_udevmon_stand_in(int fd);

int bind(int fd, const struct sockaddr *addr, socklen_t len) {
  static bind_fn real;
  if (!real) real = (bind_fn)dlsym(RTLD_NEXT, "bind");
  if (bl_udevmon_stand_in(fd)) {
    if (addr == NULL || len < sizeof(struct sockaddr_nl) || addr->sa_family != AF_NETLINK) {
      errno = EINVAL;
      return -1;
    }
    return 0;
  }
  struct sockaddr_storage local;
  unsigned guarded = guarded_port();
  if (guarded && addr_port(addr, len) == guarded && len <= sizeof(local)) {
    memcpy(&local, addr, len);
    if (addr->sa_family == AF_INET) {
      ((struct sockaddr_in *)&local)->sin_addr.s_addr = htonl(INADDR_LOOPBACK);
      addr = (struct sockaddr *)&local;
    } else if (addr->sa_family == AF_INET6) {
      ((struct sockaddr_in6 *)&local)->sin6_addr = in6addr_loopback;
      addr = (struct sockaddr *)&local;
    }
  }
  int ret = real(fd, addr, len);
  int saved = errno;
  if (ret == 0) record_port(fd, 0);
  errno = saved;
  return ret;
}

int listen(int fd, int backlog) {
  static listen_fn real;
  if (!real) real = (listen_fn)dlsym(RTLD_NEXT, "listen");
  int ret = real(fd, backlog);
  int saved = errno;
  if (ret == 0) record_port(fd, 0);
  errno = saved;
  return ret;
}

static unsigned local_port(int fd) {
  struct sockaddr_storage ss;
  socklen_t len = sizeof(ss);
  if (getsockname(fd, (struct sockaddr *)&ss, &len) != 0) {
    return 0;
  }
  return addr_port((struct sockaddr *)&ss, len);
}

static void bind_before_loopback_connect(int fd, const struct sockaddr *addr, socklen_t len) {
  if (!loopback_family(addr, len)) return;
  struct sockaddr_storage ss;
  socklen_t slen = sizeof(ss);
  if (getsockname(fd, (struct sockaddr *)&ss, &slen) != 0 || ss.ss_family != addr->sa_family ||
      addr_port((struct sockaddr *)&ss, slen) != 0) {
    return;
  }
  struct sockaddr_storage local;
  if (len > sizeof(local)) return;
  memcpy(&local, addr, len);
  if (addr->sa_family == AF_INET) ((struct sockaddr_in *)&local)->sin_port = 0;
  else ((struct sockaddr_in6 *)&local)->sin6_port = 0;
  int saved = errno;
  bind(fd, (struct sockaddr *)&local, len);
  errno = saved;
}

static unsigned guarded_port(void) {
  static int port = -1;
  if (port < 0) {
    const char *value = getenv("BL_CDP_GUARD");
    port = value && *value ? atoi(value) : 0;
    if (port < 0 || port > 65535) port = 0;
  }
  return (unsigned)port;
}

static int guest_peer(int fd) {
  struct sockaddr_storage address;
  socklen_t length = sizeof(address);
  if (getpeername(fd, (struct sockaddr *)&address, &length) != 0) return 0;
  int family = loopback_family((struct sockaddr *)&address, length);
  if (!family) return 0;
  unsigned peer = peer_port(fd);
  if (peer == 0) {
    return 0;
  }
  char path[300];
  snprintf(path, sizeof(path), "%s/p%u", net_dir(), peer);
  struct stat64 st;
  if (bl_socket_stat(path, &st) != 0 || time(NULL) - st.st_mtime > 30) {
    return 0;
  }
  int in = open(path, O_RDONLY);
  if (in < 0) {
    return 0;
  }
  char buf[128];
  int n = read(in, buf, sizeof(buf) - 1);
  syscall(SYS_close, in);
  if (n <= 0) {
    return 0;
  }
  buf[n] = '\0';
  int pid, parent, uid, descriptor, recorded_family;
  unsigned destination;
  unsigned long long inode;
  if (sscanf(buf, "%d %d %d %u %d %llu %d", &pid, &parent, &uid, &destination, &descriptor,
      &inode, &recorded_family) != 7 || pid <= 0 || descriptor < 0 || destination != guarded_port() ||
      recorded_family != family) return 0;
  snprintf(path, sizeof(path), "/proc/%d/fd/%d", pid, descriptor);
  return bl_socket_stat(path, &st) == 0 && S_ISSOCK(st.st_mode) && (unsigned long long)st.st_ino == inode;
}

int connect(int fd, const struct sockaddr *addr, socklen_t len) {
  static connect_fn real;
  if (!real) real = (connect_fn)dlsym(RTLD_NEXT, "connect");
  bind_before_loopback_connect(fd, addr, len);
  unsigned peer = loopback_family(addr, len) ? addr_port(addr, len) : 0;
  if (peer) record_port(fd, peer);
  int ret = real(fd, addr, len);
  if (ret == 0 || errno == EINPROGRESS) {
    int saved = errno;
    record_port(fd, peer);
    errno = saved;
  }
  return ret;
}

int accept4(int fd, struct sockaddr *addr, socklen_t *len, int flags) {
  static accept4_fn real;
  if (!real) real = (accept4_fn)dlsym(RTLD_NEXT, "accept4");
  for (;;) {
    int ret = real(fd, addr, len, flags);
    if (ret < 0) return ret;
    unsigned guarded = guarded_port();
    if (guarded && local_port(ret) == guarded && !guest_peer(ret)) {
      close(ret);
      continue;
    }
    record_port(ret, peer_port(ret));
    return ret;
  }
}

int accept(int fd, struct sockaddr *addr, socklen_t *len) {
  return accept4(fd, addr, len, 0);
}

extern char **environ;
typedef int (*spawn_fn)(pid_t *, const char *, const posix_spawn_file_actions_t *, const posix_spawnattr_t *,
                        char *const[], char *const[]);
__attribute__((visibility("hidden"))) void bl_binfmt_before(const char *path, char *const argv[], char *const envp[]);
__attribute__((visibility("hidden"))) int bl_binfmt_retry(const char *path, char *const argv[], char *const envp[], int error);
__attribute__((visibility("hidden"))) void bl_binfmt_before_search(const char *file, char *const argv[], char *const envp[]);
__attribute__((visibility("hidden"))) int bl_binfmt_after_search(const char *file, char *const argv[], char *const envp[], int error);
__attribute__((visibility("hidden"))) char *const *bl_webhelper_argv(const char *path, char *const argv[]);
__attribute__((visibility("hidden"))) int bl_binfmt_spawn(pid_t *pid, const char *path, const posix_spawn_file_actions_t *fa,
                                                          const posix_spawnattr_t *attr, char *const argv[], char *const envp[],
                                                          spawn_fn real);

static int maybe_answer_lsof(const char *path, char *const argv[]) {
  const char *base = strrchr(path, '/');
  base = base ? base + 1 : path;
  if (strcmp(base, "lsof") != 0) {
    return 0;
  }
  const char *port_str = NULL;
  for (int i = 0; argv[i]; i++) {
    const char *at = strstr(argv[i], "TCP@");
    if (at) {
      const char *colon = strrchr(at, ':');
      if (colon) port_str = colon + 1;
    }
  }
  if (!port_str) {
    return 0;
  }
  char path_buf[300];
  snprintf(path_buf, sizeof(path_buf), "%s/p%u", net_dir(), (unsigned)atoi(port_str));
  int in = open(path_buf, O_RDONLY);
  if (in < 0) {
    _exit(1);
  }
  char buf[64];
  int n = read(in, buf, sizeof(buf) - 1);
  syscall(SYS_close, in);
  if (n <= 0) {
    _exit(1);
  }
  buf[n] = '\0';
  int pid = 0, ppid = 0, uid = 0;
  unsigned peer = 0;
  sscanf(buf, "%d %d %d %u", &pid, &ppid, &uid, &peer);
  char out[160];
  int m = peer ? snprintf(out, sizeof(out), "p%d\nR%d\nu%d\nn127.0.0.1:%u->127.0.0.1:%u\n",
                          pid, ppid, uid, (unsigned)atoi(port_str), peer)
                : snprintf(out, sizeof(out), "p%d\nR%d\nu%d\nn127.0.0.1:%u\n",
                          pid, ppid, uid, (unsigned)atoi(port_str));
  if (write(1, out, m) != m) {   }
  _exit(0);
}

int execve(const char *path, char *const argv_in[], char *const envp[]) {
  char *const *argv = bl_webhelper_argv(path, argv_in);
  static int (*real)(const char *, char *const[], char *const[]);
  if (!real) real = (int (*)(const char *, char *const[], char *const[]))dlsym(RTLD_NEXT, "execve");
  maybe_answer_lsof(path, argv);
  bl_binfmt_before(path, argv, envp);
  real(path, argv, envp);
  return bl_binfmt_retry(path, argv, envp, errno);
}

static int answer_lsof_via_spawn(const char *path, char *const argv[], pid_t *pid) {
  pid_t child = fork();
  if (child < 0) {
    return errno;
  }
  if (child == 0) {
    maybe_answer_lsof(path, argv);
    _exit(127);
  }
  if (pid) {
    *pid = child;
  }
  return 0;
}

int execvp(const char *file, char *const argv_in[]) {
  char *const *argv = bl_webhelper_argv(file, argv_in);
  static int (*real)(const char *, char *const[]);
  if (!real) real = (int (*)(const char *, char *const[]))dlsym(RTLD_NEXT, "execvp");
  maybe_answer_lsof(file, argv);
  bl_binfmt_before_search(file, argv, environ);
  real(file, argv);
  return bl_binfmt_after_search(file, argv, environ, errno);
}

int execv(const char *path, char *const argv_in[]) {
  char *const *argv = bl_webhelper_argv(path, argv_in);
  static int (*real)(const char *, char *const[]);
  if (!real) real = (int (*)(const char *, char *const[]))dlsym(RTLD_NEXT, "execv");
  maybe_answer_lsof(path, argv);
  bl_binfmt_before(path, argv, environ);
  real(path, argv);
  return bl_binfmt_retry(path, argv, environ, errno);
}

int posix_spawn(pid_t *pid, const char *path, const posix_spawn_file_actions_t *fa,
                const posix_spawnattr_t *attr, char *const argv[], char *const envp[]) {
  const char *base = strrchr(path, '/');
  base = base ? base + 1 : path;
  if (strcmp(base, "lsof") == 0 && !fa) {
    return answer_lsof_via_spawn(path, argv, pid);
  }
  static spawn_fn real;
  if (!real) real = (spawn_fn)dlsym(RTLD_NEXT, "posix_spawn");
  return bl_binfmt_spawn(pid, path, fa, attr, argv, envp, real);
}
