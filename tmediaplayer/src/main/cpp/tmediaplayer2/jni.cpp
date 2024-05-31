//
// Created by pengcheng.tan on 2024/5/27.
//
#include "tmediaplayer.h"

extern "C" {
#include "libavcodec/jni.h"
}

// region Player control
extern "C" JNIEXPORT jlong JNICALL
Java_com_tans_tmediaplayer_player_tMediaPlayer2_createPlayerNative(
        JNIEnv * env,
        jobject j_player) {
    JavaVM * jvm = nullptr;
    env->GetJavaVM(&jvm);
    auto player = new tMediaPlayerContext;
    player->jvm = jvm;
    player->jplayer = env->NewGlobalRef(j_player);
    player->jplayerClazz = static_cast<jclass>(env->NewGlobalRef(
            env->FindClass("com/tans/tmediaplayer/player/tMediaPlayer2")));
    return reinterpret_cast<jlong>(player);
}

extern "C" JNIEXPORT jint JNICALL
Java_com_tans_tmediaplayer_player_tMediaPlayer2_prepareNative(
        JNIEnv * env,
        jobject j_player,
        jlong native_player,
        jstring file_path,
        jboolean requestHw,
        jint targetAudioChannels,
        jint targetAudioSampleRate,
        jint targetAudioSampleBitDepth) {
    auto *player = reinterpret_cast<tMediaPlayerContext *>(native_player);
    if (player == nullptr) {
        return OptFail;
    }
    av_jni_set_java_vm(player->jvm, nullptr);
    const char * file_path_chars = env->GetStringUTFChars(file_path, 0);
    return player->prepare(file_path_chars, requestHw, targetAudioChannels, targetAudioSampleRate, targetAudioSampleBitDepth);
}

extern "C" JNIEXPORT jint JNICALL
Java_com_tans_tmediaplayer_player_tMediaPlayer2_readPacketNative(
        JNIEnv * env,
        jobject j_player,
        jlong native_player) {
    auto *player = reinterpret_cast<tMediaPlayerContext *>(native_player);
    return player->readPacket();
}

extern "C" JNIEXPORT jint JNICALL
Java_com_tans_tmediaplayer_player_tMediaPlayer2_pauseReadPacketNative(
        JNIEnv * env,
        jobject j_player,
        jlong native_player) {
    auto *player = reinterpret_cast<tMediaPlayerContext *>(native_player);
    return player->pauseReadPacket();
}

extern "C" JNIEXPORT jint JNICALL
Java_com_tans_tmediaplayer_player_tMediaPlayer2_playReadPacketNative(
        JNIEnv * env,
        jobject j_player,
        jlong native_player) {
    auto *player = reinterpret_cast<tMediaPlayerContext *>(native_player);
    return player->resumeReadPacket();
}

extern "C" JNIEXPORT void JNICALL
Java_com_tans_tmediaplayer_player_tMediaPlayer2_movePacketRefNative(
        JNIEnv * env,
        jobject j_player,
        jlong native_player,
        jlong native_packet) {
    auto *player = reinterpret_cast<tMediaPlayerContext *>(native_player);
    auto *pkt = reinterpret_cast<AVPacket *>(native_packet);
    player->movePacketRef(pkt);
}

extern "C" JNIEXPORT jint JNICALL
Java_com_tans_tmediaplayer_player_tMediaPlayer2_seekToNative(
        JNIEnv * env,
        jobject j_player,
        jlong native_player,
        jlong target_pos_in_millis) {
    auto *player = reinterpret_cast<tMediaPlayerContext *>(native_player);
    return player->seekTo(target_pos_in_millis);
}

extern "C" JNIEXPORT jint JNICALL
Java_com_tans_tmediaplayer_player_tMediaPlayer2_decodeVideoNative(
        JNIEnv * env,
        jobject j_player,
        jlong native_player,
        jlong native_buffer) {
    auto *player = reinterpret_cast<tMediaPlayerContext *>(native_player);
    if (native_buffer != 0L) {
        auto *pkt = reinterpret_cast<AVPacket *>(native_buffer);
        return player->decodeVideo(pkt);
    } else {
        return player->decodeAudio(nullptr);
    }
}

