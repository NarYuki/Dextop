/* Own-source silent playback probe: verifies queue consumption, not audibility. */
#include <AudioToolbox/AudioQueue.h>
#include <stdio.h>
#include <string.h>
#include <unistd.h>
#include <stdlib.h>

struct State { unsigned callbacks = 0; unsigned error = 0; unsigned stopping = 0; };

static void completed(void *opaque, AudioQueueRef queue, AudioQueueBufferRef buffer)
{
    State *state = static_cast<State *>(opaque);
    unsigned count = __atomic_add_fetch(&state->callbacks, 1, __ATOMIC_SEQ_CST);
    if (!__atomic_load_n(&state->stopping, __ATOMIC_SEQ_CST) && count < 16 &&
        AudioQueueEnqueueBuffer(queue, buffer, 0, nullptr))
        __atomic_store_n(&state->error, 1, __ATOMIC_SEQ_CST);
}

static int playbackCycle()
{
    AudioStreamBasicDescription format = {};
    format.mSampleRate = 44100;
    format.mFormatID = kAudioFormatLinearPCM;
    format.mFormatFlags = kAudioFormatFlagIsSignedInteger | kAudioFormatFlagIsPacked;
    format.mBytesPerPacket = format.mBytesPerFrame = 4;
    format.mFramesPerPacket = 1;
    format.mChannelsPerFrame = 2;
    format.mBitsPerChannel = 16;
    State state;
    AudioQueueRef queue = nullptr;
    OSStatus status = AudioQueueNewOutput(&format, completed, &state, nullptr, nullptr, 0, &queue);
    printf("audioqueue: create=%d\n", (int)status);
    if (status || !queue) return 1;
    for (unsigned i = 0; i < 3; ++i) {
        AudioQueueBufferRef buffer = nullptr;
        status = AudioQueueAllocateBuffer(queue, 4410 * 4, &buffer);
        if (status || !buffer) return 2;
        memset(buffer->mAudioData, 0, buffer->mAudioDataBytesCapacity);
        buffer->mAudioDataByteSize = buffer->mAudioDataBytesCapacity;
        if (AudioQueueEnqueueBuffer(queue, buffer, 0, nullptr)) return 3;
    }
    status = AudioQueueStart(queue, nullptr);
    printf("audioqueue: start=%d\n", (int)status);
    if (status) { AudioQueueDispose(queue, true); return 4; }
    for (unsigned i = 0; i < 800 && __atomic_load_n(&state.callbacks, __ATOMIC_SEQ_CST) < 8; ++i)
        usleep(10000);
    unsigned consumed = __atomic_load_n(&state.callbacks, __ATOMIC_SEQ_CST);
    __atomic_store_n(&state.stopping, 1, __ATOMIC_SEQ_CST);
    status = AudioQueueStop(queue, true);
    printf("audioqueue: consumed=%u stop=%d\n", consumed, (int)status);
    OSStatus disposed = AudioQueueDispose(queue, true);
    printf("audioqueue: dispose=%d\n", (int)disposed);
    if (consumed < 8 || status || disposed || __atomic_load_n(&state.error, __ATOMIC_SEQ_CST)) return 5;
    return 0;
}

int main()
{
    setvbuf(stdout, nullptr, _IOLBF, 0);
    unsigned long cycles = 1;
    if (const char *value = getenv("DEXTOP_AUDIOQUEUE_CYCLES")) {
        char *end = nullptr;
        cycles = strtoul(value, &end, 10);
        if (!value[0] || *end || cycles < 1 || cycles > 20) return 64;
    }
    alarm(60);
    for (unsigned long cycle = 0; cycle < cycles; ++cycle) {
        printf("audioqueue: cycle=%lu/%lu\n", cycle + 1, cycles);
        int result = playbackCycle();
        if (result) return result;
    }
    alarm(0);
    puts("audioqueue: silent playback buffer consumption OK");
    return 0;
}
