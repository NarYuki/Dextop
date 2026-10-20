/**************************************************************************
 * 
 * Copyright 2008 VMware, Inc.
 * Copyright 2009-2010 VMware, Inc.
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

#ifndef P_SHADER_TOKENS_H
#define P_SHADER_TOKENS_H

#ifdef __cplusplus
extern "C" {
#endif


struct tgsi_header
{
   unsigned HeaderSize : 8;
   unsigned BodySize   : 24;
};

enum tgsi_processor_type {
   TGSI_PROCESSOR_FRAGMENT,
   TGSI_PROCESSOR_VERTEX,
   TGSI_PROCESSOR_GEOMETRY,
   TGSI_PROCESSOR_TESS_CTRL,
   TGSI_PROCESSOR_TESS_EVAL,
   TGSI_PROCESSOR_COMPUTE,
};

struct tgsi_processor
{
   unsigned Processor  : 4;  /* TGSI_PROCESSOR_ */
   unsigned Padding    : 28;
};

enum tgsi_token_type {
   TGSI_TOKEN_TYPE_DECLARATION,
   TGSI_TOKEN_TYPE_IMMEDIATE,
   TGSI_TOKEN_TYPE_INSTRUCTION,
   TGSI_TOKEN_TYPE_PROPERTY,
};

struct tgsi_token
{
   unsigned Type       : 4;  /**< TGSI_TOKEN_TYPE_x */
   unsigned NrTokens   : 8;  /**< UINT */
   unsigned Padding    : 20;
};

enum tgsi_file_type {
   TGSI_FILE_NULL                =0,
   TGSI_FILE_CONSTANT            =1,
   TGSI_FILE_INPUT               =2,
   TGSI_FILE_OUTPUT              =3,
   TGSI_FILE_TEMPORARY           =4,
   TGSI_FILE_SAMPLER             =5,
   TGSI_FILE_ADDRESS             =6,
   TGSI_FILE_IMMEDIATE           =7,
   TGSI_FILE_PREDICATE           =8,
   TGSI_FILE_SYSTEM_VALUE        =9,
   TGSI_FILE_IMAGE               =10,
   TGSI_FILE_SAMPLER_VIEW        =11,
   TGSI_FILE_BUFFER,
   TGSI_FILE_MEMORY,
   TGSI_FILE_HW_ATOMIC,
   TGSI_FILE_COUNT      /**< how many TGSI_FILE_ types */
};


#define TGSI_WRITEMASK_NONE     0x00
#define TGSI_WRITEMASK_X        0x01
#define TGSI_WRITEMASK_Y        0x02
#define TGSI_WRITEMASK_XY       0x03
#define TGSI_WRITEMASK_Z        0x04
#define TGSI_WRITEMASK_XZ       0x05
#define TGSI_WRITEMASK_YZ       0x06
#define TGSI_WRITEMASK_XYZ      0x07
#define TGSI_WRITEMASK_W        0x08
#define TGSI_WRITEMASK_XW       0x09
#define TGSI_WRITEMASK_YW       0x0A
#define TGSI_WRITEMASK_XYW      0x0B
#define TGSI_WRITEMASK_ZW       0x0C
#define TGSI_WRITEMASK_XZW      0x0D
#define TGSI_WRITEMASK_YZW      0x0E
#define TGSI_WRITEMASK_XYZW     0x0F

enum tgsi_interpolate_mode {
   TGSI_INTERPOLATE_CONSTANT,
   TGSI_INTERPOLATE_LINEAR,
   TGSI_INTERPOLATE_PERSPECTIVE,
   TGSI_INTERPOLATE_COLOR,          /* special color case for smooth/flat */
   TGSI_INTERPOLATE_COUNT,
};

enum tgsi_interpolate_loc {
   TGSI_INTERPOLATE_LOC_CENTER,
   TGSI_INTERPOLATE_LOC_CENTROID,
   TGSI_INTERPOLATE_LOC_SAMPLE,
   TGSI_INTERPOLATE_LOC_COUNT,
};

#define TGSI_CYLINDRICAL_WRAP_X (1 << 0)
#define TGSI_CYLINDRICAL_WRAP_Y (1 << 1)
#define TGSI_CYLINDRICAL_WRAP_Z (1 << 2)
#define TGSI_CYLINDRICAL_WRAP_W (1 << 3)

