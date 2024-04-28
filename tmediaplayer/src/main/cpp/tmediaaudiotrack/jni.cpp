#include <jni.h>
#include "tmediaaudiotrack.h"
#include "tmediaplayer.h"

extern "C" JNIEXPORT jlong JNICALL
Java_com_tans_tmediaplayer_audiotrack_tMediaAudioTrack_createAudioTrackNative(
        JNIEnv * env,
        jobject j_audio_track) {
    JavaVM * jvm = nullptr;
    env->GetJavaVM(&jvm);
    auto audioTrack = new tMediaAudioTrackContext;
    audioTrack->jvm = jvm;
    return reinterpret_cast<jlong>(audioTrack);
}

extern "C" JNIEXPORT jint JNICALL
Java_com_tans_tmediaplayer_audiotrack_tMediaAudioTrack_prepareNative(
        JNIEnv * env,
        jobject j_audio_track,
        jlong native_audio_track,
        jint bufferQueueSize) {
    auto audioTrack = reinterpret_cast<tMediaAudioTrackContext *>(native_audio_track);
    return audioTrack->prepare(bufferQueueSize);
}

extern "C" JNIEXPORT jint JNICALL
Java_com_tans_tmediaplayer_audiotrack_tMediaAudioTrack_enqueueBufferNative(
        JNIEnv * env,
        jobject j_audio_track,
        jlong native_audio_track,
        jlong native_buffer) {
    auto audioTrack = reinterpret_cast<tMediaAudioTrackContext *>(native_audio_track);
    auto buffer = reinterpret_cast<tMediaDecodeBuffer *>(native_buffer);
    return audioTrack->enqueueBuffer(buffer->audioBuffer);
}

extern "C" JNIEXPORT jint JNICALL
Java_com_tans_tmediaplayer_audiotrack_tMediaAudioTrack_clearBuffersNative(
        JNIEnv * env,
        jobject j_audio_track,
        jlong native_audio_track) {
    auto audioTrack = reinterpret_cast<tMediaAudioTrackContext *>(native_audio_track);
    return audioTrack->clearBuffers();
}

extern "C" JNIEXPORT jint JNICALL
Java_com_tans_tmediaplayer_audiotrack_tMediaAudioTrack_playNative(
        JNIEnv * env,
        jobject j_audio_track,
        jlong native_audio_track) {
    auto audioTrack = reinterpret_cast<tMediaAudioTrackContext *>(native_audio_track);
    return audioTrack->play();
}

extern "C" JNIEXPORT jint JNICALL
Java_com_tans_tmediaplayer_audiotrack_tMediaAudioTrack_pauseNative(
        JNIEnv * env,
        jobject j_audio_track,
        jlong native_audio_track) {
    auto audioTrack = reinterpret_cast<tMediaAudioTrackContext *>(native_audio_track);
    return audioTrack->pause();
}

extern "C" JNIEXPORT void JNICALL
Java_com_tans_tmediaplayer_audiotrack_tMediaAudioTrack_releaseNative(
        JNIEnv * env,
        jobject j_audio_track,
        jlong native_audio_track) {
    auto audioTrack = reinterpret_cast<tMediaAudioTrackContext *>(native_audio_track);
    return audioTrack->release();
}

