#define _GNU_SOURCE
#include <dlfcn.h>
#include <elf.h>
#include <errno.h>
#include <fcntl.h>
#include <limits.h>
#include <spawn.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/stat.h>
#include <sys/statvfs.h>
#include <unistd.h>

#ifndef BINFMT_HANDLER
#ifdef DEXTOP_BINFMT
#define BINFMT_HANDLER "/usr/local/bin/dextop-gaming"
#else
#define BINFMT_HANDLER "/usr/local/bin/droiddeck-open"
#endif
#endif

typedef int (*spawn_fn)(pid_t *, const char *, const posix_spawn_file_actions_t *, const posix_spawnattr_t *,
                        char *const[], char *const[]);

#if defined(__aarch64__) || defined(BINFMT_TEST)

static int desktop(void) {
  const char *value = getenv("BL_DESKTOP");
#ifdef DEXTOP_BINFMT
  return access("/etc/dextop/linux-x86", R_OK) == 0;
#else
  return value && value[0] == '1' && value[1] == 0;
#endif
}

static int steam_fex_protocol(const char *path) {
#ifdef DEXTOP_BINFMT
  const char suffix[] = "/FEX-Emu/fex-compat-tool";
  size_t n = path ? strlen(path) : 0;
  if (n >= sizeof(suffix) - 1 && !strcmp(path + n - (sizeof(suffix) - 1), suffix)) return 1;
  /* SteamOS mode wraps Linux commands in a runtime before FEX. Unwrap that
   * protocol through the same common handler instead of starting bwrap in PRoot. */
  const char *leaf = path ? strrchr(path, '/') : NULL;
  if (!leaf || leaf == path || (strcmp(leaf + 1, "_v2-entry-point") && strcmp(leaf + 1, "scout-on-soldier-entry-point-v2"))) return 0;
  const char *parent = leaf - 1;
  while (parent > path && parent[-1] != '/') --parent;
  return !strncmp(parent, "SteamLinuxRuntime", strlen("SteamLinuxRuntime"));
#else
  (void)path;
  return 0;
#endif
}

static int foreign(const char *path, int error) {
  if (!path || !strcmp(path, BINFMT_HANDLER)) return 0;
  int fd = open(path, O_RDONLY | O_CLOEXEC);
  if (fd < 0) return 0;
  struct stat st;
  unsigned char head[20];
  ssize_t n = fstat(fd, &st) == 0 && S_ISREG(st.st_mode) ? read(fd, head, sizeof head) : -1;
  int kind = 0;
  if (n >= 20 && !memcmp(head, ELFMAG, SELFMAG)) {
    unsigned machine = head[EI_DATA] == ELFDATA2MSB ? (unsigned)head[18] << 8 | head[19] : head[18] | (unsigned)head[19] << 8;
#ifdef DEXTOP_BINFMT
    kind = machine == EM_X86_64 || machine == EM_386 ? 1 : 0;
#else
    kind = machine == EM_X86_64 || machine == EM_386 || error == EACCES ? 1 : 0;
#endif
  }
#ifndef DEXTOP_BINFMT
  else if (n >= 2 && head[0] == 'M' && head[1] == 'Z') {
    kind = 2;
  } else if (n >= 2 && head[0] == '#' && head[1] == '!' && error == EACCES) {
    kind = 3;
  }
#endif
  struct statvfs vfs;
  int allowed = kind && (((st.st_mode & 0111) && error != EACCES) || (fstatvfs(fd, &vfs) == 0 && (vfs.f_flag & ST_NOEXEC)));
  close(fd);
  return allowed ? kind : 0;
}

static int wanted(const char *path, int error) {
  return (error == ENOEXEC || error == ENOENT || error == EACCES) && desktop() && foreign(path, error);
}

static int lookup(const char *file, char *out, size_t size) {
  if (!file || !*file) return 0;
  if (strchr(file, '/')) return snprintf(out, size, "%s", file) < (int)size;
  const char *paths = getenv("PATH");
  if (!paths) paths = "/bin:/usr/bin";
  for (const char *p = paths;; ) {
    const char *end = strchrnul(p, ':');
    int n = end > p ? snprintf(out, size, "%.*s/%s", (int)(end - p), p, file) : snprintf(out, size, "%s", file);
    struct stat st;
    if (n > 0 && n < (int)size && stat(out, &st) == 0 && S_ISREG(st.st_mode)) return 1;
    if (!*end) return 0;
    p = end + 1;
  }
}

static int count(char *const argv[]) {
  int n = 0;
  while (argv && argv[n]) n++;
  return n;
}

static void handler_args(char **args, const char *path, char *const argv[], int n) {
  args[0] = BINFMT_HANDLER;
  args[1] = steam_fex_protocol(path) ? "--linux" : "--exec";
  args[2] = (char *)path;
  int i = 3;
  for (int k = 1; k < n; k++) args[i++] = argv[k];
  args[i] = NULL;
}

