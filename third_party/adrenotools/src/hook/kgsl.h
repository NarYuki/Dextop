/* SPDX-License-Identifier: GPL-2.0 WITH Linux-syscall-note */
/*
 * Copyright (c) 2018-2021, The Linux Foundation. All rights reserved.
 * Copyright (c) 2022 Qualcomm Innovation Center, Inc. All rights reserved.
 */
#ifndef _UAPI_MSM_KGSL_H
#define _UAPI_MSM_KGSL_H

#include <linux/types.h>
#include <linux/ioctl.h>


#define KGSL_VERSION_MAJOR        3
#define KGSL_VERSION_MINOR        14


#define KGSL_CONTEXT_SAVE_GMEM		0x00000001
#define KGSL_CONTEXT_NO_GMEM_ALLOC	0x00000002
#define KGSL_CONTEXT_SUBMIT_IB_LIST	0x00000004
#define KGSL_CONTEXT_CTX_SWITCH		0x00000008
#define KGSL_CONTEXT_PREAMBLE		0x00000010
#define KGSL_CONTEXT_TRASH_STATE	0x00000020
#define KGSL_CONTEXT_PER_CONTEXT_TS	0x00000040
#define KGSL_CONTEXT_USER_GENERATED_TS	0x00000080
#define KGSL_CONTEXT_END_OF_FRAME	0x00000100
#define KGSL_CONTEXT_NO_FAULT_TOLERANCE 0x00000200
#define KGSL_CONTEXT_SYNC               0x00000400
#define KGSL_CONTEXT_PWR_CONSTRAINT     0x00000800
#define KGSL_CONTEXT_PRIORITY_MASK      0x0000F000
#define KGSL_CONTEXT_PRIORITY_SHIFT     12
#define KGSL_CONTEXT_PRIORITY_UNDEF     0

#define KGSL_CONTEXT_IFH_NOP            0x00010000
#define KGSL_CONTEXT_SECURE             0x00020000
#define KGSL_CONTEXT_NO_SNAPSHOT        0x00040000
#define KGSL_CONTEXT_SPARSE             0x00080000

#define KGSL_CONTEXT_PREEMPT_STYLE_MASK       0x0E000000
#define KGSL_CONTEXT_PREEMPT_STYLE_SHIFT      25
#define KGSL_CONTEXT_PREEMPT_STYLE_DEFAULT    0x0
#define KGSL_CONTEXT_PREEMPT_STYLE_RINGBUFFER 0x1
#define KGSL_CONTEXT_PREEMPT_STYLE_FINEGRAIN  0x2

#define KGSL_CONTEXT_TYPE_MASK          0x01F00000
#define KGSL_CONTEXT_TYPE_SHIFT         20
#define KGSL_CONTEXT_TYPE_ANY		0
#define KGSL_CONTEXT_TYPE_GL		1
#define KGSL_CONTEXT_TYPE_CL		2
#define KGSL_CONTEXT_TYPE_C2D		3
#define KGSL_CONTEXT_TYPE_RS		4
#define KGSL_CONTEXT_TYPE_VK		5
#define KGSL_CONTEXT_TYPE_UNKNOWN	0x1E

#define KGSL_CONTEXT_INVALIDATE_ON_FAULT 0x10000000
#define KGSL_CONTEXT_LPAC 0x20000000
#define KGSL_CONTEXT_FAULT_INFO	  0x40000000

#define KGSL_CONTEXT_INVALID 0xffffffff

#define KGSL_CMDBATCH_MEMLIST		0x00000001
#define KGSL_CMDBATCH_MARKER		0x00000002
#define KGSL_CMDBATCH_SUBMIT_IB_LIST	KGSL_CONTEXT_SUBMIT_IB_LIST
#define KGSL_CMDBATCH_CTX_SWITCH	KGSL_CONTEXT_CTX_SWITCH
#define KGSL_CMDBATCH_PROFILING		0x00000010
#define KGSL_CMDBATCH_PROFILING_KTIME	0x00000020
#define KGSL_CMDBATCH_END_OF_FRAME	KGSL_CONTEXT_END_OF_FRAME
#define KGSL_CMDBATCH_SYNC		KGSL_CONTEXT_SYNC
#define KGSL_CMDBATCH_PWR_CONSTRAINT	KGSL_CONTEXT_PWR_CONSTRAINT
#define KGSL_CMDBATCH_SPARSE	    0x1000

#define KGSL_CMDBATCH_START_RECURRING	0x00100000
#define KGSL_CMDBATCH_STOP_RECURRING	0x00200000



#define KGSL_CMDLIST_IB                  0x00000001U
#define KGSL_CMDLIST_CTXTSWITCH_PREAMBLE 0x00000002U
#define KGSL_CMDLIST_IB_PREAMBLE         0x00000004U

#define KGSL_OBJLIST_MEMOBJ  0x00000008U
#define KGSL_OBJLIST_PROFILE 0x00000010U

#define KGSL_CMD_SYNCPOINT_TYPE_TIMESTAMP 0
#define KGSL_CMD_SYNCPOINT_TYPE_FENCE 1
#define KGSL_CMD_SYNCPOINT_TYPE_TIMELINE 2


#define KGSL_MEMFLAGS_SECURE       (1ULL << 3)
#define KGSL_MEMFLAGS_GPUREADONLY  (1ULL << 24)
#define KGSL_MEMFLAGS_GPUWRITEONLY (1ULL << 25)
#define KGSL_MEMFLAGS_FORCE_32BIT  (1ULL << 32)

#define KGSL_SPARSE_BIND_MULTIPLE_TO_PHYS 0x400000000ULL
#define KGSL_SPARSE_BIND 0x1ULL
#define KGSL_SPARSE_UNBIND 0x2ULL

#define KGSL_CACHEMODE_MASK       0x0C000000U
#define KGSL_CACHEMODE_SHIFT 26

#define KGSL_CACHEMODE_WRITECOMBINE 0
#define KGSL_CACHEMODE_UNCACHED 1
#define KGSL_CACHEMODE_WRITETHROUGH 2
#define KGSL_CACHEMODE_WRITEBACK 3