enum tgsi_memory_type {
   TGSI_MEMORY_TYPE_GLOBAL,         /* OpenCL global              */
   TGSI_MEMORY_TYPE_SHARED,         /* OpenCL local / GLSL shared */
   TGSI_MEMORY_TYPE_PRIVATE,        /* OpenCL private             */
   TGSI_MEMORY_TYPE_INPUT,          /* OpenCL kernel input params */
   TGSI_MEMORY_TYPE_COUNT,
};

struct tgsi_declaration
{
   unsigned Type        : 4;  /**< TGSI_TOKEN_TYPE_DECLARATION */
   unsigned NrTokens    : 8;  /**< UINT */
   unsigned File        : 4;  /**< one of TGSI_FILE_x */
   unsigned UsageMask   : 4;  /**< bitmask of TGSI_WRITEMASK_x flags */
   unsigned Dimension   : 1;  /**< any extra dimension info? */
   unsigned Semantic    : 1;  /**< BOOL, any semantic info? */
   unsigned Interpolate : 1;  /**< any interpolation info? */
   unsigned Invariant   : 1;  /**< invariant optimization? */
   unsigned Local       : 1;  /**< optimize as subroutine local variable? */
   unsigned Array       : 1;  /**< extra array info? */
   unsigned Atomic      : 1;  /**< atomic only? for TGSI_FILE_BUFFER */
   unsigned MemType     : 2;  /**< TGSI_MEMORY_TYPE_x for TGSI_FILE_MEMORY */
   unsigned Padding     : 3;
};

struct tgsi_declaration_range
{
   unsigned First   : 16; /**< UINT */
   unsigned Last    : 16; /**< UINT */
};

struct tgsi_declaration_dimension
{
   unsigned Index2D:16; /**< UINT */
   unsigned Padding:16;
};

struct tgsi_declaration_interp
{
   unsigned Interpolate : 4;   /**< one of TGSI_INTERPOLATE_x */
   unsigned Location    : 2;   /**< one of TGSI_INTERPOLATE_LOC_x */
   unsigned CylindricalWrap:4; /**< TGSI_CYLINDRICAL_WRAP_x flags */
   unsigned Padding     : 22;
};

enum tgsi_semantic {
   TGSI_SEMANTIC_POSITION,
   TGSI_SEMANTIC_COLOR,
   TGSI_SEMANTIC_BCOLOR,       /**< back-face color */
   TGSI_SEMANTIC_FOG,
   TGSI_SEMANTIC_PSIZE,
   TGSI_SEMANTIC_GENERIC,
   TGSI_SEMANTIC_NORMAL,
   TGSI_SEMANTIC_FACE,
   TGSI_SEMANTIC_EDGEFLAG,
   TGSI_SEMANTIC_PRIMID,
   TGSI_SEMANTIC_INSTANCEID,  /**< doesn't include start_instance */
   TGSI_SEMANTIC_VERTEXID,
   TGSI_SEMANTIC_STENCIL,
   TGSI_SEMANTIC_CLIPDIST,
   TGSI_SEMANTIC_CLIPVERTEX,
   TGSI_SEMANTIC_GRID_SIZE,   /**< grid size in blocks */
   TGSI_SEMANTIC_BLOCK_ID,    /**< id of the current block */
   TGSI_SEMANTIC_BLOCK_SIZE,  /**< block size in threads */
   TGSI_SEMANTIC_THREAD_ID,   /**< block-relative id of the current thread */
   TGSI_SEMANTIC_TEXCOORD,    /**< texture or sprite coordinates */
   TGSI_SEMANTIC_PCOORD,      /**< point sprite coordinate */
   TGSI_SEMANTIC_VIEWPORT_INDEX,  /**< viewport index */
   TGSI_SEMANTIC_LAYER,       /**< layer (rendertarget index) */
   TGSI_SEMANTIC_CULLDIST,
   TGSI_SEMANTIC_SAMPLEID,
   TGSI_SEMANTIC_SAMPLEPOS,
   TGSI_SEMANTIC_SAMPLEMASK,
   TGSI_SEMANTIC_INVOCATIONID,
   TGSI_SEMANTIC_VERTEXID_NOBASE,
   TGSI_SEMANTIC_BASEVERTEX,
   TGSI_SEMANTIC_PATCH,       /**< generic per-patch semantic */
   TGSI_SEMANTIC_TESSCOORD,   /**< coordinate being processed by tess */
   TGSI_SEMANTIC_TESSOUTER,   /**< outer tessellation levels */
   TGSI_SEMANTIC_TESSINNER,   /**< inner tessellation levels */
   TGSI_SEMANTIC_VERTICESIN,  /**< number of input vertices */
   TGSI_SEMANTIC_HELPER_INVOCATION,  /**< current invocation is helper */
   TGSI_SEMANTIC_BASEINSTANCE,
   TGSI_SEMANTIC_DRAWID,
   TGSI_SEMANTIC_WORK_DIM,    /**< opencl get_work_dim value */
   TGSI_SEMANTIC_SUBGROUP_SIZE,
   TGSI_SEMANTIC_SUBGROUP_INVOCATION,
   TGSI_SEMANTIC_SUBGROUP_EQ_MASK,
   TGSI_SEMANTIC_SUBGROUP_GE_MASK,
   TGSI_SEMANTIC_SUBGROUP_GT_MASK,
   TGSI_SEMANTIC_SUBGROUP_LE_MASK,
   TGSI_SEMANTIC_SUBGROUP_LT_MASK,
   TGSI_SEMANTIC_CS_USER_DATA_AMD,
   TGSI_SEMANTIC_VIEWPORT_MASK,
   TGSI_SEMANTIC_COUNT,       /**< number of semantic values */
};

