/*
 * module-directaudio-native-sink: a PulseAudio 13 sink that is DirectAudio's AAudio engine,
 * in-process. For the native Linux Steam client on Android (DroidDeck, Bannerlator's Linux
 * session), whose sound can only ever reach a PulseAudio daemon: this module is where that
 * daemon's mixed output meets Android, one step away from AudioFlinger, with the same engine the
 * game driver has - adaptive device buffer that grows a burst on every xrun, optional decay with a
 * remembered floor, a primed ring with fades instead of clicks, route-change / error reopen on a
 * fresh stream, and a stall watchdog.
 *
 * It is the relay helper's per-client OUTPUT engine (directaudio-relay.c) with the socket and the
 * shared-memory ring replaced by a ring in this process, fed by the sink's IO thread exactly as
 * module-directaudio-sink feeds the relay. Compared with that module there is no second process
 * and no 40 ms proot safety margin: the ring here only has to cover the IO thread's own
 * scheduling jitter, so it starts at two bursts and grows only if a callback finds it short.
 *
 * The daemon must run on the Android side (the host app's process), not inside proot, for AAudio
 * to be reachable at all - which is where both host apps already run it.
 *
 * Field fixes carried from the relay (MaxsTechReview, Droid-Deck/DroidDeck#338): prime before
 * playing, fade on an underrun, one consumer per ring across a reopen, a device buffer of at
 * least two bursts.
 *
 * SPDX-License-Identifier: LGPL-2.1-or-later
 */
#ifdef HAVE_CONFIG_H
#include <config.h>
#endif

#include <errno.h>
#include <pthread.h>
#include <stdlib.h>
#include <string.h>
#include <sys/resource.h>
#include <sys/syscall.h>
#include <time.h>
#include <unistd.h>

#include <aaudio/AAudio.h>

#include <pulse/rtclock.h>
#include <pulse/timeval.h>
#include <pulse/util.h>
#include <pulse/xmalloc.h>

#include <pulsecore/core-util.h>
#include <pulsecore/log.h>
#include <pulsecore/macro.h>
#include <pulsecore/modargs.h>
#include <pulsecore/module.h>
#include <pulsecore/rtpoll.h>
#include <pulsecore/sink.h>
#include <pulsecore/thread-mq.h>
#include <pulsecore/thread.h>

#include "da_relay_proto.h"   /* struct da_ring + the ring helpers; nothing of the socket protocol */

PA_MODULE_AUTHOR("The412Banner");
PA_MODULE_DESCRIPTION("Android audio output: DirectAudio's AAudio engine, in-process");
PA_MODULE_VERSION(PACKAGE_VERSION);
PA_MODULE_LOAD_ONCE(false);
PA_MODULE_USAGE(
        "sink_name=<name for the sink> "
        "sink_properties=<properties for the sink> "
        "volume=<initial volume, linear, 1.0 = full> "
        "performance_mode=<0 none, 1 low latency, 2 power saving> "
        "buffer_ms=<device buffer to start with, ms; rounded up to whole bursts, never below two> "
        "max_ms=<largest the device buffer may grow to, ms> "
        "ring_ms=<queue kept ahead of the device, ms; never below two bursts> "
        "adaptive=<grow the buffers after underruns: 0 or 1> "
        "decay=<shrink the device buffer again after a quiet spell: 0 or 1> "
        "limiter=<soft-knee limiter on the way out: 0 or 1> "
        "watchdog=<rebuild the stream when its callback stalls: 0 or 1>");

#define DEFAULT_SINK_NAME "DirectAudio"
#define STATS_EVERY_USEC (30 * PA_USEC_PER_SEC)
#define WATCHDOG_TICK_USEC (1 * PA_USEC_PER_SEC)
#define AUDIO_NICE (-16)

/* ---- tunables: the in-process driver's, same defaults ----------------------- */
#define DA_KNEE               0.75f
#define DA_DEFAULT_MS         12
#define DA_DEFAULT_MAX_MS     100
#define DA_DEFAULT_RING_MS    12
#define DA_RING_CAP_MS_LOCAL  250
#define DA_FREEZE_GAP_NS      500000000ull
#define DA_CB_STALL_NS        1000000000ull
#define DA_DECAY_QUIET_NS     10000000000ull
#define DA_DECAY_PUNISH_NS    5000000000ull
#define DA_DECAY_MAX_BACKOFF  32
#define DA_MIN_BUFFER_BURSTS  2
#define DA_FADE_FRAMES        64

static const char* const valid_modargs[] = {
    "sink_name", "sink_properties", "volume", "performance_mode", "buffer_ms", "max_ms", "ring_ms",
    "adaptive", "decay", "limiter", "watchdog", NULL
};

struct userdata {
    pa_core *core;
    pa_module *module;
    pa_sink *sink;

    pa_thread *thread;
    pa_thread_mq thread_mq;
    pa_rtpoll *rtpoll;

    pa_sample_spec ss;
    size_t frame_size;