extern "C" JNIEXPORT void JNICALL
Java_com_tans_tmediaplayer_player_tMediaPlayer2_flushVideoCodecBufferNative(
        JNIEnv * env,
        jobject j_player,
        jlong native_player) {
    auto *player = reinterpret_cast<tMediaPlayerContext *>(native_player);
    player->flushVideoCodecBuffer();
}

extern "C" JNIEXPORT jint JNICALL
Java_com_tans_tmediaplayer_player_tMediaPlayer2_moveDecodedVideoFrameToBufferNative(
        JNIEnv * env,
        jobject j_player,
        jlong native_player,
        jlong native_buffer) {
    auto *player = reinterpret_cast<tMediaPlayerContext *>(native_player);
    auto *videoBuffer = reinterpret_cast<tMediaVideoBuffer *>(native_buffer);
    return player->moveDecodedVideoFrameToBuffer(videoBuffer);
}

extern "C" JNIEXPORT jint JNICALL
Java_com_tans_tmediaplayer_player_tMediaPlayer2_decodeAudioNative(
        JNIEnv * env,
        jobject j_player,
        jlong native_player,
        jlong native_buffer) {
    auto *player = reinterpret_cast<tMediaPlayerContext *>(native_player);
    if (native_buffer != 0) {
        auto *pkt = reinterpret_cast<AVPacket *>(native_buffer);
        return player->decodeAudio(pkt);
    } else {
        return player->decodeAudio(nullptr);
    }
}

extern "C" JNIEXPORT void JNICALL
Java_com_tans_tmediaplayer_player_tMediaPlayer2_flushAudioCodecBufferNative(
        JNIEnv * env,
        jobject j_player,
        jlong native_player) {
    auto *player = reinterpret_cast<tMediaPlayerContext *>(native_player);
    player->flushAudioCodecBuffer();
}

extern "C" JNIEXPORT jint JNICALL
Java_com_tans_tmediaplayer_player_tMediaPlayer2_moveDecodedAudioFrameToBufferNative(
        JNIEnv * env,
        jobject j_player,
        jlong native_player,
        jlong native_buffer) {
    auto *player = reinterpret_cast<tMediaPlayerContext *>(native_player);
    auto *audioBuffer = reinterpret_cast<tMediaAudioBuffer *>(native_buffer);
    return player->moveDecodedAudioFrameToBuffer(audioBuffer);
}

extern "C" JNIEXPORT void JNICALL
Java_com_tans_tmediaplayer_player_tMediaPlayer2_releaseNative(
        JNIEnv * env,
        jobject j_player,
        jlong native_player) {
    auto *player = reinterpret_cast<tMediaPlayerContext *>(native_player);
    env->DeleteGlobalRef(player->jplayer);
    player->jplayer = nullptr;
    env->DeleteGlobalRef(player->jplayerClazz);
    player->jplayerClazz = nullptr;
    player->jvm = nullptr;
    player->release();
}
// endregion

