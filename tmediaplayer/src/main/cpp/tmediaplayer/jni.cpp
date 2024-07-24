//
// Created by pengcheng.tan on 2024/5/27.
//
#include "tmediaplayer.h"
extern "C" {
#include "libavcodec/jni.h"
}

// region Player control
extern "C" JNIEXPORT jlong JNICALL
Java_com_tans_tmediaplayer_player_tMediaPlayer_createPlayerNative(
        JNIEnv * env,
        jobject j_player) {
    JavaVM * jvm = nullptr;
    env->GetJavaVM(&jvm);
    auto player = new tMediaPlayerContext;
    player->jvm = jvm;
    return reinterpret_cast<jlong>(player);
}

extern "C" JNIEXPORT jint JNICALL
Java_com_tans_tmediaplayer_player_tMediaPlayer_prepareNative(
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
    const char * file_path_chars = env->GetStringUTFChars(file_path, JNI_FALSE);
    return player->prepare(file_path_chars, requestHw, targetAudioChannels, targetAudioSampleRate, targetAudioSampleBitDepth);
}

extern "C" JNIEXPORT jint JNICALL
Java_com_tans_tmediaplayer_player_tMediaPlayer_readPacketNative(
        JNIEnv * env,
        jobject j_player,
        jlong native_player) {
    auto *player = reinterpret_cast<tMediaPlayerContext *>(native_player);
    return player->readPacket();
}

extern "C" JNIEXPORT jint JNICALL
Java_com_tans_tmediaplayer_player_tMediaPlayer_pauseReadPacketNative(
        JNIEnv * env,
        jobject j_player,
        jlong native_player) {
    auto *player = reinterpret_cast<tMediaPlayerContext *>(native_player);
    return player->pauseReadPacket();
}

extern "C" JNIEXPORT jint JNICALL
Java_com_tans_tmediaplayer_player_tMediaPlayer_playReadPacketNative(
        JNIEnv * env,
        jobject j_player,
        jlong native_player) {
    auto *player = reinterpret_cast<tMediaPlayerContext *>(native_player);
    return player->resumeReadPacket();
}

extern "C" JNIEXPORT void JNICALL
Java_com_tans_tmediaplayer_player_tMediaPlayer_movePacketRefNative(
        JNIEnv * env,
        jobject j_player,
        jlong native_player,
        jlong native_packet) {
    auto *player = reinterpret_cast<tMediaPlayerContext *>(native_player);
    auto *pkt = reinterpret_cast<AVPacket *>(native_packet);
    player->movePacketRef(pkt);
}

extern "C" JNIEXPORT jint JNICALL
Java_com_tans_tmediaplayer_player_tMediaPlayer_seekToNative(
        JNIEnv * env,
        jobject j_player,
        jlong native_player,
        jlong target_pos_in_millis) {
    auto *player = reinterpret_cast<tMediaPlayerContext *>(native_player);
    return player->seekTo(target_pos_in_millis);
}

extern "C" JNIEXPORT jint JNICALL
Java_com_tans_tmediaplayer_player_tMediaPlayer_decodeVideoNative(
        JNIEnv * env,
        jobject j_player,
        jlong native_player,
        jlong native_buffer) {
    auto *player = reinterpret_cast<tMediaPlayerContext *>(native_player);
    if (native_buffer != 0L) {
        auto *pkt = reinterpret_cast<AVPacket *>(native_buffer);
        return player->decodeVideo(pkt);
    } else {
        return player->decodeVideo(nullptr);
    }
}

extern "C" JNIEXPORT void JNICALL
Java_com_tans_tmediaplayer_player_tMediaPlayer_flushVideoCodecBufferNative(
        JNIEnv * env,
        jobject j_player,
        jlong native_player) {
    auto *player = reinterpret_cast<tMediaPlayerContext *>(native_player);
    player->flushVideoCodecBuffer();
}

