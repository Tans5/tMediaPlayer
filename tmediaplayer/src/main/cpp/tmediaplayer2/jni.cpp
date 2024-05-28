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