    /* configuration */
    aaudio_performance_mode_t perf;
    int32_t target_ms, max_ms, ring_ms;
    bool adaptive, decay, limiter, watchdog_on;

    /* the ring: this process's, same layout as the relay's so the engine is shared */
    struct da_ring *ring;
    int32_t burst;
    int32_t ring_max_target;

    /* the one OUTPUT stream and its adaptive state (the relay's struct client, minus the socket) */
    AAudioStream *aq;
    pthread_mutex_t lock;
    int32_t last_xrun;
    uint64_t last_cb_ns, last_xrun_ns, last_decay_ns, wd_last_tick_ns;
    int32_t base_buf_frames, decay_floor;
    unsigned int decay_backoff, cb_count, wd_last_cb;
    int reopen_running, reopen_redo;
    int consuming, primed, starved;
    int suspended;
    volatile int dead;

    /* what the IO thread last logged, so engine changes made on the AAudio thread get a line */
    int32_t seen_target, seen_hw_buf;
    uint32_t seen_underruns;
    pa_usec_t next_watchdog, settled_at, stats_at;
    unsigned int opens;
};

static uint64_t now_ns(void) {
    struct timespec ts;
    clock_gettime(CLOCK_MONOTONIC, &ts);
    return (uint64_t) ts.tv_sec * 1000000000ull + (uint64_t) ts.tv_nsec;
}

static void request_reopen(struct userdata *u, const char *why);

/* ---- the engine: the relay's out_cb / open_output / reopen / watchdog, on a local ring ---- */

static void fade(float *p, uint32_t frames, int in) {
    uint32_t i, ch;

    for (i = 0; i < frames; i++) {
        float g = (float) (in ? i + 1 : frames - i) / (float) (frames + 1);

        for (ch = 0; ch < DA_RING_CHANNELS; ch++)
            p[(size_t) i * DA_RING_CHANNELS + ch] *= g;
    }
}

/* The driver's soft-knee limiter: below DA_KNEE the signal is untouched, so ordinary material is
 * bit-identical; above it a smooth knee folds the overshoot back under full scale. */
static void limit(float *p, uint32_t samples) {
    uint32_t i;

    for (i = 0; i < samples; i++) {
        float x = p[i], a = x < 0.0f ? -x : x;

        if (a > DA_KNEE) {
            float t = (a - DA_KNEE) / (1.0f - DA_KNEE);
            float y = DA_KNEE + (1.0f - DA_KNEE) * (t / (1.0f + t));

            p[i] = x < 0.0f ? -y : y;
        }
    }
}

