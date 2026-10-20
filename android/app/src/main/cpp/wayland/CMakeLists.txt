# Dextop's Wayland compositor for Linux containers (arm64-v8a only), loaded by
# the shell-side Linux service. libwayland-server and libffi come from
# third_party (see third_party/README.md), built static into this library.

set(DEXTOP_THIRD_PARTY "${CMAKE_CURRENT_LIST_DIR}/../../../../../../third_party")
set(WL "${DEXTOP_THIRD_PARTY}/wayland")
set(FFI "${DEXTOP_THIRD_PARTY}/libffi")
set(WLP "${DEXTOP_THIRD_PARTY}/wayland-protocols/generated")

enable_language(ASM)
add_library(dextop_ffi STATIC
    "${FFI}/src/prep_cif.c" "${FFI}/src/types.c" "${FFI}/src/raw_api.c" "${FFI}/src/java_raw_api.c"
    "${FFI}/src/closures.c" "${FFI}/src/tramp.c" "${FFI}/src/debug.c"
    "${FFI}/src/aarch64/ffi.c" "${FFI}/src/aarch64/sysv.S")
target_include_directories(dextop_ffi PUBLIC "${FFI}/generated" PRIVATE "${FFI}/include" "${FFI}/src/aarch64" "${FFI}/src")
target_compile_definitions(dextop_ffi PRIVATE HAVE_CONFIG_H FFI_STATIC_BUILD)
target_compile_options(dextop_ffi PRIVATE -O2 -fPIC -w)

add_library(dextop_wayland_server STATIC
    "${WL}/src/wayland-server.c" "${WL}/src/wayland-shm.c" "${WL}/src/event-loop.c"
    "${WL}/src/connection.c" "${WL}/src/wayland-os.c" "${WL}/src/wayland-util.c"
    "${WL}/generated/wayland-protocol.c")
target_include_directories(dextop_wayland_server PUBLIC "${WL}/src" "${WL}/generated")
target_compile_options(dextop_wayland_server PRIVATE -O2 -fPIC -w -include "${WL}/generated/config.h")
target_link_libraries(dextop_wayland_server PUBLIC dextop_ffi)

add_library(dextop_wayland SHARED
    compositor.c
    "${WLP}/xdg-shell-protocol.c" "${WLP}/linux-dmabuf-v1-protocol.c"
    "${WLP}/xdg-decoration-unstable-v1-protocol.c" "${WLP}/text-input-unstable-v3-protocol.c"
    "${WLP}/viewporter-protocol.c" "${WLP}/server-decoration-protocol.c"
    "${WLP}/presentation-time-protocol.c"
    "${WLP}/pointer-constraints-unstable-v1-protocol.c"
    "${WLP}/relative-pointer-unstable-v1-protocol.c")
target_include_directories(dextop_wayland PRIVATE "${WLP}" "${CMAKE_CURRENT_LIST_DIR}")
target_compile_options(dextop_wayland PRIVATE -O2 -Wall -Wno-unused-parameter)
target_link_libraries(dextop_wayland dextop_wayland_server android log)

# GPU presentation of dma-buf windows: DroidDeck's DD-Turnip for Android
# (libvulkan_dextop_turnip.so, prebuilt, see third_party/README.md, shipped
# in jniLibs) loaded through adrenotools, which places it under the system
# Vulkan loader so Android surfaces and swapchains work. Without it the
# compositor keeps its CPU path.
set(BUILD_SHARED_LIBS OFF)
add_subdirectory("${DEXTOP_THIRD_PARTY}/adrenotools" adrenotools)
target_sources(dextop_wayland PRIVATE gpu_present.c)
target_compile_definitions(dextop_wayland PRIVATE DEXTOP_WAYLAND_GPU VK_USE_PLATFORM_ANDROID_KHR)
target_link_libraries(dextop_wayland adrenotools dl)
