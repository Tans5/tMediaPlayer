//
// Created by pengcheng.tan on 2024/6/27.
//


#ifndef TMEDIAPLAYER_TMEDIASUBTITLE_H
#define TMEDIAPLAYER_TMEDIASUBTITLE_H
#include "tmediaplayer.h"

typedef struct tMediaSubtitleBuffer {
    AVSubtitle * subtitle_frame = nullptr;
} tMediaSubtitleBuffer;

typedef struct tMediaSubtitleContext {
    AVStream *subtitle_stream = nullptr;
    AVCodecContext *subtitle_decoder_ctx = nullptr;
    AVPacket *subtitle_pkt = nullptr;
    AVSubtitle *subtitle_frame = nullptr;

    tMediaOptResult setupNewSubtitleStream(AVStream *stream);

    tMediaDecodeResult decodeSubtitle(AVPacket* pkt);

    void releaseLastSubtitleStream();

    void release();

} tMediaSubtitleContext;

#endif //TMEDIAPLAYER_TMEDIASUBTITLE_H