static aaudio_data_callback_result_t out_cb(AAudioStream *aq, void *user, void *audioData, int32_t numFrames) {
    struct userdata *u = user;
    struct da_ring *r = u->ring;
    float *out = audioData;
    AAudioStream *live = __atomic_load_n(&u->aq, __ATOMIC_SEQ_CST);
    uint32_t cap = r->cap_frames;
    uint32_t frames = (uint32_t) numFrames;
    uint32_t ridx, avail, n = 0, i;
    int fade_in = 0, short_read = 0, starving = 0;
    uint64_t now;

    /* Only the promoted stream reads the ring; an outgoing one during a reopen plays silence. */
    if ((live && aq != live) || __atomic_exchange_n(&u->consuming, 1, __ATOMIC_ACQUIRE)) {
        memset(out, 0, (size_t) frames * DA_RING_CHANNELS * sizeof(float));
        return AAUDIO_CALLBACK_RESULT_CONTINUE;
    }

    ridx = __atomic_load_n(&r->ridx, __ATOMIC_ACQUIRE);
    avail = __atomic_load_n(&r->widx, __ATOMIC_ACQUIRE) - ridx;
    if (!u->primed) {
        uint32_t start = (uint32_t) __atomic_load_n(&r->target_frames, __ATOMIC_ACQUIRE);

        if (start < frames) start = frames;
        if (start > cap) start = cap;
        u->primed = fade_in = avail >= start;
        if (u->primed) u->starved = 0;
        else starving = u->starved;
    }
    if (u->primed) {
        uint32_t slot = ridx % cap, first;

        n = frames < avail ? frames : avail;
        first = cap - slot < n ? cap - slot : n;
        memcpy(out, &r->data[(size_t) slot * DA_RING_CHANNELS], (size_t) first * DA_RING_CHANNELS * sizeof(float));
        if (n > first)
            memcpy(out + (size_t) first * DA_RING_CHANNELS, r->data, (size_t) (n - first) * DA_RING_CHANNELS * sizeof(float));
        if (u->limiter)
            limit(out, n * DA_RING_CHANNELS);
        if (fade_in)
            fade(out, n < DA_FADE_FRAMES ? n : DA_FADE_FRAMES, 1);
        if (n < frames) {
            uint32_t tail = n < DA_FADE_FRAMES ? n : DA_FADE_FRAMES;

            fade(out + (size_t) (n - tail) * DA_RING_CHANNELS, tail, 0);
            u->primed = 0;
            u->starved = 1;
            short_read = 1;
        }
        __atomic_store_n(&r->ridx, ridx + n, __ATOMIC_RELEASE);
    }
    for (i = n * DA_RING_CHANNELS; i < frames * DA_RING_CHANNELS; i++)
        out[i] = 0.0f;
    __atomic_store_n(&u->consuming, 0, __ATOMIC_RELEASE);

    now = now_ns();
    {
        uint64_t prev = __atomic_exchange_n(&u->last_cb_ns, now, __ATOMIC_SEQ_CST);

        if (prev && now - prev > DA_FREEZE_GAP_NS) {
            u->last_xrun = AAudioStream_getXRunCount(aq);
            u->last_xrun_ns = now;
        }
    }
    __atomic_add_fetch(&u->cb_count, 1, __ATOMIC_SEQ_CST);
    __atomic_store_n(&r->cb_count, u->cb_count, __ATOMIC_RELAXED);

    /* The ring's own adaptive step: a short ring means the IO thread did not get scheduled in
     * time - an xrun one stage earlier - so keep one more burst queued. Only grows. */
    if (short_read)
        __atomic_add_fetch(&r->underruns, 1, __ATOMIC_RELAXED);
    if ((short_read || starving) && u->adaptive) {
        int32_t burst = AAudioStream_getFramesPerBurst(aq);
        int32_t cur = __atomic_load_n(&r->target_frames, __ATOMIC_RELAXED);
        int32_t want = cur + (burst > 0 ? burst : numFrames);

        if (want > u->ring_max_target) want = u->ring_max_target;
        if (want > cur)
            __atomic_store_n(&r->target_frames, want, __ATOMIC_RELEASE);
    }

    /* The device buffer: grow a burst per xrun; decay a burst after a quiet spell, never below
     * the floor a punished decay taught us. */
    if (u->adaptive) {
        int32_t xruns = AAudioStream_getXRunCount(aq);
        int32_t burst = AAudioStream_getFramesPerBurst(aq);
        int32_t cur = AAudioStream_getBufferSizeInFrames(aq);

        if (xruns > u->last_xrun) {
            int32_t capf = AAudioStream_getBufferCapacityInFrames(aq);
            int32_t want = cur + (burst > 0 ? burst : 1);
            int32_t max_frames = u->max_ms > 0 ? u->max_ms * DA_RING_RATE / 1000 : 0;

            if (max_frames > 0 && want > max_frames) want = max_frames;
            if (want > capf) want = capf;
            if (want > cur) AAudioStream_setBufferSizeInFrames(aq, want);

            if (u->decay && u->last_decay_ns && now - u->last_decay_ns < DA_DECAY_PUNISH_NS) {
                if (want > u->decay_floor) u->decay_floor = want;
                if (u->decay_backoff < DA_DECAY_MAX_BACKOFF) u->decay_backoff *= 2;
            }
            u->last_xrun = xruns;
            u->last_xrun_ns = now;
            __atomic_store_n(&r->hw_buf_frames, AAudioStream_getBufferSizeInFrames(aq), __ATOMIC_RELAXED);
        } else if (u->decay && burst > 0) {
            uint64_t quiet = DA_DECAY_QUIET_NS * u->decay_backoff;
            int32_t floor = u->base_buf_frames;

            if (u->decay_floor > floor) floor = u->decay_floor;
            if (cur > floor && u->last_xrun_ns && now - u->last_xrun_ns > quiet &&
                (!u->last_decay_ns || now - u->last_decay_ns > quiet)) {
                int32_t want = cur - burst;

                if (want < floor) want = floor;
                if (want < cur) {
                    AAudioStream_setBufferSizeInFrames(aq, want);
                    u->last_decay_ns = now;
                    __atomic_store_n(&r->hw_buf_frames, AAudioStream_getBufferSizeInFrames(aq), __ATOMIC_RELAXED);
                }
            }
        }
    }

    return AAUDIO_CALLBACK_RESULT_CONTINUE;
}

static void out_error_cb(AAudioStream *aq, void *user, aaudio_result_t error) {
    struct userdata *u = user;

    if (aq != __atomic_load_n(&u->aq, __ATOMIC_SEQ_CST)) return;
    if (__atomic_load_n(&u->suspended, __ATOMIC_SEQ_CST)) return;
    /* A disconnected stream (headphones plugged or unplugged, a Bluetooth route coming or going)
     * can never be restarted: it has to be closed and a new one opened on the current route. */
    if (error != AAUDIO_ERROR_DISCONNECTED &&
        error != AAUDIO_ERROR_INVALID_STATE &&
        error != AAUDIO_ERROR_INVALID_HANDLE &&
        error != AAUDIO_ERROR_TIMEOUT)
        return;
    request_reopen(u, "stream error");
}

/* 48 kHz / float / stereo, buffer from the ms form rounded up to a burst and never below two,
 * capacity from the ceiling. The ring target floor is two bursts or ring_ms, whichever is more. */
