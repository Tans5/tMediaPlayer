//
// Created by pengcheng.tan on 2023/7/13.
//
#include <jni.h>
#include <string>
#include "tmediaplayer.h"
extern "C" {
#include "libavcodec/jni.h"
}


extern "C" JNIEXPORT jlong JNICALL
Java_com_tans_tmediaplayer_player_tMediaPlayer_createPlayerNative(
        JNIEnv * env,
        jobject j_player) {
    JavaVM * jvm = nullptr;
    env->GetJavaVM(&jvm);
    auto player = new tMediaPlayerContext;
    player->jvm = jvm;
    player->jplayer = env->NewGlobalRef(j_player);
    player->jplayerClazz = static_cast<jclass>(env->NewGlobalRef(
            env->FindClass("com/tans/tmediaplayer/player/tMediaPlayer")));
    player->callRequestAudioBufferMethodId = env->GetMethodID(player->jplayerClazz, "requestAudioDecodeBufferFromNative", "()J");
    player->callRequestVideoBufferMethodId = env->GetMethodID(player->jplayerClazz, "requestVideoDecodeBufferFromNative", "()J");
    player->callEnqueueAudioBufferMethodId = env->GetMethodID(player->jplayerClazz, "enqueueAudioEncodeBufferFromNative", "(J)V");
    player->callEnqueueVideoBufferMethodId = env->GetMethodID(player->jplayerClazz, "enqueueVideoEncodeBufferFromNative", "(J)V");
    return reinterpret_cast<jlong>(player);
}

extern "C" JNIEXPORT jint JNICALL
Java_com_tans_tmediaplayer_player_tMediaPlayer_prepareNative(
        JNIEnv * env,
        jobject j_player,
        jlong native_player,
        jstring file_path,
        jboolean requestHw,
        jint targetAudioChannels) {
    auto *player = reinterpret_cast<tMediaPlayerContext *>(native_player);
    if (player == nullptr) {
        return OptFail;
    }
    av_jni_set_java_vm(player->jvm, nullptr);
    const char * file_path_chars = env->GetStringUTFChars(file_path, 0);
    return player->prepare(file_path_chars, requestHw, targetAudioChannels);
}

extern "C" JNIEXPORT jlong JNICALL
Java_com_tans_tmediaplayer_player_tMediaPlayer_durationNative(
        JNIEnv * env,
        jobject j_player,
        jlong native_player) {
    auto *player = reinterpret_cast<tMediaPlayerContext *>(native_player);
    return player->duration;
}

extern "C" JNIEXPORT jint JNICALL
Java_com_tans_tmediaplayer_player_tMediaPlayer_videoWidthNative(
        JNIEnv * env,
        jobject j_player,
        jlong native_player) {
    auto *player = reinterpret_cast<tMediaPlayerContext *>(native_player);
    return player->video_width;
}

extern "C" JNIEXPORT jint JNICALL
Java_com_tans_tmediaplayer_player_tMediaPlayer_videoHeightNative(
        JNIEnv * env,
        jobject j_player,
        jlong native_player) {
    auto *player = reinterpret_cast<tMediaPlayerContext *>(native_player);
    return player->video_height;
}

extern "C" JNIEXPORT jdouble JNICALL
Java_com_tans_tmediaplayer_player_tMediaPlayer_videoFpsNative(
        JNIEnv * env,
        jobject j_player,
        jlong native_player) {
    auto *player = reinterpret_cast<tMediaPlayerContext *>(native_player);
    return player->video_fps;
}

extern "C" JNIEXPORT jlong JNICALL
Java_com_tans_tmediaplayer_player_tMediaPlayer_videoDurationNative(
        JNIEnv * env,
        jobject j_player,
        jlong native_player) {
    auto *player = reinterpret_cast<tMediaPlayerContext *>(native_player);
    return player->video_duration;
}

extern "C" JNIEXPORT jint JNICALL
Java_com_tans_tmediaplayer_player_tMediaPlayer_audioChannelsNative(
        JNIEnv * env,
        jobject j_player,
        jlong native_player) {
    auto *player = reinterpret_cast<tMediaPlayerContext *>(native_player);
    return player->audio_channels;
}

extern "C" JNIEXPORT jint JNICALL
Java_com_tans_tmediaplayer_player_tMediaPlayer_audioPreSampleBytesNative(
        JNIEnv * env,
        jobject j_player,
        jlong native_player) {
    auto *player = reinterpret_cast<tMediaPlayerContext *>(native_player);
    return player->audio_pre_sample_bytes;
}

extern "C" JNIEXPORT jint JNICALL
Java_com_tans_tmediaplayer_player_tMediaPlayer_audioSampleRateNative(
        JNIEnv * env,
        jobject j_player,
        jlong native_player) {
    auto *player = reinterpret_cast<tMediaPlayerContext *>(native_player);
    return player->audio_simple_rate;
}

