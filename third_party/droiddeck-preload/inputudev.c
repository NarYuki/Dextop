/*
 * Ported from WinNative (maxjivi05, feature/wayland-gamescope, 2026-09-18/19), GPL-3.0.
 */

#define _GNU_SOURCE
#include <dlfcn.h>
#include <fcntl.h>
#include <limits.h>
#include <linux/input.h>
#include <pthread.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/ioctl.h>
#include <sys/sysmacros.h>
#include <sys/types.h>
#include <sys/stat.h>
#include <sys/syscall.h>
#include <unistd.h>

#define RING_MAX 4
#define UINPUT_BASE 16
#define PAD_MAX 64
#define EVENT_MINOR_BASE 64

#define PAD_UEVENT_FORMAT                                                                          \
  "PRODUCT=6/28de/11ff/110\n"                                                                      \
  "NAME=\"Generic HID Gamepad %d\"\n"                                                              \
  "PHYS=\"usb-fakeinput/input%d\"\n"

#define PAD_PHYSICAL_UEVENT_FORMAT                                                                 \
  "PRODUCT=3/45e/28e/110\n"                                                                        \
  "NAME=\"Xbox 360 Controller (%d)\"\n"                                                            \
  "PHYS=\"usb-fakeinput/input%d\"\n"

struct pad {
  char syspath[64];
  char devnode[32];
  char sysname[16];
  char uevent[256];
  dev_t devnum;
};

static struct pad pads[PAD_MAX];
static int pad_count;
static pthread_mutex_t virtual_lock = PTHREAD_MUTEX_INITIALIZER;

static void discover_pads(void) {
  const char *spec = getenv("FAKE_EVDEV_MEMFD_PATHS");
  const char *entry;

  if (spec == NULL) return;
  for (entry = spec; *entry != '\0';) {
    char *end;
    long slot = strtol(entry, &end, 10);

    if (end != entry && *end == '=' && slot >= 0 && slot < RING_MAX) {
      struct pad *pad = &pads[slot];

      if (pad->devnode[0] == '\0') {
        snprintf(pad->sysname, sizeof(pad->sysname), "event%ld", slot);
        snprintf(pad->syspath, sizeof(pad->syspath), "/sys/devices/virtual/input/droiddeck%ld",
                 slot);
        snprintf(pad->devnode, sizeof(pad->devnode), "/dev/input/event%ld", slot);
        snprintf(pad->uevent, sizeof(pad->uevent), PAD_UEVENT_FORMAT, (int)slot, (int)slot);
        pad->devnum = makedev(13, EVENT_MINOR_BASE + (unsigned int)slot);
        if (slot >= pad_count) pad_count = (int)slot + 1;
      }
    }
    entry = strchr(entry, ';');
    if (entry == NULL) break;
    entry++;
  }
}

static void ensure_pads(void) {
  static pthread_once_t once = PTHREAD_ONCE_INIT;
  pthread_once(&once, discover_pads);
}

static int uinput_enabled(void) {
  const char *enabled = getenv("FAKE_EVDEV_UINPUT");
  return enabled != NULL && atoi(enabled) != 0;
}

static int read_virtual_uevent(int node, char *buf, size_t size) {
  const char *dir = getenv("FAKE_EVDEV_DIR");
  char path[PATH_MAX];
  FILE *file;
  size_t length;

  if (dir == NULL) return 0;
  snprintf(path, sizeof(path), "%s/.uinput-event%d.uevent", dir, node);
  if ((file = fopen(path, "re")) == NULL) return 0;
  length = fread(buf, 1, size - 1, file);
  fclose(file);
  buf[length] = '\0';
  return length > 0;
}

static int any_virtual_pad(void) {
  char uevent[256];
  int node;

  for (node = UINPUT_BASE; node < PAD_MAX; node++)
    if (read_virtual_uevent(node, uevent, sizeof(uevent))) return 1;
  return 0;
}

