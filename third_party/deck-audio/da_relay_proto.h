/* DirectAudio relay - the contract between the driver and the relay helper.
 *
 * Copyright 2026 The412Banner
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * WHY THIS EXISTS. The driver's whole premise is an in-process AAudio client:
 * the guest's WASAPI buffers are mixed and handed to Android's audio server
 * from inside the game, no daemon in the path. That is only possible when the
 * game process is a bionic process. Under a glibc runtime (the Linux Steam
 * client, where the game runs inside a proot'd Arch rootfs on Valve's own
 * ARM64 Proton) libaaudio cannot be loaded at all: it is a bionic library and
 * the rootfs's dynamic linker does not know how to bring it into a glibc
 * process. The game side therefore has to stop at a boundary it CAN cross, and
 * the only thing both worlds agree on is the kernel: a unix socket, a memfd and
 * a futex word.
 *
 * SHAPE. One helper process (directaudio-relay, bionic, links libaaudio) runs
 * on the Android side and listens on a unix socket. Each game process that
 * needs audio connects once, sends its launch config (the same knobs the
 * in-process build reads from BANNER_AUDIO_DIRECT_*), and gets back one or two
 * shared rings (memfds passed with SCM_RIGHTS):
 *
 *   render  ring   game (producer) -> helper (consumer) -> AAudio OUTPUT
 *   capture ring   helper (producer, AAudio INPUT) -> game (consumer)
 *
 * The driver keeps its mixer and its per-voice WASAPI rings exactly as in the
 * in-process build; what used to be the AAudio data callback is now a pump
 * thread that keeps the render ring topped up, and the capture callback is a
 * pump that drains the capture ring into the capture voices. Everything that
 * needs an AAudioStream (open, adaptive buffer growth and decay, route-change
 * reopen, the stalled-callback watchdog, the live-config mailbox) moves to the
 * helper unchanged in spirit.
 *
 * COST. One extra hand-off: the ring. The helper's callback pulls a burst from
 * the ring, so the ring must hold at least one burst at every callback; the
 * driver keeps DA_RING_TARGET_BURSTS bursts queued and the helper raises that
 * target when a callback finds the ring short. Everything else - the AAudio
 * buffer, AudioFlinger's own latency - is what the in-process build pays too.
 *
 * WIRE FORMAT. Fixed-width, native-endian, same-machine only. The two sides are
 * different libcs but the same CPU, so no marshalling beyond fixed-size structs.
 * Bump DA_RELAY_VERSION on any layout change; the helper refuses a mismatch.
 */
#ifndef DA_RELAY_PROTO_H
#define DA_RELAY_PROTO_H

#include <stdint.h>
#include <stddef.h>

#define DA_RELAY_VERSION   1
#define DA_RING_MAGIC      0x44415247u   /* 'DARG' */
#define DA_HELLO_MAGIC     0x44414831u   /* 'DAH1' */
#define DA_ACK_MAGIC       0x44414131u   /* 'DAA1' */

/* Socket path. The driver reads BANNER_AUDIO_DIRECT_RELAY; when unset it falls
 * back to $XDG_RUNTIME_DIR/DA_RELAY_DEFAULT_NAME, which a host that already
 * hands its runtime dir into the session gets for free. */
#define DA_RELAY_ENV           "BANNER_AUDIO_DIRECT_RELAY"
#define DA_RELAY_DEFAULT_NAME  "directaudio-relay"

/* Both rings carry float32 interleaved stereo. The render ring is always
 * 48 kHz (the mixer's output rate); the capture ring runs at whatever rate the
 * input stream was granted, published in da_ring.rate, and the driver resamples
 * per voice as the in-process build already does. */
#define DA_RING_RATE       48000
#define DA_RING_CHANNELS   2

/* Render ring sizing. Capacity is the ceiling adaptive growth can reach; the
 * initial target is what the driver keeps queued. Two bursts: the helper's
 * callback consumes one burst and wakes the pump, which then has a full burst
 * period to refill before the next callback. */
#define DA_RING_CAP_MS           250
#define DA_RING_TARGET_BURSTS    2
#define DA_RING_MAX_TARGET_MS    100

/* Capture ring: 200 ms is far more than a game ever leaves queued; on overrun
 * the helper drops the newest block, matching the in-process "guest is not
 * draining" behaviour of dropping data rather than blocking the mic thread. */
#define DA_CAPTURE_RING_MS       200

enum da_ring_state
{
    DA_RING_OPENING = 0,
    DA_RING_PLAYING = 1,
    DA_RING_ERROR   = 2,
};

struct da_ring
{
    uint32_t magic;
    uint32_t version;
    uint32_t cap_frames;      /* ring capacity in frames */
    uint32_t rate;            /* frames per second of the data in this ring */
    uint32_t channels;        /* DA_RING_CHANNELS */
    uint32_t _pad0;

    /* Single producer, single consumer; monotonic frame totals, wrap-safe with
     * unsigned arithmetic. Producer writes widx with RELEASE after the data;
     * consumer writes ridx with RELEASE after it has copied the data out. */
    volatile uint32_t widx;
    volatile uint32_t ridx;

