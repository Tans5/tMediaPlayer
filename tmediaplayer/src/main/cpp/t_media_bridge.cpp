#include <jni.h>
#include <string>
#include <media_player.h>

extern "C" JNIEXPORT void JNICALL
Java_com_tans_tmediaplayer_MediaPlayer_setupPlayerNative(
        JNIEnv * env,
        jobject j_player,
        jstring file_path) {
    const char * file_path_chars = env->GetStringUTFChars(file_path, 0);
    setup_media_player(file_path_chars);
}

extern "C" JNIEXPORT void JNICALL
Java_com_tans_tmediaplayer_MediaPlayer_releasePlayerNative(
        JNIEnv * env,
        jobject j_player) {
    release_media_player();
}