struct tgsi_declaration_semantic
{
   unsigned Name           : 8;  /**< one of TGSI_SEMANTIC_x */
   unsigned Index          : 16; /**< UINT */
   unsigned StreamX        : 2; /**< vertex stream (for GS output) */
   unsigned StreamY        : 2;
   unsigned StreamZ        : 2;
   unsigned StreamW        : 2;
};

struct tgsi_declaration_image {
   unsigned Resource    : 8; /**< one of TGSI_TEXTURE_ */
   unsigned Raw         : 1;
   unsigned Writable    : 1;
   unsigned Format      : 10; /**< one of PIPE_FORMAT_ */
   unsigned Padding     : 12;
};

enum tgsi_return_type {
   TGSI_RETURN_TYPE_UNORM = 0,
   TGSI_RETURN_TYPE_SNORM,
   TGSI_RETURN_TYPE_SINT,
   TGSI_RETURN_TYPE_UINT,
   TGSI_RETURN_TYPE_FLOAT,
   TGSI_RETURN_TYPE_COUNT
};

struct tgsi_declaration_sampler_view {
   unsigned Resource    : 8; /**< one of TGSI_TEXTURE_ */
   unsigned ReturnTypeX : 6; /**< one of enum tgsi_return_type */
   unsigned ReturnTypeY : 6; /**< one of enum tgsi_return_type */
   unsigned ReturnTypeZ : 6; /**< one of enum tgsi_return_type */
   unsigned ReturnTypeW : 6; /**< one of enum tgsi_return_type */
};

struct tgsi_declaration_array {
   unsigned ArrayID : 10;
   unsigned Padding : 22;
};

/*
 * Special resources that don't need to be declared.  They map to the
 * GLOBAL/LOCAL/PRIVATE/INPUT compute memory spaces.
 */
#define TGSI_RESOURCE_GLOBAL	0x7fff
#define TGSI_RESOURCE_LOCAL	0x7ffe
#define TGSI_RESOURCE_PRIVATE	0x7ffd
#define TGSI_RESOURCE_INPUT	0x7ffc

enum tgsi_imm_type {
   TGSI_IMM_FLOAT32,
   TGSI_IMM_UINT32,
   TGSI_IMM_INT32,
   TGSI_IMM_FLOAT64,
   TGSI_IMM_UINT64,
   TGSI_IMM_INT64,
};

struct tgsi_immediate
{
   unsigned Type       : 4;  /**< TGSI_TOKEN_TYPE_IMMEDIATE */
   unsigned NrTokens   : 14; /**< UINT */
   unsigned DataType   : 4;  /**< one of TGSI_IMM_x */
   unsigned Padding    : 10;
};

union tgsi_immediate_data
{
   float Float;
   unsigned Uint;
   int Int;
};