static aaudio_result_t open_output(struct userdata *u, AAudioStream **out) {
    AAudioStreamBuilder *builder = NULL;
    AAudioStream *aq = NULL;
    aaudio_result_t res;
    int32_t capf, burst, want;

    res = AAudio_createStreamBuilder(&builder);
    if (res != AAUDIO_OK || !builder) return res != AAUDIO_OK ? res : AAUDIO_ERROR_NO_MEMORY;

    AAudioStreamBuilder_setDirection(builder, AAUDIO_DIRECTION_OUTPUT);
    AAudioStreamBuilder_setSharingMode(builder, AAUDIO_SHARING_MODE_SHARED);
    AAudioStreamBuilder_setPerformanceMode(builder, u->perf);
    AAudioStreamBuilder_setUsage(builder, AAUDIO_USAGE_GAME);
    AAudioStreamBuilder_setFormat(builder, AAUDIO_FORMAT_PCM_FLOAT);
    AAudioStreamBuilder_setChannelCount(builder, DA_RING_CHANNELS);
    AAudioStreamBuilder_setSampleRate(builder, DA_RING_RATE);
    AAudioStreamBuilder_setDataCallback(builder, out_cb, u);
    AAudioStreamBuilder_setErrorCallback(builder, out_error_cb, u);
    AAudioStreamBuilder_setBufferCapacityInFrames(builder, (u->max_ms > 0 ? u->max_ms : DA_DEFAULT_MAX_MS) * DA_RING_RATE / 1000);

    res = AAudioStreamBuilder_openStream(builder, &aq);
    AAudioStreamBuilder_delete(builder);
    if (res != AAUDIO_OK || !aq) return res != AAUDIO_OK ? res : AAUDIO_ERROR_INTERNAL;

    capf = AAudioStream_getBufferCapacityInFrames(aq);
    burst = AAudioStream_getFramesPerBurst(aq);
    want = (u->target_ms > 0 ? u->target_ms : DA_DEFAULT_MS) * DA_RING_RATE / 1000;
    if (burst > 0) {
        int32_t nb = (want + burst - 1) / burst;
        want = (nb < 1 ? 1 : nb) * burst;
        if (want < DA_MIN_BUFFER_BURSTS * burst) want = DA_MIN_BUFFER_BURSTS * burst;
    }
    if (want > capf) want = capf;
    if (want > 0) AAudioStream_setBufferSizeInFrames(aq, want);

    if (burst <= 0) burst = 192;
    u->burst = burst;
    u->ring_max_target = DA_RING_MAX_TARGET_MS * DA_RING_RATE / 1000;
    if (u->ring_max_target > (int32_t) u->ring->cap_frames) u->ring_max_target = (int32_t) u->ring->cap_frames;
    {
        int32_t floor = DA_RING_TARGET_BURSTS * burst;
        int32_t asked = (u->ring_ms > 0 ? u->ring_ms : DA_DEFAULT_RING_MS) * DA_RING_RATE / 1000;

        if (floor < asked) floor = asked;
        if (floor > u->ring_max_target) floor = u->ring_max_target;
        if (__atomic_load_n(&u->ring->target_frames, __ATOMIC_ACQUIRE) < floor)
            __atomic_store_n(&u->ring->target_frames, floor, __ATOMIC_RELEASE);
    }
    __atomic_store_n(&u->ring->hw_burst, burst, __ATOMIC_RELAXED);

    u->last_xrun = AAudioStream_getXRunCount(aq);
    u->base_buf_frames = AAudioStream_getBufferSizeInFrames(aq);
    u->decay_floor = 0;
    u->decay_backoff = 1;
    u->last_xrun_ns = now_ns();
    u->last_decay_ns = 0;
    __atomic_store_n(&u->ring->hw_buf_frames, u->base_buf_frames, __ATOMIC_RELAXED);

    pa_log("directaudio-native-sink: open #%u: device buffer %d frames (%d ms), burst %d, capacity %d, performance mode %d (asked %d), ring target %d frames - Android adds its own output latency on top",
           ++u->opens, u->base_buf_frames, u->base_buf_frames * 1000 / DA_RING_RATE, AAudioStream_getFramesPerBurst(aq),
           capf, AAudioStream_getPerformanceMode(aq), u->perf, (int) u->ring->target_frames);

    res = AAudioStream_requestStart(aq);
    if (res != AAUDIO_OK) { AAudioStream_close(aq); return res; }

    __atomic_store_n(&u->last_cb_ns, now_ns(), __ATOMIC_SEQ_CST);
    u->wd_last_tick_ns = 0;
    __atomic_store_n(&u->ring->state, DA_RING_PLAYING, __ATOMIC_RELEASE);
    *out = aq;
    return AAUDIO_OK;
}

