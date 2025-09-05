//
// Created by pengcheng.tan on 2024/4/23.
//

#ifndef TMEDIAPLAYER_TMEDIAFRAMELOADER_H
#define TMEDIAPLAYER_TMEDIAFRAMELOADER_H

#include <jni.h>
#include "tmediaplayer.h"

extern "C" {
#include "libavformat/avformat.h"
#include "libavcodec/avcodec.h"
#include "libswscale/swscale.h"
#include "libavutil/imgutils.h"
}

typedef struct tMediaFrameLoaderContext {

    AVFormatContext *format_ctx = nullptr;
    AVPacket *pkt = nullptr;
    AVFrame *frame = nullptr;
    bool skipPktRead = false;
    int64_t duration = 0;

    /**
     * Java
     */
     JavaVM *jvm = nullptr;

    /**
     * Video
     */
    AVStream *video_stream = nullptr;
    const AVCodec *video_decoder = nullptr;
    SwsContext * sws_ctx = nullptr;
    int32_t video_width = 0;
    int32_t video_height = 0;
    int32_t videoDisplayRotation = 0;
    float_t videoDisplayRatio = 0.0f;
    AVCodecContext *video_decoder_ctx = nullptr;
    tMediaVideoBuffer *videoBuffer = nullptr;

    tMediaOptResult prepare(const char * media_file);

    tMediaOptResult getFrame(int64_t framePosition);

    tMediaOptResult decodeForGetFrame();

    tMediaOptResult parseDecodeVideoFrameToBuffer();

    void release();
} tMediaFrameLoaderContext;

#endif //TMEDIAPLAYER_TMEDIAFRAMELOADER_H
