//
// Created by pengcheng.tan on 2024/6/27.
//

#include <jni.h>
#include "tmediasubtitle.h"


extern "C" JNIEXPORT jlong JNICALL
Java_com_tans_tmediaplayer_subtitle_tMediaSubtitle_createSubtitleNative(
        JNIEnv * env,
        jobject j_subtitle) {
    auto ctx = new tMediaSubtitleContext ;
    return reinterpret_cast<jlong>(ctx);
}

extern "C" JNIEXPORT jint JNICALL
Java_com_tans_tmediaplayer_subtitle_tMediaSubtitle_setupSubtitleStreamFromPlayerNative(
        JNIEnv * env,
        jobject j_subtitle,
        jlong native_subtitle,
        jlong native_player,
        jint stream_index) {
    auto subtitle = reinterpret_cast<tMediaSubtitleContext *>(native_subtitle);
    auto player = reinterpret_cast<tMediaPlayerContext *>(native_player);
    AVStream *targetStream = nullptr;
    for (int i = 0; i < player->subtitleStreamCount; i ++) {
        auto s = player->subtitleStreams[i];
        if (s->stream->index == stream_index) {
            targetStream = s->stream;
            break;
        }
    }
    if (targetStream != nullptr) {
        return subtitle->setupNewSubtitleStream(targetStream);
    } else {
        LOGE("Wrong stream index: %d", stream_index);
        return OptFail;
    }
}

extern "C" JNIEXPORT jint JNICALL
Java_com_tans_tmediaplayer_subtitle_tMediaSubtitle_decodeSubtitleNative(
        JNIEnv * env,
        jobject j_subtitle,
        jlong native_subtitle,
        jlong native_pkt) {
    auto subtitle = reinterpret_cast<tMediaSubtitleContext *>(native_subtitle);
    if (native_pkt == 0) {
        return subtitle->decodeSubtitle(nullptr);
    } else {
        return subtitle->decodeSubtitle(reinterpret_cast<AVPacket *>(native_pkt));
    }
}

extern "C" JNIEXPORT void JNICALL
Java_com_tans_tmediaplayer_subtitle_tMediaSubtitle_flushSubtitleDecoderNative(
        JNIEnv * env,
        jobject j_subtitle,
        jlong native_subtitle) {
    auto subtitle = reinterpret_cast<tMediaSubtitleContext *>(native_subtitle);
    subtitle->flushDecoder();
}

extern "C" JNIEXPORT jlong JNICALL
Java_com_tans_tmediaplayer_subtitle_tMediaSubtitle_allocSubtitleBufferNative(
        JNIEnv * env,
        jobject j_subtitle) {
    auto buffer = new tMediaSubtitleBuffer;
    buffer->subtitle_frame = new AVSubtitle;
    return reinterpret_cast<jlong>(buffer);
}

extern "C" JNIEXPORT void JNICALL
Java_com_tans_tmediaplayer_subtitle_tMediaSubtitle_moveDecodedSubtitleFrameToBufferNative(
        JNIEnv * env,
        jobject j_subtitle,
        jlong native_subtitle,
        jlong native_buffer) {
    auto subtitle = reinterpret_cast<tMediaSubtitleContext *>(native_subtitle);
    auto buffer = reinterpret_cast<tMediaSubtitleBuffer *>(native_buffer);
    auto src = subtitle->subtitle_frame;
    auto target = buffer->subtitle_frame;
    target->format = src->format;
    target->start_display_time = src->start_display_time;
    target->end_display_time = src->end_display_time;
    target->num_rects = src->num_rects;
    target->rects = src->rects;
    target->pts = src->pts;
}

extern "C" JNIEXPORT jlong JNICALL
Java_com_tans_tmediaplayer_subtitle_tMediaSubtitle_getSubtitleStartPtsNative(
        JNIEnv * env,
        jobject j_subtitle,
        jlong native_buffer) {
    auto buffer = reinterpret_cast<tMediaSubtitleBuffer *>(native_buffer);
    return buffer->subtitle_frame->start_display_time;
}

extern "C" JNIEXPORT jlong JNICALL
Java_com_tans_tmediaplayer_subtitle_tMediaSubtitle_getSubtitleEndPtsNative(
        JNIEnv * env,
        jobject j_subtitle,
        jlong native_buffer) {
    auto buffer = reinterpret_cast<tMediaSubtitleBuffer *>(native_buffer);
    return buffer->subtitle_frame->end_display_time;
}

extern "C" JNIEXPORT jobjectArray JNICALL
Java_com_tans_tmediaplayer_subtitle_tMediaSubtitle_getSubtitleStringsNative(
        JNIEnv * env,
        jobject j_subtitle,
        jlong native_buffer) {
    auto buffer = reinterpret_cast<tMediaSubtitleBuffer *>(native_buffer);
    auto subtitleFrame = buffer->subtitle_frame;
    auto lineSize = subtitleFrame->num_rects;
    auto subtitleRects = subtitleFrame->rects;

    auto stringClazz = reinterpret_cast<jclass> (env->NewLocalRef(env->FindClass("java/lang/String")));
    auto  jarray = reinterpret_cast<jobjectArray>(env->NewLocalRef(env->NewObjectArray(lineSize, stringClazz, nullptr)));
    for (int i = 0; i < lineSize; i ++) {
        auto rect = subtitleRects[i];
        const char * line = "";
        switch (rect->type) {
            case SUBTITLE_TEXT:
                line = rect->text;
                break;
            case SUBTITLE_ASS:
                line = rect->ass;
                break;
            default:
                LOGE("Don't support subtitle format: %d", rect->type);
                break;
        }
        auto j_string = reinterpret_cast<jstring>(env->NewLocalRef(env->NewStringUTF(line)));
        env->SetObjectArrayElement(jarray, i, j_string);
    }
    return jarray;
}

extern "C" JNIEXPORT void JNICALL
Java_com_tans_tmediaplayer_subtitle_tMediaSubtitle_releaseSubtitleBufferNative(
        JNIEnv * env,
        jobject j_subtitle,
        jlong native_buffer) {
    auto buffer = reinterpret_cast<tMediaSubtitleBuffer *>(native_buffer);
    avsubtitle_free(buffer->subtitle_frame);
    free(buffer);
}


extern "C" JNIEXPORT void JNICALL
Java_com_tans_tmediaplayer_subtitle_tMediaSubtitle_releaseNative(
        JNIEnv * env,
        jobject j_subtitle,
        jlong native_subtitle) {
    auto subtitleCtx = reinterpret_cast<tMediaSubtitleContext *>(native_subtitle);
    subtitleCtx->release();
}
