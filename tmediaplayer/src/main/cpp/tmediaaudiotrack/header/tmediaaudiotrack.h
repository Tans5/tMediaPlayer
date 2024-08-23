
#ifndef TMEDIAPLAYER_TMEDIAAUDIOTRACK_H
#define TMEDIAPLAYER_TMEDIAAUDIOTRACK_H

#include <jni.h>
#include "tmediaplayer.h"

extern "C" {
#include <SLES/OpenSLES.h>
#include <SLES/OpenSLES_Android.h>
}

typedef struct tMediaAudioTrackContext {
    SLObjectItf engineObject = nullptr;
    SLEngineItf engineInterface = nullptr;

    SLObjectItf outputMixObject = nullptr;

    SLObjectItf playerObject = nullptr;
    SLPlayItf playerInterface = nullptr;
    SLAndroidSimpleBufferQueueItf playerBufferQueueInterface = nullptr;
    SLAndroidSimpleBufferQueueState *playerBufferQueueState = nullptr;

    SLuint32 inputSampleChannels = 2;
    SLuint32 inputChannelMask = SL_SPEAKER_FRONT_LEFT | SL_SPEAKER_FRONT_RIGHT;
    SLuint32 inputSampleRate = SL_SAMPLINGRATE_48;
    SLuint32 inputSampleFormat = SL_PCMSAMPLEFORMAT_FIXED_16;

    JavaVM *jvm = nullptr;
    jobject j_audioTrack = nullptr;
    jmethodID j_callbackMethodId = nullptr;

    tMediaOptResult prepare(unsigned int bufferQueueSize, unsigned int outputChannels, unsigned int outputSampleRate, unsigned int outputSampleBitDepth);

    tMediaOptResult play() const;

    tMediaOptResult pause() const;

    tMediaOptResult stop() const;

    tMediaOptResult enqueueBuffer(tMediaAudioBuffer* buffer) const;

    int32_t getBufferQueueCount() const;

    tMediaOptResult clearBuffers() const;

    void release();

} tMediaAudioTrackContext;

#endif