extern "C" JNIEXPORT jlong JNICALL
Java_com_tans_tmediaplayer_player_tMediaPlayer_audioDurationNative(
        JNIEnv * env,
        jobject j_player,
        jlong native_player) {
    auto *player = reinterpret_cast<tMediaPlayerContext *>(native_player);
    return player->audio_duration;
}


extern "C" JNIEXPORT jlong JNICALL
Java_com_tans_tmediaplayer_player_tMediaPlayer_allocVideoDecodeDataNative(
        JNIEnv * env,
        jobject j_player) {
    auto buffer = allocVideoDecodeBuffer();
    return reinterpret_cast<jlong>(buffer);
}

extern "C" JNIEXPORT jlong JNICALL
Java_com_tans_tmediaplayer_player_tMediaPlayer_allocAudioDecodeDataNative(
        JNIEnv * env,
        jobject j_player) {
    auto buffer = allocAudioDecodeBuffer();
    return reinterpret_cast<jlong>(buffer);
}

extern "C" JNIEXPORT jint JNICALL
Java_com_tans_tmediaplayer_player_tMediaPlayer_getBufferResultNative(
        JNIEnv * env,
        jobject j_player,
        jlong buffer_l) {
    auto buffer = reinterpret_cast<tMediaDecodeBuffer *>(buffer_l);
    return buffer->decodeResult;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_tans_tmediaplayer_player_tMediaPlayer_isVideoBufferNative(
        JNIEnv * env,
        jobject j_player,
        jlong buffer_l) {
    auto buffer = reinterpret_cast<tMediaDecodeBuffer *>(buffer_l);
    return buffer->type == BufferTypeVideo;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_tans_tmediaplayer_player_tMediaPlayer_isLastFrameBufferNative(
        JNIEnv * env,
        jobject j_player,
        jlong buffer_l) {
    auto buffer = reinterpret_cast<tMediaDecodeBuffer *>(buffer_l);
    return buffer->is_last_frame;
}

extern "C" JNIEXPORT jint JNICALL
Java_com_tans_tmediaplayer_player_tMediaPlayer_getVideoWidthNative(
        JNIEnv * env,
        jobject j_player,
        jlong buffer_l) {
    auto buffer = reinterpret_cast<tMediaDecodeBuffer *>(buffer_l);
    if (buffer->type == BufferTypeVideo) {
        return buffer->videoBuffer->width;
    } else {
        return 0;
    }
}

extern "C" JNIEXPORT jint JNICALL
Java_com_tans_tmediaplayer_player_tMediaPlayer_getVideoHeightNative(
        JNIEnv * env,
        jobject j_player,
        jlong buffer_l) {
    auto buffer = reinterpret_cast<tMediaDecodeBuffer *>(buffer_l);
    if (buffer->type == BufferTypeVideo) {
        return buffer->videoBuffer->height;
    } else {
        return 0;
    }
}

extern "C" JNIEXPORT jlong JNICALL
Java_com_tans_tmediaplayer_player_tMediaPlayer_getPtsNative(
        JNIEnv * env,
        jobject j_player,
        jlong buffer_l) {
    auto buffer = reinterpret_cast<tMediaDecodeBuffer *>(buffer_l);
    return buffer->pts;
}

extern "C" JNIEXPORT jint JNICALL
Java_com_tans_tmediaplayer_player_tMediaPlayer_getVideoFrameTypeNative(
        JNIEnv * env,
        jobject j_player,
        jlong buffer_l) {
    auto buffer = reinterpret_cast<tMediaDecodeBuffer *>(buffer_l);
    if (buffer->type == BufferTypeVideo) {
        return buffer->videoBuffer->type;
    } else {
        return Unknown;
    }
}

extern "C" JNIEXPORT jint JNICALL
Java_com_tans_tmediaplayer_player_tMediaPlayer_getVideoFrameRgbaSizeNative(
        JNIEnv * env,
        jobject j_player,
        jlong buffer_l) {
    auto buffer = reinterpret_cast<tMediaDecodeBuffer *>(buffer_l);
    if (buffer->type == BufferTypeVideo) {
        if (buffer->videoBuffer->type == Rgba) {
            return buffer->videoBuffer->rgbaSize;
        } else {
            return 0;
        }
    } else {
        return 0;
    }
}

extern "C" JNIEXPORT void JNICALL
Java_com_tans_tmediaplayer_player_tMediaPlayer_getVideoFrameRgbaBytesNative(
        JNIEnv * env,
        jobject j_player,
        jlong buffer_l,
        jbyteArray j_bytes) {
    auto buffer = reinterpret_cast<tMediaDecodeBuffer *>(buffer_l);
    if (buffer->type == BufferTypeVideo) {
        if (buffer->videoBuffer->type == Rgba) {
            env->SetByteArrayRegion(j_bytes, 0, buffer->videoBuffer->rgbaSize,
                                    reinterpret_cast<const jbyte *>(buffer->videoBuffer->rgbaBuffer));
        }
    }
}

extern "C" JNIEXPORT jint JNICALL
Java_com_tans_tmediaplayer_player_tMediaPlayer_getVideoFrameYSizeNative(
        JNIEnv * env,
        jobject j_player,
        jlong buffer_l) {
    auto buffer = reinterpret_cast<tMediaDecodeBuffer *>(buffer_l);
    if (buffer->type == BufferTypeVideo) {
        if (buffer->videoBuffer->type == Nv12 || buffer->videoBuffer->type == Nv21 || buffer->videoBuffer->type == Yuv420p) {
            return buffer->videoBuffer->ySize;
        } else {
            return 0;
        }
    } else {
        return 0;
    }
}

extern "C" JNIEXPORT void JNICALL
Java_com_tans_tmediaplayer_player_tMediaPlayer_getVideoFrameYBytesNative(
        JNIEnv * env,
        jobject j_player,
        jlong buffer_l,
        jbyteArray j_bytes) {
    auto buffer = reinterpret_cast<tMediaDecodeBuffer *>(buffer_l);
    if (buffer->type == BufferTypeVideo) {
        if (buffer->videoBuffer->type == Nv12 || buffer->videoBuffer->type == Nv21 || buffer->videoBuffer->type == Yuv420p) {
            env->SetByteArrayRegion(j_bytes, 0, buffer->videoBuffer->ySize,
                                    reinterpret_cast<const jbyte *>(buffer->videoBuffer->yBuffer));
        }
    }
}

extern "C" JNIEXPORT jint JNICALL
Java_com_tans_tmediaplayer_player_tMediaPlayer_getVideoFrameUSizeNative(
        JNIEnv * env,
        jobject j_player,
        jlong buffer_l) {
    auto buffer = reinterpret_cast<tMediaDecodeBuffer *>(buffer_l);
    if (buffer->type == BufferTypeVideo) {
        if (buffer->videoBuffer->type == Yuv420p) {
            return buffer->videoBuffer->uSize;
        } else {
            return 0;
        }
    } else {
        return 0;
    }
}

extern "C" JNIEXPORT void JNICALL
Java_com_tans_tmediaplayer_player_tMediaPlayer_getVideoFrameUBytesNative(
        JNIEnv * env,
        jobject j_player,
        jlong buffer_l,
        jbyteArray j_bytes) {
    auto buffer = reinterpret_cast<tMediaDecodeBuffer *>(buffer_l);
    if (buffer->type == BufferTypeVideo) {
        if (buffer->videoBuffer->type == Yuv420p) {
            env->SetByteArrayRegion(j_bytes, 0, buffer->videoBuffer->uSize,
                                    reinterpret_cast<const jbyte *>(buffer->videoBuffer->uBuffer));
        }
    }
}

extern "C" JNIEXPORT jint JNICALL
Java_com_tans_tmediaplayer_player_tMediaPlayer_getVideoFrameVSizeNative(
        JNIEnv * env,
        jobject j_player,
        jlong buffer_l) {
    auto buffer = reinterpret_cast<tMediaDecodeBuffer *>(buffer_l);
    if (buffer->type == BufferTypeVideo) {
        if (buffer->videoBuffer->type == Yuv420p) {
            return buffer->videoBuffer->vSize;
        } else {
            return 0;
        }
    } else {
        return 0;
    }
}

extern "C" JNIEXPORT void JNICALL
Java_com_tans_tmediaplayer_player_tMediaPlayer_getVideoFrameVBytesNative(
        JNIEnv * env,
        jobject j_player,
        jlong buffer_l,
        jbyteArray j_bytes) {
    auto buffer = reinterpret_cast<tMediaDecodeBuffer *>(buffer_l);
    if (buffer->type == BufferTypeVideo) {
        if (buffer->videoBuffer->type == Yuv420p) {
            env->SetByteArrayRegion(j_bytes, 0, buffer->videoBuffer->vSize,
                                    reinterpret_cast<const jbyte *>(buffer->videoBuffer->vBuffer));
        }
    }
}

extern "C" JNIEXPORT jint JNICALL
Java_com_tans_tmediaplayer_player_tMediaPlayer_getVideoFrameUVSizeNative(
        JNIEnv * env,
        jobject j_player,
        jlong buffer_l) {
    auto buffer = reinterpret_cast<tMediaDecodeBuffer *>(buffer_l);
    if (buffer->type == BufferTypeVideo) {
        if (buffer->videoBuffer->type == Nv12 || buffer->videoBuffer->type == Nv21) {
            return buffer->videoBuffer->uvSize;
        } else {
            return 0;
        }
    } else {
        return 0;
    }
}

extern "C" JNIEXPORT void JNICALL
Java_com_tans_tmediaplayer_player_tMediaPlayer_getVideoFrameUVBytesNative(
        JNIEnv * env,
        jobject j_player,
        jlong buffer_l,
        jbyteArray j_bytes) {
    auto buffer = reinterpret_cast<tMediaDecodeBuffer *>(buffer_l);
    if (buffer->type == BufferTypeVideo) {
        if (buffer->videoBuffer->type == Nv12 || buffer->videoBuffer->type == Nv21) {
            env->SetByteArrayRegion(j_bytes, 0, buffer->videoBuffer->uvSize,
                                    reinterpret_cast<const jbyte *>(buffer->videoBuffer->uvBuffer));
        }
    }
}

extern "C" JNIEXPORT void JNICALL
Java_com_tans_tmediaplayer_player_tMediaPlayer_getAudioFrameBytesNative(
        JNIEnv * env,
        jobject j_player,
        jlong buffer_l,
        jbyteArray j_bytes) {
    auto buffer = reinterpret_cast<tMediaDecodeBuffer *>(buffer_l);
    if (buffer->type == BufferTypeAudio) {
        env->SetByteArrayRegion(j_bytes, 0, buffer->audioBuffer->size,
                                reinterpret_cast<const jbyte *>(buffer->audioBuffer->pcmBuffer));
    }
}

extern "C" JNIEXPORT jint JNICALL
Java_com_tans_tmediaplayer_player_tMediaPlayer_getAudioFrameSizeNative(
        JNIEnv * env,
        jobject j_player,
        jlong buffer_l) {
    auto buffer = reinterpret_cast<tMediaDecodeBuffer *>(buffer_l);
    if (buffer->type == BufferTypeAudio) {
        return buffer->audioBuffer->size;
    } else {
        return 0;
    }
}

extern "C" JNIEXPORT void JNICALL
Java_com_tans_tmediaplayer_player_tMediaPlayer_freeDecodeDataNative(
        JNIEnv * env,
        jobject j_player,
        jlong buffer_l) {
    auto buffer = reinterpret_cast<tMediaDecodeBuffer *>(buffer_l);
    freeDecodeBuffer(buffer);
}

extern "C" JNIEXPORT jint JNICALL
Java_com_tans_tmediaplayer_player_tMediaPlayer_seekToNative(
        JNIEnv * env,
        jobject j_player,
        jlong player_l,
        jlong video_buffer_l,
        jlong audio_buffer_l,
        jlong targetPtsInMillis) {
    auto player = reinterpret_cast<tMediaPlayerContext *>(player_l);
    tMediaDecodeBuffer* videoBuffer = reinterpret_cast<tMediaDecodeBuffer *>(video_buffer_l);
    tMediaDecodeBuffer* audioBuffer = reinterpret_cast<tMediaDecodeBuffer *>(audio_buffer_l);
    return player->seekTo((long) targetPtsInMillis, videoBuffer, audioBuffer, true);
}

extern "C" JNIEXPORT jlong JNICALL
Java_com_tans_tmediaplayer_player_tMediaPlayer_decodeNative(
        JNIEnv * env,
        jobject j_player,
        jlong player_l) {
    auto player = reinterpret_cast<tMediaPlayerContext *>(player_l);
    return reinterpret_cast<jlong>(player->decode(nullptr));
}

extern "C" JNIEXPORT jint JNICALL
Java_com_tans_tmediaplayer_player_tMediaPlayer_resetNative(
        JNIEnv * env,
        jobject j_player,
        jlong native_player) {
    auto *player = reinterpret_cast<tMediaPlayerContext *>(native_player);
    return player->resetDecodeProgress();
}

extern "C" JNIEXPORT void JNICALL
Java_com_tans_tmediaplayer_player_tMediaPlayer_releaseNative(
        JNIEnv * env,
        jobject j_player,
        jlong native_player) {
    auto *player = reinterpret_cast<tMediaPlayerContext *>(native_player);
    env->DeleteGlobalRef(player->jplayer);
    player->jplayer = nullptr;
    env->DeleteGlobalRef(player->jplayerClazz);
    player->jplayerClazz = nullptr;
    player->jvm = nullptr;
    player->callRequestVideoBufferMethodId = nullptr;
    player->callRequestAudioBufferMethodId = nullptr;
    player->callEnqueueVideoBufferMethodId = nullptr;
    player->callEnqueueAudioBufferMethodId = nullptr;
    player->release();
}