extern "C" JNIEXPORT jint JNICALL
Java_com_tans_tmediaplayer_player_tMediaPlayer_moveDecodedVideoFrameToBufferNative(
        JNIEnv * env,
        jobject j_player,
        jlong native_player,
        jlong native_buffer) {
    auto *player = reinterpret_cast<tMediaPlayerContext *>(native_player);
    auto *videoBuffer = reinterpret_cast<tMediaVideoBuffer *>(native_buffer);
    return player->moveDecodedVideoFrameToBuffer(videoBuffer);
}

extern "C" JNIEXPORT jint JNICALL
Java_com_tans_tmediaplayer_player_tMediaPlayer_decodeAudioNative(
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
Java_com_tans_tmediaplayer_player_tMediaPlayer_flushAudioCodecBufferNative(
        JNIEnv * env,
        jobject j_player,
        jlong native_player) {
    auto *player = reinterpret_cast<tMediaPlayerContext *>(native_player);
    player->flushAudioCodecBuffer();
}

extern "C" JNIEXPORT jint JNICALL
Java_com_tans_tmediaplayer_player_tMediaPlayer_moveDecodedAudioFrameToBufferNative(
        JNIEnv * env,
        jobject j_player,
        jlong native_player,
        jlong native_buffer) {
    auto *player = reinterpret_cast<tMediaPlayerContext *>(native_player);
    auto *audioBuffer = reinterpret_cast<tMediaAudioBuffer *>(native_buffer);
    return player->moveDecodedAudioFrameToBuffer(audioBuffer);
}

extern "C" JNIEXPORT void JNICALL
Java_com_tans_tmediaplayer_player_tMediaPlayer_releaseNative(
        JNIEnv * env,
        jobject j_player,
        jlong native_player) {
    auto *player = reinterpret_cast<tMediaPlayerContext *>(native_player);
    player->jvm = nullptr;
    player->release();
}
// endregion

// region Media file info
extern "C" JNIEXPORT jlong JNICALL
Java_com_tans_tmediaplayer_player_tMediaPlayer_durationNative(
        JNIEnv * env,
        jobject j_player,
        jlong native_player) {
    auto *player = reinterpret_cast<tMediaPlayerContext *>(native_player);
    return player->duration;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_tans_tmediaplayer_player_tMediaPlayer_containVideoStreamNative(
        JNIEnv * env,
        jobject j_player,
        jlong native_player) {
    auto *player = reinterpret_cast<tMediaPlayerContext *>(native_player);
    return player->video_decoder_ctx != nullptr;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_tans_tmediaplayer_player_tMediaPlayer_containAudioStreamNative(
        JNIEnv * env,
        jobject j_player,
        jlong native_player) {
    auto *player = reinterpret_cast<tMediaPlayerContext *>(native_player);
    return player->audio_decoder_ctx != nullptr;
}

jobjectArray readMetadata(JNIEnv *env, Metadata *src) {
    auto stringClazz = reinterpret_cast<jclass> (env->NewLocalRef(env->FindClass("java/lang/String")));
    auto  jarray = reinterpret_cast<jobjectArray>(env->NewLocalRef(env->NewObjectArray(src->metadataCount * 2, stringClazz, nullptr)));
    for (int i = 0; i < src->metadataCount; i ++) {
        auto key = reinterpret_cast<jstring>(env->NewLocalRef(env->NewStringUTF(src->metadata[i * 2])));
        auto value = reinterpret_cast<jstring>(env->NewLocalRef(env->NewStringUTF(src->metadata[i * 2 + 1])));
        env->SetObjectArrayElement(jarray, i * 2, key);
        env->SetObjectArrayElement(jarray, i * 2 + 1, value);
    }
    return jarray;
}

extern "C" JNIEXPORT jobjectArray JNICALL
Java_com_tans_tmediaplayer_player_tMediaPlayer_getMetadataNative(
        JNIEnv * env,
        jobject j_player,
        jlong native_player) {
    auto *player = reinterpret_cast<tMediaPlayerContext *>(native_player);
    return readMetadata(env, &player->fileMetadata);
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_tans_tmediaplayer_player_tMediaPlayer_getContainerNameNative(
        JNIEnv * env,
        jobject j_player,
        jlong native_player) {
    auto *player = reinterpret_cast<tMediaPlayerContext *>(native_player);
    auto containerName = reinterpret_cast<jstring>(env->NewLocalRef(env->NewStringUTF(player->containerName)));
    return containerName;
}
// endregion

// region Video stream info

extern "C" JNIEXPORT jboolean JNICALL
Java_com_tans_tmediaplayer_player_tMediaPlayer_videoStreamIsAttachmentNative(
        JNIEnv * env,
        jobject j_player,
        jlong native_player) {
    auto *player = reinterpret_cast<tMediaPlayerContext *>(native_player);
    return player->videoIsAttachPic;
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

extern "C" JNIEXPORT jint JNICALL
Java_com_tans_tmediaplayer_player_tMediaPlayer_videoBitrateNative(
        JNIEnv * env,
        jobject j_player,
        jlong native_player) {
    auto *player = reinterpret_cast<tMediaPlayerContext *>(native_player);
    return player->video_bitrate;
}

extern "C" JNIEXPORT jint JNICALL
Java_com_tans_tmediaplayer_player_tMediaPlayer_videoPixelBitDepthNative(
        JNIEnv * env,
        jobject j_player,
        jlong native_player) {
    auto *player = reinterpret_cast<tMediaPlayerContext *>(native_player);
    return player->video_bits_per_raw_sample;
}

extern "C" JNIEXPORT jint JNICALL
Java_com_tans_tmediaplayer_player_tMediaPlayer_videoPixelFmtNative(
        JNIEnv * env,
        jobject j_player,
        jlong native_player) {
    auto *player = reinterpret_cast<tMediaPlayerContext *>(native_player);
    return player->video_pixel_format;
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
Java_com_tans_tmediaplayer_player_tMediaPlayer_videoCodecIdNative(
        JNIEnv * env,
        jobject j_player,
        jlong native_player) {
    auto *player = reinterpret_cast<tMediaPlayerContext *>(native_player);
    return player->video_codec_id;
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_tans_tmediaplayer_player_tMediaPlayer_videoDecoderNameNative(
        JNIEnv * env,
        jobject j_player,
        jlong native_player) {
    auto *player = reinterpret_cast<tMediaPlayerContext *>(native_player);
    auto decoderName = reinterpret_cast<jstring>(env->NewLocalRef(env->NewStringUTF(player->videoDecoderName)));
    return decoderName;
}

extern "C" JNIEXPORT jobjectArray JNICALL
Java_com_tans_tmediaplayer_player_tMediaPlayer_videoStreamMetadataNative(
        JNIEnv * env,
        jobject j_player,
        jlong native_player) {
    auto *player = reinterpret_cast<tMediaPlayerContext *>(native_player);
    return readMetadata(env, player->videoMetaData);
}
// endregion

// region Audio stream info
extern "C" JNIEXPORT jint JNICALL
Java_com_tans_tmediaplayer_player_tMediaPlayer_audioChannelsNative(
        JNIEnv * env,
        jobject j_player,
        jlong native_player) {
    auto *player = reinterpret_cast<tMediaPlayerContext *>(native_player);
    return player->audio_channels;
}

extern "C" JNIEXPORT jint JNICALL
Java_com_tans_tmediaplayer_player_tMediaPlayer_audioPerSampleBytesNative(
        JNIEnv * env,
        jobject j_player,
        jlong native_player) {
    auto *player = reinterpret_cast<tMediaPlayerContext *>(native_player);
    return player->audio_per_sample_bytes;
}

extern "C" JNIEXPORT jint JNICALL
Java_com_tans_tmediaplayer_player_tMediaPlayer_audioBitrateNative(
        JNIEnv * env,
        jobject j_player,
        jlong native_player) {
    auto *player = reinterpret_cast<tMediaPlayerContext *>(native_player);
    return player->audio_bitrate;
}

extern "C" JNIEXPORT jint JNICALL
Java_com_tans_tmediaplayer_player_tMediaPlayer_audioSampleBitDepthNative(
        JNIEnv * env,
        jobject j_player,
        jlong native_player) {
    auto *player = reinterpret_cast<tMediaPlayerContext *>(native_player);
    return player->audio_bits_per_raw_sample;
}

extern "C" JNIEXPORT jint JNICALL
Java_com_tans_tmediaplayer_player_tMediaPlayer_audioSampleFmtNative(
        JNIEnv * env,
        jobject j_player,
        jlong native_player) {
    auto *player = reinterpret_cast<tMediaPlayerContext *>(native_player);
    return player->audio_sample_format;
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

extern "C" JNIEXPORT jint JNICALL
Java_com_tans_tmediaplayer_player_tMediaPlayer_audioCodecIdNative(
        JNIEnv * env,
        jobject j_player,
        jlong native_player) {
    auto *player = reinterpret_cast<tMediaPlayerContext *>(native_player);
    return player->audio_codec_id;
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_tans_tmediaplayer_player_tMediaPlayer_audioDecoderNameNative(
        JNIEnv * env,
        jobject j_player,
        jlong native_player) {
    auto *player = reinterpret_cast<tMediaPlayerContext *>(native_player);
    auto decoderName = reinterpret_cast<jstring>(env->NewLocalRef(env->NewStringUTF(player->audioDecoderName)));
    return decoderName;
}

extern "C" JNIEXPORT jobjectArray JNICALL
Java_com_tans_tmediaplayer_player_tMediaPlayer_audioStreamMetadataNative(
        JNIEnv * env,
        jobject j_player,
        jlong native_player) {
    auto *player = reinterpret_cast<tMediaPlayerContext *>(native_player);
    return readMetadata(env, player->audioMetadata);
}
// endregion

// region Subtitle streams info
extern "C" JNIEXPORT jint JNICALL
Java_com_tans_tmediaplayer_player_tMediaPlayer_subtitleStreamCountNative(
        JNIEnv * env,
        jobject j_player,
        jlong native_player) {
    auto *player = reinterpret_cast<tMediaPlayerContext *>(native_player);
    return player->subtitleStreamCount;
}

extern "C" JNIEXPORT jint JNICALL
Java_com_tans_tmediaplayer_player_tMediaPlayer_subtitleStreamIdNative(
        JNIEnv * env,
        jobject j_player,
        jlong native_player,
        jint index) {
    auto *player = reinterpret_cast<tMediaPlayerContext *>(native_player);
    return player->subtitleStreams[index]->stream->index;
}

extern "C" JNIEXPORT jobjectArray JNICALL
Java_com_tans_tmediaplayer_player_tMediaPlayer_subtitleStreamMetadataNative(
        JNIEnv * env,
        jobject j_player,
        jlong native_player,
        jint index) {
    auto *player = reinterpret_cast<tMediaPlayerContext *>(native_player);
    return readMetadata(env, &player->subtitleStreams[index]->streamMetadata);
}

// endregion

// region Packet buffer
#pragma clang diagnostic push
#pragma ide diagnostic ignored "MemoryLeak"
extern "C" JNIEXPORT jlong JNICALL
Java_com_tans_tmediaplayer_player_tMediaPlayer_allocPacketNative(
        JNIEnv * env,
        jobject j_player) {
    auto pkt = av_packet_alloc();
    return reinterpret_cast<jlong>(pkt);
}
#pragma clang diagnostic pop

extern "C" JNIEXPORT jint JNICALL
Java_com_tans_tmediaplayer_player_tMediaPlayer_getPacketStreamIndexNative(
        JNIEnv * env,
        jobject j_player,
        jlong nativeBuffer) {
    auto *pkt = reinterpret_cast<AVPacket*>(nativeBuffer);
    return pkt->stream_index;
}

extern "C" JNIEXPORT jlong JNICALL
Java_com_tans_tmediaplayer_player_tMediaPlayer_getPacketPtsNative(
        JNIEnv * env,
        jobject j_player,
        jlong nativeBuffer) {
    auto *pkt = reinterpret_cast<AVPacket*>(nativeBuffer);
    if (pkt->pts == AV_NOPTS_VALUE) {
        return 0L;
    } else {
        return (jlong) ((double)pkt->pts * av_q2d(pkt->time_base) * 1000.0);
    }
}

extern "C" JNIEXPORT jlong JNICALL
Java_com_tans_tmediaplayer_player_tMediaPlayer_getPacketDurationNative(
        JNIEnv * env,
        jobject j_player,
        jlong nativeBuffer) {
    auto *pkt = reinterpret_cast<AVPacket*>(nativeBuffer);
    if (pkt->duration == AV_NOPTS_VALUE) {
        return 0L;
    } else {
        return (jlong) ((double)pkt->duration * av_q2d(pkt->time_base) * 1000.0);
    }
}

extern "C" JNIEXPORT jint JNICALL
Java_com_tans_tmediaplayer_player_tMediaPlayer_getPacketBytesSizeNative(
        JNIEnv * env,
        jobject j_player,
        jlong nativeBuffer) {
    auto *pkt = reinterpret_cast<AVPacket*>(nativeBuffer);
    return pkt->size;
}

extern "C" JNIEXPORT void JNICALL
Java_com_tans_tmediaplayer_player_tMediaPlayer_releasePacketNative(
        JNIEnv * env,
        jobject j_player,
        jlong nativeBuffer) {
    auto *pkt = reinterpret_cast<AVPacket*>(nativeBuffer);
    av_packet_unref(pkt);
    av_packet_free(&pkt);
}
// endregion

// region VideoBuffer
#pragma clang diagnostic push
#pragma ide diagnostic ignored "MemoryLeak"
extern "C" JNIEXPORT jlong JNICALL
Java_com_tans_tmediaplayer_player_tMediaPlayer_allocVideoBufferNative(
        JNIEnv * env,
        jobject j_player) {
    auto buffer = new tMediaVideoBuffer;
    return reinterpret_cast<jlong>(buffer);
}
#pragma clang diagnostic pop

extern "C" JNIEXPORT jlong JNICALL
Java_com_tans_tmediaplayer_player_tMediaPlayer_getVideoPtsNative(
        JNIEnv * env,
        jobject j_player,
        jlong buffer_l) {
    auto buffer = reinterpret_cast<tMediaVideoBuffer *>(buffer_l);
    return buffer -> pts;
}

extern "C" JNIEXPORT jlong JNICALL
Java_com_tans_tmediaplayer_player_tMediaPlayer_getVideoDurationNative(
        JNIEnv * env,
        jobject j_player,
        jlong buffer_l) {
    auto buffer = reinterpret_cast<tMediaVideoBuffer *>(buffer_l);
    return buffer -> duration;
}

extern "C" JNIEXPORT jint JNICALL
Java_com_tans_tmediaplayer_player_tMediaPlayer_getVideoWidthNative(
        JNIEnv * env,
        jobject j_player,
        jlong buffer_l) {
    auto buffer = reinterpret_cast<tMediaVideoBuffer *>(buffer_l);
    return buffer -> width;
}

extern "C" JNIEXPORT jint JNICALL
Java_com_tans_tmediaplayer_player_tMediaPlayer_getVideoHeightNative(
        JNIEnv * env,
        jobject j_player,
        jlong buffer_l) {
    auto buffer = reinterpret_cast<tMediaVideoBuffer *>(buffer_l);
    return buffer -> height;
}

extern "C" JNIEXPORT jint JNICALL
Java_com_tans_tmediaplayer_player_tMediaPlayer_getVideoFrameTypeNative(
        JNIEnv * env,
        jobject j_player,
        jlong buffer_l) {
    auto buffer = reinterpret_cast<tMediaVideoBuffer *>(buffer_l);
    return buffer->type;
}

extern "C" JNIEXPORT jint JNICALL
Java_com_tans_tmediaplayer_player_tMediaPlayer_getVideoFrameRgbaSizeNative(
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
Java_com_tans_tmediaplayer_player_tMediaPlayer_getVideoFrameRgbaBytesNative(
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
Java_com_tans_tmediaplayer_player_tMediaPlayer_getVideoFrameYSizeNative(
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
Java_com_tans_tmediaplayer_player_tMediaPlayer_getVideoFrameYBytesNative(
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
Java_com_tans_tmediaplayer_player_tMediaPlayer_getVideoFrameUSizeNative(
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
Java_com_tans_tmediaplayer_player_tMediaPlayer_getVideoFrameUBytesNative(
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
Java_com_tans_tmediaplayer_player_tMediaPlayer_getVideoFrameVSizeNative(
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
Java_com_tans_tmediaplayer_player_tMediaPlayer_getVideoFrameVBytesNative(
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
Java_com_tans_tmediaplayer_player_tMediaPlayer_getVideoFrameUVSizeNative(
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
Java_com_tans_tmediaplayer_player_tMediaPlayer_getVideoFrameUVBytesNative(
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
Java_com_tans_tmediaplayer_player_tMediaPlayer_releaseVideoBufferNative(
        JNIEnv * env,
        jobject j_player,
        jlong native_buffer) {
    auto buffer = reinterpret_cast<tMediaVideoBuffer *>(native_buffer);
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
Java_com_tans_tmediaplayer_player_tMediaPlayer_allocAudioBufferNative(
        JNIEnv * env,
        jobject j_player) {
    auto buffer = new tMediaAudioBuffer;
    return reinterpret_cast<jlong>(buffer);
}
#pragma clang diagnostic pop

extern "C" JNIEXPORT void JNICALL
Java_com_tans_tmediaplayer_player_tMediaPlayer_getAudioFrameBytesNative(
        JNIEnv * env,
        jobject j_player,
        jlong buffer_l,
        jbyteArray j_bytes) {
    auto buffer = reinterpret_cast<tMediaAudioBuffer *>(buffer_l);
    env->SetByteArrayRegion(j_bytes, 0, buffer->contentSize,
                            reinterpret_cast<const jbyte *>(buffer->pcmBuffer));
}

extern "C" JNIEXPORT jint JNICALL
Java_com_tans_tmediaplayer_player_tMediaPlayer_getAudioFrameSizeNative(
        JNIEnv * env,
        jobject j_player,
        jlong buffer_l) {
    auto buffer = reinterpret_cast<tMediaAudioBuffer *>(buffer_l);
    return buffer->contentSize;
}

extern "C" JNIEXPORT jlong JNICALL
Java_com_tans_tmediaplayer_player_tMediaPlayer_getAudioPtsNative(
        JNIEnv * env,
        jobject j_player,
        jlong buffer_l) {
    auto buffer = reinterpret_cast<tMediaAudioBuffer *>(buffer_l);
    return buffer->pts;
}

extern "C" JNIEXPORT jlong JNICALL
Java_com_tans_tmediaplayer_player_tMediaPlayer_getAudioDurationNative(
        JNIEnv * env,
        jobject j_player,
        jlong buffer_l) {
    auto buffer = reinterpret_cast<tMediaAudioBuffer *>(buffer_l);
    return buffer->duration;
}

extern "C" JNIEXPORT void JNICALL
Java_com_tans_tmediaplayer_player_tMediaPlayer_releaseAudioBufferNative(
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
