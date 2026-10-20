# VirGL GPU acceleration for Dextop Linux containers (arm64-v8a only).
#
# virgl_server (libvirgl_server.so, an executable named like a library so
# the packager ships it in nativeLibraryDir) is virglrenderer's vtest server.
# The privileged shell runs it next to each container; Mesa's virpipe driver
# inside the container sends it Gallium commands, which it replays with the
# device's own OpenGL ES driver through libepoxy.
#
# Sources and the generated files come from third_party/virglrenderer and
# third_party/libepoxy (see third_party/README.md).

set(DEXTOP_THIRD_PARTY "${CMAKE_CURRENT_LIST_DIR}/../../../../../../third_party")
set(VIRGL "${DEXTOP_THIRD_PARTY}/virglrenderer")
set(EPOXY "${DEXTOP_THIRD_PARTY}/libepoxy")

# libepoxy: GL/EGL dispatch, loading the system libEGL.so and libGLESv2.so.
add_library(dextop_epoxy STATIC
    "${EPOXY}/src/dispatch_common.c"
    "${EPOXY}/src/dispatch_egl.c"
    "${EPOXY}/generated/gl_generated_dispatch.c"
    "${EPOXY}/generated/egl_generated_dispatch.c")
target_include_directories(dextop_epoxy
    PUBLIC "${EPOXY}/include" "${EPOXY}/generated"
    PRIVATE "${EPOXY}/src")
target_compile_definitions(dextop_epoxy PRIVATE _FILE_OFFSET_BITS=64)
target_compile_options(dextop_epoxy PRIVATE -O2 -fPIC -std=gnu99 -fno-strict-aliasing
    -include "${EPOXY}/generated/config.h" -Wno-int-conversion -w)

set(VIRGL_INCLUDES
    "${VIRGL}/generated"
    "${VIRGL}/src"
    "${VIRGL}/src/gallium/include"
    "${VIRGL}/src/gallium/auxiliary"
    "${VIRGL}/src/gallium/auxiliary/util"
    "${VIRGL}/src/mesa"
    "${VIRGL}/src/mesa/compat"
    "${VIRGL}/src/mesa/pipe"
    "${VIRGL}/src/mesa/util"
    "${VIRGL}/src/venus"
    "${VIRGL}/src/drm"
    "${VIRGL}/src/drm/drm-uapi"
    "${VIRGL}/vtest")