static void refresh_pad(int node) {
  struct pad *pad;

  if (node < 0 || node >= PAD_MAX) return;
  pad = &pads[node];
  pthread_mutex_lock(&virtual_lock);
  if (getenv("DEXTOP_EVDEV_UDEV") != NULL && getenv("FAKE_EVDEV_MEMFD_PATHS") == NULL) {
    char path[64], name[128] = "";
    unsigned char keys[KEY_MAX / 8 + 1] = {0};
    struct input_id id = {0};
    struct stat st;
    snprintf(path, sizeof(path), "/dev/input/event%d", node);
    /* Probe without the controller-grab open wrapper: discovery must not take ownership. */
    int fd = syscall(SYS_openat, AT_FDCWD, path, O_RDONLY | O_NONBLOCK | O_CLOEXEC);
    pad->devnode[0] = '\0';
    if (fd >= 0) {
      int valid = ioctl(fd, EVIOCGBIT(EV_KEY, sizeof(keys)), keys) >= 0 &&
        ((keys[BTN_GAMEPAD / 8] & (1 << (BTN_GAMEPAD % 8))) ||
         (keys[BTN_JOYSTICK / 8] & (1 << (BTN_JOYSTICK % 8))));
      if (valid && ioctl(fd, EVIOCGID, &id) >= 0 && fstat(fd, &st) == 0) {
        ioctl(fd, EVIOCGNAME(sizeof(name) - 1), name);
        snprintf(pad->sysname, sizeof(pad->sysname), "event%d", node);
        snprintf(pad->syspath, sizeof(pad->syspath), "/sys/devices/virtual/input/dextop%d", node);
        snprintf(pad->devnode, sizeof(pad->devnode), "%s", path);
        snprintf(pad->uevent, sizeof(pad->uevent), "PRODUCT=%x/%x/%x/%x\nNAME=\"%s\"\n",
                 id.bustype, id.vendor, id.product, id.version, name);
        pad->devnum = st.st_rdev;
        if (node >= pad_count) pad_count = node + 1;
      }
      syscall(SYS_close, fd);
    }
  } else if (node >= UINPUT_BASE) {
    char uevent[sizeof(pad->uevent)];

    if (read_virtual_uevent(node, uevent, sizeof(uevent))) {
      snprintf(pad->sysname, sizeof(pad->sysname), "event%d", node);
      snprintf(pad->syspath, sizeof(pad->syspath), "/sys/devices/virtual/input/droiddeck%d", node);
      memcpy(pad->uevent, uevent, sizeof(uevent));
      pad->devnum = makedev(13, EVENT_MINOR_BASE + (unsigned int)node);
      snprintf(pad->devnode, sizeof(pad->devnode), "/dev/input/event%d", node);
      if (node >= pad_count) pad_count = node + 1;
    } else {
      pad->devnode[0] = '\0';
    }
  } else if (pad->devnode[0] != '\0' && uinput_enabled()) {
    snprintf(pad->uevent, sizeof(pad->uevent),
             any_virtual_pad() ? PAD_PHYSICAL_UEVENT_FORMAT : PAD_UEVENT_FORMAT, node, node);
  }
  pthread_mutex_unlock(&virtual_lock);
}

static struct pad *pad_for_syspath(const char *syspath) {
  int i;

  if (syspath == NULL) return NULL;
  ensure_pads();
  for (i = 0; i < pad_count; i++) {
    if (pads[i].devnode[0] != '\0' && strcmp(pads[i].syspath, syspath) == 0) return &pads[i];
  }
  return NULL;
}

static struct pad *pad_for_device(void *device) {
  int i;

  if (device == NULL) return NULL;
  ensure_pads();
  for (i = 0; i < pad_count; i++) {
    if (&pads[i] == device) return &pads[i];
  }
  return NULL;
}

static struct pad *pad_for_syslink(const char *path) {
  static const char prefix[] = "/sys/class/input/";
  const char *name;
  int i;

  if (path == NULL || strncmp(path, prefix, sizeof(prefix) - 1) != 0) return NULL;
  name = path + sizeof(prefix) - 1;
  ensure_pads();
  if (strncmp(name, "event", 5) == 0 && name[5] >= '0' && name[5] <= '9') refresh_pad(atoi(name + 5));
  for (i = 0; i < pad_count; i++) {
    if (pads[i].devnode[0] != '\0' && strcmp(pads[i].sysname, name) == 0) return &pads[i];
  }
  return NULL;
}

static void *next_symbol(const char *name) { return dlsym(RTLD_NEXT, name); }

char *bl_cached_realpath(const char *path, char *resolved, char *(*real)(const char *, char *))
    __attribute__((visibility("hidden")));

static int caller_is_winebus(void *caller) {
  Dl_info info;

  if (dladdr(caller, &info) == 0 || info.dli_fname == NULL) return 0;
  return strstr(info.dli_fname, "winebus.so") != NULL;
}

/* Wine's HID haptics give every XInput rumble a 1000 ms cutoff and xinput only writes on a change,
 * but XInput motors hold their speed until the next XInputSetState; evdev's length 0 holds until
 * stopped. Only this library, preloaded first, sees winebus as the caller. The length is put back
 * because winebus compares its copy with the next state to decide whether to upload again. */
__attribute__((visibility("hidden"))) int bl_winebus_rumble_ioctl(int (*real)(int, unsigned long, void *), int fd,
                                                                  unsigned long request, void *arg, void *caller,
                                                                  int *rc) {
  struct ff_effect *effect = arg;
  __u16 length;

  if (request != EVIOCSFF || effect == NULL || effect->type != FF_RUMBLE || !caller_is_winebus(caller)) return 0;
  length = effect->replay.length;
  effect->replay.length = 0;
  *rc = real(fd, request, arg);
  effect->replay.length = length;
  return 1;
}

char *realpath(const char *path, char *resolved) {
  static char *(*real_realpath)(const char *, char *);
  void *caller = __builtin_return_address(0);
  struct pad *pad = caller_is_winebus(caller) ? pad_for_syslink(path) : NULL;

  if (pad != NULL) {
    if (resolved == NULL) return strdup(pad->syspath);
    memcpy(resolved, pad->syspath, strlen(pad->syspath) + 1);
    return resolved;
  }
  if (real_realpath == NULL) real_realpath = next_symbol("realpath");
  if (real_realpath == NULL) return NULL;
  return bl_cached_realpath(path, resolved, real_realpath);
}