enum tgsi_property_name {
   TGSI_PROPERTY_GS_INPUT_PRIM,
   TGSI_PROPERTY_GS_OUTPUT_PRIM,
   TGSI_PROPERTY_GS_MAX_OUTPUT_VERTICES,
   TGSI_PROPERTY_FS_COORD_ORIGIN,
   TGSI_PROPERTY_FS_COORD_PIXEL_CENTER,
   TGSI_PROPERTY_FS_COLOR0_WRITES_ALL_CBUFS,
   TGSI_PROPERTY_FS_DEPTH_LAYOUT,
   TGSI_PROPERTY_VS_PROHIBIT_UCPS,
   TGSI_PROPERTY_GS_INVOCATIONS,
   TGSI_PROPERTY_VS_WINDOW_SPACE_POSITION,
   TGSI_PROPERTY_TCS_VERTICES_OUT,
   TGSI_PROPERTY_TES_PRIM_MODE,
   TGSI_PROPERTY_TES_SPACING,
   TGSI_PROPERTY_TES_VERTEX_ORDER_CW,
   TGSI_PROPERTY_TES_POINT_MODE,
   TGSI_PROPERTY_NUM_CLIPDIST_ENABLED,
   TGSI_PROPERTY_NUM_CULLDIST_ENABLED,
   TGSI_PROPERTY_FS_EARLY_DEPTH_STENCIL,
   TGSI_PROPERTY_FS_POST_DEPTH_COVERAGE,
   TGSI_PROPERTY_NEXT_SHADER,
   TGSI_PROPERTY_CS_FIXED_BLOCK_WIDTH,
   TGSI_PROPERTY_CS_FIXED_BLOCK_HEIGHT,
   TGSI_PROPERTY_CS_FIXED_BLOCK_DEPTH,
   TGSI_PROPERTY_MUL_ZERO_WINS,
   TGSI_PROPERTY_VS_BLIT_SGPRS_AMD,
   TGSI_PROPERTY_CS_USER_DATA_COMPONENTS_AMD,
   TGSI_PROPERTY_LAYER_VIEWPORT_RELATIVE,
   TGSI_PROPERTY_FS_BLEND_EQUATION_ADVANCED,
   TGSI_PROPERTY_SEPARABLE_PROGRAM,
   TGSI_PROPERTY_COUNT,
};

struct tgsi_property {
   unsigned Type         : 4;  /**< TGSI_TOKEN_TYPE_PROPERTY */
   unsigned NrTokens     : 8;  /**< UINT */
   unsigned PropertyName : 8;  /**< one of TGSI_PROPERTY */
   unsigned Padding      : 12;
};

enum tgsi_fs_coord_origin {
   TGSI_FS_COORD_ORIGIN_UPPER_LEFT,
   TGSI_FS_COORD_ORIGIN_LOWER_LEFT,
};

enum tgsi_fs_coord_pixcenter {
   TGSI_FS_COORD_PIXEL_CENTER_HALF_INTEGER,
   TGSI_FS_COORD_PIXEL_CENTER_INTEGER,
};

enum tgsi_fs_depth_layout {
   TGSI_FS_DEPTH_LAYOUT_NONE,
   TGSI_FS_DEPTH_LAYOUT_ANY,
   TGSI_FS_DEPTH_LAYOUT_GREATER,
   TGSI_FS_DEPTH_LAYOUT_LESS,
   TGSI_FS_DEPTH_LAYOUT_UNCHANGED,
};

struct tgsi_property_data {
   unsigned Data;
};

/* TGSI opcodes.  
 * 
 * For more information on semantics of opcodes and
 * which APIs are known to use which opcodes, see
 * gallium/docs/source/tgsi.rst
 */
/* VIRGLRENDERER specific - DON'T SYNC WITH MESA
 * OR REMOVE OPCODES - FILL in and REWRITE tgsi_info
 * accordingly.
 */

