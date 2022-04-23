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
    setup_media_player(media_player_data, file_path_chars);
}

extern "C" JNIEXPORT void JNICALL
Java_com_tans_tmediaplayer_MediaPlayer_setWindowNative(
        JNIEnv * env,
        jobject j_player,
        jobject j_surface) {
    if (media_player_data != nullptr) {
        if (j_surface != nullptr) {
            auto native_window = ANativeWindow_fromSurface(env, j_surface);
            set_window(media_player_data, native_window);
        } else {
            set_window(media_player_data, nullptr);
        }
    }
}


extern "C" JNIEXPORT void JNICALL
Java_com_tans_tmediaplayer_MediaPlayer_decodeNative(
        JNIEnv * env,
        jobject j_player) {
    if (media_player_data != nullptr) {
        decode(media_player_data);
    }
}

extern "C" JNIEXPORT void JNICALL
Java_com_tans_tmediaplayer_MediaPlayer_releasePlayerNative(
        JNIEnv * env,
        jobject j_player) {
    if (media_player_data != nullptr) {
        release_media_player(media_player_data);
    }
    media_player_data = nullptr;
}