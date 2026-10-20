/* Minimal replace.h for building talloc on Android (bionic provides the libc). */
#ifndef DEXTOP_TALLOC_REPLACE_H
#define DEXTOP_TALLOC_REPLACE_H
#include <errno.h>
#include <inttypes.h>
#include <limits.h>
#include <stdarg.h>
#include <stdbool.h>
#include <stddef.h>
#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/types.h>
#include <unistd.h>
#define TALLOC_BUILD_VERSION_MAJOR 2
#define TALLOC_BUILD_VERSION_MINOR 4
#define TALLOC_BUILD_VERSION_RELEASE 2
#define HAVE_VA_COPY 1
#define HAVE_CONSTRUCTOR_ATTRIBUTE 1
#define HAVE_SYS_AUXV_H 1
#ifndef _PUBLIC_
#define _PUBLIC_ __attribute__((visibility("default")))
#endif
#ifndef likely
#define likely(x) __builtin_expect(!!(x), 1)
#define unlikely(x) __builtin_expect(!!(x), 0)
#endif
#ifndef discard_const
#define discard_const(ptr) ((void *)((uintptr_t)(ptr)))
#endif
#ifndef discard_const_p
#define discard_const_p(type, ptr) ((type *)discard_const(ptr))
#endif
#ifndef MIN
#define MIN(a, b) ((a) < (b) ? (a) : (b))
#endif
#ifndef MAX
#define MAX(a, b) ((a) > (b) ? (a) : (b))
#endif
#endif