#define KGSL_MEMFLAGS_USE_CPU_MAP (1ULL << 28)
#define KGSL_MEMFLAGS_SPARSE_PHYS (1ULL << 29)
#define KGSL_MEMFLAGS_SPARSE_VIRT (1ULL << 30)
#define KGSL_MEMFLAGS_IOCOHERENT  (1ULL << 31)
#define KGSL_MEMFLAGS_GUARD_PAGE  (1ULL << 33)
#define KGSL_MEMFLAGS_VBO         (1ULL << 34)

#define KGSL_MEMTYPE_MASK		0x0000FF00
#define KGSL_MEMTYPE_SHIFT		8

#define KGSL_MEMTYPE_OBJECTANY			0
#define KGSL_MEMTYPE_FRAMEBUFFER		1
#define KGSL_MEMTYPE_RENDERBUFFER		2
#define KGSL_MEMTYPE_ARRAYBUFFER		3
#define KGSL_MEMTYPE_ELEMENTARRAYBUFFER		4
#define KGSL_MEMTYPE_VERTEXARRAYBUFFER		5
#define KGSL_MEMTYPE_TEXTURE			6
#define KGSL_MEMTYPE_SURFACE			7
#define KGSL_MEMTYPE_EGL_SURFACE		8
#define KGSL_MEMTYPE_GL				9
#define KGSL_MEMTYPE_CL				10
#define KGSL_MEMTYPE_CL_BUFFER_MAP		11
#define KGSL_MEMTYPE_CL_BUFFER_NOMAP		12
#define KGSL_MEMTYPE_CL_IMAGE_MAP		13
#define KGSL_MEMTYPE_CL_IMAGE_NOMAP		14
#define KGSL_MEMTYPE_CL_KERNEL_STACK		15
#define KGSL_MEMTYPE_COMMAND			16
#define KGSL_MEMTYPE_2D				17
#define KGSL_MEMTYPE_EGL_IMAGE			18
#define KGSL_MEMTYPE_EGL_SHADOW			19
#define KGSL_MEMTYPE_MULTISAMPLE		20
#define KGSL_MEMTYPE_KERNEL			255

#define KGSL_MEMALIGN_MASK		0x00FF0000
#define KGSL_MEMALIGN_SHIFT		16

enum kgsl_user_mem_type {
    KGSL_USER_MEM_TYPE_PMEM		= 0x00000000,
    KGSL_USER_MEM_TYPE_ASHMEM	= 0x00000001,
    KGSL_USER_MEM_TYPE_ADDR		= 0x00000002,
    KGSL_USER_MEM_TYPE_ION		= 0x00000003,
    KGSL_USER_MEM_TYPE_DMABUF       = 0x00000003,
    KGSL_USER_MEM_TYPE_MAX		= 0x00000007,
};
#define KGSL_MEMFLAGS_USERMEM_MASK 0x000000e0
#define KGSL_MEMFLAGS_USERMEM_SHIFT 5

#define KGSL_USERMEM_FLAG(x) (((x) + 1) << KGSL_MEMFLAGS_USERMEM_SHIFT)

#define KGSL_MEMFLAGS_NOT_USERMEM 0
#define KGSL_MEMFLAGS_USERMEM_PMEM KGSL_USERMEM_FLAG(KGSL_USER_MEM_TYPE_PMEM)
#define KGSL_MEMFLAGS_USERMEM_ASHMEM \
		KGSL_USERMEM_FLAG(KGSL_USER_MEM_TYPE_ASHMEM)
#define KGSL_MEMFLAGS_USERMEM_ADDR KGSL_USERMEM_FLAG(KGSL_USER_MEM_TYPE_ADDR)
#define KGSL_MEMFLAGS_USERMEM_ION KGSL_USERMEM_FLAG(KGSL_USER_MEM_TYPE_ION)


#define KGSL_FLAGS_NORMALMODE  0x00000000
#define KGSL_FLAGS_SAFEMODE    0x00000001
#define KGSL_FLAGS_INITIALIZED0 0x00000002
#define KGSL_FLAGS_INITIALIZED 0x00000004
#define KGSL_FLAGS_STARTED     0x00000008
#define KGSL_FLAGS_ACTIVE      0x00000010
#define KGSL_FLAGS_RESERVED0   0x00000020
#define KGSL_FLAGS_RESERVED1   0x00000040
#define KGSL_FLAGS_RESERVED2   0x00000080
#define KGSL_FLAGS_SOFT_RESET  0x00000100
#define KGSL_FLAGS_PER_CONTEXT_TIMESTAMPS 0x00000200

#define KGSL_SYNCOBJ_SERVER_TIMEOUT 2000

#define KGSL_UBWC_NONE	0
#define KGSL_UBWC_1_0	1
#define KGSL_UBWC_2_0	2
#define KGSL_UBWC_3_0	3
#define KGSL_UBWC_4_0	4

enum kgsl_ctx_reset_stat {
    KGSL_CTX_STAT_NO_ERROR				= 0x00000000,
    KGSL_CTX_STAT_GUILTY_CONTEXT_RESET_EXT		= 0x00000001,
    KGSL_CTX_STAT_INNOCENT_CONTEXT_RESET_EXT	= 0x00000002,
    KGSL_CTX_STAT_UNKNOWN_CONTEXT_RESET_EXT		= 0x00000003
};

#define KGSL_CONVERT_TO_MBPS(val) \
	(val*1000*1000U)

struct kgsl_devinfo {

    unsigned int device_id;
    unsigned int chip_id;
    unsigned int mmu_enabled;
    unsigned long gmem_gpubaseaddr;
    unsigned int gpu_id;
    __kernel_size_t gmem_sizebytes;
};

struct kgsl_devmemstore {
    volatile unsigned int soptimestamp;
    unsigned int sbz;
    volatile unsigned int eoptimestamp;
    unsigned int sbz2;
    volatile unsigned int preempted;
    unsigned int sbz3;
    volatile unsigned int ref_wait_ts;
    unsigned int sbz4;
    unsigned int current_context;
    unsigned int sbz5;
};

#define KGSL_MEMSTORE_OFFSET(ctxt_id, field) \
	((ctxt_id)*sizeof(struct kgsl_devmemstore) + \
	 offsetof(struct kgsl_devmemstore, field))

enum kgsl_timestamp_type {
    KGSL_TIMESTAMP_CONSUMED = 0x00000001,
    KGSL_TIMESTAMP_RETIRED  = 0x00000002,
    KGSL_TIMESTAMP_QUEUED   = 0x00000003,
};

