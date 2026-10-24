/*
 * Game controllers for Dextop's Steam mode, kept from Android.
 *
 * Android reads every evdev node too and hands controllers to the focused window, so a
 * controller used in Steam would also drive Android Auto. A process here that opens a
 * controller takes it with EVIOCGRAB: its events then reach that process only. Controllers
 * the user keeps for Android are listed in /tmp/dextop-pads.conf ("vendor product name" in
 * hex, one per line) and are hidden from the guest the next time it opens them. Only the
 * open calls are wrapped; the file descriptors are the kernel's own.
 */
#define _GNU_SOURCE
#include <dlfcn.h>
#include <errno.h>
#include <fcntl.h>
#include <linux/input.h>
#include <pthread.h>
#include <stdarg.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/ioctl.h>
#include <sys/syscall.h>
#include <unistd.h>

#define STATE "/tmp/dextop-pads.conf"

static int (*next_open)(const char *, int, ...);
static int (*next_open64)(const char *, int, ...);
static int (*next_openat)(int, const char *, int, ...);
static int (*next_openat64)(int, const char *, int, ...);
static int (*next_open_2)(const char *, int);
static int (*next_open64_2)(const char *, int);

static void init(void) {
  next_open = dlsym(RTLD_NEXT, "open");
  next_open64 = dlsym(RTLD_NEXT, "open64");
  next_openat = dlsym(RTLD_NEXT, "openat");
  next_openat64 = dlsym(RTLD_NEXT, "openat64");
  next_open_2 = dlsym(RTLD_NEXT, "__open_2");
  next_open64_2 = dlsym(RTLD_NEXT, "__open64_2");
}

static void resolve(void) {
  static pthread_once_t once = PTHREAD_ONCE_INIT;
  pthread_once(&once, init);
}

static int has_bit(const unsigned char *bits, int bit) { return bits[bit / 8] >> (bit % 8) & 1; }

static int is_controller(int fd) {
  unsigned char keys[KEY_MAX / 8 + 1] = {0};
  if (ioctl(fd, EVIOCGBIT(EV_KEY, sizeof(keys)), keys) < 0) return 0;
  return has_bit(keys, BTN_GAMEPAD) || has_bit(keys, BTN_JOYSTICK);
}

/* Whether the user keeps this controller for Android; raw syscalls, so no wrapper runs again. */
static int kept_for_android(unsigned short vendor, unsigned short product, const char *name) {
  int fd = (int)syscall(SYS_openat, AT_FDCWD, STATE, O_RDONLY | O_CLOEXEC);
  if (fd < 0) return 0;
  char text[4096];
  ssize_t length = syscall(SYS_read, fd, text, sizeof(text) - 1);
  syscall(SYS_close, fd);
  if (length <= 0) return 0;
  text[length] = '\0';
  char *cursor = NULL;
  for (char *line = strtok_r(text, "\n", &cursor); line != NULL; line = strtok_r(NULL, "\n", &cursor)) {
    unsigned int v, p;
    int used = 0;
    if (sscanf(line, "%x %x %n", &v, &p, &used) == 2 && used > 0 && v == vendor && p == product &&
        strcmp(line + used, name) == 0)
      return 1;
  }
  return 0;
}

static int adopt(const char *path, int fd, int flags) {
  if (path == NULL || strncmp(path, "/dev/input/event", 16) != 0) return fd;
  /* Samsung permits shell to read Steam's uinput nodes but denies O_RDWR.
   * Winebus requires an O_RDWR open even for input. Keep the kernel event
   * stream usable in Deck's process tree without opening keyboards/touch or
   * changing node permissions. Force feedback may still be unavailable. */
  if (fd < 0) {
    int error = errno;
    const char *deck = getenv("DEXTOP_STEAM_INSIDE");
    if ((error != EACCES && error != EPERM) || (flags & O_ACCMODE) != O_RDWR ||
        !deck || strcmp(deck, "1")) return -1;
    fd = next_open(path, (flags & ~O_ACCMODE) | O_RDONLY);
    if (fd < 0) return -1;
    if (!is_controller(fd)) {
      syscall(SYS_close, fd);
      errno = error;
      return -1;
    }
  }
  int saved = errno;
  if (is_controller(fd)) {
    struct input_id id = {0};
    char name[128] = "";
    ioctl(fd, EVIOCGID, &id);
    ioctl(fd, EVIOCGNAME(sizeof(name) - 1), name);
    if (kept_for_android(id.vendor, id.product, name)) {
      syscall(SYS_close, fd);
      errno = ENOENT;
      return -1;
    }
    /* An evdev grab belongs to one open file, not every process in the guest.
     * Exposing Steam's grabbed physical pad to Wine creates a silent controller
     * beside Steam Input's virtual pad, which games may select instead. */
    if (ioctl(fd, EVIOCGRAB, 1) < 0 && errno == EBUSY) {
      syscall(SYS_close, fd);
      errno = ENODEV;
      return -1;
    }
  }
  errno = saved;
  return fd;
}

static int mode_of(int flags, va_list args) { return (flags & (O_CREAT | O_TMPFILE)) ? va_arg(args, int) : 0; }

int open(const char *path, int flags, ...) {
  resolve();
  va_list args;
  va_start(args, flags);
  int mode = mode_of(flags, args);
  va_end(args);
  return adopt(path, next_open(path, flags, mode), flags);
}

int open64(const char *path, int flags, ...) {
  resolve();
  va_list args;
  va_start(args, flags);
  int mode = mode_of(flags, args);
  va_end(args);
  return adopt(path, (next_open64 ? next_open64 : next_open)(path, flags, mode), flags);
}

int openat(int dir, const char *path, int flags, ...) {
  resolve();
  va_list args;
  va_start(args, flags);
  int mode = mode_of(flags, args);
  va_end(args);
  return adopt(path, next_openat(dir, path, flags, mode), flags);
}

int openat64(int dir, const char *path, int flags, ...) {
  resolve();
  va_list args;
  va_start(args, flags);
  int mode = mode_of(flags, args);
  va_end(args);
  return adopt(path, (next_openat64 ? next_openat64 : next_openat)(dir, path, flags, mode), flags);
}

int __open_2(const char *path, int flags) {
  resolve();
  return adopt(path, next_open_2 ? next_open_2(path, flags) : next_open(path, flags), flags);
}

int __open64_2(const char *path, int flags) {
  resolve();
  return adopt(path, next_open64_2 ? next_open64_2(path, flags) : next_open(path, flags), flags);
}
