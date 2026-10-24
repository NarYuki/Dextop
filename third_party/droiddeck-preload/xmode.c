#define _GNU_SOURCE
#include <dlfcn.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

typedef struct _XDisplay Display;
typedef unsigned long Atom;
typedef unsigned long Window;
typedef int Bool;

typedef int (*change_property_fn)(Display *, Window, Atom, Atom, int, int, const unsigned char *, int);
typedef Atom (*intern_atom_fn)(Display *, const char *, Bool);

static int following(void) {
  static int cached = -1;
  if (cached < 0) {
    const char *v = getenv("BL_FOLLOW_OUTPUT");
    cached = v && v[0] == '1';
  }
  return cached;
}

int XChangeProperty(Display *dpy, Window w, Atom property, Atom type, int format, int mode,
                    const unsigned char *data, int nelements) {
  static change_property_fn real;
  if (!real) real = (change_property_fn)dlsym(RTLD_NEXT, "XChangeProperty");
  if (!real) return 0;
  if (following() && format == 32 && nelements == 4 && data) {
    static intern_atom_fn intern;
    static Display *atom_dpy;
    static Atom mode_control;
    if (!intern) intern = (intern_atom_fn)dlsym(RTLD_NEXT, "XInternAtom");
    if (intern && atom_dpy != dpy) {
      mode_control = intern(dpy, "GAMESCOPE_XWAYLAND_MODE_CONTROL", 1);
      atom_dpy = dpy;
    }
    const long *v = (const long *)data;
    if (mode_control && property == mode_control && v[0] == 0) {
      static int said;
      if (said++ < 3)
        fprintf(stderr, "== steam: kept its screen at the panel's size (it asked for %ldx%ld)\n", v[1], v[2]);
      return 1;
    }
  }
  return real(dpy, w, property, type, format, mode, data, nelements);
}
