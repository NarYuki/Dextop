#pragma once
#include <stdlib.h>
#include <string.h>

static inline int dextop_deck_runtime(void) {
    const char *mode = getenv("DEXTOP_STEAM_INSIDE");
    return mode && strcmp(mode, "1") == 0;
}
