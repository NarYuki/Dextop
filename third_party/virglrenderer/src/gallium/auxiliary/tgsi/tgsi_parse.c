/**************************************************************************
 * 
 * Copyright 2007 VMware, Inc.
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

#include "util/u_debug.h"
#include "pipe/p_shader_tokens.h"
#include "tgsi_parse.h"
#include "util/u_memory.h"

unsigned
tgsi_parse_init(
   struct tgsi_parse_context *ctx,
   const struct tgsi_token *tokens )
{
   ctx->FullHeader.Header = *(struct tgsi_header *) &tokens[0];
   if( ctx->FullHeader.Header.HeaderSize >= 2 ) {
      ctx->FullHeader.Processor = *(struct tgsi_processor *) &tokens[1];
   }
   else {
      return TGSI_PARSE_ERROR;
   }

   ctx->Tokens = tokens;
   ctx->Position = ctx->FullHeader.Header.HeaderSize;

   return TGSI_PARSE_OK;
}

void
tgsi_parse_free(
   UNUSED struct tgsi_parse_context *ctx )
{
}

bool
tgsi_parse_end_of_tokens(
   struct tgsi_parse_context *ctx )
{
   /* All values involved are unsigned, but the sum will be promoted to
    * a signed value (at least on 64 bit). To capture a possible overflow
    * make it a signed comparison.
    */
   return (int)ctx->Position >=
         ctx->FullHeader.Header.HeaderSize + ctx->FullHeader.Header.BodySize;
}


/**
 * This function is used to avoid and work-around type punning/aliasing
 * warnings.  The warnings seem harmless on x86 but on PPC they cause
 * real failures.
 */
static inline void
copy_token(void *dst, const void *src)
{
   memcpy(dst, src, 4);
}


/**
 * Get next 4-byte token, return it at address specified by 'token'
 */
static bool
next_token(
   struct tgsi_parse_context *ctx,
   void *token )
{
   if (tgsi_parse_end_of_tokens( ctx ) )
      return false;
   copy_token(token, &ctx->Tokens[ctx->Position]);
   ctx->Position++;
   return true;
}


bool
tgsi_parse_token(
   struct tgsi_parse_context *ctx )
{
   struct tgsi_token token;
   unsigned i;

   if (!next_token( ctx, &token ))
      return false;