static void *reopen_thread(void *user) {
    struct userdata *u = user;

    for (;;) {
        AAudioStream *old = NULL, *neu = NULL;
        int expected = 0;

        __atomic_store_n(&u->reopen_redo, 0, __ATOMIC_SEQ_CST);
        if (u->dead) break;

        if (open_output(u, &neu) == AAUDIO_OK) {
            pthread_mutex_lock(&u->lock);
            old = u->aq;
            __atomic_store_n(&u->aq, neu, __ATOMIC_SEQ_CST);
            if (__atomic_load_n(&u->suspended, __ATOMIC_SEQ_CST))
                AAudioStream_requestStop(neu);
            pthread_mutex_unlock(&u->lock);
        } else
            pa_log("directaudio-native-sink: reopen failed; keeping the old stream");

        if (old) {
            AAudioStream_requestStop(old);
            AAudioStream_close(old);
            pa_log("directaudio-native-sink: reopened the output on the current route");
        }

        if (__atomic_load_n(&u->reopen_redo, __ATOMIC_SEQ_CST)) { usleep(20000); continue; }
        __atomic_store_n(&u->reopen_running, 0, __ATOMIC_SEQ_CST);
        if (!__atomic_load_n(&u->reopen_redo, __ATOMIC_SEQ_CST)) break;
        if (!__atomic_compare_exchange_n(&u->reopen_running, &expected, 1, 0, __ATOMIC_SEQ_CST, __ATOMIC_SEQ_CST))
            break;
    }
    return NULL;
}

static void request_reopen(struct userdata *u, const char *why) {
    int expected = 0;

    __atomic_store_n(&u->reopen_redo, 1, __ATOMIC_SEQ_CST);
    if (__atomic_compare_exchange_n(&u->reopen_running, &expected, 1, 0, __ATOMIC_SEQ_CST, __ATOMIC_SEQ_CST)) {
        pthread_t th;

        pa_log("directaudio-native-sink: reopen: %s", why);
        if (pthread_create(&th, NULL, reopen_thread, u))
            __atomic_store_n(&u->reopen_running, 0, __ATOMIC_SEQ_CST);
        else
            pthread_detach(th);
    }
}

/* Ticked once a second from the IO thread: a callback silent for a second is a dead stream. */
static void watchdog(struct userdata *u) {
    uint64_t now = now_ns(), last_cb, prev_tick;
    unsigned int cb;

    if (!u->watchdog_on) return;
    if (!__atomic_load_n(&u->aq, __ATOMIC_SEQ_CST)) return;

    prev_tick = u->wd_last_tick_ns;
    u->wd_last_tick_ns = now;
    cb = __atomic_load_n(&u->cb_count, __ATOMIC_SEQ_CST);

    if (!prev_tick || now - prev_tick > 2 * DA_FREEZE_GAP_NS + 1000000000ull) {
        u->wd_last_cb = cb;
        __atomic_store_n(&u->last_cb_ns, now, __ATOMIC_SEQ_CST);
        return;
    }
    if (cb != u->wd_last_cb) { u->wd_last_cb = cb; return; }

    last_cb = __atomic_load_n(&u->last_cb_ns, __ATOMIC_SEQ_CST);
    if (!last_cb || now - last_cb < DA_CB_STALL_NS) return;

    __atomic_store_n(&u->last_cb_ns, now, __ATOMIC_SEQ_CST);
    request_reopen(u, "data callback stalled");
}

static void close_output(struct userdata *u) {
    AAudioStream *aq;

    u->dead = 1;
    pthread_mutex_lock(&u->lock);
    aq = u->aq;
    __atomic_store_n(&u->aq, NULL, __ATOMIC_SEQ_CST);
    pthread_mutex_unlock(&u->lock);
    if (aq) {
        AAudioStream_requestStop(aq);
        AAudioStream_close(aq);
    }
    /* A reopen still in flight sees u->dead and stops; give it a moment to let go of u. */
    while (__atomic_load_n(&u->reopen_running, __ATOMIC_SEQ_CST))
        usleep(10000);
}

/* ---- the PulseAudio side: module-directaudio-sink's producer, on the local ring ---- */

static pa_usec_t frames_to_usec(const struct userdata *u, int64_t frames) {
    return (pa_usec_t) (frames * (int64_t) PA_USEC_PER_SEC / (int64_t) u->ss.rate);
}

static pa_usec_t sink_latency(struct userdata *u) {
    return frames_to_usec(u, (int64_t) da_ring_avail(u->ring) + (u->ring->hw_buf_frames > 0 ? u->ring->hw_buf_frames : 0));
}

static void produce(struct userdata *u, uint32_t frames) {
    struct da_ring *r = u->ring;
    uint32_t w = __atomic_load_n(&r->widx, __ATOMIC_ACQUIRE);
    uint32_t pos = w % r->cap_frames;
    uint32_t first = PA_MIN(frames, r->cap_frames - pos);
    float *dst = r->data + (size_t) pos * DA_RING_CHANNELS;
    size_t bytes = (size_t) frames * u->frame_size;

    if (PA_SINK_IS_OPENED(u->sink->thread_info.state)) {
        pa_memchunk chunk;
        const uint8_t *src;

        if (u->sink->thread_info.rewind_requested)
            pa_sink_process_rewind(u->sink, 0);
        pa_sink_render_full(u->sink, bytes, &chunk);
        src = pa_memblock_acquire_chunk(&chunk);
        memcpy(dst, src, (size_t) first * u->frame_size);
        if (frames > first)
            memcpy(r->data, src + (size_t) first * u->frame_size, (size_t) (frames - first) * u->frame_size);
        pa_memblock_release(chunk.memblock);
        pa_memblock_unref(chunk.memblock);
    } else {
        memset(dst, 0, (size_t) first * u->frame_size);
        if (frames > first)
            memset(r->data, 0, (size_t) (frames - first) * u->frame_size);
    }
    __atomic_store_n(&r->widx, w + frames, __ATOMIC_RELEASE);
}

