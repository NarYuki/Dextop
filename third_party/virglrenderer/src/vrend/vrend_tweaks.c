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

#include <string.h>
#include <stdlib.h>
#include "vrend_tweaks.h"
#include "vrend_debug.h"
#include "virgl_protocol.h"

inline static void get_tf3_samples_passed_factor(struct vrend_context_tweaks *ctx, void *params)
{
   *(uint32_t *)params =  ctx->tf3_samples_passed_factor;
}

bool vrend_get_tweak_is_active_with_params(struct vrend_context_tweaks *ctx, enum vrend_tweak_type t, void *params)
{
   if (!(ctx->active_tweaks & (1u << t)))
      return false;

   switch (t) {
   case virgl_tweak_gles_tf3_samples_passes_multiplier:
      get_tf3_samples_passed_factor(ctx, params); break;
   default:
      ;
   }

   return true;
}

bool vrend_get_tweak_is_active(struct vrend_context_tweaks *ctx, enum vrend_tweak_type t)
{
   return (ctx->active_tweaks & (1u << t)) ? true : false;
}

const char *tweak_debug_table[] = {
   [virgl_tweak_gles_brga_emulate] =
   "(non-functional) GLES: Skip linearization in blits to BGRA_UNORM surfaces",

   [virgl_tweak_gles_brga_apply_dest_swizzle] =
   "(non-functional) GLES: Apply dest swizzle when a BGRA surface is emulated by an RGBA surface",

   [virgl_tweak_gles_tf3_samples_passes_multiplier] =
    "GLES: Value to return when emulating GL_SAMPLES_PASSES by using GL_ANY_SAMPLES_PASSES",

   [virgl_tweak_undefined] = "Undefined tweak"
};

static void set_tweak_and_params(struct vrend_context_tweaks *ctx,
                                 enum vrend_tweak_type t, uint32_t value)
{
   ctx->active_tweaks |= 1u << t;

   switch (t) {
   case virgl_tweak_gles_tf3_samples_passes_multiplier:
      ctx->tf3_samples_passed_factor = value;
      break;
   default:
      ;
   }
}

static void set_tweak_and_params_from_string(struct vrend_context_tweaks *ctx,
                                             enum vrend_tweak_type t, const char *value)
{
   ctx->active_tweaks |= 1u << t;

   switch (t) {
   case virgl_tweak_gles_tf3_samples_passes_multiplier:
      ctx->tf3_samples_passed_factor = value ? atoi(value) : 2048;
      break;
   default:
      ;
   }
}

/* we expect a string like tweak1:value,tweak2:value */
void vrend_set_active_tweaks(struct vrend_context_tweaks *ctx, uint32_t tweak_id, uint32_t value)
{
   if (tweak_id < virgl_tweak_undefined) {
      VREND_DEBUG(dbg_tweak, NULL, "Apply tweak '%s' = %u\n", tweak_debug_table[tweak_id], value);
      set_tweak_and_params(ctx, tweak_id, value);
   } else {
      VREND_DEBUG(dbg_tweak, NULL, "Unknown tweak %d = %d sent\n", tweak_id, value);
   }
}

struct {
   enum vrend_tweak_type flag;
   const char *name;
   const char *descr;
} tweak_table [] = {
   { virgl_tweak_gles_brga_emulate, "emu-bgra",
     "(non-functional) Emulate BGRA_UNORM and BGRA_SRB by using swizzled RGBA formats" },

   { virgl_tweak_gles_brga_apply_dest_swizzle, "bgra-dest-swz",
     "(non-functional) Apply the destination swizzle of emulated BGRA surfaces in blits"},

   { virgl_tweak_gles_tf3_samples_passes_multiplier, "samples-passed",
     "Return this value when GL_SAMPLES_PASSED is emulated by GL_ANY_SAMPLES_PASSED"},

   { virgl_tweak_undefined, NULL, NULL}
};


void vrend_set_tweak_from_env(struct vrend_context_tweaks *ctx)
{
   char *tweaks = getenv("VREND_TWEAK");
   if (tweaks) {
      VREND_DEBUG(dbg_tweak, NULL, "ENV tweaks %s\n", tweaks);

      char *saveptr;
      char *tweak_descr_copy = strdup(tweaks);

      char *tweak = strtok_r(tweak_descr_copy, ":", &saveptr);
      while (tweak) {
         char *tweak_param = strtok_r(NULL, ",", &saveptr);

         for (int i = 0; i < virgl_tweak_undefined; ++i) {
            if (!strcmp(tweak, tweak_table[i].name)) {
               VREND_DEBUG(dbg_tweak, NULL, "Apply tweak %s=%s\n", tweak, tweak_param);
               set_tweak_and_params_from_string(ctx, tweak_table[i].flag, tweak_param);
            }
         }
         tweak = strtok_r(NULL, ":", &saveptr);
      }
      free(tweak_descr_copy);
   }
}