#define KGSL_PROP_DEVICE_INFO		0x1
#define KGSL_PROP_DEVICE_SHADOW		0x2
#define KGSL_PROP_DEVICE_POWER		0x3
#define KGSL_PROP_SHMEM			0x4
#define KGSL_PROP_SHMEM_APERTURES	0x5
#define KGSL_PROP_MMU_ENABLE		0x6
#define KGSL_PROP_INTERRUPT_WAITS	0x7
#define KGSL_PROP_VERSION		0x8
#define KGSL_PROP_GPU_RESET_STAT	0x9
#define KGSL_PROP_PWRCTRL		0xE
#define KGSL_PROP_PWR_CONSTRAINT	0x12
#define KGSL_PROP_UCHE_GMEM_VADDR	0x13
#define KGSL_PROP_SP_GENERIC_MEM	0x14
#define KGSL_PROP_UCODE_VERSION		0x15
#define KGSL_PROP_GPMU_VERSION		0x16
#define KGSL_PROP_HIGHEST_BANK_BIT	0x17
#define KGSL_PROP_DEVICE_BITNESS	0x18
#define KGSL_PROP_DEVICE_QDSS_STM	0x19
#define KGSL_PROP_MIN_ACCESS_LENGTH	0x1A
#define KGSL_PROP_UBWC_MODE		0x1B
#define KGSL_PROP_DEVICE_QTIMER		0x20
#define KGSL_PROP_L3_PWR_CONSTRAINT     0x22
#define KGSL_PROP_SECURE_BUFFER_ALIGNMENT 0x23
#define KGSL_PROP_SECURE_CTXT_SUPPORT 0x24
#define KGSL_PROP_SPEED_BIN		0x25
#define KGSL_PROP_GAMING_BIN		0x26
#define KGSL_PROP_QUERY_CAPABILITIES	0x27
#define KGSL_PROP_CONTEXT_PROPERTY	0x28
#define KGSL_PROP_GPU_MODEL		0x29
#define KGSL_PROP_VK_DEVICE_ID		0x2A
#define KGSL_PROP_IS_LPAC_ENABLED	0x2B

struct kgsl_capabilities_properties {
    __u64 list;
    __u32 count;
};

#define KGSL_QUERY_CAPS_PROPERTIES 1

struct kgsl_capabilities {
    __u64 data;
    __u64 size;
    __u32 querytype;
};

struct kgsl_shadowprop {
    unsigned long gpuaddr;
    __kernel_size_t size;
    unsigned int flags;
};

struct kgsl_qdss_stm_prop {
    __u64 gpuaddr;
    __u64 size;
};

struct kgsl_qtimer_prop {
    __u64 gpuaddr;
    __u64 size;
};

struct kgsl_version {
    unsigned int drv_major;
    unsigned int drv_minor;
    unsigned int dev_major;
    unsigned int dev_minor;
};

struct kgsl_sp_generic_mem {
    __u64 local;
    __u64 pvt;
};

struct kgsl_ucode_version {
    unsigned int pfp;
    unsigned int pm4;
};

struct kgsl_gpmu_version {
    unsigned int major;
    unsigned int minor;
    unsigned int features;
};

struct kgsl_context_property {
    __u64 data;
    __u32 size;
    __u32 type;
    __u32 contextid;
};

struct kgsl_context_property_fault {
    __s32 faults;
    __u32 timestamp;
};

struct kgsl_gpu_model {
    char gpu_model[32];
};

#define KGSL_CONTEXT_PROP_FAULTS 1


#define KGSL_PERFCOUNTER_GROUP_CP 0x0
#define KGSL_PERFCOUNTER_GROUP_RBBM 0x1
#define KGSL_PERFCOUNTER_GROUP_PC 0x2
#define KGSL_PERFCOUNTER_GROUP_VFD 0x3
#define KGSL_PERFCOUNTER_GROUP_HLSQ 0x4
#define KGSL_PERFCOUNTER_GROUP_VPC 0x5
#define KGSL_PERFCOUNTER_GROUP_TSE 0x6
#define KGSL_PERFCOUNTER_GROUP_RAS 0x7
#define KGSL_PERFCOUNTER_GROUP_UCHE 0x8
#define KGSL_PERFCOUNTER_GROUP_TP 0x9
#define KGSL_PERFCOUNTER_GROUP_SP 0xA
#define KGSL_PERFCOUNTER_GROUP_RB 0xB
#define KGSL_PERFCOUNTER_GROUP_PWR 0xC
#define KGSL_PERFCOUNTER_GROUP_VBIF 0xD
#define KGSL_PERFCOUNTER_GROUP_VBIF_PWR 0xE
#define KGSL_PERFCOUNTER_GROUP_MH 0xF
#define KGSL_PERFCOUNTER_GROUP_PA_SU 0x10
#define KGSL_PERFCOUNTER_GROUP_SQ 0x11
#define KGSL_PERFCOUNTER_GROUP_SX 0x12
#define KGSL_PERFCOUNTER_GROUP_TCF 0x13
#define KGSL_PERFCOUNTER_GROUP_TCM 0x14
#define KGSL_PERFCOUNTER_GROUP_TCR 0x15
#define KGSL_PERFCOUNTER_GROUP_L2 0x16
#define KGSL_PERFCOUNTER_GROUP_VSC 0x17
#define KGSL_PERFCOUNTER_GROUP_CCU 0x18
#define KGSL_PERFCOUNTER_GROUP_LRZ 0x19
#define KGSL_PERFCOUNTER_GROUP_CMP 0x1A
#define KGSL_PERFCOUNTER_GROUP_ALWAYSON 0x1B
#define KGSL_PERFCOUNTER_GROUP_SP_PWR 0x1C
#define KGSL_PERFCOUNTER_GROUP_TP_PWR 0x1D
#define KGSL_PERFCOUNTER_GROUP_RB_PWR 0x1E
#define KGSL_PERFCOUNTER_GROUP_CCU_PWR 0x1F
#define KGSL_PERFCOUNTER_GROUP_UCHE_PWR 0x20
#define KGSL_PERFCOUNTER_GROUP_CP_PWR 0x21
#define KGSL_PERFCOUNTER_GROUP_GPMU_PWR 0x22
#define KGSL_PERFCOUNTER_GROUP_ALWAYSON_PWR 0x23
#define KGSL_PERFCOUNTER_GROUP_GLC 0x24
#define KGSL_PERFCOUNTER_GROUP_FCHE 0x25
#define KGSL_PERFCOUNTER_GROUP_MHUB 0x26
#define KGSL_PERFCOUNTER_GROUP_GMU_XOCLK 0x27
#define KGSL_PERFCOUNTER_GROUP_GMU_GMUCLK  0x28
#define KGSL_PERFCOUNTER_GROUP_GMU_PERF  0x29
#define KGSL_PERFCOUNTER_GROUP_SW 0x2a
#define KGSL_PERFCOUNTER_GROUP_UFC 0x2b
#define KGSL_PERFCOUNTER_GROUP_BV_CP 0x2c
#define KGSL_PERFCOUNTER_GROUP_BV_PC 0x2d
#define KGSL_PERFCOUNTER_GROUP_BV_VFD 0x2e
#define KGSL_PERFCOUNTER_GROUP_BV_VPC 0x2f
#define KGSL_PERFCOUNTER_GROUP_BV_TP 0x30
#define KGSL_PERFCOUNTER_GROUP_BV_SP 0x31
#define KGSL_PERFCOUNTER_GROUP_BV_UFC 0x32
#define KGSL_PERFCOUNTER_GROUP_BV_TSE 0x33
#define KGSL_PERFCOUNTER_GROUP_BV_RAS 0x34
#define KGSL_PERFCOUNTER_GROUP_BV_LRZ 0x35
#define KGSL_PERFCOUNTER_GROUP_BV_HLSQ 0x36
#define KGSL_PERFCOUNTER_GROUP_MAX 0x37