enum tgsi_opcode {
   TGSI_OPCODE_ARL,
   TGSI_OPCODE_MOV,
   TGSI_OPCODE_LIT,
   TGSI_OPCODE_RCP,
   TGSI_OPCODE_RSQ,
   TGSI_OPCODE_EXP,
   TGSI_OPCODE_LOG,
   TGSI_OPCODE_MUL,
   TGSI_OPCODE_ADD,
   TGSI_OPCODE_DP3,
   TGSI_OPCODE_DP4,
   TGSI_OPCODE_DST,
   TGSI_OPCODE_MIN,
   TGSI_OPCODE_MAX,
   TGSI_OPCODE_SLT,
   TGSI_OPCODE_SGE,
   TGSI_OPCODE_MAD,
   TGSI_OPCODE_SUB,
   TGSI_OPCODE_LRP,
   TGSI_OPCODE_FMA,
   TGSI_OPCODE_SQRT,
   TGSI_OPCODE_FRC,
   TGSI_OPCODE_FLR,
   TGSI_OPCODE_ROUND,
   TGSI_OPCODE_EX2,
   TGSI_OPCODE_LG2,
   TGSI_OPCODE_POW,
   TGSI_OPCODE_XPD,
   TGSI_OPCODE_ABS,
   TGSI_OPCODE_DPH,
   TGSI_OPCODE_COS,
   TGSI_OPCODE_DDX,
   TGSI_OPCODE_DDY,
   TGSI_OPCODE_KILL /* unconditional */,
   TGSI_OPCODE_PK2H,
   TGSI_OPCODE_PK2US,
   TGSI_OPCODE_PK4B,
   TGSI_OPCODE_PK4UB,
   TGSI_OPCODE_SEQ,
   TGSI_OPCODE_SGT,
   TGSI_OPCODE_SIN,
   TGSI_OPCODE_SLE,
   TGSI_OPCODE_SNE,
   TGSI_OPCODE_TEX,
   TGSI_OPCODE_TXD,
   TGSI_OPCODE_TXP,
   TGSI_OPCODE_UP2H,
   TGSI_OPCODE_UP2US,
   TGSI_OPCODE_UP4B,
   TGSI_OPCODE_UP4UB,
   TGSI_OPCODE_ARR,
   TGSI_OPCODE_CAL,
   TGSI_OPCODE_RET,
   TGSI_OPCODE_SSG /* SGN */,
   TGSI_OPCODE_CMP,
   TGSI_OPCODE_SCS,
   TGSI_OPCODE_TXB,
   TGSI_OPCODE_FBFETCH,
   TGSI_OPCODE_DIV,
   TGSI_OPCODE_DP2,
   TGSI_OPCODE_TXL,
   TGSI_OPCODE_BRK,
   TGSI_OPCODE_IF,
   TGSI_OPCODE_UIF,
   TGSI_OPCODE_ELSE,
   TGSI_OPCODE_ENDIF,
   TGSI_OPCODE_DDX_FINE,
   TGSI_OPCODE_DDY_FINE,
   TGSI_OPCODE_CEIL,
   TGSI_OPCODE_I2F,
   TGSI_OPCODE_NOT,
   TGSI_OPCODE_TRUNC,
   TGSI_OPCODE_SHL,
   TGSI_OPCODE_AND,
   TGSI_OPCODE_OR,
   TGSI_OPCODE_MOD,
   TGSI_OPCODE_XOR,
   TGSI_OPCODE_TXF,
   TGSI_OPCODE_TXQ,
   TGSI_OPCODE_CONT,
   TGSI_OPCODE_EMIT,
   TGSI_OPCODE_ENDPRIM,
   TGSI_OPCODE_BGNLOOP,
   TGSI_OPCODE_BGNSUB,
   TGSI_OPCODE_ENDLOOP,
   TGSI_OPCODE_ENDSUB,
   TGSI_OPCODE_TXQS,
   TGSI_OPCODE_RESQ,
   TGSI_OPCODE_NOP,
   TGSI_OPCODE_FSEQ,
   TGSI_OPCODE_FSGE,
   TGSI_OPCODE_FSLT,
   TGSI_OPCODE_FSNE,

   TGSI_OPCODE_MEMBAR,

   TGSI_OPCODE_VOTE_ANY,
   TGSI_OPCODE_VOTE_ALL,
   TGSI_OPCODE_VOTE_EQ,

   TGSI_OPCODE_KILL_IF  /* conditional kill */,
   TGSI_OPCODE_END  /* aka HALT */,
   TGSI_OPCODE_DFMA,
   TGSI_OPCODE_F2I,
   TGSI_OPCODE_IDIV,
   TGSI_OPCODE_IMAX,
   TGSI_OPCODE_IMIN,
   TGSI_OPCODE_INEG,
   TGSI_OPCODE_ISGE,
   TGSI_OPCODE_ISHR,
   TGSI_OPCODE_ISLT,
   TGSI_OPCODE_F2U,
   TGSI_OPCODE_U2F,
   TGSI_OPCODE_UADD,
   TGSI_OPCODE_UDIV,
   TGSI_OPCODE_UMAD,
   TGSI_OPCODE_UMAX,
   TGSI_OPCODE_UMIN,
   TGSI_OPCODE_UMOD,
   TGSI_OPCODE_UMUL,
   TGSI_OPCODE_USEQ,
   TGSI_OPCODE_USGE,
   TGSI_OPCODE_USHR,
   TGSI_OPCODE_USLT,
   TGSI_OPCODE_USNE,
   TGSI_OPCODE_SWITCH,
   TGSI_OPCODE_CASE,
   TGSI_OPCODE_DEFAULT,
   TGSI_OPCODE_ENDSWITCH,

/* resource related opcodes */
   TGSI_OPCODE_SAMPLE,
   TGSI_OPCODE_SAMPLE_I,
   TGSI_OPCODE_SAMPLE_I_MS,
   TGSI_OPCODE_SAMPLE_B,
   TGSI_OPCODE_SAMPLE_C,
   TGSI_OPCODE_SAMPLE_C_LZ,
   TGSI_OPCODE_SAMPLE_D,
   TGSI_OPCODE_SAMPLE_L,
   TGSI_OPCODE_GATHER4,
   TGSI_OPCODE_SVIEWINFO,
   TGSI_OPCODE_SAMPLE_POS,
   TGSI_OPCODE_SAMPLE_INFO,

