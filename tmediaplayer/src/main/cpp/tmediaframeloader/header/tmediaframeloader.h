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
    const char *media_file = nullptr;

    AVFormatContext *format_ctx = nullptr;
    AVPacket *pkt = nullptr;
    AVFrame *frame = nullptr;
    long duration = 0;
    bool skipPktRead = false;

    /**
     * Video
     */
    AVStream *video_stream = nullptr;
    const AVCodec *video_decoder = nullptr;
    SwsContext * sws_ctx = nullptr;
    int video_width = 0;
    int video_height = 0;
    AVCodecContext *video_decoder_ctx = nullptr;
    tMediaVideoBuffer *videoBuffer = nullptr;

} tMediaFrameLoaderContext;

#endif //TMEDIAPLAYER_TMEDIAFRAMELOADER_H
