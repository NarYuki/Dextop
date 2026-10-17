cmake_minimum_required(VERSION 3.22.1)

project(dextop_input LANGUAGES C CXX ASM)

add_library(
    dextop_input
    SHARED
    native_input.cpp
)

target_compile_features(dextop_input PRIVATE cxx_std_20)

target_link_libraries(
    dextop_input
    android
    log
)

# Linux containers run on arm64 devices only.
if(ANDROID_ABI STREQUAL "arm64-v8a")
    add_subdirectory(proot)
    # VirGL GPU acceleration server and the guest socket shim.
    add_subdirectory(virgl)

    # Framebuffer-to-Surface copies for Linux windows (loaded by the shell-side service).
    add_library(dextop_linux SHARED linux_bridge.cpp)
    target_compile_features(dextop_linux PRIVATE cxx_std_20)
    target_compile_options(dextop_linux PRIVATE -O3)
    target_link_libraries(dextop_linux android log)
endif()
