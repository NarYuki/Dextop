#pragma once
#include <stdint.h>
#define DECK_AHB_MAX_FDS 8
#define DECK_AHB_MAX_INTS 128
#define DECK_AHB_WIRE_VERSION 1
struct deck_ahb_desc {
    uint32_t width, height, layers, format;
    uint64_t usage;
    uint32_t stride, rfu0;
    uint64_t rfu1;
};
struct deck_ahb_request { uint32_t version, operation; struct deck_ahb_desc desc; };
struct deck_ahb_reply {
    int32_t status;
    struct deck_ahb_desc desc;
    int32_t num_fds, num_ints;
    int32_t ints[DECK_AHB_MAX_INTS];
};
