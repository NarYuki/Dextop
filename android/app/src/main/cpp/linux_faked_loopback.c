/* Loaded only by Dextop's private faked launcher, never by guest applications.
 * Keeps the distro's actual faked/libfakeroot protocol and confines its listener.
 * This is daemon configuration, not a sandbox against malicious shell processes. */
#include <stddef.h>
#include <stdint.h>
extern int *__errno_location(void);
static long call3(long number, long a, long b, long c) {
    register long x8 __asm__("x8") = number;
    register long x0 __asm__("x0") = a;
    register long x1 __asm__("x1") = b;
    register long x2 __asm__("x2") = c;
    __asm__ volatile("svc #0" : "+r"(x0) : "r"(x8), "r"(x1), "r"(x2) : "memory", "cc");
    return x0;
}
static int checked(long result) {
    if (result < 0 && result >= -4095) { *__errno_location() = (int)-result; return -1; }
    return (int)result;
}
__attribute__((visibility("default")))
int bind(int fd, const void *address, unsigned length) {
    unsigned char local[128];
    const unsigned char *source = address;
    if (address && length <= sizeof local && length >= 2 &&
        (*(const unsigned short *)address == 2 || *(const unsigned short *)address == 10)) {
        for (unsigned i = 0; i < length; ++i) local[i] = source[i];
        if (*(const unsigned short *)address == 2 && length >= 16) {
            local[4] = 127; local[5] = 0; local[6] = 0; local[7] = 1;
        } else if (*(const unsigned short *)address == 10 && length >= 28) {
            for (unsigned i = 8; i < 24; ++i) local[i] = 0;
            local[23] = 1;
        }
        address = local;
    }
    return checked(call3(200, fd, (long)address, length));
}

/* faked can call listen() without bind() when requesting an ephemeral port.
 * Linux otherwise auto-binds that socket to every interface. */
__attribute__((visibility("default")))
int listen(int fd, int backlog) {
    unsigned char address[128];
    unsigned length = sizeof address;
    if (call3(204, fd, (long)address, (long)&length) == 0 && length >= 4 &&
        (*(unsigned short *)address == 2 || *(unsigned short *)address == 10) &&
        address[2] == 0 && address[3] == 0) {
        if (bind(fd, address, length) != 0) return -1;
    }
    return checked(call3(201, fd, backlog, 0));
}
