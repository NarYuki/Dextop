/* Benign containment regression: caller supplies a disposable directory. */
#define _GNU_SOURCE
#include <errno.h>
#include <fcntl.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/stat.h>
#include <sys/wait.h>
#include <unistd.h>
#include "tracee/landlock.h"
int main(int argc, char **argv) {
  if (argc != 2) return 2;
  char allowed[4096], outside[4096], link[4096], file[4096];
  snprintf(allowed, sizeof allowed, "%s/allowed", argv[1]);
  snprintf(outside, sizeof outside, "%s/outside", argv[1]);
  snprintf(file, sizeof file, "%s/file", allowed);
  snprintf(link, sizeof link, "%s/link", allowed);
  mkdir(allowed, 0700);
  int fd = open(file, O_CREAT | O_WRONLY, 0600); if (fd < 0) return 3; close(fd);
  fd = open(outside, O_CREAT | O_WRONLY, 0600); if (fd < 0) return 4; close(fd);
  symlink(outside, link);
  if (proot_landlock_active()) return 5;
  setenv("PROOT_LANDLOCK_PATHS", allowed, 1);
  int applied = proot_landlock_apply();
  if (applied) { printf("SKIP Landlock ABI 6 unavailable (%d)\n", applied); return 77; }
  if (!proot_landlock_active()) return 6;
  fd = open(file, O_RDONLY); if (fd < 0) return 7; close(fd);
  fd = open(file, O_WRONLY | O_TRUNC); if (fd < 0) return 8; close(fd);
  if (open(outside, O_RDONLY) >= 0 || errno != EACCES) return 9;
  if (open(outside, O_WRONLY | O_TRUNC) >= 0 || errno != EACCES) return 10;
  if (open(link, O_RDONLY) >= 0 || errno != EACCES) return 11;
  pid_t pid = fork();
  if (!pid) _exit(open(outside, O_RDONLY) < 0 && errno == EACCES ? 0 : 12);
  int status; if (pid < 0 || waitpid(pid, &status, 0) != pid || status) return 13;
  puts("PASS allowed read/write; outside read/truncate and symlink denied; child inherits policy");
  return 0;
}
