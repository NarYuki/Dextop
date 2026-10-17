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

#include "vrend_debug.h"
#include "vrend_renderer.h"
#include "util/u_debug.h"
#include <stdlib.h>
#include <stdio.h>

#ifdef _WIN32
#include <process.h>
#endif

static const char *command_names[VIRGL_MAX_COMMANDS] = {
   "NOP",
   "CREATE_OBJECT",
   "BIND_OBJECT",
   "DESTROY_OBJECT",
   "SET_VIEWPORT_STATE",
   "SET_FRAMEBUFFER_STATE",
   "SET_VERTEX_BUFFERS",
   "CLEAR",
   "DRAW_VBO",
   "RESOURCE_INLINE_WRITE",
   "SET_SAMPLER_VIEWS",
   "SET_INDEX_BUFFER",
   "SET_CONSTANT_BUFFER",
   "SET_STENCIL_REF",
   "SET_BLEND_COLOR",
   "SET_SCISSOR_STATE",
   "BLIT",
   "RESOURCE_COPY_REGION",
   "BIND_SAMPLER_STATES",
   "BEGIN_QUERY",
   "END_QUERY",
   "GET_QUERY_RESULT",
   "SET_POLYGON_STIPPLE",
   "SET_CLIP_STATE",
   "SET_SAMPLE_MASK",
   "SET_STREAMOUT_TARGETS",
   "SET_RENDER_CONDITION",
   "SET_UNIFORM_BUFFER",
   "SET_SUB_CTX",
   "CREATE_SUB_CTX",
   "DESTROY_SUB_CTX",
   "BIND_SHADER",
   "SET_TESS_STATE",
   "SET_MIN_SAMPLES",
   "SET_SHADER_BUFFERS",
   "SET_SHADER_IMAGES",
   "MEMORY_BARRIER",
   "LAUNCH_GRID",
   "SET_FRAMEBUFFER_STATE_NO_ATTACH",
   "TEXTURE_BARRIER",
   "SET_ATOMIC_BUFFERS",
   "SET_DEBUG_FLAGS",
   "GET_QUERY_RESULT_QBO",
   "TRANSFER3D",
   "END_TRANSFERS",
   "COPY_TRANSFER3D",
   "SET_TWEAKS",
   "CLEAR_TEXTURE",
   "PIPE_RESOURCE_CREATE",
   "PIPE_RESOURCE_SET_TYPE",
   "GET_MEMORY_INFO",
   "SEND_STRING_MARKER",
   "LINK_SHADER",
   "CREATE_VIDEO_CODEC",
   "DESTROY_VIDEO_CODEC",
   "CREATE_VIDEO_BUFFER",
   "DESTROY_VIDEO_BUFFER",
   "BEGIN_FRAME",
   "DECODE_MACROBLOCK",
   "DECODE_BITSTREAM",
   "ENCODE_BITSTREAM",
   "END_FRAME",
   "CLEAR_SURFACE",
   "GET_PIPE_RESOURCE_LAYOUT",
};

static const char *object_type_names[VIRGL_MAX_OBJECTS] = {
   "NULL",
   "BLEND",
   "RASTERIZER",
   "DSA",
   "SHADER",
   "VERTEX_ELEMENTS",
   "SAMPLER_VIEW",
   "SAMPLER_STATE",
   "SURFACE",
   "QUERY",
   "STREAMOUT_TARGET",
   "MSAA_SURFACE"
};

#ifdef _WIN32
static int setenv(const char *name, const char *value, int overwrite)
{
    int errcode = 0;
    if(!overwrite) {
        size_t envsize = 0;
        errcode = getenv_s(&envsize, NULL, 0, name);
        if(errcode || envsize) return errcode;
    }
    return _putenv_s(name, value);
}

static int unsetenv(const char *name)
{
   return _putenv_s(name, NULL);
}
#endif

const char *vrend_get_comand_name(enum virgl_context_cmd cmd)
{
   if (cmd < VIRGL_MAX_COMMANDS)
      return command_names[cmd];
   return "UNKNOWN";
}

extern const char *vrend_get_object_type_name(enum virgl_object_type obj)
{
   if (obj < VIRGL_MAX_OBJECTS)
      return object_type_names[obj];
   return "UNKNOWN";
}

static const struct debug_named_value vrend_debug_options[] = {
   {"tgsi", dbg_shader_tgsi, "Print TGSI"},
   {"glsl", dbg_shader_glsl, "Print GLSL shaders created from TGSI"},
   {"shader", dbg_shader, "Print TGSI and created GLSL shaders"},
   {"stream", dbg_shader_streamout, "Print shader streamout"},
   {"cmd", dbg_cmd, "Print incoming commands"},
   {"obj", dbg_object, "Print object creation"},
   {"blit", dbg_blit, "Debug blit code path"},
   {"copyres", dbg_copy_resource, "Debug copy resource code path"},
   {"feat", dbg_features, "Log features found"},
   {"tex", dbg_tex, "Log texture operations"},
   {"caller", dbg_caller, "Log who is creating the context"},
   {"tweak", dbg_tweak, "Log tweaks"},
   {"query", dbg_query, "Log queries"},
   {"gles", dbg_gles, "GLES host specific debug"},
   {"bgra", dbg_bgra, "Debug specific to BGRA emulation on GLES hosts"},
   {"all", dbg_all, "Enable all debugging output"},
   {"guestallow", dbg_allow_guest_override, "Allow the guest to override the debug flags"},
   {"khr", dbg_khr, "Enable debug via KHR_debug extension"},
   {"d3d", dbg_d3d, "Enable D3D-related debug"},
   {"dump_cmd_streams", dbg_dump_cmd_streams, "Dump command streams so they can be used as seeds"},
   DEBUG_NAMED_VALUE_END
};

static uint64_t vrend_debug_flags = 0;
static int vrend_debug_flags_initalized = 0;

int vrend_get_debug_flags(const char *flagstring)
{
   int retval;
   char buf[1024] = "";

   /* Unfortunately the available function to scan the flags take the string
    * from the environment. The alternative to using setenv would be to
    * duplicate code or to change the gallium/util intefaces and diverge more
    * from mesa. So just stick to the environment variable. */
   snprintf(buf, 1024, "VREND_TEMP_DEBUG_STRING_%d", getpid());
   setenv(buf, flagstring, 1);

   retval = (int)debug_get_flags_option(buf,
                                        vrend_debug_options, 0);
   unsetenv(buf);
   return retval;
}

void vrend_init_debug_flags(void)
{
   if (!vrend_debug_flags_initalized)  {
      vrend_debug_flags_initalized = 1;
      vrend_debug_flags = debug_get_flags_option("VREND_DEBUG",
                                                 vrend_debug_options, 0);
      if (vrend_debug_flags)
         virgl_override_log_level(VIRGL_LOG_LEVEL_DEBUG);
   }
}

unsigned vrend_debug(const struct vrend_context *ctx, enum virgl_debug_flags flag)
{
   return (vrend_debug_flags & flag) || vrend_context_has_debug_flag(ctx, flag);
}

void vrend_debug_add_flag(enum virgl_debug_flags flag)
{
   vrend_debug_flags |= flag;
}

int  vrend_debug_can_override(void)
{
   return vrend_debug_flags & dbg_allow_guest_override;
}