void *udev_device_new_from_syspath(void *udev, const char *syspath) {
  static void *(*real_new)(void *, const char *);
  void *caller = __builtin_return_address(0);
  struct pad *pad = caller_is_winebus(caller) ? pad_for_syspath(syspath) : NULL;

  if (pad != NULL) return pad;
  if (real_new == NULL) real_new = next_symbol("udev_device_new_from_syspath");
  if (real_new == NULL) return NULL;
  return real_new(udev, syspath);
}

void *udev_device_ref(void *device) {
  static void *(*real_ref)(void *);

  if (pad_for_device(device) != NULL) return device;
  if (real_ref == NULL) real_ref = next_symbol("udev_device_ref");
  if (real_ref == NULL) return NULL;
  return real_ref(device);
}

void *udev_device_unref(void *device) {
  static void *(*real_unref)(void *);

  if (pad_for_device(device) != NULL) return NULL;
  if (real_unref == NULL) real_unref = next_symbol("udev_device_unref");
  if (real_unref == NULL) return NULL;
  return real_unref(device);
}

const char *udev_device_get_devnode(void *device) {
  static const char *(*real_get)(void *);
  struct pad *pad = pad_for_device(device);

  if (pad != NULL) return pad->devnode;
  if (real_get == NULL) real_get = next_symbol("udev_device_get_devnode");
  if (real_get == NULL) return NULL;
  return real_get(device);
}

const char *udev_device_get_syspath(void *device) {
  static const char *(*real_get)(void *);
  struct pad *pad = pad_for_device(device);

  if (pad != NULL) return pad->syspath;
  if (real_get == NULL) real_get = next_symbol("udev_device_get_syspath");
  if (real_get == NULL) return NULL;
  return real_get(device);
}

const char *udev_device_get_sysname(void *device) {
  static const char *(*real_get)(void *);
  struct pad *pad = pad_for_device(device);

  if (pad != NULL) return pad->sysname;
  if (real_get == NULL) real_get = next_symbol("udev_device_get_sysname");
  if (real_get == NULL) return NULL;
  return real_get(device);
}

const char *udev_device_get_subsystem(void *device) {
  static const char *(*real_get)(void *);
  struct pad *pad = pad_for_device(device);

  if (pad != NULL) return "input";
  if (real_get == NULL) real_get = next_symbol("udev_device_get_subsystem");
  if (real_get == NULL) return NULL;
  return real_get(device);
}

const char *udev_device_get_devtype(void *device) {
  static const char *(*real_get)(void *);
  struct pad *pad = pad_for_device(device);

  if (pad != NULL) return NULL;
  if (real_get == NULL) real_get = next_symbol("udev_device_get_devtype");
  if (real_get == NULL) return NULL;
  return real_get(device);
}

const char *udev_device_get_action(void *device) {
  static const char *(*real_get)(void *);
  struct pad *pad = pad_for_device(device);

  if (pad != NULL) return NULL;
  if (real_get == NULL) real_get = next_symbol("udev_device_get_action");
  if (real_get == NULL) return NULL;
  return real_get(device);
}

dev_t udev_device_get_devnum(void *device) {
  static dev_t (*real_get)(void *);
  struct pad *pad = pad_for_device(device);

  if (pad != NULL) return pad->devnum;
  if (real_get == NULL) real_get = next_symbol("udev_device_get_devnum");
  if (real_get == NULL) return makedev(0, 0);
  return real_get(device);
}

void *udev_device_get_parent_with_subsystem_devtype(void *device, const char *subsystem,
                                                    const char *devtype) {
  static void *(*real_get)(void *, const char *, const char *);
  struct pad *pad = pad_for_device(device);

  if (pad != NULL) {
    if (subsystem != NULL && strcmp(subsystem, "input") == 0 && devtype == NULL) return pad;
    return NULL;
  }
  if (real_get == NULL) real_get = next_symbol("udev_device_get_parent_with_subsystem_devtype");
  if (real_get == NULL) return NULL;
  return real_get(device, subsystem, devtype);
}

const char *udev_device_get_sysattr_value(void *device, const char *attr) {
  static const char *(*real_get)(void *, const char *);
  struct pad *pad = pad_for_device(device);

  if (pad != NULL) return attr != NULL && strcmp(attr, "uevent") == 0 ? pad->uevent : NULL;
  if (real_get == NULL) real_get = next_symbol("udev_device_get_sysattr_value");
  if (real_get == NULL) return NULL;
  return real_get(device, attr);
}

const char *udev_device_get_property_value(void *device, const char *key) {
  static const char *(*real_get)(void *, const char *);
  struct pad *pad = pad_for_device(device);

  if (pad != NULL) return key != NULL && strcmp(key, "ID_INPUT_JOYSTICK") == 0 ? "1" : NULL;
  if (real_get == NULL) real_get = next_symbol("udev_device_get_property_value");
  if (real_get == NULL) return NULL;
  return real_get(device, key);
}
