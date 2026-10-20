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

#ifndef VREND_TWEAKS_H
#define VREND_TWEAKS_H

#include "virgl_protocol.h"

#include <stdint.h>
#include <stdbool.h>

struct vrend_context_tweaks {
   uint32_t active_tweaks;
   int32_t tf3_samples_passed_factor;
};

bool vrend_get_tweak_is_active(struct vrend_context_tweaks *ctx,
                               enum vrend_tweak_type t);

bool vrend_get_tweak_is_active_with_params(struct vrend_context_tweaks *ctx,
                                           enum vrend_tweak_type t, void *params);

void vrend_set_active_tweaks(struct vrend_context_tweaks *ctx, uint32_t tweak_id, uint32_t tweak_value);


void vrend_set_tweak_from_env(struct vrend_context_tweaks *ctx);

#endif // VREND_TWEAKS_H
