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
