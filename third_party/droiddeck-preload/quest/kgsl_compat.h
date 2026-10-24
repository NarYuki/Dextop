/* SPDX-License-Identifier: GPL-3.0-or-later */
#ifndef DROIDDECK_QUEST_KGSL_COMPAT_H
#define DROIDDECK_QUEST_KGSL_COMPAT_H

#ifndef __user
#define __user
#endif
#include "../../adrenotools/src/hook/kgsl.h" /* DroidDeck app/src/main/cpp/adrenotools */

typedef int (*quest_ioctl_fn)(int, unsigned long, void *);
__attribute__((visibility("hidden")))
int quest_kgsl_ioctl(quest_ioctl_fn real, int fd, unsigned long request, void *arg);

#endif
