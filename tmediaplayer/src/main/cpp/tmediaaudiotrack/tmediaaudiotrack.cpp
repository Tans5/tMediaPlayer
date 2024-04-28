#include "tmediaaudiotrack.h"


void playerBufferQueueCallback(SLAndroidSimpleBufferQueueItf bq, void *context) {

    if (context == nullptr) {
        return;
    }
    tMediaAudioTrackContext *audioTrackContext = reinterpret_cast<tMediaAudioTrackContext *>(context);
    if (bq != audioTrackContext->playerBufferQueueInterface) {
        return;
    }
    JNIEnv *env = nullptr;
    audioTrackContext->jvm->GetEnv((void **)&env, JNI_VERSION_1_6);
    if (env == nullptr) {
        JavaVMAttachArgs jvmAttachArgs {
                .version = JNI_VERSION_1_6,
                .name = "AudioTrackCallback",
                .group = nullptr
        };
        auto result = audioTrackContext->jvm->AttachCurrentThread(&env, &jvmAttachArgs);
        if (result != JNI_OK) {
            LOGE("Audio track callback attach java thread fail.");
            return;
        }
    }
    env->CallVoidMethod(audioTrackContext->j_audioTrack, audioTrackContext->j_callbackMethodId);
    return;
}

tMediaOptResult tMediaAudioTrackContext::prepare(unsigned int bufferQueueSize) {
    // region Init sl engine
    SLresult result = slCreateEngine(&engineObject, 0, nullptr, 0, nullptr, nullptr);
    if (result != SL_RESULT_SUCCESS) {
        LOGE("Create sl engine object fail: %d", result);
        return OptFail;
    }
    result = (*engineObject)->Realize(engineObject, SL_BOOLEAN_FALSE);
    if (result != SL_RESULT_SUCCESS) {
        LOGE("Realize sl engine object fail: %d", result);
        return OptFail;
    }
    result = (*engineObject)->GetInterface(engineObject, SL_IID_ENGINE, &engineInterface);
    if (result != SL_RESULT_SUCCESS) {
        LOGE("Get sl engine interface fail: %d", result);
        return OptFail;
    }
    // endregion

    // region Init output mix
//    const SLInterfaceID outputMixIds[1] = {SL_IID_ENVIRONMENTALREVERB};
//    const SLboolean outputMixReq[1] = {SL_BOOLEAN_FALSE};
    result = (*engineInterface)->CreateOutputMix(engineInterface, &outputMixObject, 0, nullptr,
                                                 nullptr);
    if (result != SL_RESULT_SUCCESS) {
        LOGE("Create output mix object fail: %d", result);
        return OptFail;
    }
    result = (*outputMixObject)->Realize(outputMixObject, SL_BOOLEAN_FALSE);
    if (result != SL_RESULT_SUCCESS) {
        LOGE("Realize output mix object fail: %d", result);
        return OptFail;
    }
    // endregion

    // region Create player

    // Audio source configure
    SLDataLocator_AndroidSimpleBufferQueue audioInputQueue = {SL_DATALOCATOR_ANDROIDSIMPLEBUFFERQUEUE, bufferQueueSize};
    SLDataFormat_PCM audioInputFormat = {SL_DATAFORMAT_PCM, inputSampleChannels, inputSampleRate,
                                         inputSampleFormat, inputSampleFormat,
                                         SL_SPEAKER_FRONT_LEFT | SL_SPEAKER_FRONT_RIGHT, SL_BYTEORDER_LITTLEENDIAN};
    SLDataSource audioInputSource = {&audioInputQueue, &audioInputFormat};

    // Audio sink configure
    SLDataLocator_OutputMix outputMix = {SL_DATALOCATOR_OUTPUTMIX, outputMixObject};
    SLDataSink audioSink = {&outputMix, NULL};

    const SLInterfaceID playerIds[3] = {SL_IID_BUFFERQUEUE };
    // not need volume and effect send.
    const SLboolean playerReq[3] = {SL_BOOLEAN_TRUE };
    result = (*engineInterface)->CreateAudioPlayer(engineInterface, &playerObject, &audioInputSource, &audioSink, 1, playerIds, playerReq);
    if (result != SL_RESULT_SUCCESS) {
        LOGE("Create audio player object fail: %d", result);
        return OptFail;
    }
    result = (*playerObject)->Realize(playerObject, SL_BOOLEAN_FALSE);
    if (result != SL_RESULT_SUCCESS) {
        LOGE("Realize audio player fail: %d", result);
        return OptFail;
    }
    result = (*playerObject)->GetInterface(playerObject, SL_IID_PLAY, &playerInterface);
    if (result != SL_RESULT_SUCCESS) {
        LOGE("Get audio player interface fail: %d", result);
        return OptFail;
    }
    result = (*playerObject)->GetInterface(playerObject, SL_IID_BUFFERQUEUE, &playerBufferQueueInterface);
    if (result != SL_RESULT_SUCCESS) {
        LOGE("Get audio buffer queue interface fail: %d", result);
        return OptFail;
    }
    result = (*playerBufferQueueInterface)->RegisterCallback(playerBufferQueueInterface, playerBufferQueueCallback, this);
    if (result != SL_RESULT_SUCCESS) {
        LOGE("Register audio queue callback fail: %d", result);
        return OptFail;
    }
    // endregion

    LOGD("Prepare audio track success!!");

    return OptSuccess;
}

tMediaOptResult tMediaAudioTrackContext::play() {
    SLresult result = (*playerInterface)->SetPlayState(playerInterface, SL_PLAYSTATE_PLAYING);
    if (result == SL_RESULT_SUCCESS) {
        return OptSuccess;
    } else {
        return OptFail;
    }
}

tMediaOptResult tMediaAudioTrackContext::pause() {
    SLresult result = (*playerInterface)->SetPlayState(playerInterface, SL_PLAYSTATE_PAUSED);
    if (result == SL_RESULT_SUCCESS) {
        return OptSuccess;
    } else {
        return OptFail;
    }
}

tMediaOptResult tMediaAudioTrackContext::enqueueBuffer(tMediaAudioBuffer *buffer) {
    SLresult result = (*playerBufferQueueInterface)->Enqueue(playerBufferQueueInterface, buffer->pcmBuffer, buffer->size);
    if (result == SL_RESULT_SUCCESS) {
        return OptSuccess;
    } else {
        return OptFail;
    }
}

tMediaOptResult tMediaAudioTrackContext ::clearBuffers() {
    SLresult  result = (*playerBufferQueueInterface)->Clear(playerBufferQueueInterface);
    if (result == SL_RESULT_SUCCESS) {
        return OptSuccess;
    } else {
        return OptFail;
    }
}


void tMediaAudioTrackContext::release() {
    if (playerObject != nullptr) {
        (*playerObject)->Destroy(playerObject);
        playerObject = nullptr;
        playerInterface = nullptr;
        playerBufferQueueInterface = nullptr;
    }
    if (outputMixObject != nullptr) {
        (*outputMixObject)->Destroy(outputMixObject);
        outputMixObject = nullptr;
    }
    if (engineObject != nullptr) {
        (*engineObject)->Destroy(engineObject);
        engineObject = nullptr;
        engineInterface = nullptr;
    }
    free(this);
    LOGD("Audio track released.");
}