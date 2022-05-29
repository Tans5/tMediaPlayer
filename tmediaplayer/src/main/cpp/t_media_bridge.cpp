#include <jni.h>
#include <string>
#include <media_player.h>
#include <android/native_window_jni.h>
#include <android/native_window.h>
#include "media_player_pool.h"

PlayerPool* player_pool = new PlayerPool;

extern "C" JNIEXPORT jlong JNICALL
Java_com_tans_tmediaplayer_MediaPlayer_setupPlayerNative(
        JNIEnv * env,
        jobject j_player,
        jstring file_path) {
    const char * file_path_chars = env->GetStringUTFChars(file_path, 0);
    auto player = new MediaPlayerContext;
    long id = player_pool->add_player(player);
    player->setup_media_player(file_path_chars);
    return id;
}

extern "C" JNIEXPORT void JNICALL
Java_com_tans_tmediaplayer_MediaPlayer_setWindowNative(
        JNIEnv * env,
        jobject j_player,
        jlong j_player_id,
        jobject j_surface) {
    auto media_player_data= player_pool->get_player(j_player_id);
    if (media_player_data != nullptr) {
        if (j_surface != nullptr) {
            auto native_window = ANativeWindow_fromSurface(env, j_surface);
            media_player_data->set_window(native_window);
        } else {
            media_player_data->set_window(nullptr);
        }
    }
}


extern "C" JNIEXPORT void JNICALL
Java_com_tans_tmediaplayer_MediaPlayer_decodeNative(
        JNIEnv * env,
        jobject j_player,
        jlong j_player_id) {
    auto media_player_data= player_pool->get_player(j_player_id);
    if (media_player_data != nullptr) {
        media_player_data->decode();
    }
}

extern "C" JNIEXPORT void JNICALL
Java_com_tans_tmediaplayer_MediaPlayer_releasePlayerNative(
        JNIEnv * env,
        jobject j_player,
        jlong j_player_id) {
    auto media_player_data= player_pool->get_player(j_player_id);
    if (media_player_data != nullptr) {
        media_player_data->release_media_player();
    }
    player_pool->remove_player(j_player_id);
}