//
// Created by pengcheng.tan on 2024/7/3.
//
#include <jni.h>
#include "tmediasubtitlepktreader.h"

extern "C" JNIEXPORT jlong JNICALL
Java_com_tans_tmediaplayer_subtitle_ExternalSubtitle_createExternalSubtitlePktReaderNative(
        JNIEnv * env,
        jobject j_subtitle) {
    auto ctx = new tMediaSubtitlePktReaderContext;
    return reinterpret_cast<jlong>(ctx);
}

extern "C" JNIEXPORT jint JNICALL
Java_com_tans_tmediaplayer_subtitle_ExternalSubtitle_loadFileNative(
        JNIEnv * env,
        jobject j_subtitle,
        jlong native_reader,
        jstring j_file_path) {
    auto readerCtx = reinterpret_cast<tMediaSubtitlePktReaderContext *>(native_reader);
    const char *file_path = env->GetStringUTFChars(j_file_path, JNI_FALSE);
    auto result = readerCtx->prepare(file_path);
    env->ReleaseStringUTFChars(j_file_path, file_path);
    return result;
}

extern "C" JNIEXPORT jint JNICALL
Java_com_tans_tmediaplayer_subtitle_ExternalSubtitle_seekToNative(
        JNIEnv * env,
        jobject j_subtitle,
        jlong native_reader,
        jlong j_position) {
    auto readerCtx = reinterpret_cast<tMediaSubtitlePktReaderContext *>(native_reader);
    return readerCtx->seekTo(j_position);
}

extern "C" JNIEXPORT jint JNICALL
Java_com_tans_tmediaplayer_subtitle_ExternalSubtitle_readPacketNative(
        JNIEnv * env,
        jobject j_subtitle,
        jlong native_reader) {
    auto readerCtx = reinterpret_cast<tMediaSubtitlePktReaderContext *>(native_reader);
    return readerCtx->readPacket();
}

extern "C" JNIEXPORT void JNICALL
Java_com_tans_tmediaplayer_subtitle_ExternalSubtitle_movePacketRefNative(
        JNIEnv * env,
        jobject j_subtitle,
        jlong native_reader,
        jlong native_pkt) {
    auto readerCtx = reinterpret_cast<tMediaSubtitlePktReaderContext *>(native_reader);
    auto pkt = reinterpret_cast<AVPacket *>(native_pkt);
    readerCtx->movePacketRef(pkt);
}

extern "C" JNIEXPORT void JNICALL
Java_com_tans_tmediaplayer_subtitle_ExternalSubtitle_releaseNative(
        JNIEnv * env,
        jobject j_subtitle,
        jlong native_reader) {
    auto readerCtx = reinterpret_cast<tMediaSubtitlePktReaderContext *>(native_reader);
    readerCtx->release();
    delete readerCtx;
}
