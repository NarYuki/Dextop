/**************************************************************************
 *
 * Copyright (C) 2018 Collabora Ltd
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

#ifndef vrend_debug_h
#define vrend_debug_h

#include "virgl_protocol.h"
#include "virgl_util.h"
#include <stdarg.h>

struct vrend_context;
void vrend_print_context_name(const struct vrend_context *ctx);

enum virgl_debug_flags {
   dbg_shader_tgsi = 1 << 0,
   dbg_shader_glsl = 1 << 1,
   dbg_shader_streamout = 1 << 2,
   dbg_shader = dbg_shader_tgsi | dbg_shader_glsl | dbg_shader_streamout,
   dbg_cmd = 1 << 3,
   dbg_object = 1 << 4,
   dbg_blit = 1 << 5,
   dbg_copy_resource = 1 << 6,
   dbg_features = 1 << 7,
   dbg_tex = 1 << 8,
   dbg_caller = 1 << 9,
   dbg_tweak =  1 << 10,
   dbg_query =  1 << 11,
   dbg_gles =  1 << 12,
   dbg_bgra = 1 << 13,
   dbg_all = (1 << 14) - 1,
   dbg_allow_guest_override = 1 << 16,
   dbg_feature_use = 1 << 17,
   dbg_khr = 1 << 18,
   dbg_d3d = 1 << 19,
   dbg_dump_cmd_streams = 1 << 20,
};

const char *vrend_get_comand_name(enum virgl_context_cmd cmd);

const char *vrend_get_object_type_name(enum virgl_object_type cmd);


void vrend_init_debug_flags(void);

int  vrend_debug_can_override(void);

int vrend_get_debug_flags(const char *flagstring);

void vrend_context_set_debug_flags(struct vrend_context *ctx, const char *flags);

unsigned vrend_debug(const struct vrend_context *ctx, enum virgl_debug_flags flag);

void vrend_debug_add_flag(enum virgl_debug_flags flag);

#ifdef NDEBUG
#define VREND_DEBUG_ENABLED (false)
#else
#define VREND_DEBUG_ENABLED (true)
#endif

#define VREND_DEBUG(flag, ctx,  ...) \
   if (VREND_DEBUG_ENABLED && vrend_debug(ctx, flag)) \
      do { \
            vrend_print_context_name(ctx); \
            virgl_debug(__VA_ARGS__); \
      } while (0)

#define VREND_DEBUG_EXT(flag, ctx, X) \
   if (VREND_DEBUG_ENABLED && vrend_debug(ctx, flag)) \
      do { \
            vrend_print_context_name(ctx); \
            X; \
      } while (0)

#define VREND_DEBUG_NOCTX(flag, ctx, ...) \
   if (VREND_DEBUG_ENABLED && vrend_debug(ctx, flag)) \
      do { \
            virgl_debug(__VA_ARGS__); \
      } while (0)

#endif
