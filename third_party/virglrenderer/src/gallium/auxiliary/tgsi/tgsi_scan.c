/**************************************************************************
 * 
 * Copyright 2008 VMware, Inc.
 * All Rights Reserved.
 * Copyright 2008 VMware, Inc.  All rights Reserved.
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

/**
 * TGSI program scan utility.
 * Used to determine which registers and instructions are used by a shader.
 *
 * Authors:  Brian Paul
 */


#include "util/u_debug.h"
#include "util/u_math.h"
#include "util/u_memory.h"
#include "util/u_prim.h"
#include "tgsi/tgsi_parse.h"
#include "tgsi/tgsi_util.h"
#include "tgsi/tgsi_scan.h"




/**
 * Scan the given TGSI shader to collect information such as number of
 * registers used, special instructions used, etc.
 * \return info  the result of the scan
 */
bool
tgsi_scan_shader(const struct tgsi_token *tokens,
                 struct tgsi_shader_info *info)
{
   unsigned procType, i;
   struct tgsi_parse_context parse;
   unsigned current_depth = 0;

   memset(info, 0, sizeof(*info));
   for (i = 0; i < TGSI_FILE_COUNT; i++)
      info->file_max[i] = -1;
   for (i = 0; i < ARRAY_SIZE(info->const_file_max); i++)
      info->const_file_max[i] = -1;
   info->properties[TGSI_PROPERTY_GS_INVOCATIONS] = 1;

   /**
    ** Setup to begin parsing input shader
    **/
   if (tgsi_parse_init( &parse, tokens ) != TGSI_PARSE_OK) {
      debug_printf("tgsi_parse_init() failed in tgsi_scan_shader()!\n");
      return false;
   }
   procType = parse.FullHeader.Processor.Processor;
   assert(procType == TGSI_PROCESSOR_FRAGMENT ||
          procType == TGSI_PROCESSOR_VERTEX ||
          procType == TGSI_PROCESSOR_GEOMETRY ||
          procType == TGSI_PROCESSOR_TESS_CTRL ||
          procType == TGSI_PROCESSOR_TESS_EVAL ||
          procType == TGSI_PROCESSOR_COMPUTE);
   info->processor = procType;


