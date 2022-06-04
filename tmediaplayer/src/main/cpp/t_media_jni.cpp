#include <jni.h>
#include <string>
#include <media_player.h>
#include <android/native_window_jni.h>
#include <android/native_window.h>

extern "C" JNIEXPORT jlong JNICALL
Java_com_tans_tmediaplayer_MediaPlayer_setupPlayerNative(
        JNIEnv * env,
        jobject j_player,
        jstring file_path) {
    const char * file_path_chars = env->GetStringUTFChars(file_path, 0);
    auto player = new MediaPlayerContext;
    auto result = player->setup_media_player(file_path_chars);
    if (result == OPT_SUCCESS) {
        return reinterpret_cast<jlong>(player);
    } else {
        return OPT_FAIL;
    }
}

extern "C"
JNIEXPORT jlong JNICALL
Java_com_tans_tmediaplayer_MediaPlayer_getDurationNative(JNIEnv *env, jobject thiz,
                                                         jlong j_player_id) {
    MediaPlayerContext* media_player_data= reinterpret_cast<MediaPlayerContext *>(j_player_id);
    if (media_player_data != nullptr) {
        return media_player_data->duration;
    } else {
        return 0L;
    }
}

extern "C" JNIEXPORT jint JNICALL
Java_com_tans_tmediaplayer_MediaPlayer_setWindowNative(
        JNIEnv * env,
        jobject j_player,
        jlong j_player_id,
        jobject j_surface) {
    MediaPlayerContext* media_player_data= reinterpret_cast<MediaPlayerContext *>(j_player_id);
    if (media_player_data != nullptr) {
        if (j_surface != nullptr) {
            auto native_window = ANativeWindow_fromSurface(env, j_surface);
            media_player_data->set_window(native_window);
        } else {
            media_player_data->set_window(nullptr);
        }
        return OPT_SUCCESS;
    } else {
        return OPT_FAIL;
    }
}

extern "C"
JNIEXPORT jint JNICALL
Java_com_tans_tmediaplayer_MediaPlayer_resetPlayProgress(JNIEnv *env, jobject thiz,
                                                         jlong j_player_id) {

    MediaPlayerContext* media_player_data= reinterpret_cast<MediaPlayerContext *>(j_player_id);
    if (media_player_data != nullptr) {
        return media_player_data->reset_play_progress();
    } else {
        return OPT_FAIL;
    }
}


extern "C" JNIEXPORT jlongArray JNICALL
Java_com_tans_tmediaplayer_MediaPlayer_decodeNextFrameNative(
        JNIEnv * env,
        jobject j_player,
        jlong j_player_id,
        jlong j_data_id) {
    MediaPlayerContext* media_player_data = reinterpret_cast<MediaPlayerContext *>(j_player_id);
    RenderRawData* raw_data = reinterpret_cast<RenderRawData *>(j_data_id);
    if (media_player_data != nullptr && raw_data != nullptr) {
        auto decode_result = media_player_data->decode_next_frame(raw_data);
        jlong j_buff[2] = {decode_result, raw_data->video_data->pts};
        auto result = env->NewLongArray(2);
        env->SetLongArrayRegion(result, 0, 2, j_buff);
        return result;
    } else {
        jlong j_buff[2] = {DECODE_FRAME_FAIL, 0};
        auto result = env->NewLongArray(2);
        env->SetLongArrayRegion(result, 0, 2, j_buff);
        return result;
    }
}

extern "C" JNIEXPORT jint JNICALL
Java_com_tans_tmediaplayer_MediaPlayer_renderRawDataNative(
        JNIEnv * env,
        jobject j_player,
        jlong j_player_id,
        jlong j_data_id) {
    MediaPlayerContext* media_player_data = reinterpret_cast<MediaPlayerContext *>(j_player_id);
    RenderRawData* raw_data = reinterpret_cast<RenderRawData *>(j_data_id);
    if (media_player_data != nullptr && raw_data != nullptr) {
        return media_player_data->render_raw_data(raw_data);
    } else {
        return OPT_FAIL;
    }
}

extern "C"
JNIEXPORT jlong JNICALL
Java_com_tans_tmediaplayer_MediaPlayer_newRawDataNative(JNIEnv *env, jobject thiz, jlong j_player_id) {
    MediaPlayerContext* media_player_data = reinterpret_cast<MediaPlayerContext *>(j_player_id);
    if (media_player_data != nullptr) {
        auto data = media_player_data->new_render_raw_data();
        return reinterpret_cast<jlong>(data);
    } else {
        return OPT_FAIL;
    }
}

extern "C"
JNIEXPORT jint JNICALL
Java_com_tans_tmediaplayer_MediaPlayer_releaseRawDataNative(JNIEnv *env, jobject thiz,
                                                            jlong j_player_id, jlong j_data_id) {
    MediaPlayerContext* media_player_data = reinterpret_cast<MediaPlayerContext *>(j_player_id);
    RenderRawData* raw_data = reinterpret_cast<RenderRawData *>(j_data_id);
    if (media_player_data != nullptr && raw_data != nullptr) {
        media_player_data->release_render_raw_data(raw_data);
        return OPT_SUCCESS;
    } else {
        return OPT_FAIL;
    }
}

extern "C" JNIEXPORT void JNICALL
Java_com_tans_tmediaplayer_MediaPlayer_releasePlayerNative(
        JNIEnv * env,
        jobject j_player,
        jlong j_player_id) {
    MediaPlayerContext* media_player_data = reinterpret_cast<MediaPlayerContext *>(j_player_id);
    if (media_player_data != nullptr) {
        media_player_data->release_media_player();
    }
}