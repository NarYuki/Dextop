/*
 * Ported from WinNative (maxjivi05, feature/wayland-gamescope, 2026-09-18/19), GPL-3.0.
 */

#define _GNU_SOURCE
#include <ctype.h>
#include <errno.h>
#include <fcntl.h>
#include <string.h>
#include <sys/mman.h>
#include <sys/syscall.h>
#include <unistd.h>

#define STATUS_MAX 16384
#define TRACER_FIELD "\nTracerPid:\t"

static const char *skip_digits(const char *s) {
  const char *end = s;
  while (isdigit((unsigned char)*end)) end++;
  return end == s ? NULL : end;
}

static int is_status_path(const char *path) {
  const char *rest;
  if (path == NULL || strncmp(path, "/proc/", 6) != 0) return 0;
  path += 6;
  if (strncmp(path, "self/", 5) == 0) rest = path + 4;
  else if (strncmp(path, "thread-self/", 12) == 0) rest = path + 11;
  else if ((rest = skip_digits(path)) == NULL) return 0;
  if (strncmp(rest, "/task/", 6) == 0 && (rest = skip_digits(rest + 6)) == NULL) return 0;
  return strcmp(rest, "/status") == 0;
}

static int is_web_helper(void) {
  return strcmp(program_invocation_short_name, "steamwebhelper") == 0 ||
         strcmp(program_invocation_short_name, "vrwebhelper") == 0;
}

static int write_all(int fd, const char *data, size_t len) {
  while (len > 0) {
    ssize_t n = write(fd, data, len);
    if (n < 0 && errno == EINTR) continue;
    if (n <= 0) return -1;
    data += n;
    len -= (size_t)n;
  }
  return 0;
}

__attribute__((visibility("hidden"))) int bl_status_without_tracer(const char *path, int flags) {
  char text[STATUS_MAX];
  size_t len = 0;
  int saved = errno;
  int out = -1;

  if ((flags & O_ACCMODE) != O_RDONLY || !is_status_path(path) || is_web_helper()) return -1;
  int in = (int)syscall(SYS_openat, AT_FDCWD, path, O_RDONLY | O_CLOEXEC);
  if (in < 0) goto done;
  for (;;) {
    ssize_t n = read(in, text + len, sizeof(text) - 1 - len);
    if (n < 0 && errno == EINTR) continue;
    if (n <= 0) break;
    len += (size_t)n;
  }
  close(in);
  text[len] = '\0';

  char *field = strstr(text, TRACER_FIELD);
  if (field == NULL) goto done;
  char *value = field + strlen(TRACER_FIELD);
  char *line_end = strchr(value, '\n');
  if (line_end == NULL) goto done;

  out = memfd_create("status", (flags & O_CLOEXEC) ? MFD_CLOEXEC : 0);
  if (out < 0) goto done;
  if (write_all(out, text, (size_t)(value - text)) != 0 || write_all(out, "0", 1) != 0
      || write_all(out, line_end, len - (size_t)(line_end - text)) != 0
      || lseek(out, 0, SEEK_SET) != 0) {
    close(out);
    out = -1;
  }
done:
  errno = saved;
  return out;
}