   switch( token.Type ) {
   case TGSI_TOKEN_TYPE_DECLARATION:
   {
      struct tgsi_full_declaration *decl = &ctx->FullToken.FullDeclaration;

      memset(decl, 0, sizeof *decl);
      copy_token(&decl->Declaration, &token);

      if (!next_token( ctx, &decl->Range ))
         return false;

      if (decl->Declaration.Dimension) {
         if (!next_token(ctx, &decl->Dim))
            return false;
      }

      if (decl->Declaration.Interpolate) {
         if (!next_token( ctx, &decl->Interp ))
            return false;
      }

      if (decl->Declaration.Semantic) {
         if (!next_token( ctx, &decl->Semantic ))
            return false;
      }

      if (decl->Declaration.File == TGSI_FILE_IMAGE) {
         if (!next_token(ctx, &decl->Image))
            return false;
      }

      if (decl->Declaration.File == TGSI_FILE_SAMPLER_VIEW) {
         if (!next_token(ctx, &decl->SamplerView))
            return false;
      }

      if (decl->Declaration.Array) {
         if (!next_token(ctx, &decl->Array))
            return false;
      }

      break;
   }

   case TGSI_TOKEN_TYPE_IMMEDIATE:
   {
      struct tgsi_full_immediate *imm = &ctx->FullToken.FullImmediate;
      unsigned imm_count;

      memset(imm, 0, sizeof *imm);
      copy_token(&imm->Immediate, &token);

      imm_count = imm->Immediate.NrTokens - 1;

      if (imm_count > TGSI_MAX_IMMEDIATE_PER_SLOT)
         return false;

      switch (imm->Immediate.DataType) {
      case TGSI_IMM_FLOAT32:
      case TGSI_IMM_FLOAT64:
         for (i = 0; i < imm_count; i++) {
            if (!next_token(ctx, &imm->u[i].Float))
               return false;
         }
         break;

      case TGSI_IMM_UINT32:
      case TGSI_IMM_UINT64:
         for (i = 0; i < imm_count; i++) {
            if (!next_token(ctx, &imm->u[i].Uint))
               return false;
         }
         break;

      case TGSI_IMM_INT32:
      case TGSI_IMM_INT64:
         for (i = 0; i < imm_count; i++) {
            if (!next_token(ctx, &imm->u[i].Int))
               return false;
         }
         break;

      default:
         return false;
      }

      break;
   }

   case TGSI_TOKEN_TYPE_INSTRUCTION:
   {
      struct tgsi_full_instruction *inst = &ctx->FullToken.FullInstruction;

      memset(inst, 0, sizeof *inst);
      copy_token(&inst->Instruction, &token);

      if (inst->Instruction.Label) {
         if (!next_token( ctx, &inst->Label))
            return false;
      }

      if (inst->Instruction.Texture) {
         if (!next_token( ctx, &inst->Texture))
            return false;
         if (inst->Texture.NumOffsets > TGSI_FULL_MAX_TEX_OFFSETS)
            return false;
         for (i = 0; i < inst->Texture.NumOffsets; i++) {
            if (!next_token( ctx, &inst->TexOffsets[i] ))
               return false;
         }
      }

      if (inst->Instruction.Memory) {
         if (!next_token(ctx, &inst->Memory))
            return false;
      }

      if ( inst->Instruction.NumDstRegs > TGSI_FULL_MAX_DST_REGISTERS )
          return false;

      for (i = 0; i < inst->Instruction.NumDstRegs; i++) {

         if (!next_token( ctx, &inst->Dst[i].Register ))
            return false;

         if (inst->Dst[i].Register.Indirect)
            if (!next_token( ctx, &inst->Dst[i].Indirect ))
               return false;

         if (inst->Dst[i].Register.Dimension) {
            if (!next_token( ctx, &inst->Dst[i].Dimension ))
               return false;

            /*
             * No support for multi-dimensional addressing.
             */
            if ( inst->Dst[i].Dimension.Dimension )
               return false;

            if (inst->Dst[i].Dimension.Indirect)
               if (!next_token( ctx, &inst->Dst[i].DimIndirect ))
                  return false;
         }
      }

      if (inst->Instruction.NumSrcRegs > TGSI_FULL_MAX_SRC_REGISTERS )
          return false;

      for (i = 0; i < inst->Instruction.NumSrcRegs; i++) {

         if (!next_token( ctx, &inst->Src[i].Register ))
            return false;

         if (inst->Src[i].Register.Indirect)
            if (!next_token( ctx, &inst->Src[i].Indirect ))
               return false;

         if (inst->Src[i].Register.Dimension) {
            if (!next_token( ctx, &inst->Src[i].Dimension ))
               return false;

            /*
             * No support for multi-dimensional addressing.
             */
            if ( inst->Src[i].Dimension.Dimension )
               return false;

            if (inst->Src[i].Dimension.Indirect)
               if (!next_token( ctx, &inst->Src[i].DimIndirect ))
                  return false;
         }
      }

      break;
   }

   case TGSI_TOKEN_TYPE_PROPERTY:
   {
      struct tgsi_full_property *prop = &ctx->FullToken.FullProperty;
      unsigned prop_count;

      memset(prop, 0, sizeof *prop);
      copy_token(&prop->Property, &token);

      prop_count = prop->Property.NrTokens - 1;

      if (prop_count > TGSI_MAX_PROPERTY_DATA_SLOTS)
         return false;

      for (i = 0; i < prop_count; i++) {
         if (!next_token(ctx, &prop->u[i]))
            return false;
      }

      break;
   }

   default:
      return false;
   }
   return true;
}




/**
 * Make a new copy of a token array.
 */
struct tgsi_token *
tgsi_dup_tokens(const struct tgsi_token *tokens)
{
   unsigned n = tgsi_num_tokens(tokens);
   unsigned bytes = n * sizeof(struct tgsi_token);
   struct tgsi_token *new_tokens = (struct tgsi_token *) MALLOC(bytes);
   if (new_tokens)
      memcpy(new_tokens, tokens, bytes);
   return new_tokens;
}


/**
 * Allocate memory for num_tokens tokens.
 */
struct tgsi_token *
tgsi_alloc_tokens(unsigned num_tokens)
{
   unsigned bytes = num_tokens * sizeof(struct tgsi_token);
   return (struct tgsi_token *) MALLOC(bytes);
}


void
tgsi_dump_tokens(const struct tgsi_token *tokens)
{
   const unsigned *dwords = (const unsigned *)tokens;
   int nr = tgsi_num_tokens(tokens);
   int i;
   
   assert(sizeof(*tokens) == sizeof(unsigned));

   debug_printf("const unsigned tokens[%d] = {\n", nr);
   for (i = 0; i < nr; i++)
      debug_printf("0x%08x,\n", dwords[i]);
   debug_printf("};\n");
}
