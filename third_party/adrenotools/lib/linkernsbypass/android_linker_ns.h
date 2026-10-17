// SPDX-License-Identifier: BSD-2-Clause
// Copyright © 2021 Billy Laws


#pragma once

#ifdef __cplusplus
extern "C" {
#endif

#include <stdint.h>

enum {
  ANDROID_NAMESPACE_TYPE_REGULAR = 0,
  ANDROID_NAMESPACE_TYPE_ISOLATED = 1,
  ANDROID_NAMESPACE_TYPE_SHARED = 2,
  ANDROID_NAMESPACE_TYPE_EXEMPT_LIST_ENABLED = 0x08000000,
  ANDROID_NAMESPACE_TYPE_ALSO_USED_AS_ANONYMOUS = 0x10000000,
  ANDROID_NAMESPACE_TYPE_SHARED_ISOLATED = ANDROID_NAMESPACE_TYPE_SHARED | ANDROID_NAMESPACE_TYPE_ISOLATED,
};

bool linkernsbypass_load_status();

struct android_namespace_t *android_create_namespace(const char *name,
                                                     const char *ld_library_path,
                                                     const char *default_library_path,
                                                     uint64_t type,
                                                     const char *permitted_when_isolated_path,
                                                     struct android_namespace_t *parent_namespace);

struct android_namespace_t *android_create_namespace_escape(const char *name,
                                                            const char *ld_library_path,
                                                            const char *default_library_path,
                                                            uint64_t type,
                                                            const char *permitted_when_isolated_path,
                                                            struct android_namespace_t *parent_namespace);

typedef struct android_namespace_t *(*android_get_exported_namespace_t)(const char *);
extern android_get_exported_namespace_t android_get_exported_namespace;

typedef bool (*android_link_namespaces_all_libs_t)(struct android_namespace_t *, struct android_namespace_t *);
extern android_link_namespaces_all_libs_t android_link_namespaces_all_libs;

typedef bool (*android_link_namespaces_t)(struct android_namespace_t *, struct android_namespace_t *, const char *);
extern android_link_namespaces_t android_link_namespaces;

bool linkernsbypass_link_namespace_to_default_all_libs(struct android_namespace_t *to);

void *linkernsbypass_namespace_dlopen(const char *filename, int flags, struct android_namespace_t *ns);

void *linkernsbypass_namespace_dlopen_unique(const char *libPath, const char *libTargetDir, int flags, struct android_namespace_t *ns);

#ifdef __cplusplus
}
#endif