add_executable(virgl_server
    # Mesa util
    "${VIRGL}/src/mesa/util/bitscan.c"
    "${VIRGL}/src/mesa/util/hash_table.c"
    "${VIRGL}/src/mesa/util/os_file.c"
    "${VIRGL}/src/mesa/util/os_misc.c"
    "${VIRGL}/src/mesa/util/ralloc.c"
    "${VIRGL}/src/mesa/util/u_cpu_detect.c"
    "${VIRGL}/src/mesa/util/u_debug.c"
    "${VIRGL}/src/mesa/util/u_math.c"
    "${VIRGL}/src/mesa/util/anon_file.c"
    # Gallium auxiliary
    "${VIRGL}/generated/u_format_table.c"
    "${VIRGL}/src/gallium/auxiliary/util/u_format.c"
    "${VIRGL}/src/gallium/auxiliary/util/u_texture.c"
    "${VIRGL}/src/gallium/auxiliary/util/u_hash_table.c"
    "${VIRGL}/src/gallium/auxiliary/util/u_debug_describe.c"
    "${VIRGL}/src/gallium/auxiliary/cso_cache/cso_cache.c"
    "${VIRGL}/src/gallium/auxiliary/cso_cache/cso_hash.c"
    "${VIRGL}/src/gallium/auxiliary/tgsi/tgsi_dump.c"
    "${VIRGL}/src/gallium/auxiliary/tgsi/tgsi_build.c"
    "${VIRGL}/src/gallium/auxiliary/tgsi/tgsi_scan.c"
    "${VIRGL}/src/gallium/auxiliary/tgsi/tgsi_info.c"
    "${VIRGL}/src/gallium/auxiliary/tgsi/tgsi_parse.c"
    "${VIRGL}/src/gallium/auxiliary/tgsi/tgsi_text.c"
    "${VIRGL}/src/gallium/auxiliary/tgsi/tgsi_strings.c"
    "${VIRGL}/src/gallium/auxiliary/tgsi/tgsi_sanity.c"
    "${VIRGL}/src/gallium/auxiliary/tgsi/tgsi_iterate.c"
    "${VIRGL}/src/gallium/auxiliary/tgsi/tgsi_util.c"
    # virgl and vrend
    "${VIRGL}/src/virgl_context.c"
    "${VIRGL}/src/virgl_fence.c"
    "${VIRGL}/src/virgl_resource.c"
    "${VIRGL}/src/virgl_util.c"
    "${VIRGL}/src/virglrenderer.c"
    "${VIRGL}/src/vrend/iov.c"
    "${VIRGL}/src/vrend/vrend_blitter.c"
    "${VIRGL}/src/vrend/vrend_debug.c"
    "${VIRGL}/src/vrend/vrend_decode.c"
    "${VIRGL}/src/vrend/vrend_formats.c"
    "${VIRGL}/src/vrend/vrend_object.c"
    "${VIRGL}/src/vrend/vrend_renderer.c"
    "${VIRGL}/src/vrend/vrend_shader.c"
    "${VIRGL}/src/vrend/vrend_tweaks.c"
    "${VIRGL}/src/vrend/vrend_winsys.c"
    "${VIRGL}/src/vrend/vrend_winsys_egl.c"
    # vtest server
    "${VIRGL}/vtest/threadpool.c"
    "${VIRGL}/vtest/util.c"
    "${VIRGL}/vtest/vtest_shm.c"
    "${VIRGL}/vtest/vtest_server.c"
    "${VIRGL}/vtest/vtest_renderer.c"
    "${VIRGL}/vtest/vtest_main.c")
target_include_directories(virgl_server PRIVATE ${VIRGL_INCLUDES})
target_compile_definitions(virgl_server PRIVATE NDEBUG _FILE_OFFSET_BITS=64 HAVE_CONFIG_H=1)
target_compile_options(virgl_server PRIVATE -O2 -std=gnu11 -fno-strict-aliasing -pthread
    -imacros "${VIRGL}/generated/config.h"
    -Wno-gnu-offsetof-extensions -Wno-gnu-pointer-arith -w)
target_link_libraries(virgl_server PRIVATE dextop_epoxy dl m log)
set_target_properties(virgl_server PROPERTIES PREFIX "lib" OUTPUT_NAME "virgl_server" SUFFIX ".so")

# Socket shim preloaded into container processes. It runs under the guest's
# glibc or musl, so it is built for the GNU target with no libc at all.
add_library(dextop_unixsock SHARED "${CMAKE_CURRENT_LIST_DIR}/../linux_unixsock.c")
target_compile_options(dextop_unixsock PRIVATE
    --target=aarch64-unknown-linux-gnu -O2 -fPIC -ffreestanding -fno-stack-protector -fno-builtin)
# errno and environ resolve against the guest's libc at load time (-z undefs
# overrides the toolchain's --no-undefined). The NDK adds -latomic -lm to every
# link; the Android sysroot satisfies them and --as-needed drops them.
target_link_options(dextop_unixsock PRIVATE
    --target=aarch64-unknown-linux-gnu -nostdlib -fuse-ld=lld
    "-L${CMAKE_SYSROOT}/usr/lib/aarch64-linux-android/${ANDROID_PLATFORM_LEVEL}"
    "-L${CMAKE_SYSROOT}/usr/lib/aarch64-linux-android"
    "LINKER:-z,noexecstack,-z,undefs,--hash-style=both,--as-needed")
