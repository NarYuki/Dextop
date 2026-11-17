/* Test-only LD_PRELOAD library. Inject one startup prctl failure to verify
 * PRoot exits before executing any guest command. Never package this library. */
#define _GNU_SOURCE
#include <errno.h>
#include <stdarg.h>
#include <stdlib.h>
#include <sys/prctl.h>
#include <sys/syscall.h>
#include <unistd.h>

int prctl(int option, ...)
{
    va_list args;
    unsigned long a, b, c, d;
    va_start(args, option);
    a = va_arg(args, unsigned long);
    b = va_arg(args, unsigned long);
    c = va_arg(args, unsigned long);
    d = va_arg(args, unsigned long);
    va_end(args);
    const char *failure = getenv("DEXTOP_TEST_FAIL_PRCTL");
    if (failure && option == atoi(failure)) { errno = EPERM; return -1; }
    return syscall(__NR_prctl, option, a, b, c, d);
}
