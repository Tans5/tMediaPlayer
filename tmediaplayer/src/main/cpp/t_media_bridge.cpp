#include <jni.h>
#include <string>
#include <media_player.h>
#include <android/native_window_jni.h>
#include <android/native_window.h>

extern "C" JNIEXPORT void JNICALL
Java_com_tans_tmediaplayer_MediaPlayer_setupPlayerNative(
        JNIEnv * env,
        jobject j_player,
        jstring file_path) {
    const char * file_path_chars = env->GetStringUTFChars(file_path, 0);
    setup_media_player(file_path_chars);
}

extern "C" JNIEXPORT void JNICALL
Java_com_tans_tmediaplayer_MediaPlayer_setWindow(
        JNIEnv * env,
        jobject j_player,
        jobject j_surface) {
    auto native_window = ANativeWindow_fromSurface(env, j_surface);
    set_window(native_window);
}

extern "C" JNIEXPORT void JNICALL
Java_com_tans_tmediaplayer_MediaPlayer_releasePlayerNative(
        JNIEnv * env,
        jobject j_player) {
    release_media_player();
}