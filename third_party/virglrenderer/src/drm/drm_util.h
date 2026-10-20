/*
 * Copyright 2022 Google LLC
 * SPDX-License-Identifier: MIT
 */

#ifndef DRM_UTIL_H_
#define DRM_UTIL_H_

#pragma GCC diagnostic push

#if defined(__clang__)
#pragma GCC diagnostic ignored "-Wgnu-zero-variadic-macro-arguments"
#pragma GCC diagnostic ignored "-Wmissing-field-initializers"
#pragma GCC diagnostic ignored "-Wlanguage-extension-token"
#pragma GCC diagnostic ignored "-Wgnu-statement-expression"
#endif

#if defined(__GNUC__)
#pragma GCC diagnostic ignored "-Wpedantic"
#endif

#include "linux/overflow.h"

#include "virglrenderer.h"

void _drm_log(enum virgl_log_level_flags level, const char *fmt, ...) __attribute__((format(printf, 2, 3)));
#define drm_log(fmt, ...) _drm_log(VIRGL_LOG_LEVEL_INFO, "%s:%d: " fmt "\n", __func__, __LINE__, ##__VA_ARGS__)
#define drm_err(fmt, ...) _drm_log(VIRGL_LOG_LEVEL_ERROR, "%s:%d: " fmt "\n", __func__, __LINE__, ##__VA_ARGS__)
#define drm_dbg(fmt, ...) _drm_log(VIRGL_LOG_LEVEL_DEBUG, "%s:%d: " fmt "\n", __func__, __LINE__, ##__VA_ARGS__)

#pragma GCC diagnostic pop

#define VOID2U64(x) ((uint64_t)(unsigned long)(x))
#define U642VOID(x) ((void *)(unsigned long)(x))

#ifndef NSEC_PER_SEC
#define NSEC_PER_SEC 1000000000ull
#endif

#if defined(__MINGW32__) || defined(__MINGW64__)
#  define DRM_ALIGN_4 __attribute__((gcc_struct,__packed__,__aligned__(4)))
#else
#  define DRM_ALIGN_4 __attribute__((__packed__, __aligned__(4)))
#endif

#endif /* DRM_UTIL_H_ */