   /**
    ** Loop over incoming program tokens/instructions
    */
   while( !tgsi_parse_end_of_tokens( &parse ) ) {

      info->num_tokens++;

      tgsi_parse_token( &parse );

      switch( parse.FullToken.Token.Type ) {
      case TGSI_TOKEN_TYPE_INSTRUCTION:
         {
            const struct tgsi_full_instruction *fullinst
               = &parse.FullToken.FullInstruction;
            unsigned i;

            assert(fullinst->Instruction.Opcode < TGSI_OPCODE_LAST);
            info->opcode_count[fullinst->Instruction.Opcode]++;

            switch (fullinst->Instruction.Opcode) {
            case TGSI_OPCODE_IF:
            case TGSI_OPCODE_UIF:
            case TGSI_OPCODE_BGNLOOP:
               current_depth++;
               info->max_depth = MAX2(info->max_depth, current_depth);
               break;
            case TGSI_OPCODE_ENDIF:
            case TGSI_OPCODE_ENDLOOP:
               current_depth--;
               break;
            default:
               break;
            }

            if (fullinst->Instruction.Opcode == TGSI_OPCODE_INTERP_CENTROID ||
                fullinst->Instruction.Opcode == TGSI_OPCODE_INTERP_OFFSET ||
                fullinst->Instruction.Opcode == TGSI_OPCODE_INTERP_SAMPLE) {
               const struct tgsi_full_src_register *src0 = &fullinst->Src[0];
               unsigned input;

               if (src0->Register.Indirect && src0->Indirect.ArrayID) {
                  if (src0->Indirect.ArrayID >= PIPE_MAX_SHADER_INPUTS) {
                     debug_printf("TGSI Error: Indirect ArrayID %d exeeds supported limit\n",
                                  src0->Indirect.ArrayID);
                     return false;
                  }
                  input = info->input_array_first[src0->Indirect.ArrayID];
               } else
                  input = src0->Register.Index;

               if (input >= PIPE_MAX_SHADER_INPUTS) {
                  debug_printf("TGSI Error: input %d exeeds supported limit\n", input);
                  return false;
               }

               /* For the INTERP opcodes, the interpolation is always
                * PERSPECTIVE unless LINEAR is specified.
                */
               switch (info->input_interpolate[input]) {
               case TGSI_INTERPOLATE_COLOR:
               case TGSI_INTERPOLATE_CONSTANT:
               case TGSI_INTERPOLATE_PERSPECTIVE:
                  switch (fullinst->Instruction.Opcode) {
                  case TGSI_OPCODE_INTERP_CENTROID:
                     info->uses_persp_opcode_interp_centroid = true;
                     break;
                  case TGSI_OPCODE_INTERP_OFFSET:
                     info->uses_persp_opcode_interp_offset = true;
                     break;
                  case TGSI_OPCODE_INTERP_SAMPLE:
                     info->uses_persp_opcode_interp_sample = true;
                     break;
                  }
                  break;

               case TGSI_INTERPOLATE_LINEAR:
                  switch (fullinst->Instruction.Opcode) {
                  case TGSI_OPCODE_INTERP_CENTROID:
                     info->uses_linear_opcode_interp_centroid = true;
                     break;
                  case TGSI_OPCODE_INTERP_OFFSET:
                     info->uses_linear_opcode_interp_offset = true;
                     break;
                  case TGSI_OPCODE_INTERP_SAMPLE:
                     info->uses_linear_opcode_interp_sample = true;
                     break;
                  }
                  break;
               }
            }

            if (fullinst->Instruction.Opcode >= TGSI_OPCODE_F2D &&
                fullinst->Instruction.Opcode <= TGSI_OPCODE_DSSG)
               info->uses_doubles = true;

            for (i = 0; i < fullinst->Instruction.NumSrcRegs; i++) {
               const struct tgsi_full_src_register *src =
                  &fullinst->Src[i];
               int ind = src->Register.Index;

               /* Mark which inputs are effectively used */
               if (src->Register.File == TGSI_FILE_INPUT) {
                  unsigned usage_mask;
                  usage_mask = tgsi_util_get_inst_usage_mask(fullinst, i);
                  if (src->Register.Indirect) {
                     for (ind = 0; ind < info->num_inputs; ++ind) {
                        info->input_usage_mask[ind] |= usage_mask;
                     }
                  } else {
                     if (ind < 0 || ind >= PIPE_MAX_SHADER_INPUTS) {
                        debug_printf("TGSI Error: input %d exeeds supported limit\n", ind);
                        return false;
                     }
                     info->input_usage_mask[ind] |= usage_mask;
                  }

                  if (procType == TGSI_PROCESSOR_FRAGMENT &&
                      info->reads_position &&
                      src->Register.Index == 0 &&
                      (src->Register.SwizzleX == TGSI_SWIZZLE_Z ||
                       src->Register.SwizzleY == TGSI_SWIZZLE_Z ||
                       src->Register.SwizzleZ == TGSI_SWIZZLE_Z ||
                       src->Register.SwizzleW == TGSI_SWIZZLE_Z)) {
                     info->reads_z = true;
                  }
               }

               /* check for indirect register reads */
               if (src->Register.Indirect) {
                  info->indirect_files |= (1u << src->Register.File);
                  info->indirect_files_read |= (1u << src->Register.File);
               }

               if (src->Register.Dimension && src->Dimension.Indirect) {
                  info->dimension_indirect_files |= (1u << src->Register.File);
               }
               /* MSAA samplers */
               if (src->Register.File == TGSI_FILE_SAMPLER) {
                  if (!fullinst->Instruction.Texture) {
                     debug_printf("TGSI Error: unspecified sampler instruction texture\n");
                     return false;
                  }

                  if ((unsigned)src->Register.Index >= PIPE_MAX_SAMPLERS) {
                     debug_printf("TGSI Error: sampler ID %d out of range\n", src->Register.Index);
                     return false;
                  }

                  if (fullinst->Texture.Texture == TGSI_TEXTURE_2D_MSAA ||
                       fullinst->Texture.Texture == TGSI_TEXTURE_2D_ARRAY_MSAA) {
                     info->is_msaa_sampler[src->Register.Index] = true;
                  }
               }
            }

            /* check for indirect register writes */
            for (i = 0; i < fullinst->Instruction.NumDstRegs; i++) {
               const struct tgsi_full_dst_register *dst = &fullinst->Dst[i];
               if (dst->Register.Indirect) {
                  info->indirect_files |= (1u << dst->Register.File);
                  info->indirect_files_written |= (1u << dst->Register.File);
               }
               if (dst->Register.Dimension && dst->Dimension.Indirect)
                  info->dimension_indirect_files |= (1u << dst->Register.File);
            }

            info->num_instructions++;
         }
         break;

      case TGSI_TOKEN_TYPE_DECLARATION:
         {
            const struct tgsi_full_declaration *fulldecl
               = &parse.FullToken.FullDeclaration;
            const unsigned file = fulldecl->Declaration.File;
            unsigned reg;

            if (file >= TGSI_FILE_COUNT) {
               debug_printf("TGSI Error: unknown file %d\n", file);
               return false;
            }


            if (fulldecl->Declaration.Array) {
               unsigned array_id = fulldecl->Array.ArrayID;

               switch (file) {
               case TGSI_FILE_INPUT:
                  if (array_id >= PIPE_MAX_SHADER_INPUTS) {
                     debug_printf("TGSI Error: input array ID %d exeeds supported limit\n", array_id);
                     return false;
                  }

                  info->input_array_first[array_id] = fulldecl->Range.First;
                  info->input_array_last[array_id] = fulldecl->Range.Last;
                  break;
               case TGSI_FILE_OUTPUT:
                  if (array_id >= PIPE_MAX_SHADER_OUTPUTS) {
                     debug_printf("TGSI Error: output array ID %d exeeds supported limit\n", array_id);
                     return false;
                  }
                  info->output_array_first[array_id] = fulldecl->Range.First;
                  info->output_array_last[array_id] = fulldecl->Range.Last;
                  break;
               }
               info->array_max[file] = MAX2(info->array_max[file], array_id);
            }

            for (reg = fulldecl->Range.First;
                 reg <= fulldecl->Range.Last;
                 reg++) {
               unsigned semName = fulldecl->Semantic.Name;
               unsigned semIndex =
                  fulldecl->Semantic.Index + (reg - fulldecl->Range.First);

               /*
                * only first 32 regs will appear in this bitfield, if larger
                * bits will wrap around.
                */
               info->file_mask[file] |= (1u << (reg & 31));
               info->file_count[file]++;
               info->file_max[file] = MAX2(info->file_max[file], (int)reg);

               if (file == TGSI_FILE_CONSTANT) {
                  unsigned buffer = 0;

                  if (fulldecl->Declaration.Dimension)
                     buffer = fulldecl->Dim.Index2D;

                  if (buffer >= PIPE_MAX_CONSTANT_BUFFERS) {
                     debug_printf("TGSI Error: constant buffer id %d exeeds supported limit\n", buffer);
                     return false;
                  }

                  info->const_file_max[buffer] =
                        MAX2(info->const_file_max[buffer], (int)reg);
               }
               else if (file == TGSI_FILE_INPUT) {
                  if (reg >= PIPE_MAX_SHADER_INPUTS) {
                     debug_printf("TGSI Error: input register %d exeeds supported limit\n", reg);
                     return false;
                  }

                  info->input_semantic_name[reg] = (uint8_t) semName;
                  info->input_semantic_index[reg] = (uint8_t) semIndex;
                  info->input_interpolate[reg] = (uint8_t)fulldecl->Interp.Interpolate;
                  info->input_interpolate_loc[reg] = (uint8_t)fulldecl->Interp.Location;
                  info->input_cylindrical_wrap[reg] = (uint8_t)fulldecl->Interp.CylindricalWrap;
                  info->num_inputs++;

                  if (info->num_inputs >= PIPE_MAX_SHADER_INPUTS) {
                     debug_printf("TGSI Error: mumber of inputs %d exeeds supported limit\n",
                                  info->num_inputs);
                     return false;
                  }

                  /* Only interpolated varyings. Don't include POSITION.
                   * Don't include integer varyings, because they are not
                   * interpolated.
                   */
                  if (semName == TGSI_SEMANTIC_GENERIC ||
                      semName == TGSI_SEMANTIC_TEXCOORD ||
                      semName == TGSI_SEMANTIC_COLOR ||
                      semName == TGSI_SEMANTIC_BCOLOR ||
                      semName == TGSI_SEMANTIC_FOG ||
                      semName == TGSI_SEMANTIC_CLIPDIST ||
                      semName == TGSI_SEMANTIC_CULLDIST) {
                     switch (fulldecl->Interp.Interpolate) {
                     case TGSI_INTERPOLATE_COLOR:
                     case TGSI_INTERPOLATE_PERSPECTIVE:
                        switch (fulldecl->Interp.Location) {
                        case TGSI_INTERPOLATE_LOC_CENTER:
                           info->uses_persp_center = true;
                           break;
                        case TGSI_INTERPOLATE_LOC_CENTROID:
                           info->uses_persp_centroid = true;
                           break;
                        case TGSI_INTERPOLATE_LOC_SAMPLE:
                           info->uses_persp_sample = true;
                           break;
                        }
                        break;
                     case TGSI_INTERPOLATE_LINEAR:
                        switch (fulldecl->Interp.Location) {
                        case TGSI_INTERPOLATE_LOC_CENTER:
                           info->uses_linear_center = true;
                           break;
                        case TGSI_INTERPOLATE_LOC_CENTROID:
                           info->uses_linear_centroid = true;
                           break;
                        case TGSI_INTERPOLATE_LOC_SAMPLE:
                           info->uses_linear_sample = true;
                           break;
                        }
                        break;
                     /* TGSI_INTERPOLATE_CONSTANT doesn't do any interpolation. */
                     }
                  }

                  if (semName == TGSI_SEMANTIC_PRIMID)
                     info->uses_primid = true;
                  else if (procType == TGSI_PROCESSOR_FRAGMENT) {
                     if (semName == TGSI_SEMANTIC_POSITION)
                        info->reads_position = true;
                     else if (semName == TGSI_SEMANTIC_FACE)
                        info->uses_frontface = true;
                  }
               }
               else if (file == TGSI_FILE_SYSTEM_VALUE) {
                  unsigned index = fulldecl->Range.First;

                  if (index >= PIPE_MAX_SHADER_INPUTS) {
                     debug_printf("TGSI Error: system value %d exeeds supported limit\n", index);
                     return false;
                  }

                  info->system_value_semantic_name[index] = semName;
                  info->num_system_values = MAX2(info->num_system_values,
                                                 index + 1);

                  if (semName == TGSI_SEMANTIC_INSTANCEID) {
                     info->uses_instanceid = true;
                  }
                  else if (semName == TGSI_SEMANTIC_VERTEXID) {
                     info->uses_vertexid = true;
                  }
                  else if (semName == TGSI_SEMANTIC_VERTEXID_NOBASE) {
                     info->uses_vertexid_nobase = true;
                  }
                  else if (semName == TGSI_SEMANTIC_BASEVERTEX) {
                     info->uses_basevertex = true;
                  }
                  else if (semName == TGSI_SEMANTIC_PRIMID) {
                     info->uses_primid = true;
                  } else if (semName == TGSI_SEMANTIC_INVOCATIONID) {
                     info->uses_invocationid = true;
                  }
               }
               else if (file == TGSI_FILE_OUTPUT) {

                  if (reg >= PIPE_MAX_SHADER_OUTPUTS) {
                     debug_printf("TGSI Error: output %d exeeds supported limit\n", reg);
                     return false;
                  }

                  info->output_semantic_name[reg] = (uint8_t) semName;
                  info->output_semantic_index[reg] = (uint8_t) semIndex;
                  info->num_outputs++;

                  if (info->num_outputs >= PIPE_MAX_SHADER_OUTPUTS) {
                     debug_printf("TGSI Error: number of outputs %d exeeds supported  limit\n",
                                  info->num_outputs);
                     return false;
                  }

                  if (semName == TGSI_SEMANTIC_COLOR)
                     info->colors_written |= 1u << semIndex;

                  if (procType == TGSI_PROCESSOR_VERTEX ||
                      procType == TGSI_PROCESSOR_GEOMETRY ||
                      procType == TGSI_PROCESSOR_TESS_CTRL ||
                      procType == TGSI_PROCESSOR_TESS_EVAL) {
                     if (semName == TGSI_SEMANTIC_VIEWPORT_INDEX) {
                        info->writes_viewport_index = true;
                     }
                     else if (semName == TGSI_SEMANTIC_LAYER) {
                        info->writes_layer = true;
                     }
                     else if (semName == TGSI_SEMANTIC_PSIZE) {
                        info->writes_psize = true;
                     }
                     else if (semName == TGSI_SEMANTIC_CLIPVERTEX) {
                        info->writes_clipvertex = true;
                     }
                  }

                  if (procType == TGSI_PROCESSOR_FRAGMENT) {
                     if (semName == TGSI_SEMANTIC_POSITION) {
                        info->writes_z = true;
                     }
                     else if (semName == TGSI_SEMANTIC_STENCIL) {
                        info->writes_stencil = true;
                     }
                  }

                  if (procType == TGSI_PROCESSOR_VERTEX) {
                     if (semName == TGSI_SEMANTIC_EDGEFLAG) {
                        info->writes_edgeflag = true;
                     }
                  }
               } else if (file == TGSI_FILE_SAMPLER) {
                  info->samplers_declared |= 1u << reg;
               }
            }
         }
         break;

      case TGSI_TOKEN_TYPE_IMMEDIATE:
         {
            unsigned reg = info->immediate_count++;
            unsigned file = TGSI_FILE_IMMEDIATE;

            info->file_mask[file] |= (1u << reg);
            info->file_count[file]++;
            info->file_max[file] = MAX2(info->file_max[file], (int)reg);
         }
         break;

      case TGSI_TOKEN_TYPE_PROPERTY:
         {
            const struct tgsi_full_property *fullprop
               = &parse.FullToken.FullProperty;
            unsigned name = fullprop->Property.PropertyName;
            unsigned value = fullprop->u[0].Data;

            if (name >= ARRAY_SIZE(info->properties)) {
               debug_printf("TGSI Error: Unknown property %d\n", name);
               return false;
            }

            info->properties[name] = value;

            switch (name) {
            case TGSI_PROPERTY_NUM_CLIPDIST_ENABLED:
               info->num_written_clipdistance = value;
               info->clipdist_writemask |= (1u << value) - 1;
               break;
            case TGSI_PROPERTY_NUM_CULLDIST_ENABLED:
               info->num_written_culldistance = value;
               info->culldist_writemask |= (1u << value) - 1;
               break;
            }
         }
         break;

      default:
         debug_printf("TGSI Error: Unknown token type %d\n", parse.FullToken.Token.Type);
         return false;
      }
   }

