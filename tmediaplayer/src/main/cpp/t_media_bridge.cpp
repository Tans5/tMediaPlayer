#include <jni.h>
#include <string>
#include <media_player.h>
#include <android/native_window_jni.h>
#include <android/native_window.h>


 MediaPlayerContext *media_player_data;

extern "C" JNIEXPORT void JNICALL
Java_com_tans_tmediaplayer_MediaPlayer_setupPlayerNative(
        JNIEnv * env,
        jobject j_player,
        jstring file_path) {
    const char * file_path_chars = env->GetStringUTFChars(file_path, 0);
    media_player_data = new MediaPlayerContext;
    media_player_data->setup_media_player(file_path_chars);
}

extern "C" JNIEXPORT void JNICALL
Java_com_tans_tmediaplayer_MediaPlayer_setWindowNative(
        JNIEnv * env,
        jobject j_player,
        jobject j_surface) {
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
        jobject j_player) {
    if (media_player_data != nullptr) {
        media_player_data->decode();
    }
}

extern "C" JNIEXPORT void JNICALL
Java_com_tans_tmediaplayer_MediaPlayer_releasePlayerNative(
        JNIEnv * env,
        jobject j_player) {
    if (media_player_data != nullptr) {
        media_player_data->release_media_player();
    }
    media_player_data = nullptr;
}