//
// Created by pengcheng.tan on 2023/7/13.
//
#include <tmediaplayer.h>
#include <jni.h>
#include <string>
extern "C" {
#include "libavcodec/jni.h"
}


extern "C" JNIEXPORT jlong JNICALL
Java_com_tans_tmediaplayer_tMediaPlayer_createPlayerNative(
        JNIEnv * env,
        jobject j_player) {
    JavaVM * jvm = nullptr;
    env->GetJavaVM(&jvm);
    auto player = new tMediaPlayerContext;
    player->jplayer = j_player;
    player->jvm = jvm;
    player->jniEnv = env;
    return reinterpret_cast<jlong>(player);
}

extern "C" JNIEXPORT jint JNICALL
Java_com_tans_tmediaplayer_tMediaPlayer_prepareNative(
        JNIEnv * env,
        jobject j_player,
        jlong native_player,
        jstring file_path,
        jboolean requestHw,
        jint targetAudioChannels) {
    auto *player = reinterpret_cast<tMediaPlayerContext *>(native_player);
    if (player == nullptr) {
        return Fail;
    }
    av_jni_set_java_vm(player->jvm, nullptr);
    const char * file_path_chars = env->GetStringUTFChars(file_path, 0);
    return player->prepare(file_path_chars, requestHw, targetAudioChannels);
}

extern "C" JNIEXPORT jlong JNICALL
Java_com_tans_tmediaplayer_tMediaPlayer_durationNative(
        JNIEnv * env,
        jobject j_player,
        jlong native_player) {
    auto *player = reinterpret_cast<tMediaPlayerContext *>(native_player);
    return player->duration;
}

extern "C" JNIEXPORT jint JNICALL
Java_com_tans_tmediaplayer_tMediaPlayer_videoWidthNative(
        JNIEnv * env,
        jobject j_player,
        jlong native_player) {
    auto *player = reinterpret_cast<tMediaPlayerContext *>(native_player);
    return player->video_width;
}

extern "C" JNIEXPORT jint JNICALL
Java_com_tans_tmediaplayer_tMediaPlayer_videoHeightNative(
        JNIEnv * env,
        jobject j_player,
        jlong native_player) {
    auto *player = reinterpret_cast<tMediaPlayerContext *>(native_player);
    return player->video_height;
}

extern "C" JNIEXPORT jdouble JNICALL
Java_com_tans_tmediaplayer_tMediaPlayer_videoFpsNative(
        JNIEnv * env,
        jobject j_player,
        jlong native_player) {
    auto *player = reinterpret_cast<tMediaPlayerContext *>(native_player);
    return player->video_fps;
}

extern "C" JNIEXPORT jlong JNICALL
Java_com_tans_tmediaplayer_tMediaPlayer_videoDurationNative(
        JNIEnv * env,
        jobject j_player,
        jlong native_player) {
    auto *player = reinterpret_cast<tMediaPlayerContext *>(native_player);
    return player->video_duration;
}

extern "C" JNIEXPORT jint JNICALL
Java_com_tans_tmediaplayer_tMediaPlayer_audioChannelsNative(
        JNIEnv * env,
        jobject j_player,
        jlong native_player) {
    auto *player = reinterpret_cast<tMediaPlayerContext *>(native_player);
    return player->audio_channels;
}

extern "C" JNIEXPORT jint JNICALL
Java_com_tans_tmediaplayer_tMediaPlayer_audioPreSampleBytesNative(
        JNIEnv * env,
        jobject j_player,
        jlong native_player) {
    auto *player = reinterpret_cast<tMediaPlayerContext *>(native_player);
    return player->audio_pre_sample_bytes;
}

extern "C" JNIEXPORT jint JNICALL
Java_com_tans_tmediaplayer_tMediaPlayer_audioSampleRateNative(
        JNIEnv * env,
        jobject j_player,
        jlong native_player) {
    auto *player = reinterpret_cast<tMediaPlayerContext *>(native_player);
    return player->audio_simple_rate;
}

extern "C" JNIEXPORT jlong JNICALL
Java_com_tans_tmediaplayer_tMediaPlayer_audioDurationNative(
        JNIEnv * env,
        jobject j_player,
        jlong native_player) {
    auto *player = reinterpret_cast<tMediaPlayerContext *>(native_player);
    return player->audio_duration;
}


extern "C" JNIEXPORT jlong JNICALL
Java_com_tans_tmediaplayer_tMediaPlayer_allocDecodeDataNative(
        JNIEnv * env,
        jobject j_player,
        jlong player_l) {
    auto player = reinterpret_cast<tMediaPlayerContext *>(player_l);
    auto buffer = player->allocDecodeBuffer();
    return reinterpret_cast<jlong>(buffer);
}

extern "C" JNIEXPORT void JNICALL
Java_com_tans_tmediaplayer_tMediaPlayer_freeDecodeDataNative(
        JNIEnv * env,
        jobject j_player,
        jlong player_l,
        jlong buffer_l) {
    auto player = reinterpret_cast<tMediaPlayerContext *>(player_l);
    auto buffer = reinterpret_cast<tMediaDecodeBuffer *>(buffer_l);
    player->freeDecodeBuffer(buffer);
}

extern "C" JNIEXPORT void JNICALL
Java_com_tans_tmediaplayer_tMediaPlayer_releaseNative(
        JNIEnv * env,
        jobject j_player,
        jlong native_player) {
    auto *player = reinterpret_cast<tMediaPlayerContext *>(native_player);
    player->release();
}