#define KGSL_PERFCOUNTER_NOT_USED 0xFFFFFFFF
#define KGSL_PERFCOUNTER_BROKEN 0xFFFFFFFE

struct kgsl_ibdesc {
    unsigned long gpuaddr;
    unsigned long __pad;
    __kernel_size_t sizedwords;
    unsigned int ctrl;
};

struct kgsl_cmdbatch_profiling_buffer {
    __u64 wall_clock_s;
    __u64 wall_clock_ns;
    __u64 gpu_ticks_queued;
    __u64 gpu_ticks_submitted;
    __u64 gpu_ticks_retired;
};

#define KGSL_IOC_TYPE 0x09

struct kgsl_device_getproperty {
    unsigned int type;
    void __user *value;
    __kernel_size_t sizebytes;
};

#define IOCTL_KGSL_DEVICE_GETPROPERTY \
	_IOWR(KGSL_IOC_TYPE, 0x2, struct kgsl_device_getproperty)


struct kgsl_device_waittimestamp {
    unsigned int timestamp;
    unsigned int timeout;
};

#define IOCTL_KGSL_DEVICE_WAITTIMESTAMP \
	_IOW(KGSL_IOC_TYPE, 0x6, struct kgsl_device_waittimestamp)

struct kgsl_device_waittimestamp_ctxtid {
    unsigned int context_id;
    unsigned int timestamp;
    unsigned int timeout;
};

#define IOCTL_KGSL_DEVICE_WAITTIMESTAMP_CTXTID \
	_IOW(KGSL_IOC_TYPE, 0x7, struct kgsl_device_waittimestamp_ctxtid)

struct kgsl_ringbuffer_issueibcmds {
    unsigned int drawctxt_id;
    unsigned long ibdesc_addr;
    unsigned int numibs;
    unsigned int timestamp;
    unsigned int flags;
};

#define IOCTL_KGSL_RINGBUFFER_ISSUEIBCMDS \
	_IOWR(KGSL_IOC_TYPE, 0x10, struct kgsl_ringbuffer_issueibcmds)

struct kgsl_cmdstream_readtimestamp {
    unsigned int type;
    unsigned int timestamp;
};

#define IOCTL_KGSL_CMDSTREAM_READTIMESTAMP_OLD \
	_IOR(KGSL_IOC_TYPE, 0x11, struct kgsl_cmdstream_readtimestamp)

#define IOCTL_KGSL_CMDSTREAM_READTIMESTAMP \
	_IOWR(KGSL_IOC_TYPE, 0x11, struct kgsl_cmdstream_readtimestamp)

struct kgsl_cmdstream_freememontimestamp {
    unsigned long gpuaddr;
    unsigned int type;
    unsigned int timestamp;
};

#define IOCTL_KGSL_CMDSTREAM_FREEMEMONTIMESTAMP \
	_IOW(KGSL_IOC_TYPE, 0x12, struct kgsl_cmdstream_freememontimestamp)


#define IOCTL_KGSL_CMDSTREAM_FREEMEMONTIMESTAMP_OLD \
	_IOR(KGSL_IOC_TYPE, 0x12, struct kgsl_cmdstream_freememontimestamp)

struct kgsl_drawctxt_create {
    unsigned int flags;
    unsigned int drawctxt_id;
};

#define IOCTL_KGSL_DRAWCTXT_CREATE \
	_IOWR(KGSL_IOC_TYPE, 0x13, struct kgsl_drawctxt_create)

struct kgsl_drawctxt_destroy {
    unsigned int drawctxt_id;
};

#define IOCTL_KGSL_DRAWCTXT_DESTROY \
	_IOW(KGSL_IOC_TYPE, 0x14, struct kgsl_drawctxt_destroy)

struct kgsl_map_user_mem {
    int fd;
    unsigned long gpuaddr;
    __kernel_size_t len;
    __kernel_size_t offset;
    unsigned long hostptr;
    enum kgsl_user_mem_type memtype;
    unsigned int flags;
};

#define IOCTL_KGSL_MAP_USER_MEM \
	_IOWR(KGSL_IOC_TYPE, 0x15, struct kgsl_map_user_mem)

struct kgsl_cmdstream_readtimestamp_ctxtid {
    unsigned int context_id;
    unsigned int type;
    unsigned int timestamp;
};

#define IOCTL_KGSL_CMDSTREAM_READTIMESTAMP_CTXTID \
	_IOWR(KGSL_IOC_TYPE, 0x16, struct kgsl_cmdstream_readtimestamp_ctxtid)