/* Tops the ring up to the engine's target and narrates what the engine changed. */
static void service_ring(struct userdata *u) {
    struct da_ring *r = u->ring;
    uint32_t avail, space;
    int32_t target = __atomic_load_n(&r->target_frames, __ATOMIC_ACQUIRE);
    int32_t hw = __atomic_load_n(&r->hw_buf_frames, __ATOMIC_RELAXED);
    uint32_t under = __atomic_load_n(&r->underruns, __ATOMIC_RELAXED);
    pa_usec_t now = pa_rtclock_now();

    if (target <= 0) target = 2 * u->burst;
    avail = da_ring_avail(r);
    space = da_ring_space(r);
    if (avail < (uint32_t) target) {
        uint32_t want = PA_MIN((uint32_t) target - avail, space);

        if (want > 0)
            produce(u, want);
    }
    if (target != u->seen_target || hw != u->seen_hw_buf) {
        pa_sink_set_max_request_within_thread(u->sink, (size_t) target * u->frame_size);
        pa_sink_set_fixed_latency_within_thread(u->sink, frames_to_usec(u, (int64_t) target + (hw > 0 ? hw : 0)));
    }
    if (target != u->seen_target) {
        if (u->seen_target)
            pa_log("directaudio-native-sink: ring grow: target %d -> %d frames (%d ms)", u->seen_target, target, target * 1000 / DA_RING_RATE);
        u->seen_target = target;
    }
    if (hw != u->seen_hw_buf) {
        if (u->seen_hw_buf)
            pa_log("directaudio-native-sink: device buffer %d -> %d frames (%d ms)", u->seen_hw_buf, hw, hw * 1000 / DA_RING_RATE);
        u->seen_hw_buf = hw;
    }
    if (u->settled_at && now >= u->settled_at) {
        pa_log("directaudio-native-sink: after 10 s: device buffer %d frames, ring target %d, ring underruns %u, stream xruns %d",
               hw, target, under, u->aq ? AAudioStream_getXRunCount(u->aq) : -1);
        u->settled_at = 0;
    }
    if (under != u->seen_underruns && now - u->stats_at >= STATS_EVERY_USEC) {
        pa_log("directaudio-native-sink: %u ring underrun(s) so far, target %d frames, device buffer %d", under, target, hw);
        u->seen_underruns = under;
        u->stats_at = now;
    }
    if (now >= u->next_watchdog) {
        watchdog(u);
        u->next_watchdog = now + WATCHDOG_TICK_USEC;
    }
}

static int sink_process_msg(pa_msgobject *o, int code, void *data, int64_t offset, pa_memchunk *chunk) {
    struct userdata *u = PA_SINK(o)->userdata;

    switch (code) {
        case PA_SINK_MESSAGE_GET_LATENCY:
            *((pa_usec_t*) data) = sink_latency(u);
            return 0;
    }
    return pa_sink_process_msg(o, code, data, offset, chunk);
}

/* A started AAudio stream keeps audioserver's AudioMix wake lock held, so the device never
 * suspends while the session sleeps: stop it, and the IO thread's timer, until the sink reopens. */
static int sink_set_state_in_io_thread_cb(pa_sink *s, pa_sink_state_t new_state, pa_suspend_cause_t new_suspend_cause) {
    struct userdata *u = s->userdata;
    aaudio_result_t res = AAUDIO_OK;

    if (new_state == PA_SINK_SUSPENDED && !u->suspended) {
        pthread_mutex_lock(&u->lock);
        __atomic_store_n(&u->suspended, 1, __ATOMIC_SEQ_CST);
        if (u->aq) AAudioStream_requestStop(u->aq);
        pthread_mutex_unlock(&u->lock);
        pa_rtpoll_set_timer_disabled(u->rtpoll);
        pa_log("directaudio-native-sink: suspended; output stopped");
    } else if (PA_SINK_IS_OPENED(new_state) && u->suspended) {
        pthread_mutex_lock(&u->lock);
        __atomic_store_n(&u->suspended, 0, __ATOMIC_SEQ_CST);
        u->primed = 0;
        __atomic_store_n(&u->last_cb_ns, now_ns(), __ATOMIC_SEQ_CST);
        u->wd_last_tick_ns = 0;
        if (u->aq) res = AAudioStream_requestStart(u->aq);
        pthread_mutex_unlock(&u->lock);
        if (res != AAUDIO_OK || !u->aq) request_reopen(u, "resume");
        pa_rtpoll_set_timer_relative(u->rtpoll, 0);
        pa_log("directaudio-native-sink: resumed");
    }
    return 0;
}

