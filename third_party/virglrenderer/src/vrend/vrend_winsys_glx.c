/**************************************************************************
 *
 * Copyright (C) 2016 Red Hat Inc.
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


#ifdef HAVE_CONFIG_H
#include "config.h"
#endif

#include <epoxy/glx.h>
#include "virglrenderer.h"
#include "vrend_winsys_glx.h"

struct virgl_glx {
   Display *display;
   GLXFBConfig* fbConfigs;
   GLXPbuffer pbuffer;
};

struct virgl_glx *virgl_glx_init(void)
{
   struct virgl_glx *d;

   d = malloc(sizeof(struct virgl_glx));
   if (!d)
      return NULL;

   d->display = XOpenDisplay(NULL);

   if (!d->display) {
      free(d);
      return NULL;
   }

   int visualAttribs[] = { None };
   int numberOfFramebufferConfigurations = 0;

   d->fbConfigs = glXChooseFBConfig(d->display, DefaultScreen(d->display),
                                    visualAttribs, &numberOfFramebufferConfigurations);

   int pbufferAttribs[] = {
      GLX_PBUFFER_WIDTH,  32,
      GLX_PBUFFER_HEIGHT, 32,
      None
   };
   d->pbuffer = glXCreatePbuffer(d->display, d->fbConfigs[0], pbufferAttribs);

   return d;
}

void virgl_glx_destroy(struct virgl_glx *d)
{
   XFree(d->fbConfigs);
   glXDestroyPbuffer(d->display,d->pbuffer);
   XCloseDisplay(d->display);
   free(d);
}

virgl_renderer_gl_context virgl_glx_create_context(struct virgl_glx *d, struct virgl_gl_ctx_param *vparams)
{
   int compat = vparams->compat_ctx ? GLX_CONTEXT_COMPATIBILITY_PROFILE_BIT_ARB : GLX_CONTEXT_CORE_PROFILE_BIT_ARB;

   int context_attribs[] = {
      GLX_CONTEXT_MAJOR_VERSION_ARB, vparams->major_ver,
      GLX_CONTEXT_MINOR_VERSION_ARB, vparams->minor_ver,
      GLX_CONTEXT_PROFILE_MASK_ARB, compat,
      None
   };

   GLXContext ctx =
      glXCreateContextAttribsARB(d->display, d->fbConfigs[0],
                                 vparams->shared ? glXGetCurrentContext() : NULL,
                                 True, context_attribs);
   return (virgl_renderer_gl_context)ctx;
}

void virgl_glx_destroy_context(struct virgl_glx *d, virgl_renderer_gl_context virglctx)
{
   GLXContext ctx = (GLXContext)virglctx;

   glXDestroyContext(d->display,ctx);
}

int virgl_glx_make_context_current(struct virgl_glx *d, virgl_renderer_gl_context virglctx)
{
   return glXMakeContextCurrent(d->display, d->pbuffer, d->pbuffer, virglctx) ? 0 : -1;
}

uint32_t virgl_glx_query_video_memory(struct virgl_glx *d)
{
   uint32_t video_memory = 0;
   if (d) {
      if (epoxy_has_glx_extension(d->display, DefaultScreen(d->display), "GLX_MESA_query_renderer")) {
         glXQueryCurrentRendererIntegerMESA(GLX_RENDERER_VIDEO_MEMORY_MESA, &video_memory);
      }
   }

   return video_memory;
}