struct kgsl_cmdstream_freememontimestamp_ctxtid {
    unsigned int context_id;
    unsigned long gpuaddr;
    unsigned int type;
    unsigned int timestamp;
};

#define IOCTL_KGSL_CMDSTREAM_FREEMEMONTIMESTAMP_CTXTID \
	_IOW(KGSL_IOC_TYPE, 0x17, \
	struct kgsl_cmdstream_freememontimestamp_ctxtid)

struct kgsl_sharedmem_from_pmem {
    int pmem_fd;
    unsigned long gpuaddr;
    unsigned int len;
    unsigned int offset;
};

#define IOCTL_KGSL_SHAREDMEM_FROM_PMEM \
	_IOWR(KGSL_IOC_TYPE, 0x20, struct kgsl_sharedmem_from_pmem)

struct kgsl_sharedmem_free {
    unsigned long gpuaddr;
};

#define IOCTL_KGSL_SHAREDMEM_FREE \
	_IOW(KGSL_IOC_TYPE, 0x21, struct kgsl_sharedmem_free)

struct kgsl_cff_user_event {
    unsigned char cff_opcode;
    unsigned int op1;
    unsigned int op2;
    unsigned int op3;
    unsigned int op4;
    unsigned int op5;
    unsigned int __pad[2];
};

#define IOCTL_KGSL_CFF_USER_EVENT \
	_IOW(KGSL_IOC_TYPE, 0x31, struct kgsl_cff_user_event)

struct kgsl_gmem_desc {
    unsigned int x;
    unsigned int y;
    unsigned int width;
    unsigned int height;
    unsigned int pitch;
};

struct kgsl_buffer_desc {
    void		*hostptr;
    unsigned long	gpuaddr;
    int		size;
    unsigned int	format;
    unsigned int	pitch;
    unsigned int	enabled;
};

struct kgsl_bind_gmem_shadow {
    unsigned int drawctxt_id;
    struct kgsl_gmem_desc gmem_desc;
    unsigned int shadow_x;
    unsigned int shadow_y;
    struct kgsl_buffer_desc shadow_buffer;
    unsigned int buffer_id;
};

#define IOCTL_KGSL_DRAWCTXT_BIND_GMEM_SHADOW \
	_IOW(KGSL_IOC_TYPE, 0x22, struct kgsl_bind_gmem_shadow)



struct kgsl_sharedmem_from_vmalloc {
    unsigned long gpuaddr;
    unsigned int hostptr;
    unsigned int flags;
};

#define IOCTL_KGSL_SHAREDMEM_FROM_VMALLOC \
	_IOWR(KGSL_IOC_TYPE, 0x23, struct kgsl_sharedmem_from_vmalloc)


#define IOCTL_KGSL_SHAREDMEM_FLUSH_CACHE \
	_IOW(KGSL_IOC_TYPE, 0x24, struct kgsl_sharedmem_free)

struct kgsl_drawctxt_set_bin_base_offset {
    unsigned int drawctxt_id;
    unsigned int offset;
};

#define IOCTL_KGSL_DRAWCTXT_SET_BIN_BASE_OFFSET \
	_IOW(KGSL_IOC_TYPE, 0x25, struct kgsl_drawctxt_set_bin_base_offset)

enum kgsl_cmdwindow_type {
    KGSL_CMDWINDOW_MIN     = 0x00000000,
    KGSL_CMDWINDOW_2D      = 0x00000000,
    KGSL_CMDWINDOW_3D      = 0x00000001,
    KGSL_CMDWINDOW_MMU     = 0x00000002,
    KGSL_CMDWINDOW_ARBITER = 0x000000FF,
    KGSL_CMDWINDOW_MAX     = 0x000000FF,
};

struct kgsl_cmdwindow_write {
    enum kgsl_cmdwindow_type target;
    unsigned int addr;
    unsigned int data;
};

#define IOCTL_KGSL_CMDWINDOW_WRITE \
	_IOW(KGSL_IOC_TYPE, 0x2e, struct kgsl_cmdwindow_write)

struct kgsl_gpumem_alloc {
    unsigned long gpuaddr;
    __kernel_size_t size;
    unsigned int flags;
};

#define IOCTL_KGSL_GPUMEM_ALLOC \
	_IOWR(KGSL_IOC_TYPE, 0x2f, struct kgsl_gpumem_alloc)

struct kgsl_cff_syncmem {
    unsigned long gpuaddr;
    __kernel_size_t len;
    unsigned int __pad[2];
};

#define IOCTL_KGSL_CFF_SYNCMEM \
	_IOW(KGSL_IOC_TYPE, 0x30, struct kgsl_cff_syncmem)


struct kgsl_timestamp_event {
    int type;
    unsigned int timestamp;
    unsigned int context_id;
    void __user *priv;
    __kernel_size_t len;
};

#define IOCTL_KGSL_TIMESTAMP_EVENT_OLD \
	_IOW(KGSL_IOC_TYPE, 0x31, struct kgsl_timestamp_event)


#define KGSL_TIMESTAMP_EVENT_GENLOCK 1

struct kgsl_timestamp_event_genlock {
    int handle;
};


#define KGSL_TIMESTAMP_EVENT_FENCE 2

struct kgsl_timestamp_event_fence {
    int fence_fd;
};


#define IOCTL_KGSL_SETPROPERTY \
	_IOW(KGSL_IOC_TYPE, 0x32, struct kgsl_device_getproperty)

#define IOCTL_KGSL_TIMESTAMP_EVENT \
	_IOWR(KGSL_IOC_TYPE, 0x33, struct kgsl_timestamp_event)

struct kgsl_gpumem_alloc_id {
    unsigned int id;
    unsigned int flags;
    __kernel_size_t size;
    __kernel_size_t mmapsize;
    unsigned long gpuaddr;
    unsigned long __pad[2];
};

#define IOCTL_KGSL_GPUMEM_ALLOC_ID \
	_IOWR(KGSL_IOC_TYPE, 0x34, struct kgsl_gpumem_alloc_id)

struct kgsl_gpumem_free_id {
    unsigned int id;
    unsigned int __pad;
};

#define IOCTL_KGSL_GPUMEM_FREE_ID \
	_IOWR(KGSL_IOC_TYPE, 0x35, struct kgsl_gpumem_free_id)