// region Media file info
extern "C" JNIEXPORT jlong JNICALL
Java_com_tans_tmediaplayer_player_tMediaPlayer2_durationNative(
        JNIEnv * env,
        jobject j_player,
        jlong native_player) {
    auto *player = reinterpret_cast<tMediaPlayerContext *>(native_player);
    return player->duration;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_tans_tmediaplayer_player_tMediaPlayer2_containVideoStreamNative(
        JNIEnv * env,
        jobject j_player,
        jlong native_player) {
    auto *player = reinterpret_cast<tMediaPlayerContext *>(native_player);
    return player->video_decoder_ctx != nullptr;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_tans_tmediaplayer_player_tMediaPlayer2_containAudioStreamNative(
        JNIEnv * env,
        jobject j_player,
        jlong native_player) {
    auto *player = reinterpret_cast<tMediaPlayerContext *>(native_player);
    return player->audio_decoder_ctx != nullptr;
}

extern "C" JNIEXPORT jobjectArray JNICALL
Java_com_tans_tmediaplayer_player_tMediaPlayer2_getMetadataNative(
        JNIEnv * env,
        jobject j_player,
        jlong native_player) {
    auto *player = reinterpret_cast<tMediaPlayerContext *>(native_player);
    char ** metadata = player->metadata;
    jclass stringClazz = static_cast<jclass> (env->NewLocalRef(env->FindClass("java/lang/String")));
    jobjectArray  jarray = static_cast<jobjectArray>(env->NewLocalRef(env->NewObjectArray(player->metadataCount * 2, stringClazz, nullptr)));
    for (int i = 0; i < player->metadataCount; i ++) {
        jstring key = static_cast<jstring>(env->NewLocalRef(env->NewStringUTF(metadata[i * 2])));
        jstring value = static_cast<jstring>(env->NewLocalRef(env->NewStringUTF(metadata[i * 2 + 1])));
        env->SetObjectArrayElement(jarray, i * 2, key);
        env->SetObjectArrayElement(jarray, i * 2 + 1, value);
    }
    return jarray;
}
// endregion

// region Video stream info
extern "C" JNIEXPORT jint JNICALL
Java_com_tans_tmediaplayer_player_tMediaPlayer2_videoWidthNative(
        JNIEnv * env,
        jobject j_player,
        jlong native_player) {
    auto *player = reinterpret_cast<tMediaPlayerContext *>(native_player);
    return player->video_width;
}

extern "C" JNIEXPORT jint JNICALL
Java_com_tans_tmediaplayer_player_tMediaPlayer2_videoHeightNative(
        JNIEnv * env,
        jobject j_player,
        jlong native_player) {
    auto *player = reinterpret_cast<tMediaPlayerContext *>(native_player);
    return player->video_height;
}

extern "C" JNIEXPORT jint JNICALL
Java_com_tans_tmediaplayer_player_tMediaPlayer2_videoBitrateNative(
        JNIEnv * env,
        jobject j_player,
        jlong native_player) {
    auto *player = reinterpret_cast<tMediaPlayerContext *>(native_player);
    return player->video_bitrate;
}

extern "C" JNIEXPORT jint JNICALL
Java_com_tans_tmediaplayer_player_tMediaPlayer2_videoPixelBitDepthNative(
        JNIEnv * env,
        jobject j_player,
        jlong native_player) {
    auto *player = reinterpret_cast<tMediaPlayerContext *>(native_player);
    return player->video_bits_per_raw_sample;
}

extern "C" JNIEXPORT jint JNICALL
Java_com_tans_tmediaplayer_player_tMediaPlayer2_videoPixelFmtNative(
        JNIEnv * env,
        jobject j_player,
        jlong native_player) {
    auto *player = reinterpret_cast<tMediaPlayerContext *>(native_player);
    return player->video_pixel_format;
}

extern "C" JNIEXPORT jdouble JNICALL
Java_com_tans_tmediaplayer_player_tMediaPlayer2_videoFpsNative(
        JNIEnv * env,
        jobject j_player,
        jlong native_player) {
    auto *player = reinterpret_cast<tMediaPlayerContext *>(native_player);
    return player->video_fps;
}

extern "C" JNIEXPORT jlong JNICALL
Java_com_tans_tmediaplayer_player_tMediaPlayer2_videoDurationNative(
        JNIEnv * env,
        jobject j_player,
        jlong native_player) {
    auto *player = reinterpret_cast<tMediaPlayerContext *>(native_player);
    return player->video_duration;
}

extern "C" JNIEXPORT jint JNICALL
Java_com_tans_tmediaplayer_player_tMediaPlayer2_videoCodecIdNative(
        JNIEnv * env,
        jobject j_player,
        jlong native_player) {
    auto *player = reinterpret_cast<tMediaPlayerContext *>(native_player);
    return player->video_codec_id;
}
// endregion

// region Audio stream info
extern "C" JNIEXPORT jint JNICALL
Java_com_tans_tmediaplayer_player_tMediaPlayer2_audioChannelsNative(
        JNIEnv * env,
        jobject j_player,
        jlong native_player) {
    auto *player = reinterpret_cast<tMediaPlayerContext *>(native_player);
    return player->audio_channels;
}

extern "C" JNIEXPORT jint JNICALL
Java_com_tans_tmediaplayer_player_tMediaPlayer2_audioPerSampleBytesNative(
        JNIEnv * env,
        jobject j_player,
        jlong native_player) {
    auto *player = reinterpret_cast<tMediaPlayerContext *>(native_player);
    return player->audio_per_sample_bytes;
}

extern "C" JNIEXPORT jint JNICALL
Java_com_tans_tmediaplayer_player_tMediaPlayer2_audioBitrateNative(
        JNIEnv * env,
        jobject j_player,
        jlong native_player) {
    auto *player = reinterpret_cast<tMediaPlayerContext *>(native_player);
    return player->audio_bitrate;
}

extern "C" JNIEXPORT jint JNICALL
Java_com_tans_tmediaplayer_player_tMediaPlayer2_audioSampleBitDepthNative(
        JNIEnv * env,
        jobject j_player,
        jlong native_player) {
    auto *player = reinterpret_cast<tMediaPlayerContext *>(native_player);
    return player->audio_bits_per_raw_sample;
}

extern "C" JNIEXPORT jint JNICALL
Java_com_tans_tmediaplayer_player_tMediaPlayer2_audioSampleFmtNative(
        JNIEnv * env,
        jobject j_player,
        jlong native_player) {
    auto *player = reinterpret_cast<tMediaPlayerContext *>(native_player);
    return player->audio_sample_format;
}

extern "C" JNIEXPORT jint JNICALL
Java_com_tans_tmediaplayer_player_tMediaPlayer2_audioSampleRateNative(
        JNIEnv * env,
        jobject j_player,
        jlong native_player) {
    auto *player = reinterpret_cast<tMediaPlayerContext *>(native_player);
    return player->audio_simple_rate;
}

extern "C" JNIEXPORT jlong JNICALL
Java_com_tans_tmediaplayer_player_tMediaPlayer2_audioDurationNative(
        JNIEnv * env,
        jobject j_player,
        jlong native_player) {
    auto *player = reinterpret_cast<tMediaPlayerContext *>(native_player);
    return player->audio_duration;
}

extern "C" JNIEXPORT jint JNICALL
Java_com_tans_tmediaplayer_player_tMediaPlayer2_audioCodecIdNative(
        JNIEnv * env,
        jobject j_player,
        jlong native_player) {
    auto *player = reinterpret_cast<tMediaPlayerContext *>(native_player);
    return player->audio_codec_id;
}
// endregion

// region Packet buffer
#pragma clang diagnostic push
#pragma ide diagnostic ignored "MemoryLeak"
extern "C" JNIEXPORT jlong JNICALL
Java_com_tans_tmediaplayer_player_tMediaPlayer2_allocPacketNative(
        JNIEnv * env,
        jobject j_player) {
    auto pkt = av_packet_alloc();
    return reinterpret_cast<jlong>(pkt);
}
#pragma clang diagnostic pop

extern "C" JNIEXPORT jlong JNICALL
Java_com_tans_tmediaplayer_player_tMediaPlayer2_getPacketPtsNative(
        JNIEnv * env,
        jobject j_player,
        jlong nativeBuffer) {
    AVPacket *pkt = reinterpret_cast<AVPacket*>(nativeBuffer);
    if (pkt->pts == AV_NOPTS_VALUE) {
        return 0L;
    } else {
        return (jlong) ((double)pkt->pts * av_q2d(pkt->time_base) * 1000.0);
    }
}

extern "C" JNIEXPORT jlong JNICALL
Java_com_tans_tmediaplayer_player_tMediaPlayer2_getPacketDurationNative(
        JNIEnv * env,
        jobject j_player,
        jlong nativeBuffer) {
    AVPacket *pkt = reinterpret_cast<AVPacket*>(nativeBuffer);
    if (pkt->duration == AV_NOPTS_VALUE) {
        return 0L;
    } else {
        return (jlong) ((double)pkt->duration * av_q2d(pkt->time_base) * 1000.0);
    }
}

extern "C" JNIEXPORT jint JNICALL
Java_com_tans_tmediaplayer_player_tMediaPlayer2_getPacketBytesSizeNative(
        JNIEnv * env,
        jobject j_player,
        jlong nativeBuffer) {
    AVPacket *pkt = reinterpret_cast<AVPacket*>(nativeBuffer);
    return pkt->size;
}

extern "C" JNIEXPORT void JNICALL
Java_com_tans_tmediaplayer_player_tMediaPlayer2_releasePacketNative(
        JNIEnv * env,
        jobject j_player,
        jlong nativeBuffer) {
    AVPacket *pkt = reinterpret_cast<AVPacket*>(nativeBuffer);
    av_packet_unref(pkt);
    av_packet_free(&pkt);
}
// endregion

// region VideoBuffer
#pragma clang diagnostic push
#pragma ide diagnostic ignored "MemoryLeak"
extern "C" JNIEXPORT jlong JNICALL
Java_com_tans_tmediaplayer_player_tMediaPlayer2_allocVideoBufferNative(
        JNIEnv * env,
        jobject j_player) {
    auto buffer = new tMediaVideoBuffer;
    return reinterpret_cast<jlong>(buffer);
}
#pragma clang diagnostic pop

extern "C" JNIEXPORT jlong JNICALL
Java_com_tans_tmediaplayer_player_tMediaPlayer2_getVideoPtsNative(
        JNIEnv * env,
        jobject j_player,
        jlong buffer_l) {
    auto buffer = reinterpret_cast<tMediaVideoBuffer *>(buffer_l);
    return buffer -> pts;
}

extern "C" JNIEXPORT jlong JNICALL
Java_com_tans_tmediaplayer_player_tMediaPlayer2_getVideoDurationNative(
        JNIEnv * env,
        jobject j_player,
        jlong buffer_l) {
    auto buffer = reinterpret_cast<tMediaVideoBuffer *>(buffer_l);
    return buffer -> duration;
}

extern "C" JNIEXPORT jint JNICALL
Java_com_tans_tmediaplayer_player_tMediaPlayer2_getVideoWidthNative(
        JNIEnv * env,
        jobject j_player,
        jlong buffer_l) {
    auto buffer = reinterpret_cast<tMediaVideoBuffer *>(buffer_l);
    return buffer -> width;
}

extern "C" JNIEXPORT jint JNICALL
Java_com_tans_tmediaplayer_player_tMediaPlayer2_getVideoHeightNative(
        JNIEnv * env,
        jobject j_player,
        jlong buffer_l) {
    auto buffer = reinterpret_cast<tMediaVideoBuffer *>(buffer_l);
    return buffer -> height;
}

extern "C" JNIEXPORT jint JNICALL
Java_com_tans_tmediaplayer_player_tMediaPlayer2_getVideoFrameTypeNative(
        JNIEnv * env,
        jobject j_player,
        jlong buffer_l) {
    auto buffer = reinterpret_cast<tMediaVideoBuffer *>(buffer_l);
    return buffer->type;
}

extern "C" JNIEXPORT jint JNICALL
Java_com_tans_tmediaplayer_player_tMediaPlayer2_getVideoFrameRgbaSizeNative(
        JNIEnv * env,
        jobject j_player,
        jlong buffer_l) {
    auto buffer = reinterpret_cast<tMediaVideoBuffer *>(buffer_l);
    if (buffer->type == Rgba) {
        return buffer->rgbaContentSize;
    } else {
        return 0;
    }
}

extern "C" JNIEXPORT void JNICALL
Java_com_tans_tmediaplayer_player_tMediaPlayer2_getVideoFrameRgbaBytesNative(
        JNIEnv * env,
        jobject j_player,
        jlong buffer_l,
        jbyteArray j_bytes) {
    auto buffer = reinterpret_cast<tMediaVideoBuffer *>(buffer_l);
    if (buffer->type == Rgba) {
        env->SetByteArrayRegion(j_bytes, 0, buffer->rgbaContentSize,
                                reinterpret_cast<const jbyte *>(buffer->rgbaBuffer));
    }
}

extern "C" JNIEXPORT jint JNICALL
Java_com_tans_tmediaplayer_player_tMediaPlayer2_getVideoFrameYSizeNative(
        JNIEnv * env,
        jobject j_player,
        jlong buffer_l) {
    auto buffer = reinterpret_cast<tMediaVideoBuffer *>(buffer_l);
    if (buffer->type == Nv12 || buffer->type == Nv21 || buffer->type == Yuv420p) {
        return buffer->yContentSize;
    } else {
        return 0;
    }
}

extern "C" JNIEXPORT void JNICALL
Java_com_tans_tmediaplayer_player_tMediaPlayer2_getVideoFrameYBytesNative(
        JNIEnv * env,
        jobject j_player,
        jlong buffer_l,
        jbyteArray j_bytes) {
    auto buffer = reinterpret_cast<tMediaVideoBuffer *>(buffer_l);
    if (buffer->type == Nv12 || buffer->type == Nv21 || buffer->type == Yuv420p) {
        env->SetByteArrayRegion(j_bytes, 0, buffer->yContentSize,
                                reinterpret_cast<const jbyte *>(buffer->yBuffer));
    }
}

extern "C" JNIEXPORT jint JNICALL
Java_com_tans_tmediaplayer_player_tMediaPlayer2_getVideoFrameUSizeNative(
        JNIEnv * env,
        jobject j_player,
        jlong buffer_l) {
    auto buffer = reinterpret_cast<tMediaVideoBuffer *>(buffer_l);
    if (buffer->type == Yuv420p) {
        return buffer->uContentSize;
    } else {
        return 0;
    }
}

extern "C" JNIEXPORT void JNICALL
Java_com_tans_tmediaplayer_player_tMediaPlayer2_getVideoFrameUBytesNative(
        JNIEnv * env,
        jobject j_player,
        jlong buffer_l,
        jbyteArray j_bytes) {
    auto buffer = reinterpret_cast<tMediaVideoBuffer *>(buffer_l);
    if (buffer->type == Yuv420p) {
        env->SetByteArrayRegion(j_bytes, 0, buffer->uContentSize,
                                reinterpret_cast<const jbyte *>(buffer->uBuffer));
    }
}

extern "C" JNIEXPORT jint JNICALL
Java_com_tans_tmediaplayer_player_tMediaPlayer2_getVideoFrameVSizeNative(
        JNIEnv * env,
        jobject j_player,
        jlong buffer_l) {
    auto buffer = reinterpret_cast<tMediaVideoBuffer *>(buffer_l);
    if (buffer->type == Yuv420p) {
        return buffer->vContentSize;
    } else {
        return 0;
    }
}

extern "C" JNIEXPORT void JNICALL
Java_com_tans_tmediaplayer_player_tMediaPlayer2_getVideoFrameVBytesNative(
        JNIEnv * env,
        jobject j_player,
        jlong buffer_l,
        jbyteArray j_bytes) {
    auto buffer = reinterpret_cast<tMediaVideoBuffer *>(buffer_l);
    if (buffer->type == Yuv420p) {
        env->SetByteArrayRegion(j_bytes, 0, buffer->vContentSize,
                                reinterpret_cast<const jbyte *>(buffer->vBuffer));
    }
}

extern "C" JNIEXPORT jint JNICALL
Java_com_tans_tmediaplayer_player_tMediaPlayer2_getVideoFrameUVSizeNative(
        JNIEnv * env,
        jobject j_player,
        jlong buffer_l) {
    auto buffer = reinterpret_cast<tMediaVideoBuffer *>(buffer_l);
    if (buffer->type == Nv12 || buffer->type == Nv21) {
        return buffer->uvContentSize;
    } else {
        return 0;
    }
}

extern "C" JNIEXPORT void JNICALL
Java_com_tans_tmediaplayer_player_tMediaPlayer2_getVideoFrameUVBytesNative(
        JNIEnv * env,
        jobject j_player,
        jlong buffer_l,
        jbyteArray j_bytes) {
    auto buffer = reinterpret_cast<tMediaVideoBuffer *>(buffer_l);
    if (buffer->type == Nv12 || buffer->type == Nv21) {
        env->SetByteArrayRegion(j_bytes, 0, buffer->uvContentSize,
                                reinterpret_cast<const jbyte *>(buffer->uvBuffer));
    }
}

extern "C" JNIEXPORT void JNICALL
Java_com_tans_tmediaplayer_player_tMediaPlayer2_releaseVideoBufferNative(
        JNIEnv * env,
        jobject j_player,
        jlong native_buffer) {
    auto buffer = reinterpret_cast<tMediaVideoBuffer *>(native_buffer);
    if (buffer->rgbaFrame != nullptr) {
        av_frame_unref(buffer->rgbaFrame);
        av_frame_free(&buffer->rgbaFrame);
    }
    if (buffer->rgbaBuffer != nullptr) {
        free(buffer->rgbaBuffer);
    }
    if (buffer->yBuffer != nullptr) {
        free(buffer->yBuffer);
    }
    if (buffer->uBuffer != nullptr) {
        free(buffer->uBuffer);
    }
    if (buffer->vBuffer != nullptr) {
        free(buffer->vBuffer);
    }
    if (buffer->uvBuffer != nullptr) {
        free(buffer->uvBuffer);
    }
    free(buffer);
}

// endregion

// region AudioBuffer
#pragma clang diagnostic push
#pragma ide diagnostic ignored "MemoryLeak"
extern "C" JNIEXPORT jlong JNICALL
Java_com_tans_tmediaplayer_player_tMediaPlayer2_allocAudioBufferNative(
        JNIEnv * env,
        jobject j_player) {
    auto buffer = new tMediaAudioBuffer;
    return reinterpret_cast<jlong>(buffer);
}
#pragma clang diagnostic pop

extern "C" JNIEXPORT void JNICALL
Java_com_tans_tmediaplayer_player_tMediaPlayer2_getAudioFrameBytesNative(
        JNIEnv * env,
        jobject j_player,
        jlong buffer_l,
        jbyteArray j_bytes) {
    auto buffer = reinterpret_cast<tMediaAudioBuffer *>(buffer_l);
    env->SetByteArrayRegion(j_bytes, 0, buffer->contentSize,
                            reinterpret_cast<const jbyte *>(buffer->pcmBuffer));
}

extern "C" JNIEXPORT jint JNICALL
Java_com_tans_tmediaplayer_player_tMediaPlayer2_getAudioFrameSizeNative(
        JNIEnv * env,
        jobject j_player,
        jlong buffer_l) {
    auto buffer = reinterpret_cast<tMediaAudioBuffer *>(buffer_l);
    return buffer->contentSize;
}

extern "C" JNIEXPORT jlong JNICALL
Java_com_tans_tmediaplayer_player_tMediaPlayer2_getAudioPtsNative(
        JNIEnv * env,
        jobject j_player,
        jlong buffer_l) {
    auto buffer = reinterpret_cast<tMediaAudioBuffer *>(buffer_l);
    return buffer->pts;
}

extern "C" JNIEXPORT jlong JNICALL
Java_com_tans_tmediaplayer_player_tMediaPlayer2_getAudioDurationNative(
        JNIEnv * env,
        jobject j_player,
        jlong buffer_l) {
    auto buffer = reinterpret_cast<tMediaAudioBuffer *>(buffer_l);
    return buffer->duration;
}

extern "C" JNIEXPORT void JNICALL
Java_com_tans_tmediaplayer_player_tMediaPlayer2_releaseAudioBufferNative(
        JNIEnv * env,
        jobject j_player,
        jlong native_buffer) {
    auto buffer = reinterpret_cast<tMediaAudioBuffer *>(native_buffer);
    if (buffer->pcmBuffer != nullptr) {
        free(buffer->pcmBuffer);
    }
    free(buffer);
}
//endregion
