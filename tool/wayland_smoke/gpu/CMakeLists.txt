cmake_minimum_required(VERSION 3.22)
project(dextop_linux_gpu_probe C CXX)

if(NOT ANDROID_ABI STREQUAL "arm64-v8a")
    message(FATAL_ERROR "The Linux GPU probe requires Android arm64-v8a")
endif()
if(NOT EXISTS "${DEXTOP_ADRENOTOOLS_DIR}/CMakeLists.txt")
    message(FATAL_ERROR "Set DEXTOP_ADRENOTOOLS_DIR to the inspected adrenotools source directory")
endif()

add_subdirectory("${DEXTOP_ADRENOTOOLS_DIR}" adrenotools)
add_executable(vulkan-import-probe ../vulkan-import-probe.c ../vulkan-dmabuf-roundtrip.c ../vulkan-dmabuf-surface.c)
target_compile_definitions(vulkan-import-probe PRIVATE
    VK_USE_PLATFORM_ANDROID_KHR HAVE_ADRENOTOOLS HAVE_DMABUF_ROUNDTRIP)
target_compile_options(vulkan-import-probe PRIVATE -Wall -Wextra)
target_link_libraries(vulkan-import-probe adrenotools vulkan dl log)
add_library(vulkan-surface-probe SHARED ../vulkan-surface-jni.c ../vulkan-dmabuf-roundtrip.c ../vulkan-dmabuf-surface.c
    ../../../android/app/src/main/cpp/wayland/gpu_present.c)
target_include_directories(vulkan-surface-probe PRIVATE ../../../android/app/src/main/cpp/wayland)
target_compile_definitions(vulkan-surface-probe PRIVATE VK_USE_PLATFORM_ANDROID_KHR)
target_link_libraries(vulkan-surface-probe adrenotools vulkan dl log android)

add_subdirectory(../../../android/app/src/main/cpp/wayland compositor)
target_sources(dextop_wayland PRIVATE ../../../android/app/src/main/cpp/wayland/gpu_present.c)
target_compile_definitions(dextop_wayland PRIVATE DEXTOP_WAYLAND_GPU VK_USE_PLATFORM_ANDROID_KHR)
target_link_libraries(dextop_wayland adrenotools dl)