struct kgsl_gpumem_get_info {
    unsigned long gpuaddr;
    unsigned int id;
    unsigned int flags;
    __kernel_size_t size;
    __kernel_size_t mmapsize;
    unsigned long useraddr;
    unsigned long __pad[4];
};

#define IOCTL_KGSL_GPUMEM_GET_INFO\
	_IOWR(KGSL_IOC_TYPE, 0x36, struct kgsl_gpumem_get_info)

struct kgsl_gpumem_sync_cache {
    unsigned long gpuaddr;
    unsigned int id;
    unsigned int op;
    __kernel_size_t offset;
    __kernel_size_t length;
};

#define KGSL_GPUMEM_CACHE_CLEAN (1 << 0)
#define KGSL_GPUMEM_CACHE_TO_GPU KGSL_GPUMEM_CACHE_CLEAN

#define KGSL_GPUMEM_CACHE_INV (1 << 1)
#define KGSL_GPUMEM_CACHE_FROM_GPU KGSL_GPUMEM_CACHE_INV

#define KGSL_GPUMEM_CACHE_FLUSH \
	(KGSL_GPUMEM_CACHE_CLEAN | KGSL_GPUMEM_CACHE_INV)

#define KGSL_GPUMEM_CACHE_RANGE (1 << 31U)

#define IOCTL_KGSL_GPUMEM_SYNC_CACHE \
	_IOW(KGSL_IOC_TYPE, 0x37, struct kgsl_gpumem_sync_cache)

struct kgsl_perfcounter_get {
    unsigned int groupid;
    unsigned int countable;
    unsigned int offset;
    unsigned int offset_hi;

    unsigned int __pad;
};

#define IOCTL_KGSL_PERFCOUNTER_GET \
	_IOWR(KGSL_IOC_TYPE, 0x38, struct kgsl_perfcounter_get)

struct kgsl_perfcounter_put {
    unsigned int groupid;
    unsigned int countable;

    unsigned int __pad[2];
};

#define IOCTL_KGSL_PERFCOUNTER_PUT \
	_IOW(KGSL_IOC_TYPE, 0x39, struct kgsl_perfcounter_put)

struct kgsl_perfcounter_query {
    unsigned int groupid;
    unsigned int __user *countables;
    unsigned int count;
    unsigned int max_counters;

    unsigned int __pad[2];
};

#define IOCTL_KGSL_PERFCOUNTER_QUERY \
	_IOWR(KGSL_IOC_TYPE, 0x3A, struct kgsl_perfcounter_query)


struct kgsl_perfcounter_read_group {
    unsigned int groupid;
    unsigned int countable;
    unsigned long long value;
};

struct kgsl_perfcounter_read {
    struct kgsl_perfcounter_read_group __user *reads;
    unsigned int count;

    unsigned int __pad[2];
};

#define IOCTL_KGSL_PERFCOUNTER_READ \
	_IOWR(KGSL_IOC_TYPE, 0x3B, struct kgsl_perfcounter_read)
struct kgsl_gpumem_sync_cache_bulk {
    unsigned int __user *id_list;
    unsigned int count;
    unsigned int op;

    unsigned int __pad[2];
};

#define IOCTL_KGSL_GPUMEM_SYNC_CACHE_BULK \
	_IOWR(KGSL_IOC_TYPE, 0x3C, struct kgsl_gpumem_sync_cache_bulk)

struct kgsl_cmd_syncpoint_timestamp {
    unsigned int context_id;
    unsigned int timestamp;
};

struct kgsl_cmd_syncpoint_fence {
    int fd;
};

struct kgsl_cmd_syncpoint_timeline {
    __u64 timelines;
    __u32 count;
    __u32 timelines_size;
};

struct kgsl_cmd_syncpoint {
    int type;
    void __user *priv;
    __kernel_size_t size;
};

#define KGSL_IBDESC_MEMLIST 0x1

#define KGSL_IBDESC_PROFILING_BUFFER 0x2


struct kgsl_submit_commands {
    unsigned int context_id;
    unsigned int flags;
    struct kgsl_ibdesc __user *cmdlist;
    unsigned int numcmds;
    struct kgsl_cmd_syncpoint __user *synclist;
    unsigned int numsyncs;
    unsigned int timestamp;
    unsigned int __pad[4];
};

#define IOCTL_KGSL_SUBMIT_COMMANDS \
	_IOWR(KGSL_IOC_TYPE, 0x3D, struct kgsl_submit_commands)

struct kgsl_device_constraint {
    unsigned int type;
    unsigned int context_id;
    void __user *data;
    __kernel_size_t size;
};

#define KGSL_CONSTRAINT_NONE 0
#define KGSL_CONSTRAINT_PWRLEVEL 1

#define KGSL_CONSTRAINT_L3_NONE	2
#define KGSL_CONSTRAINT_L3_PWRLEVEL	3

#define KGSL_CONSTRAINT_PWR_MIN    0
#define KGSL_CONSTRAINT_PWR_MAX    1

struct kgsl_device_constraint_pwrlevel {
    unsigned int level;
};


struct kgsl_syncsource_create {
    unsigned int id;
    unsigned int __pad[3];
};

#define IOCTL_KGSL_SYNCSOURCE_CREATE \
	_IOWR(KGSL_IOC_TYPE, 0x40, struct kgsl_syncsource_create)


struct kgsl_syncsource_destroy {
    unsigned int id;
    unsigned int __pad[3];
};

#define IOCTL_KGSL_SYNCSOURCE_DESTROY \
	_IOWR(KGSL_IOC_TYPE, 0x41, struct kgsl_syncsource_destroy)

struct kgsl_syncsource_create_fence {
    unsigned int id;
    int fence_fd;
    unsigned int __pad[4];
};

#define IOCTL_KGSL_SYNCSOURCE_CREATE_FENCE \
	_IOWR(KGSL_IOC_TYPE, 0x42, struct kgsl_syncsource_create_fence)

struct kgsl_syncsource_signal_fence {
    unsigned int id;
    int fence_fd;
    unsigned int __pad[4];
};

#define IOCTL_KGSL_SYNCSOURCE_SIGNAL_FENCE \
	_IOWR(KGSL_IOC_TYPE, 0x43, struct kgsl_syncsource_signal_fence)

