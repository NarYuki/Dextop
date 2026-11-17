/* Test removal of device-specific guards without applying a kernel policy. */
#define _GNU_SOURCE
#include <errno.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/utsname.h>
#include <unistd.h>
#include "tracee/landlock.h"
static const char *release;
static int queries, uname_error;
int uname(struct utsname *out)
{
    if (uname_error) { errno = EIO; return -1; }
    memset(out, 0, sizeof(*out));
    snprintf(out->release, sizeof(out->release), "%s", release);
    return 0;
}
long syscall(long number, ...)
{
    (void)number;
    queries++;
    errno = ENOSYS;
    return -1;
}
int main(void)
{
    setenv("PROOT_LANDLOCK_PATHS", "/tmp", 1);
    queries = 0;
    release = "6.12.69-android16-6-pee899be-abogkiS942ZSCU1BZI9-4k";
    int result = proot_landlock_apply();
    printf("former blocked build result=%d queries=%d\n", result, queries);
    if (result != -ENOSYS || queries != 1 || proot_landlock_active()) return 1;
    release = "6.12.69-android16-6-other-vendor-build";
    result = proot_landlock_apply();
    if (result != -ENOSYS || queries != 2 || proot_landlock_active()) return 2;
    uname_error = 1;
    if (proot_landlock_apply() != -ENOSYS || queries != 3 || proot_landlock_active()) return 3;
    puts("PASS capability query is independent of vendor release and uname failures");
    return 0;
}
