//
// Created by pengcheng.tan on 2024/6/27.
//

#include <jni.h>
#include "tmediasubtitle.h"
#include "tmediasubtitlepktreader.h"


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
        jint stream_index,
        jint frame_width,
        jint frame_height) {
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
        return subtitle->setupNewSubtitleStream(targetStream, frame_width, frame_height);
    } else {
        LOGE("Wrong stream index: %d", stream_index);
        return OptFail;
    }
}

extern "C" JNIEXPORT jint JNICALL
Java_com_tans_tmediaplayer_subtitle_tMediaSubtitle_setupSubtitleStreamFromPktReaderNative(
        JNIEnv * env,
        jobject j_subtitle,
        jlong native_subtitle,
        jlong native_pkt_reader,
        jint frame_width,
        jint frame_height) {
    auto subtitle = reinterpret_cast<tMediaSubtitleContext *>(native_subtitle);
    auto pktReader = reinterpret_cast<tMediaSubtitlePktReaderContext *>(native_pkt_reader);
    auto stream = pktReader->subtitle_stream;
    if (stream != nullptr) {
        return subtitle->setupNewSubtitleStream(stream, frame_width, frame_height);
    } else {
        return OptFail;
    }
}

extern "C" JNIEXPORT jint JNICALL
Java_com_tans_tmediaplayer_subtitle_tMediaSubtitle_decodeSubtitleNative(
        JNIEnv * env,
        jobject j_subtitle,
        jlong native_subtitle,
        jlong native_pkt,
        jlong native_buffer) {
    auto subtitle = reinterpret_cast<tMediaSubtitleContext *>(native_subtitle);
    auto buffer = reinterpret_cast<tMediaSubtitleBuffer *>(native_buffer);
    avsubtitle_free(buffer->subtitle_frame);
    if (native_pkt == 0) {
        return subtitle->decodeSubtitle(nullptr, buffer->subtitle_frame);
    } else {
        return subtitle->decodeSubtitle(reinterpret_cast<AVPacket *>(native_pkt), buffer->subtitle_frame);
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
    buffer->subtitle_frame = reinterpret_cast<AVSubtitle *>(av_mallocz(sizeof(AVSubtitle)));
    return reinterpret_cast<jlong>(buffer);
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

    auto stringClazz_ref = reinterpret_cast<jclass> (env->NewLocalRef(env->FindClass("java/lang/String")));
    auto result = env->NewObjectArray((int) lineSize, stringClazz_ref, nullptr);
    auto jarray_ref = reinterpret_cast<jobjectArray>(env->NewLocalRef(result));
    env->DeleteLocalRef(stringClazz_ref);
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
        LOGD("ReadSubtitle: x=%d, y=%d, w=%d, h=%d, lineSize=%d", rect->x, rect->y, rect->w, rect->h, rect->linesize[0]);
        auto j_string_ref = reinterpret_cast<jstring>(env->NewLocalRef(env->NewStringUTF(line)));
        env->SetObjectArrayElement(jarray_ref, i, j_string_ref);
        env->DeleteLocalRef(j_string_ref);
    }
    env->DeleteLocalRef(jarray_ref);
    return result;
}

// for bitmap subtitle
//uint32_t* convert_rect_to_argb(AVSubtitleRect *rect) {
//    if (!rect || rect->type != SUBTITLE_BITMAP || !rect->data[0] || !rect->data[1]) {
//        return nullptr;
//    }
//
//    int width = rect->w;
//    int height = rect->h;
//    uint8_t *pixel_indices = rect->data[0]; // 8-bit palette indices
//    uint32_t *palette = (uint32_t*)rect->data[1]; // 32-bit RGBA palette
//
//    // Allocate memory for our final ARGB pixel buffer
//    uint32_t* argb_pixels = new uint32_t[width * height];
//
//    for (int y = 0; y < height; y++) {
//        for (int x = 0; x < width; x++) {
//            // 1. Get the palette index for the current pixel.
//            //    IMPORTANT: Use rect->linesize[0] (the stride), not width!
//            uint8_t index = pixel_indices[y * rect->linesize[0] + x];
//
//            // 2. Look up the 32-bit color from the palette.
//            //    FFmpeg's palette is RGBA.
//            uint32_t rgba_color = palette[index];
//
//            // 3. Convert RGBA to ARGB for Android's Bitmap.Config.ARGB_8888.
//            //    This involves byte shuffling.
//            uint8_t r = (rgba_color >> 24) & 0xFF;
//            uint8_t g = (rgba_color >> 16) & 0xFF;
//            uint8_t b = (rgba_color >> 8) & 0xFF;
//            uint8_t a = rgba_color & 0xFF;
//
//            uint32_t argb_color = (a << 24) | (r << 16) | (g << 8) | b;
//
//            // 4. Store the final ARGB color in our buffer.
//            argb_pixels[y * width + x] = argb_color;
//        }
//    }
//
//    return argb_pixels;
//}

extern "C" JNIEXPORT void JNICALL
Java_com_tans_tmediaplayer_subtitle_tMediaSubtitle_releaseSubtitleBufferNative(
        JNIEnv * env,
        jobject j_subtitle,
        jlong native_buffer) {
    auto buffer = reinterpret_cast<tMediaSubtitleBuffer *>(native_buffer);
    if (buffer->subtitle_frame != nullptr) {
        avsubtitle_free(buffer->subtitle_frame);
        av_freep(buffer->subtitle_frame);
    }
    delete buffer;
}


extern "C" JNIEXPORT void JNICALL
Java_com_tans_tmediaplayer_subtitle_tMediaSubtitle_releaseNative(
        JNIEnv * env,
        jobject j_subtitle,
        jlong native_subtitle) {
    auto subtitleCtx = reinterpret_cast<tMediaSubtitleContext *>(native_subtitle);
    subtitleCtx->release();
    delete subtitleCtx;
}
