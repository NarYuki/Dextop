# zstd stream decompressor (arm64-v8a only), an executable named libzstd_cat.so
# for nativeLibraryDir; the shell-side Linux service unpacks .tar.zst root
# filesystems with it. Decompression sources from third_party/zstd.

set(ZSTD_LIB "${CMAKE_CURRENT_LIST_DIR}/../../../../../../third_party/zstd/lib")
file(GLOB ZSTD_SOURCES "${ZSTD_LIB}/common/*.c" "${ZSTD_LIB}/decompress/*.c")

add_executable(zstd_cat zstd_cat.c ${ZSTD_SOURCES})
target_include_directories(zstd_cat PRIVATE "${ZSTD_LIB}" "${ZSTD_LIB}/common")
target_compile_definitions(zstd_cat PRIVATE ZSTD_DISABLE_ASM ZSTD_MULTITHREAD=0 ZSTD_LEGACY_SUPPORT=0)
target_compile_options(zstd_cat PRIVATE -O2 -w)
target_link_options(zstd_cat PRIVATE "LINKER:-z,noexecstack")
set_target_properties(zstd_cat PROPERTIES PREFIX "lib" OUTPUT_NAME "zstd_cat" SUFFIX ".so")