struct kgsl_cff_sync_gpuobj {
    __u64 offset;
    __u64 length;
    unsigned int id;
};

#define IOCTL_KGSL_CFF_SYNC_GPUOBJ \
	_IOW(KGSL_IOC_TYPE, 0x44, struct kgsl_cff_sync_gpuobj)

struct kgsl_gpuobj_alloc {
    __u64 size;
    __u64 flags;
    __u64 va_len;
    __u64 mmapsize;
    unsigned int id;
    unsigned int metadata_len;
    __u64 metadata;
};

#define KGSL_GPUOBJ_ALLOC_METADATA_MAX 64

#define IOCTL_KGSL_GPUOBJ_ALLOC \
	_IOWR(KGSL_IOC_TYPE, 0x45, struct kgsl_gpuobj_alloc)

struct kgsl_gpuobj_free {
    __u64 flags;
    __u64 __user priv;
    unsigned int id;
    unsigned int type;
    unsigned int len;
};

#define KGSL_GPUOBJ_FREE_ON_EVENT 1

#define KGSL_GPU_EVENT_TIMESTAMP 1
#define KGSL_GPU_EVENT_FENCE     2

struct kgsl_gpu_event_timestamp {
    unsigned int context_id;
    unsigned int timestamp;
};

struct kgsl_gpu_event_fence {
    int fd;
};

#define IOCTL_KGSL_GPUOBJ_FREE \
	_IOW(KGSL_IOC_TYPE, 0x46, struct kgsl_gpuobj_free)

struct kgsl_gpuobj_info {
    __u64 gpuaddr;
    __u64 flags;
    __u64 size;
    __u64 va_len;
    __u64 va_addr;
    unsigned int id;
};

#define IOCTL_KGSL_GPUOBJ_INFO \
	_IOWR(KGSL_IOC_TYPE, 0x47, struct kgsl_gpuobj_info)


struct kgsl_gpuobj_import {
    __u64 __user priv;
    __u64 priv_len;
    __u64 flags;
    unsigned int type;
    unsigned int id;
};

struct kgsl_gpuobj_import_dma_buf {
    int fd;
};

struct kgsl_gpuobj_import_useraddr {
    __u64 virtaddr;
};

#define IOCTL_KGSL_GPUOBJ_IMPORT \
	_IOWR(KGSL_IOC_TYPE, 0x48, struct kgsl_gpuobj_import)


struct kgsl_gpuobj_sync_obj {
    __u64 offset;
    __u64 length;
    unsigned int id;
    unsigned int op;
};


struct kgsl_gpuobj_sync {
    __u64 __user objs;
    unsigned int obj_len;
    unsigned int count;
};

#define IOCTL_KGSL_GPUOBJ_SYNC \
	_IOW(KGSL_IOC_TYPE, 0x49, struct kgsl_gpuobj_sync)

struct kgsl_command_object {
    __u64 offset;
    __u64 gpuaddr;
    __u64 size;
    unsigned int flags;
    unsigned int id;
};

struct kgsl_command_syncpoint {
    __u64 __user priv;
    __u64 size;
    unsigned int type;
};

struct kgsl_gpu_command {
    __u64 flags;
    __u64 __user cmdlist;
    unsigned int cmdsize;
    unsigned int numcmds;
    __u64 __user objlist;
    unsigned int objsize;
    unsigned int numobjs;
    __u64 __user synclist;
    unsigned int syncsize;
    unsigned int numsyncs;
    unsigned int context_id;
    unsigned int timestamp;
};

#define IOCTL_KGSL_GPU_COMMAND \
	_IOWR(KGSL_IOC_TYPE, 0x4A, struct kgsl_gpu_command)

struct kgsl_preemption_counters_query {
    __u64 __user counters;
    unsigned int size_user;
    unsigned int size_priority_level;
    unsigned int max_priority_level;
};

#define IOCTL_KGSL_PREEMPTIONCOUNTER_QUERY \
	_IOWR(KGSL_IOC_TYPE, 0x4B, struct kgsl_preemption_counters_query)


#define KGSL_GPUOBJ_SET_INFO_METADATA (1 << 0)
#define KGSL_GPUOBJ_SET_INFO_TYPE (1 << 1)

struct kgsl_gpuobj_set_info {
    __u64 flags;
    __u64 metadata;
    unsigned int id;
    unsigned int metadata_len;
    unsigned int type;
};

#define IOCTL_KGSL_GPUOBJ_SET_INFO \
	_IOW(KGSL_IOC_TYPE, 0x4C, struct kgsl_gpuobj_set_info)

struct kgsl_sparse_phys_alloc {
    __u64 size;
    __u64 pagesize;
    __u64 flags;
    unsigned int id;
};

#define IOCTL_KGSL_SPARSE_PHYS_ALLOC \
	_IOWR(KGSL_IOC_TYPE, 0x50, struct kgsl_sparse_phys_alloc)

struct kgsl_sparse_phys_free {
    unsigned int id;
};

#define IOCTL_KGSL_SPARSE_PHYS_FREE \
	_IOW(KGSL_IOC_TYPE, 0x51, struct kgsl_sparse_phys_free)

struct kgsl_sparse_virt_alloc {
    __u64 size;
    __u64 pagesize;
    __u64 flags;
    __u64 gpuaddr;
    unsigned int id;
};

#define IOCTL_KGSL_SPARSE_VIRT_ALLOC \
	_IOWR(KGSL_IOC_TYPE, 0x52, struct kgsl_sparse_virt_alloc)

struct kgsl_sparse_virt_free {
    unsigned int id;
};

#define IOCTL_KGSL_SPARSE_VIRT_FREE \
	_IOW(KGSL_IOC_TYPE, 0x53, struct kgsl_sparse_virt_free)

struct kgsl_sparse_binding_object {
    __u64 virtoffset;
    __u64 physoffset;
    __u64 size;
    __u64 flags;
    unsigned int id;
};

struct kgsl_sparse_bind {
    __u64 __user list;
    unsigned int id;
    unsigned int size;
    unsigned int count;
};

#define IOCTL_KGSL_SPARSE_BIND \
	_IOW(KGSL_IOC_TYPE, 0x54, struct kgsl_sparse_bind)

