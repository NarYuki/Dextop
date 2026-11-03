/* Own-source functional PCM conversion probe using the public AudioToolbox API. */
#include <AudioToolbox/AudioConverter.h>
#include <stdint.h>
#include <stdio.h>
#include <string.h>
#include <unistd.h>

struct input {
    int16_t samples[4096 * 2];
    UInt32 position;
};

static OSStatus provide(AudioConverterRef converter, UInt32 *packets,
                        AudioBufferList *buffers,
                        AudioStreamPacketDescription **descriptions, void *opaque)
{
    (void)converter;
    struct input *input = opaque;
    UInt32 available = 4096 - input->position;
    if (*packets > available) *packets = available;
    buffers->mNumberBuffers = 1;
    buffers->mBuffers[0].mNumberChannels = 2;
    buffers->mBuffers[0].mDataByteSize = *packets * 4;
    buffers->mBuffers[0].mData = input->samples + input->position * 2;
    input->position += *packets;
    if (descriptions) *descriptions = NULL;
    return 0;
}

int main(void)
{
    setvbuf(stdout, NULL, _IOLBF, 0);
    alarm(20);
    AudioStreamBasicDescription source = {0}, destination = {0};
    source.mSampleRate = 48000;
    source.mFormatID = kAudioFormatLinearPCM;
    source.mFormatFlags = kAudioFormatFlagIsSignedInteger | kAudioFormatFlagIsPacked;
    source.mBytesPerPacket = source.mBytesPerFrame = 4;
    source.mFramesPerPacket = 1;
    source.mChannelsPerFrame = 2;
    source.mBitsPerChannel = 16;
    destination = source;
    destination.mSampleRate = 24000;
    destination.mFormatFlags = kAudioFormatFlagIsFloat | kAudioFormatFlagIsPacked;
    destination.mBytesPerPacket = destination.mBytesPerFrame = 8;
    destination.mBitsPerChannel = 32;
    AudioConverterRef converter = NULL;
    OSStatus status = AudioConverterNew(&source, &destination, &converter);
    printf("audio: create=%d\n", (int)status);
    if (status || !converter) return 1;
    for (unsigned pass = 0; pass < 4; ++pass) {
        struct input input = {0};
        for (unsigned i = 0; i < 4096; ++i) {
            input.samples[2*i] = ((int)(i % 96) - 48) * 400;
            input.samples[2*i+1] = -input.samples[2*i];
        }
        float samples[2048 * 2];
        memset(samples, 0, sizeof(samples));
        AudioBufferList output = {0};
        output.mNumberBuffers = 1;
        output.mBuffers[0].mNumberChannels = 2;
        output.mBuffers[0].mDataByteSize = sizeof(samples);
        output.mBuffers[0].mData = samples;
        UInt32 packets = 2048;
        status = AudioConverterFillComplexBuffer(converter, provide, &input, &packets, &output, NULL);
        printf("audio: convert=%d packets=%u bytes=%u input=%u\n", (int)status,
               (unsigned)packets, (unsigned)output.mBuffers[0].mDataByteSize, (unsigned)input.position);
        unsigned count = output.mBuffers[0].mDataByteSize / sizeof(float), nonzero = 0;
        int valid = !status && packets > 512 && count <= 4096 && count >= packets * 2;
        for (unsigned i = 0; valid && i < count; ++i) {
            if (!(samples[i] >= -1.0f && samples[i] <= 1.0f)) valid = 0;
            if (samples[i] > 0.01f || samples[i] < -0.01f) ++nonzero;
        }
        for (unsigned i = 0; valid && i + 1 < count; i += 2) {
            float sum = samples[i] + samples[i+1];
            if (sum < -0.0001f || sum > 0.0001f) valid = 0;
        }
        if (!valid || nonzero < 256) {
            AudioConverterDispose(converter);
            return 2;
        }
    }
    if (AudioConverterDispose(converter)) return 3;
    alarm(0);
    puts("audio: PCM resample and sample-format conversion OK");
    return 0;
}