    /* Futex word the CONSUMER of a render ring / PRODUCER of a capture ring
     * bumps after each block, so the other side can sleep on it instead of
     * polling. Waiters always use a timeout, so a missed wake only costs one
     * period. */
    volatile uint32_t wake;

    volatile uint32_t quit;   /* either side: tear down */
    volatile uint32_t state;  /* enum da_ring_state, helper-owned */

    /* Render ring only, helper-owned, read by the driver: */
    volatile int32_t  target_frames;  /* keep at least this many queued */
    volatile int32_t  hw_buf_frames;  /* AAudio buffer size right now (get_latency) */
    volatile int32_t  hw_burst;       /* frames per burst of the output stream */
    volatile uint32_t underruns;      /* callbacks that found fewer than they needed */
    volatile uint32_t cb_count;       /* data callbacks so far (liveness) */
    uint32_t _pad1[3];

    float data[];             /* cap_frames * channels */
};

static inline size_t da_ring_bytes(uint32_t cap_frames)
{
    return sizeof(struct da_ring) + (size_t)cap_frames * DA_RING_CHANNELS * sizeof(float);
}

static inline uint32_t da_ring_avail(const struct da_ring *r)
{
    uint32_t w = __atomic_load_n(&r->widx, __ATOMIC_ACQUIRE);
    uint32_t rd = __atomic_load_n(&r->ridx, __ATOMIC_ACQUIRE);
    return w - rd;
}

static inline uint32_t da_ring_space(const struct da_ring *r)
{
    return r->cap_frames - da_ring_avail(r);
}

/* ---- wire messages ------------------------------------------------------- */

#define DA_HELLO_MIC        0x01u   /* also open an INPUT stream + capture ring */
#define DA_HELLO_EXCLUSIVE  0x02u   /* AAUDIO_SHARING_MODE_EXCLUSIVE requested */
#define DA_HELLO_ADAPTIVE   0x04u
#define DA_HELLO_DECAY      0x08u
#define DA_HELLO_WATCHDOG   0x10u

/* First message on a fresh connection, driver -> helper. Mirrors the launch
 * config the in-process build derives from BANNER_AUDIO_DIRECT_* so a preset
 * means the same thing under both runtimes. */
struct da_hello
{
    uint32_t magic;           /* DA_HELLO_MAGIC */
    uint32_t version;         /* DA_RELAY_VERSION */
    uint32_t flags;           /* DA_HELLO_* */
    int32_t  perf;            /* 0 NONE, 1 LOW_LATENCY, 2 POWER_SAVING (host convention) */
    int32_t  target_ms;       /* AAudio buffer, ms; 0 = use target_frames or the default */
    int32_t  max_ms;          /* adaptive ceiling, ms; 0 = max_frames or the default */
    int32_t  target_frames;   /* frame forms, used only when the ms forms are 0 */
    int32_t  max_frames;
    uint32_t stall_ms;        /* watchdog: callback silent this long => rebuild */
    uint32_t quiet_ms;        /* decay pacing, see the driver */
    uint32_t punish_ms;
    uint32_t max_backoff;
    int32_t  pid;             /* the game process, for the log */
    uint32_t log;             /* BANNER_AUDIO_DIRECT_LOG */
    char     runtime_path[256]; /* BANNER_AUDIO_DIRECT_RUNTIME mailbox, "" = none */
    char     name[64];        /* process name, for the log */
};

#define DA_ACK_MIC_FAILED   0x01u   /* mic asked for, input stream would not open */

/* Reply, helper -> driver, followed by SCM_RIGHTS carrying the ring fds:
 * fd[0] = render ring, fd[1] = capture ring (present iff in_cap_frames > 0). */
struct da_ack
{
    uint32_t magic;           /* DA_ACK_MAGIC */
    int32_t  status;          /* 0 = ok, else an AAudio error code (negative) */
    int32_t  rate;            /* output stream rate (48000) */
    int32_t  burst;           /* output frames per burst */
    int32_t  buf_frames;      /* output buffer size as opened */
    int32_t  out_cap_frames;  /* render ring capacity */
    int32_t  in_cap_frames;   /* capture ring capacity, 0 = no capture ring */
    int32_t  in_rate;         /* rate of the capture ring's data */
    uint32_t flags;           /* DA_ACK_* */
    uint32_t _pad;
};

/* Subsequent messages, driver -> helper, on the same socket. The helper never
 * writes after the ack; everything it has to say is in the ring header. EOF on
 * the socket is how the helper learns the game is gone. */
enum da_msg_type
{
    DA_MSG_MIC_START = 1,     /* first capture voice started: make the mic hot */
    DA_MSG_MIC_STOP  = 2,     /* last capture voice gone: stop (keep open) */
    DA_MSG_PING      = 3,
};

struct da_msg
{
    uint32_t type;
    int32_t  arg;
};

#endif /* DA_RELAY_PROTO_H */