static void hand_off(const char *path, char *const argv[], char *const envp[]) {
  static int (*real)(const char *, char *const[], char *const[]);
  if (!real) real = (int (*)(const char *, char *const[], char *const[]))dlsym(RTLD_NEXT, "execve");
  int n = count(argv);
  char *args[n + 4];
  handler_args(args, path, argv, n);
  real(BINFMT_HANDLER, args, envp);
}

static int spawn_handler(pid_t *pid, const char *path, const posix_spawn_file_actions_t *fa, const posix_spawnattr_t *attr,
                         char *const argv[], char *const envp[], spawn_fn real) {
  int n = count(argv);
  char *args[n + 4];
  handler_args(args, path, argv, n);
  return real(pid, BINFMT_HANDLER, fa, attr, args, envp);
}

__attribute__((visibility("hidden"))) void bl_binfmt_before(const char *path, char *const argv[], char *const envp[]) {
  if (desktop() && (steam_fex_protocol(path) || foreign(path, ENOEXEC))) hand_off(path, argv, envp);
}

__attribute__((visibility("hidden"))) int bl_binfmt_retry(const char *path, char *const argv[], char *const envp[], int error) {
  if (wanted(path, error)) hand_off(path, argv, envp);
  errno = error;
  return -1;
}

__attribute__((visibility("hidden"))) void bl_binfmt_before_search(const char *file, char *const argv[], char *const envp[]) {
  char path[PATH_MAX];
  if (desktop() && lookup(file, path, sizeof path)) bl_binfmt_before(path, argv, envp);
}

__attribute__((visibility("hidden"))) int bl_binfmt_after_search(const char *file, char *const argv[], char *const envp[], int error) {
  char path[PATH_MAX];
  if (desktop() && lookup(file, path, sizeof path)) return bl_binfmt_retry(path, argv, envp, error);
  errno = error;
  return -1;
}

__attribute__((visibility("hidden"))) int bl_binfmt_runnable(const char *path, int mode) {
  if (mode != X_OK || !desktop()) return 0;
  int runs = foreign(path, EACCES) != 0;
  errno = EACCES;
  return runs;
}

__attribute__((visibility("hidden"))) int bl_binfmt_spawn(pid_t *pid, const char *path, const posix_spawn_file_actions_t *fa,
                                                          const posix_spawnattr_t *attr, char *const argv[], char *const envp[],
                                                          spawn_fn real) {
  if (desktop() && (steam_fex_protocol(path) || foreign(path, ENOEXEC))) return spawn_handler(pid, path, fa, attr, argv, envp, real);
  int error = real(pid, path, fa, attr, argv, envp);
  return error && wanted(path, error) ? spawn_handler(pid, path, fa, attr, argv, envp, real) : error;
}

int execvpe(const char *file, char *const argv[], char *const envp[]) {
  static int (*real)(const char *, char *const[], char *const[]);
  if (!real) real = (int (*)(const char *, char *const[], char *const[]))dlsym(RTLD_NEXT, "execvpe");
  bl_binfmt_before_search(file, argv, envp);
  real(file, argv, envp);
  return bl_binfmt_after_search(file, argv, envp, errno);
}

int posix_spawnp(pid_t *pid, const char *file, const posix_spawn_file_actions_t *fa, const posix_spawnattr_t *attr,
                 char *const argv[], char *const envp[]) {
  static spawn_fn real, spawn;
  if (!real) real = (spawn_fn)dlsym(RTLD_NEXT, "posix_spawnp");
  if (!spawn) spawn = (spawn_fn)dlsym(RTLD_NEXT, "posix_spawn");
  char path[PATH_MAX];
  if (!desktop() || !lookup(file, path, sizeof path)) return real(pid, file, fa, attr, argv, envp);
  return bl_binfmt_spawn(pid, path, fa, attr, argv, envp, spawn);
}

#else

__attribute__((visibility("hidden"))) int bl_binfmt_runnable(const char *path, int mode) {
  (void)path, (void)mode;
  return 0;
}

__attribute__((visibility("hidden"))) void bl_binfmt_before(const char *path, char *const argv[], char *const envp[]) {
  (void)path, (void)argv, (void)envp;
}

__attribute__((visibility("hidden"))) int bl_binfmt_retry(const char *path, char *const argv[], char *const envp[], int error) {
  (void)path, (void)argv, (void)envp;
  errno = error;
  return -1;
}

__attribute__((visibility("hidden"))) void bl_binfmt_before_search(const char *file, char *const argv[], char *const envp[]) {
  (void)file, (void)argv, (void)envp;
}

__attribute__((visibility("hidden"))) int bl_binfmt_after_search(const char *file, char *const argv[], char *const envp[], int error) {
  (void)file, (void)argv, (void)envp;
  errno = error;
  return -1;
}

__attribute__((visibility("hidden"))) int bl_binfmt_spawn(pid_t *pid, const char *path, const posix_spawn_file_actions_t *fa,
                                                          const posix_spawnattr_t *attr, char *const argv[], char *const envp[],
                                                          spawn_fn real) {
  return real(pid, path, fa, attr, argv, envp);
}

#endif
