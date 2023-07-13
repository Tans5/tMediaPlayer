//
// Created by pengcheng.tan on 2023/7/13.
//

#ifndef TMEDIAPLAYER_TMEDIAPLAYER_H
#define TMEDIAPLAYER_TMEDIAPLAYER_H

#include <android/log.h>
#include <jni.h>

extern "C" {
#include "libavformat/avformat.h"
#include "libavcodec/avcodec.h"
#include "libswscale/swscale.h"
#include "libavutil/imgutils.h"
#include "libswresample/swresample.h"
}

#define LOG_TAG "tMediaPlayerNative"
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

enum tMediaOptResult {
    Success,
    Fail
};

typedef struct tMediaPlayerContext {
    const char *media_file = nullptr;

    AVFormatContext *format_ctx = nullptr;
    AVPacket *pkt = nullptr;
    AVFrame *frame = nullptr;
    long duration;
    bool skipPktRead = false;

    /**
     * Java
     */
    JNIEnv* jniEnv = nullptr;
    JavaVM* jvm = nullptr;
    jobject jplayer = nullptr;

    /**
     * Video
     */
    AVStream *video_stream = nullptr;
    AVBufferRef *hardware_ctx = nullptr;
    const AVCodec *video_decoder = nullptr;
    SwsContext * sws_ctx = nullptr;
    int video_width;
    int video_height;
    double video_fps;
    AVCodecContext *video_decoder_ctx = nullptr;

    /**
     * Audio
     */
    AVStream *audio_stream = nullptr;
    const AVCodec *audio_decoder = nullptr;
    AVCodecContext *audio_decoder_ctx = nullptr;
    SwrContext *swr_ctx = nullptr;
    int audio_channels;
    int audio_pre_sample_bytes;
    int audio_simple_rate;


    tMediaOptResult prepare(const char * media_file, bool is_request_hw, int target_audio_channels);
} tMediaPlayerContext;

#endif //TMEDIAPLAYER_TMEDIAPLAYER_H
