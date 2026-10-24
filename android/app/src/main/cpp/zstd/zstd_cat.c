/*
 * Decompresses a zstd stream from stdin to stdout, for the Linux service to
 * unpack .tar.zst root filesystems (DroidDeck's runtime). Built as
 * libzstd_cat.so so that the APK ships it in nativeLibraryDir, from where the
 * shell user may execute it.
 */
#include <errno.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <unistd.h>

#include "zstd.h"

static int write_all(const void *data, size_t size) {
    const char *p = data;
    while (size) {
        ssize_t n = write(STDOUT_FILENO, p, size);
        if (n < 0) {
            if (errno == EINTR) continue;
            return -1;
        }
        p += n;
        size -= (size_t)n;
    }
    return 0;
}

int main(void) {
    ZSTD_DStream *stream = ZSTD_createDStream();
    if (!stream) return 2;
    size_t in_size = ZSTD_DStreamInSize(), out_size = ZSTD_DStreamOutSize();
    void *in_buffer = malloc(in_size), *out_buffer = malloc(out_size);
    if (!in_buffer || !out_buffer) return 2;
    size_t last = 0;
    for (;;) {
        ssize_t got = read(STDIN_FILENO, in_buffer, in_size);
        if (got < 0) {
            if (errno == EINTR) continue;
            fprintf(stderr, "zstd_cat: read: %s\n", strerror(errno));
            return 1;
        }
        if (got == 0) break;
        ZSTD_inBuffer input = {in_buffer, (size_t)got, 0};
        while (input.pos < input.size) {
            ZSTD_outBuffer output = {out_buffer, out_size, 0};
            last = ZSTD_decompressStream(stream, &output, &input);
            if (ZSTD_isError(last)) {
                fprintf(stderr, "zstd_cat: %s\n", ZSTD_getErrorName(last));
                return 1;
            }
            if (write_all(out_buffer, output.pos)) {
                fprintf(stderr, "zstd_cat: write: %s\n", strerror(errno));
                return 1;
            }
        }
    }
    if (last != 0) {
        fprintf(stderr, "zstd_cat: truncated input\n");
        return 1;
    }
    ZSTD_freeDStream(stream);
    return 0;
}