   TGSI_OPCODE_UARL,
   TGSI_OPCODE_UCMP,
   TGSI_OPCODE_IABS,
   TGSI_OPCODE_ISSG,

   TGSI_OPCODE_LOAD,
   TGSI_OPCODE_STORE,
   TGSI_OPCODE_BARRIER,

   TGSI_OPCODE_ATOMUADD,
   TGSI_OPCODE_ATOMXCHG,
   TGSI_OPCODE_ATOMCAS,
   TGSI_OPCODE_ATOMAND,
   TGSI_OPCODE_ATOMOR,
   TGSI_OPCODE_ATOMXOR,
   TGSI_OPCODE_ATOMUMIN,
   TGSI_OPCODE_ATOMUMAX,
   TGSI_OPCODE_ATOMIMIN,
   TGSI_OPCODE_ATOMIMAX,

/* to be used for shadow cube map compares */
   TGSI_OPCODE_TEX2,
   TGSI_OPCODE_TXB2,
   TGSI_OPCODE_TXL2,

   TGSI_OPCODE_IMUL_HI,
   TGSI_OPCODE_UMUL_HI,

   TGSI_OPCODE_TG4,

   TGSI_OPCODE_LODQ,

   TGSI_OPCODE_IBFE,
   TGSI_OPCODE_UBFE,
   TGSI_OPCODE_BFI,
   TGSI_OPCODE_BREV,
   TGSI_OPCODE_POPC,
   TGSI_OPCODE_LSB,
   TGSI_OPCODE_IMSB,
   TGSI_OPCODE_UMSB,

   TGSI_OPCODE_INTERP_CENTROID,
   TGSI_OPCODE_INTERP_SAMPLE,
   TGSI_OPCODE_INTERP_OFFSET,

/* sm5 marked opcodes are supported in D3D11 optionally - also DMOV, DMOVC */
   TGSI_OPCODE_F2D /* SM5 */,
   TGSI_OPCODE_D2F,
   TGSI_OPCODE_DABS,
   TGSI_OPCODE_DNEG /* SM5 */,
   TGSI_OPCODE_DADD /* SM5 */,
   TGSI_OPCODE_DMUL /* SM5 */,
   TGSI_OPCODE_DMAX /* SM5 */,
   TGSI_OPCODE_DMIN /* SM5 */,
   TGSI_OPCODE_DSLT /* SM5 */,
   TGSI_OPCODE_DSGE /* SM5 */,
   TGSI_OPCODE_DSEQ /* SM5 */,
   TGSI_OPCODE_DSNE /* SM5 */,
   TGSI_OPCODE_DRCP /* eg, cayman */,
   TGSI_OPCODE_DSQRT /* eg, cayman also has DRSQ */,
   TGSI_OPCODE_DMAD,
   TGSI_OPCODE_DFRAC /* eg, cayman */,
   TGSI_OPCODE_DLDEXP /* eg, cayman */,
   TGSI_OPCODE_DFRACEXP /* eg, cayman */,
   TGSI_OPCODE_D2I,
   TGSI_OPCODE_I2D,
   TGSI_OPCODE_D2U,
   TGSI_OPCODE_U2D,
   TGSI_OPCODE_DRSQ /* eg, cayman also has DRSQ */,
   TGSI_OPCODE_DTRUNC /* nvc0 */,
   TGSI_OPCODE_DCEIL /* nvc0 */,
   TGSI_OPCODE_DFLR /* nvc0 */,
   TGSI_OPCODE_DROUND /* nvc0 */,
   TGSI_OPCODE_DSSG,
   TGSI_OPCODE_DDIV,
   TGSI_OPCODE_CLOCK,

/* opcodes for ARB_gpu_shader_int64 */
   TGSI_OPCODE_I64ABS,
   TGSI_OPCODE_I64NEG,
   TGSI_OPCODE_I64SSG,
   TGSI_OPCODE_I64SLT,
   TGSI_OPCODE_I64SGE,
   TGSI_OPCODE_I64MIN,
   TGSI_OPCODE_I64MAX,
   TGSI_OPCODE_I64SHR,
   TGSI_OPCODE_I64DIV,
   TGSI_OPCODE_I64MOD,
   TGSI_OPCODE_F2I64,
   TGSI_OPCODE_U2I64,
   TGSI_OPCODE_I2I64,
   TGSI_OPCODE_D2I64,
   TGSI_OPCODE_I642F,
   TGSI_OPCODE_I642D,

