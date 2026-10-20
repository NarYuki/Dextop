/**************************************************************************
 * 
 * Copyright 2008 VMware, Inc.
 * All Rights Reserved.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a
 * copy of this software and associated documentation files (the
 * "Software"), to deal in the Software without restriction, including
 * without limitation the rights to use, copy, modify, merge, publish,
 * distribute, sub license, and/or sell copies of the Software, and to
 * permit persons to whom the Software is furnished to do so, subject to
 * the following conditions:
 * 
 * The above copyright notice and this permission notice (including the
 * next paragraph) shall be included in all copies or substantial portions
 * of the Software.
 * 
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS
 * OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF
 * MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NON-INFRINGEMENT.
 * IN NO EVENT SHALL VMWARE AND/OR ITS SUPPLIERS BE LIABLE FOR
 * ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT,
 * TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE
 * SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 * 
 **************************************************************************/

#ifndef TGSI_SCAN_H
#define TGSI_SCAN_H


#include "pipe/p_compiler.h"
#include "pipe/p_state.h"
#include "pipe/p_shader_tokens.h"

#ifdef __cplusplus
extern "C" {
#endif

/**
 * Shader summary info
 */
struct tgsi_shader_info
{
   unsigned num_tokens;

   uint8_t num_inputs;
   uint8_t num_outputs;
   uint8_t input_semantic_name[PIPE_MAX_SHADER_INPUTS]; /**< TGSI_SEMANTIC_x */
   uint8_t input_semantic_index[PIPE_MAX_SHADER_INPUTS];
   uint8_t input_interpolate[PIPE_MAX_SHADER_INPUTS];
   uint8_t input_interpolate_loc[PIPE_MAX_SHADER_INPUTS];
   uint8_t input_usage_mask[PIPE_MAX_SHADER_INPUTS];
   uint8_t input_cylindrical_wrap[PIPE_MAX_SHADER_INPUTS];
   uint8_t output_semantic_name[PIPE_MAX_SHADER_OUTPUTS]; /**< TGSI_SEMANTIC_x */
   uint8_t output_semantic_index[PIPE_MAX_SHADER_OUTPUTS];

   uint8_t num_system_values;
   uint8_t system_value_semantic_name[PIPE_MAX_SHADER_INPUTS];

   uint8_t processor;

   uint32_t file_mask[TGSI_FILE_COUNT];  /**< bitmask of declared registers */
   unsigned file_count[TGSI_FILE_COUNT];  /**< number of declared registers */
   int file_max[TGSI_FILE_COUNT];  /**< highest index of declared registers */
   int const_file_max[PIPE_MAX_CONSTANT_BUFFERS];
   unsigned samplers_declared; /**< bitmask of declared samplers */

   uint8_t input_array_first[PIPE_MAX_SHADER_INPUTS];
   uint8_t input_array_last[PIPE_MAX_SHADER_INPUTS];
   uint8_t output_array_first[PIPE_MAX_SHADER_OUTPUTS];
   uint8_t output_array_last[PIPE_MAX_SHADER_OUTPUTS];
   unsigned array_max[TGSI_FILE_COUNT];  /**< highest index array per register file */

   unsigned immediate_count; /**< number of immediates declared */
   unsigned num_instructions;

   unsigned opcode_count[TGSI_OPCODE_LAST];  /**< opcode histogram */

   uint8_t colors_written;
   bool reads_position; /**< does fragment shader read position? */
   bool reads_z; /**< does fragment shader read depth? */
   bool writes_z;  /**< does fragment shader write Z value? */
   bool writes_stencil; /**< does fragment shader write stencil value? */
   bool writes_edgeflag; /**< vertex shader outputs edgeflag */
   bool uses_kill;  /**< KILL or KILL_IF instruction used? */
   bool uses_persp_center;
   bool uses_persp_centroid;
   bool uses_persp_sample;
   bool uses_linear_center;
   bool uses_linear_centroid;
   bool uses_linear_sample;
   bool uses_persp_opcode_interp_centroid;
   bool uses_persp_opcode_interp_offset;
   bool uses_persp_opcode_interp_sample;
   bool uses_linear_opcode_interp_centroid;
   bool uses_linear_opcode_interp_offset;
   bool uses_linear_opcode_interp_sample;
   bool uses_instanceid;
   bool uses_vertexid;
   bool uses_vertexid_nobase;
   bool uses_basevertex;
   bool uses_primid;
   bool uses_frontface;
   bool uses_invocationid;
   bool writes_psize;
   bool writes_clipvertex;
   bool writes_viewport_index;
   bool writes_layer;
   bool is_msaa_sampler[PIPE_MAX_SAMPLERS];
   bool uses_doubles; /**< uses any of the double instructions */
   unsigned clipdist_writemask;
   unsigned culldist_writemask;
   unsigned num_written_culldistance;
   unsigned num_written_clipdistance;
   /**
    * Bitmask indicating which register files are accessed with
    * indirect addressing.  The bits are (1 << TGSI_FILE_x), etc.
    */
   unsigned indirect_files;
   /**
    * Bitmask indicating which register files are read / written with
    * indirect addressing.  The bits are (1 << TGSI_FILE_x).
    */
   unsigned indirect_files_read;
   unsigned indirect_files_written;

   unsigned dimension_indirect_files;

   unsigned properties[TGSI_PROPERTY_COUNT]; /* index with TGSI_PROPERTY_ */

   /**
    * Max nesting limit of loops/if's
    */
   unsigned max_depth;
};

extern bool
tgsi_scan_shader(const struct tgsi_token *tokens,
                 struct tgsi_shader_info *info);


extern bool
tgsi_is_passthrough_shader(const struct tgsi_token *tokens);

#ifdef __cplusplus
} // extern "C"
#endif

#endif /* TGSI_SCAN_H */