struct kgsl_gpu_sparse_command {
    __u64 flags;
    __u64 __user sparselist;
    __u64 __user synclist;
    unsigned int sparsesize;
    unsigned int numsparse;
    unsigned int syncsize;
    unsigned int numsyncs;
    unsigned int context_id;
    unsigned int timestamp;
    unsigned int id;
};

#define IOCTL_KGSL_GPU_SPARSE_COMMAND \
	_IOWR(KGSL_IOC_TYPE, 0x55, struct kgsl_gpu_sparse_command)

#define KGSL_GPUMEM_RANGE_OP_BIND 1
#define KGSL_GPUMEM_RANGE_OP_UNBIND 2

struct kgsl_gpumem_bind_range {
    __u64 child_offset;
    __u64 target_offset;
    __u64 length;
    __u32 child_id;
    __u32 op;
};

#define KGSL_GPUMEM_BIND_ASYNC		(1UL << 0)
#define KGSL_GPUMEM_BIND_FENCE_OUT	(1UL << 1)

struct kgsl_gpumem_bind_ranges {
    __u64 ranges;
    __u32 ranges_nents;
    __u32 ranges_size;
    __u32 id;
    __u32 flags;
    int fence_id;
    __u32 padding;
};

#define IOCTL_KGSL_GPUMEM_BIND_RANGES \
	_IOWR(KGSL_IOC_TYPE, 0x56, struct kgsl_gpumem_bind_ranges)

#define KGSL_GPU_AUX_COMMAND_BIND	(1 << 0)
#define KGSL_GPU_AUX_COMMAND_TIMELINE	(1 << 1)
#define KGSL_GPU_AUX_COMMAND_SYNC	KGSL_CMDBATCH_SYNC

struct kgsl_gpu_aux_command_bind {
    __u64 rangeslist;
    __u64 numranges;
    __u64 rangesize;
    __u32 target;
    __u32 padding;
};

struct kgsl_gpu_aux_command_generic {
    __u64 priv;
    __u64 size;
    __u32 type;
    __u32 padding;
};

struct kgsl_gpu_aux_command {
    __u64 flags;
    __u64 cmdlist;
    __u32 cmdsize;
    __u32 numcmds;
    __u64 synclist;
    __u32 syncsize;
    __u32 numsyncs;
    __u32 context_id;
    __u32 timestamp;
};

#define IOCTL_KGSL_GPU_AUX_COMMAND \
	_IOWR(KGSL_IOC_TYPE, 0x57, struct kgsl_gpu_aux_command)

struct kgsl_timeline_create {
    __u64 seqno;
    __u32 id;
    __u32 padding;
};

#define IOCTL_KGSL_TIMELINE_CREATE \
	_IOWR(KGSL_IOC_TYPE, 0x58, struct kgsl_timeline_create)

struct kgsl_timeline_val {
    __u64 seqno;
    __u32 timeline;
    __u32 padding;
};

#define KGSL_TIMELINE_WAIT_ALL 1
#define KGSL_TIMELINE_WAIT_ANY 2

struct kgsl_timeline_wait {
    __s64 tv_sec;
    __s64 tv_nsec;
    __u64 timelines;
    __u32 count;
    __u32 timelines_size;
    __u32 flags;
    __u32 padding;
};

#define IOCTL_KGSL_TIMELINE_WAIT \
	_IOW(KGSL_IOC_TYPE, 0x59, struct kgsl_timeline_wait)

#define IOCTL_KGSL_TIMELINE_QUERY \
	_IOWR(KGSL_IOC_TYPE, 0x5A, struct kgsl_timeline_val)

struct kgsl_timeline_signal {
    __u64 timelines;
    __u32 count;
    __u32 timelines_size;
};

#define IOCTL_KGSL_TIMELINE_SIGNAL \
	_IOW(KGSL_IOC_TYPE, 0x5B, struct kgsl_timeline_signal)

struct kgsl_timeline_fence_get {
    __u64 seqno;
    __u32 timeline;
    int handle;
};

#define IOCTL_KGSL_TIMELINE_FENCE_GET \
	_IOWR(KGSL_IOC_TYPE, 0x5C, struct kgsl_timeline_fence_get)
#define IOCTL_KGSL_TIMELINE_DESTROY _IOW(KGSL_IOC_TYPE, 0x5D, __u32)

struct kgsl_gpu_aux_command_timeline {
    __u64 timelines;
    __u32 count;
    __u32 timelines_size;
};

#define KGSL_FAULT_TYPE_NO_FAULT    0
#define KGSL_FAULT_TYPE_PAGEFAULT   1
#define KGSL_FAULT_TYPE_MAX         2

#define KGSL_PAGEFAULT_TYPE_NONE                  0
#define KGSL_PAGEFAULT_TYPE_READ                  (1 << 0)
#define KGSL_PAGEFAULT_TYPE_WRITE                 (1 << 1)
#define KGSL_PAGEFAULT_TYPE_TRANSLATION           (1 << 2)
#define KGSL_PAGEFAULT_TYPE_PERMISSION            (1 << 3)
#define KGSL_PAGEFAULT_TYPE_EXTERNAL              (1 << 4)
#define KGSL_PAGEFAULT_TYPE_TRANSACTION_STALLED   (1 << 5)

struct kgsl_pagefault_report {
    __u64 fault_addr;
    __u64 reserved[2];
    __u32 fault_type;
    __u32 __pad;
};

struct kgsl_fault {
    __u64 fault;
    __u32 type;
    __u32 count;
    __u32 size;
    __u32 padding;
};

struct kgsl_fault_report {
    __u64 faultlist;
    __u32 faultnents;
    __u32 faultsize;
    __u32 context_id;
    __u32 padding;
};

#define IOCTL_KGSL_GET_FAULT_REPORT \
	_IOWR(KGSL_IOC_TYPE, 0x5E, struct kgsl_fault_report)

struct kgsl_recurring_command {
    __u64 flags;
    __u64 __user cmdlist;
    __u32 cmdsize;
    __u32 numcmds;
    __u64 __user objlist;
    __u32 objsize;
    __u32 numobjs;
    __u32 context_id;
    __u32 padding;
};

#define IOCTL_KGSL_RECURRING_COMMAND \
	_IOWR(KGSL_IOC_TYPE, 0x5F, struct kgsl_recurring_command)

#endif