   info->uses_kill = (info->opcode_count[TGSI_OPCODE_KILL_IF] ||
                      info->opcode_count[TGSI_OPCODE_KILL]);

   /* The dimensions of the IN decleration in geometry shader have
    * to be deduced from the type of the input primitive.
    */
   if (procType == TGSI_PROCESSOR_GEOMETRY) {
      unsigned input_primitive =
            info->properties[TGSI_PROPERTY_GS_INPUT_PRIM];
      int num_verts = u_vertices_per_prim(input_primitive);
      int j;
      info->file_count[TGSI_FILE_INPUT] = num_verts;
      info->file_max[TGSI_FILE_INPUT] =
            MAX2(info->file_max[TGSI_FILE_INPUT], num_verts - 1);
      for (j = 0; j < num_verts; ++j) {
         info->file_mask[TGSI_FILE_INPUT] |= (1u << j);
      }
   }

   tgsi_parse_free (&parse);

   return true;
}



/**
 * Check if the given shader is a "passthrough" shader consisting of only
 * MOV instructions of the form:  MOV OUT[n], IN[n]
 *  
 */
bool
tgsi_is_passthrough_shader(const struct tgsi_token *tokens)
{
   struct tgsi_parse_context parse;

   /**
    ** Setup to begin parsing input shader
    **/
   if (tgsi_parse_init(&parse, tokens) != TGSI_PARSE_OK) {
      debug_printf("tgsi_parse_init() failed in tgsi_is_passthrough_shader()!\n");
      return false;
   }

   /**
    ** Loop over incoming program tokens/instructions
    */
   while (!tgsi_parse_end_of_tokens(&parse)) {

      tgsi_parse_token(&parse);

      switch (parse.FullToken.Token.Type) {
      case TGSI_TOKEN_TYPE_INSTRUCTION:
         {
            struct tgsi_full_instruction *fullinst =
               &parse.FullToken.FullInstruction;
            const struct tgsi_full_src_register *src =
               &fullinst->Src[0];
            const struct tgsi_full_dst_register *dst =
               &fullinst->Dst[0];

            /* Do a whole bunch of checks for a simple move */
            if (fullinst->Instruction.Opcode != TGSI_OPCODE_MOV ||
                (src->Register.File != TGSI_FILE_INPUT &&
                 src->Register.File != TGSI_FILE_SYSTEM_VALUE) ||
                dst->Register.File != TGSI_FILE_OUTPUT ||
                src->Register.Index != dst->Register.Index ||

                src->Register.Negate ||
                src->Register.Absolute ||

                src->Register.SwizzleX != TGSI_SWIZZLE_X ||
                src->Register.SwizzleY != TGSI_SWIZZLE_Y ||
                src->Register.SwizzleZ != TGSI_SWIZZLE_Z ||
                src->Register.SwizzleW != TGSI_SWIZZLE_W ||

                dst->Register.WriteMask != TGSI_WRITEMASK_XYZW)
            {
               tgsi_parse_free(&parse);
               return false;
            }
         }
         break;

      case TGSI_TOKEN_TYPE_DECLARATION:
         /* fall-through */
      case TGSI_TOKEN_TYPE_IMMEDIATE:
         /* fall-through */
      case TGSI_TOKEN_TYPE_PROPERTY:
         /* fall-through */
      default:
         ; /* no-op */
      }
   }

   tgsi_parse_free(&parse);

   /* if we get here, it's a pass-through shader */
   return true;
}
