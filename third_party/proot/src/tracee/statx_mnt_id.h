#ifndef DROIDDECK_PROOT_STATX_MNT_ID_H
#define DROIDDECK_PROOT_STATX_MNT_ID_H

#include <errno.h>
#include <fcntl.h>
#include <stdbool.h>
#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/stat.h>
#include <sys/sysmacros.h>
#include <sys/types.h>
#include <unistd.h>

static inline bool droiddeck_proot_read_fdinfo(int fd, uint64_t *mnt_id, uint64_t *ino) {
  if (fd < 0 || !mnt_id || !ino) return false;

  char fdinfo_path[64];
  int n = snprintf(fdinfo_path, sizeof fdinfo_path, "/proc/self/fdinfo/%d", fd);
  if (n < 0 || (size_t)n >= sizeof fdinfo_path) return false;

  FILE *fdinfo = fopen(fdinfo_path, "r");
  if (!fdinfo) return false;

  bool have_mnt_id = false, have_ino = false;
  uint64_t parsed_mnt_id = 0, parsed_ino = 0;
  char line[256];
  while (fgets(line, sizeof line, fdinfo)) {
    const char *value = NULL;
    uint64_t *field = NULL;
    if (strncmp(line, "mnt_id:", 7) == 0) {
      value = line + 7;
      field = &parsed_mnt_id;
      have_mnt_id = true;
    } else if (strncmp(line, "ino:", 4) == 0) {
      value = line + 4;
      field = &parsed_ino;
      have_ino = true;
    }
    if (!value) continue;

    errno = 0;
    char *end = NULL;
    unsigned long long parsed = strtoull(value, &end, 10);
    if (errno || end == value || (*end && *end != '\n' && *end != '\r')) {
      if (field == &parsed_mnt_id) have_mnt_id = false;
      else have_ino = false;
      continue;
    }
    *field = (uint64_t)parsed;
  }
  bool read_error = ferror(fdinfo);
  fclose(fdinfo);

  if (read_error || !have_mnt_id || !have_ino || parsed_mnt_id == 0) return false;
  *mnt_id = parsed_mnt_id;
  *ino = parsed_ino;
  return true;
}

static inline bool droiddeck_proot_statx_fill_mnt_id(pid_t pid, int fd,
                                                     int flags, const char *path,
                                                     unsigned int mask,
                                                     struct statx *buf) {
  if (fd < 0 || !(flags & AT_EMPTY_PATH) || !path || path[0] != '\0'
      || !(mask & STATX_MNT_ID) || (buf->stx_mask & STATX_MNT_ID)
      || !(buf->stx_mask & STATX_INO))
    return false;

  char tracee_fd_path[64];
  int n = snprintf(tracee_fd_path, sizeof tracee_fd_path, "/proc/%ld/fd/%d", (long)pid, fd);
  if (n < 0 || (size_t)n >= sizeof tracee_fd_path) return false;

  /* Another thread sharing the tracee's fd table can close/reuse fd after statx returns. Pin the
   * current referent, then require both device and inode to match statx before trusting its mount.
   * Read fdinfo from our pin so a later tracee fd reuse cannot change which mount ID is observed.
   * A same-inode bind-mount alias has the same device/inode pair; at this exit stop we cannot
   * distinguish that rare concurrent replacement from the mount used by the completed syscall. */
  int pinned = open(tracee_fd_path, O_PATH | O_CLOEXEC);
  if (pinned < 0) return false;
  struct stat st;
  bool same = fstat(pinned, &st) == 0 && st.st_ino == buf->stx_ino &&
              major(st.st_dev) == buf->stx_dev_major && minor(st.st_dev) == buf->stx_dev_minor;
  uint64_t mnt_id = 0, ino = 0;
  bool have_fdinfo = same && droiddeck_proot_read_fdinfo(pinned, &mnt_id, &ino);
  close(pinned);
  if (!have_fdinfo || ino != st.st_ino) return false;

  buf->stx_mnt_id = mnt_id;
  buf->stx_mask |= STATX_MNT_ID;
  return true;
}

#endif
