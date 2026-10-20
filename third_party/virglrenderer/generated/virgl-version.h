/**************************************************************************
 *
 * Copyright (C) 2024 Red Hat Inc.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a
 * copy of this software and associated documentation files (the "Software"),
 * to deal in the Software without restriction, including without limitation
 * the rights to use, copy, modify, merge, publish, distribute, sublicense,
 * and/or sell copies of the Software, and to permit persons to whom the
 * Software is furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included
 * in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS
 * OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT.  IN NO EVENT SHALL
 * THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR
 * OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE,
 * ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR
 * OTHER DEALINGS IN THE SOFTWARE.
 *
 **************************************************************************/

#ifndef VIRGL_VERSION_H
#define VIRGL_VERSION_H

/**
 * VIRGL_MAJOR_VERSION:
 *
 * Evaluates to the major version number of the library version,
 * e.g. 1 in 1.2.3.
 *
 * Since: 1.1
 */
#define VIRGL_MAJOR_VERSION          (1)

/**
 * VIRGL_MINOR_VERSION:
 *
 * Evaluates to the minor version number of the library version,
 * e.g. 2 in 1.2.3.
 *
 * Since: 1.1
 */
#define VIRGL_MINOR_VERSION          (3)

/**
 * VIRGL_MICRO_VERSION:
 *
 * Evaluates to the micro version number of the library version,
 * e.g. 3 in 1.2.3.
 *
 * Since: 1.1
 */
#define VIRGL_MICRO_VERSION          (0)

#define VIRGL_CHECK_VERSION(major,minor,micro) \
  (VIRGL_MAJOR_VERSION > (major) || \
   VIRGL_MAJOR_VERSION == (major) && VIRGL_MINOR_VERSION > (minor) || \
   VIRGL_MAJOR_VERSION == (major) && VIRGL_MINOR_VERSION == (minor) && VIRGL_MICRO_VERSION >= (micro))

#endif /* VIRGL_VERSION_H */
