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
    audioTrack->j_audioTrack = env->NewGlobalRef(j_audio_track);
    auto clazz = reinterpret_cast<jclass>(env->NewLocalRef(env->FindClass("com/tans/tmediaplayer/audiotrack/tMediaAudioTrack")));
    audioTrack->j_callbackMethodId = env->GetMethodID(clazz, "audioTrackQueueCallback", "()V");
    env->DeleteLocalRef(clazz);
    return reinterpret_cast<jlong>(audioTrack);
}

extern "C" JNIEXPORT jint JNICALL
Java_com_tans_tmediaplayer_audiotrack_tMediaAudioTrack_prepareNative(
        JNIEnv * env,
        jobject j_audio_track,
        jlong native_audio_track,
        jint bufferQueueSize,
        jint outputChannels,
        jint outputSampleRate,
        jint outputSampleBitDepth) {
    auto audioTrack = reinterpret_cast<tMediaAudioTrackContext *>(native_audio_track);
    return audioTrack->prepare(bufferQueueSize, outputChannels, outputSampleRate, outputSampleBitDepth);
}

extern "C" JNIEXPORT jint JNICALL
Java_com_tans_tmediaplayer_audiotrack_tMediaAudioTrack_enqueueBufferNative(
        JNIEnv * env,
        jobject j_audio_track,
        jlong native_audio_track,
        jlong native_buffer) {
    auto audioTrack = reinterpret_cast<tMediaAudioTrackContext *>(native_audio_track);
    auto buffer = reinterpret_cast<tMediaAudioBuffer *>(native_buffer);
    return audioTrack->enqueueBuffer(buffer);
}

extern "C" JNIEXPORT jint JNICALL
Java_com_tans_tmediaplayer_audiotrack_tMediaAudioTrack_getBufferQueueCountNative(
        JNIEnv * env,
        jobject j_audio_track,
        jlong native_audio_track) {
    auto audioTrack = reinterpret_cast<tMediaAudioTrackContext *>(native_audio_track);
    return audioTrack->getBufferQueueCount();
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

extern "C" JNIEXPORT jint JNICALL
Java_com_tans_tmediaplayer_audiotrack_tMediaAudioTrack_stopNative(
        JNIEnv * env,
        jobject j_audio_track,
        jlong native_audio_track) {
    auto audioTrack = reinterpret_cast<tMediaAudioTrackContext *>(native_audio_track);
    return audioTrack->stop();
}

extern "C" JNIEXPORT void JNICALL
Java_com_tans_tmediaplayer_audiotrack_tMediaAudioTrack_releaseNative(
        JNIEnv * env,
        jobject j_audio_track,
        jlong native_audio_track) {
    auto audioTrack = reinterpret_cast<tMediaAudioTrackContext *>(native_audio_track);
    audioTrack->release();
    audioTrack->jvm = nullptr;
    env->DeleteGlobalRef(audioTrack->j_audioTrack);
    audioTrack->j_audioTrack = nullptr;
    audioTrack->j_callbackMethodId = nullptr;
    delete audioTrack;
}