   TGSI_OPCODE_U64ADD,
   TGSI_OPCODE_U64MUL,
   TGSI_OPCODE_U64SEQ,
   TGSI_OPCODE_U64SNE,
   TGSI_OPCODE_U64SLT,
   TGSI_OPCODE_U64SGE,
   TGSI_OPCODE_U64MIN,
   TGSI_OPCODE_U64MAX,
   TGSI_OPCODE_U64SHL,
   TGSI_OPCODE_U64SHR,
   TGSI_OPCODE_U64DIV,
   TGSI_OPCODE_U64MOD,
   TGSI_OPCODE_F2U64,
   TGSI_OPCODE_D2U64,
   TGSI_OPCODE_U642F,
   TGSI_OPCODE_U642D,

   TGSI_OPCODE_LAST,
};

/**
 * Opcode is the operation code to execute. A given operation defines the
 * semantics how the source registers (if any) are interpreted and what is
 * written to the destination registers (if any) as a result of execution.
 *
 * NumDstRegs and NumSrcRegs is the number of destination and source registers,
 * respectively. For a given operation code, those numbers are fixed and are
 * present here only for convenience.
 *
 * Saturate controls how are final results in destination registers modified.
 */

/*
 * VIRGLRENDERER specific -
 * we no long keep this in sync with mesa, we had to increase the NrTokens
 * as mesa can remove old opcodes, but the renderer cannot.
 */
struct tgsi_instruction
{
   unsigned Type       : 4;  /* TGSI_TOKEN_TYPE_INSTRUCTION */
   unsigned NrTokens   : 9;  /* UINT */
   unsigned Opcode     : 8;  /* TGSI_OPCODE_ */
   unsigned Saturate   : 1;  /* BOOL */
   unsigned NumDstRegs : 2;  /* UINT */
   unsigned NumSrcRegs : 4;  /* UINT */
   unsigned Label      : 1;
   unsigned Texture    : 1;
   unsigned Memory     : 1;
   unsigned Precise    : 1;
};

/*
 * If tgsi_instruction::Label is TRUE, tgsi_instruction_label follows.
 *
 * If tgsi_instruction::Texture is TRUE, tgsi_instruction_texture follows.
 *   if texture instruction has a number of offsets,
 *   then tgsi_instruction::Texture::NumOffset of tgsi_texture_offset follow.
 * 
 * Then, tgsi_instruction::NumDstRegs of tgsi_dst_register follow.
 * 
 * Then, tgsi_instruction::NumSrcRegs of tgsi_src_register follow.
 *
 * tgsi_instruction::NrTokens contains the total number of words that make the
 * instruction, including the instruction word.
 */

enum tgsi_swizzle {
   TGSI_SWIZZLE_X,
   TGSI_SWIZZLE_Y,
   TGSI_SWIZZLE_Z,
   TGSI_SWIZZLE_W,
};

struct tgsi_instruction_label
{
   unsigned Label    : 24;   /* UINT */
   unsigned Padding  : 8;
};

enum tgsi_texture_type {
   TGSI_TEXTURE_BUFFER,
   TGSI_TEXTURE_1D,
   TGSI_TEXTURE_2D,
   TGSI_TEXTURE_3D,
   TGSI_TEXTURE_CUBE,
   TGSI_TEXTURE_RECT,
   TGSI_TEXTURE_SHADOW1D,
   TGSI_TEXTURE_SHADOW2D,
   TGSI_TEXTURE_SHADOWRECT,
   TGSI_TEXTURE_1D_ARRAY,
   TGSI_TEXTURE_2D_ARRAY,
   TGSI_TEXTURE_SHADOW1D_ARRAY,
   TGSI_TEXTURE_SHADOW2D_ARRAY,
   TGSI_TEXTURE_SHADOWCUBE,
   TGSI_TEXTURE_2D_MSAA,
   TGSI_TEXTURE_2D_ARRAY_MSAA,
   TGSI_TEXTURE_CUBE_ARRAY,
   TGSI_TEXTURE_SHADOWCUBE_ARRAY,
   TGSI_TEXTURE_UNKNOWN,
   TGSI_TEXTURE_COUNT,
};

