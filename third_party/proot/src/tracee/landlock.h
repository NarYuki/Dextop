#ifndef PROOT_LANDLOCK_H
#define PROOT_LANDLOCK_H
#include <stdbool.h>
/* Only the child which successfully restricted itself can enable the exemption. */
bool proot_landlock_active(void);
int proot_landlock_apply(void);
#endif