static void thread_func(void *userdata) {
    struct userdata *u = userdata;

    pa_log_debug("IO thread starting");
    pa_thread_mq_install(&u->thread_mq);
    if (setpriority(PRIO_PROCESS, (id_t) syscall(SYS_gettid), AUDIO_NICE) < 0)
        pa_log_debug("directaudio-native-sink: could not raise the IO thread's priority (%s)", strerror(errno));
    if (u->core->realtime_scheduling)
        pa_thread_make_realtime(u->core->realtime_priority);
    pa_rtpoll_set_timer_relative(u->rtpoll, 0);

    for (;;) {
        int ret;

        /* Half a burst between visits, never blocking on the stream: clients' audio reaches this
         * thread as messages pa_rtpoll_run handles, and a thread stuck in a write or a wait lets
         * them run dry (the crackle #338 fixed). */
        if (!u->suspended) {
            service_ring(u);
            pa_rtpoll_set_timer_relative(u->rtpoll, PA_MAX(frames_to_usec(u, u->burst) / 2, PA_USEC_PER_MSEC));
        }

        if ((ret = pa_rtpoll_run(u->rtpoll)) < 0)
            goto fail;
        if (ret == 0)
            goto finish;
    }

fail:
    pa_asyncmsgq_post(u->thread_mq.outq, PA_MSGOBJECT(u->core), PA_CORE_MESSAGE_UNLOAD_MODULE, u->module, 0, NULL, NULL);
    pa_asyncmsgq_wait_for(u->thread_mq.inq, PA_MESSAGE_SHUTDOWN);

finish:
    pa_log_debug("IO thread shutting down");
}

void pa__done(pa_module *m);