struct tgsi_instruction_texture
{
   unsigned Texture  : 8;    /* TGSI_TEXTURE_ */
   unsigned NumOffsets : 4;
   unsigned Padding : 20;
};

/* for texture offsets in GLSL and DirectX.
 * Generally these always come from TGSI_FILE_IMMEDIATE,
 * however DX11 appears to have the capability to do
 * non-constant texture offsets.
 */
struct tgsi_texture_offset
{
   int      Index    : 16;
   unsigned File     : 4;  /**< one of TGSI_FILE_x */
   unsigned SwizzleX : 2;  /* TGSI_SWIZZLE_x */
   unsigned SwizzleY : 2;  /* TGSI_SWIZZLE_x */
   unsigned SwizzleZ : 2;  /* TGSI_SWIZZLE_x */
   unsigned Padding  : 6;
};

/**
 * File specifies the register array to access.
 *
 * Index specifies the element number of a register in the register file.
 *
 * If Indirect is TRUE, Index should be offset by the X component of the indirect
 * register that follows. The register can be now fetched into local storage
 * for further processing.
 *
 * If Negate is TRUE, all components of the fetched register are negated.
 *
 * The fetched register components are swizzled according to SwizzleX, SwizzleY,
 * SwizzleZ and SwizzleW.
 *
 */

struct tgsi_src_register
{
   unsigned File        : 4;  /* TGSI_FILE_ */
   unsigned Indirect    : 1;  /* BOOL */
   unsigned Dimension   : 1;  /* BOOL */
   int      Index       : 16; /* SINT */
   unsigned SwizzleX    : 2;  /* TGSI_SWIZZLE_ */
   unsigned SwizzleY    : 2;  /* TGSI_SWIZZLE_ */
   unsigned SwizzleZ    : 2;  /* TGSI_SWIZZLE_ */
   unsigned SwizzleW    : 2;  /* TGSI_SWIZZLE_ */
   unsigned Absolute    : 1;    /* BOOL */
   unsigned Negate      : 1;    /* BOOL */
};

/**
 * If tgsi_src_register::Indirect is TRUE, tgsi_ind_register follows.
 *
 * File, Index and Swizzle are handled the same as in tgsi_src_register.
 *
 * If ArrayID is zero the whole register file might be indirectly addressed,
 * if not only the Declaration with this ArrayID is accessed by this operand.
 *
 */

struct tgsi_ind_register
{
   unsigned File    : 4;  /* TGSI_FILE_ */
   int      Index   : 16; /* SINT */
   unsigned Swizzle : 2;  /* TGSI_SWIZZLE_ */
   unsigned ArrayID : 10; /* UINT */
};

/**
 * If tgsi_src_register::Dimension is TRUE, tgsi_dimension follows.
 */

struct tgsi_dimension
{
   unsigned Indirect    : 1;  /* BOOL */
   unsigned Dimension   : 1;  /* BOOL */
   unsigned Padding     : 14;
   int      Index       : 16; /* SINT */
};

struct tgsi_dst_register
{
   unsigned File        : 4;  /* TGSI_FILE_ */
   unsigned WriteMask   : 4;  /* TGSI_WRITEMASK_ */
   unsigned Indirect    : 1;  /* BOOL */
   unsigned Dimension   : 1;  /* BOOL */
   int      Index       : 16; /* SINT */
   unsigned Padding     : 6;
};

#define TGSI_MEMORY_COHERENT (1 << 0)
#define TGSI_MEMORY_RESTRICT (1 << 1)
#define TGSI_MEMORY_VOLATILE (1 << 2)

/**
 * Specifies the type of memory access to do for the LOAD/STORE instruction.
 */
struct tgsi_instruction_memory
{
   unsigned Qualifier : 3;  /* TGSI_MEMORY_ */
   unsigned Texture   : 8;  /* only for images: TGSI_TEXTURE_ */
   unsigned Format    : 10; /* only for images: PIPE_FORMAT_ */
   unsigned Padding   : 11;
};

#define TGSI_MEMBAR_SHADER_BUFFER (1 << 0)
#define TGSI_MEMBAR_ATOMIC_BUFFER (1 << 1)
#define TGSI_MEMBAR_SHADER_IMAGE  (1 << 2)
#define TGSI_MEMBAR_SHARED        (1 << 3)
#define TGSI_MEMBAR_THREAD_GROUP  (1 << 4)

#ifdef __cplusplus
}
#endif

#endif /* P_SHADER_TOKENS_H */