int pa__init(pa_module *m) {
    struct userdata *u = NULL;
    pa_modargs *ma = NULL;
    pa_sink_new_data data;
    pa_channel_map map;
    pa_cvolume volume;
    uint32_t perf = 1, buffer_ms = DA_DEFAULT_MS, max_ms = DA_DEFAULT_MAX_MS, ring_ms = DA_DEFAULT_RING_MS;
    double linear_volume = 1.0;
    bool adaptive = true, decay = false, limiter = true, watchdog_on = true;
    AAudioStream *aq = NULL;
    aaudio_result_t res;
    uint32_t cap_frames;

    pa_assert(m);

    if (!(ma = pa_modargs_new(m->argument, valid_modargs))) {
        pa_log("Failed to parse module arguments.");
        goto fail;
    }
    if (pa_modargs_get_value_u32(ma, "performance_mode", &perf) < 0 || perf > 2) {
        pa_log("performance_mode must be 0, 1 or 2");
        goto fail;
    }
    if (pa_modargs_get_value_u32(ma, "buffer_ms", &buffer_ms) < 0 || buffer_ms == 0 || buffer_ms > 200) {
        pa_log("buffer_ms must be between 1 and 200");
        goto fail;
    }
    if (pa_modargs_get_value_u32(ma, "max_ms", &max_ms) < 0 || max_ms < buffer_ms || max_ms > 250) {
        pa_log("max_ms must be between buffer_ms and 250");
        goto fail;
    }
    if (pa_modargs_get_value_u32(ma, "ring_ms", &ring_ms) < 0 || ring_ms == 0 || ring_ms > 100) {
        pa_log("ring_ms must be between 1 and 100");
        goto fail;
    }
    if (pa_modargs_get_value_boolean(ma, "adaptive", &adaptive) < 0 ||
        pa_modargs_get_value_boolean(ma, "decay", &decay) < 0 ||
        pa_modargs_get_value_boolean(ma, "limiter", &limiter) < 0 ||
        pa_modargs_get_value_boolean(ma, "watchdog", &watchdog_on) < 0) {
        pa_log("adaptive, decay, limiter and watchdog must be 0 or 1");
        goto fail;
    }
    if (pa_modargs_get_value_double(ma, "volume", &linear_volume) < 0 || linear_volume < 0.0 || linear_volume > 10.0) {
        pa_log("volume must be a linear factor between 0 and 10");
        goto fail;
    }

    m->userdata = u = pa_xnew0(struct userdata, 1);
    u->core = m->core;
    u->module = m;
    u->perf = perf == 0 ? AAUDIO_PERFORMANCE_MODE_NONE : perf == 2 ? AAUDIO_PERFORMANCE_MODE_POWER_SAVING : AAUDIO_PERFORMANCE_MODE_LOW_LATENCY;
    u->target_ms = (int32_t) buffer_ms;
    u->max_ms = (int32_t) max_ms;
    u->ring_ms = (int32_t) ring_ms;
    u->adaptive = adaptive;
    u->decay = decay;
    u->limiter = limiter;
    u->watchdog_on = watchdog_on;
    u->ss.format = PA_SAMPLE_FLOAT32NE;
    u->ss.rate = DA_RING_RATE;
    u->ss.channels = DA_RING_CHANNELS;
    u->frame_size = pa_frame_size(&u->ss);
    u->burst = 192;
    pthread_mutex_init(&u->lock, NULL);
    pa_channel_map_init_stereo(&map);

    /* The ring: the relay's layout in this process's memory. */
    cap_frames = DA_RING_CAP_MS_LOCAL * DA_RING_RATE / 1000;
    u->ring = pa_xmalloc0(da_ring_bytes(cap_frames));
    u->ring->magic = DA_RING_MAGIC;
    u->ring->version = DA_RELAY_VERSION;
    u->ring->cap_frames = cap_frames;
    u->ring->rate = DA_RING_RATE;
    u->ring->channels = DA_RING_CHANNELS;
    u->ring->target_frames = 0;

    if ((res = open_output(u, &aq)) != AAUDIO_OK) {
        pa_log("directaudio-native-sink: could not open the AAudio output (%s); is the daemon running on the Android side?", AAudio_convertResultToText(res));
        goto fail;
    }
    __atomic_store_n(&u->aq, aq, __ATOMIC_SEQ_CST);
    u->seen_hw_buf = u->base_buf_frames;
    u->seen_target = u->ring->target_frames;
    u->settled_at = pa_rtclock_now() + 10 * PA_USEC_PER_SEC;
    u->stats_at = pa_rtclock_now();

    u->rtpoll = pa_rtpoll_new();
    if (pa_thread_mq_init(&u->thread_mq, m->core->mainloop, u->rtpoll) < 0) {
        pa_log("pa_thread_mq_init() failed.");
        goto fail;
    }

    pa_sink_new_data_init(&data);
    data.driver = __FILE__;
    data.module = m;
    pa_sink_new_data_set_name(&data, pa_modargs_get_value(ma, "sink_name", DEFAULT_SINK_NAME));
    pa_sink_new_data_set_sample_spec(&data, &u->ss);
    pa_sink_new_data_set_channel_map(&data, &map);
    pa_cvolume_set(&volume, u->ss.channels, pa_sw_volume_from_linear(linear_volume));
    pa_sink_new_data_set_volume(&data, &volume);
    pa_proplist_sets(data.proplist, PA_PROP_DEVICE_STRING, "directaudio");
    pa_proplist_sets(data.proplist, PA_PROP_DEVICE_DESCRIPTION, "Android audio output (DirectAudio)");
    pa_proplist_sets(data.proplist, PA_PROP_DEVICE_CLASS, "sound");
    pa_proplist_sets(data.proplist, PA_PROP_DEVICE_API, "directaudio");
    if (pa_modargs_get_proplist(ma, "sink_properties", data.proplist, PA_UPDATE_REPLACE) < 0) {
        pa_log("Invalid properties");
        pa_sink_new_data_done(&data);
        goto fail;
    }
    u->sink = pa_sink_new(m->core, &data, PA_SINK_HARDWARE | PA_SINK_LATENCY);
    pa_sink_new_data_done(&data);
    if (!u->sink) {
        pa_log("Failed to create sink.");
        goto fail;
    }
    u->sink->parent.process_msg = sink_process_msg;
    u->sink->set_state_in_io_thread = sink_set_state_in_io_thread_cb;
    u->sink->userdata = u;
    pa_sink_set_asyncmsgq(u->sink, u->thread_mq.inq);
    pa_sink_set_rtpoll(u->sink, u->rtpoll);
    pa_sink_set_max_request(u->sink, (size_t) u->ring->target_frames * u->frame_size);
    /* What clients should keep queued on their side: the ring's starting target plus the device
     * buffer. Grows are reported live through GET_LATENCY. */
    pa_sink_set_fixed_latency(u->sink, frames_to_usec(u, (int64_t) u->ring->target_frames + u->base_buf_frames));

    if (!(u->thread = pa_thread_new("directaudio-native-sink", thread_func, u))) {
        pa_log("Failed to create thread.");
        goto fail;
    }
    pa_sink_put(u->sink);
    pa_modargs_free(ma);
    return 0;

fail:
    if (ma)
        pa_modargs_free(ma);
    pa__done(m);
    return -1;
}

int pa__get_n_used(pa_module *m) {
    struct userdata *u;

    pa_assert(m);
    pa_assert_se(u = m->userdata);
    return pa_sink_linked_by(u->sink);
}

void pa__done(pa_module *m) {
    struct userdata *u;

    pa_assert(m);
    if (!(u = m->userdata))
        return;
    if (u->sink)
        pa_sink_unlink(u->sink);
    if (u->thread) {
        pa_asyncmsgq_send(u->thread_mq.inq, NULL, PA_MESSAGE_SHUTDOWN, NULL, 0, NULL);
        pa_thread_free(u->thread);
    }
    pa_thread_mq_done(&u->thread_mq);
    if (u->sink)
        pa_sink_unref(u->sink);
    close_output(u);
    if (u->rtpoll)
        pa_rtpoll_free(u->rtpoll);
    pthread_mutex_destroy(&u->lock);
    pa_xfree(u->ring);
    pa_xfree(u);
}
