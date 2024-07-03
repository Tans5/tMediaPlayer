//
// Created by pengcheng.tan on 2024/7/3.
//

#ifndef TMEDIAPLAYER_TMEDIASUBTITLEPKTREADER_H
#define TMEDIAPLAYER_TMEDIASUBTITLEPKTREADER_H
#include "tmediaplayer.h"

typedef struct tMediaSubtitlePktReaderContext {
    AVFormatContext *format_ctx = nullptr;
    AVStream *subtitle_stream = nullptr;
    AVPacket *pkt = nullptr;

    tMediaOptResult prepare(const char *subtitle_file);

    tMediaReadPktResult readPacket();

    void movePacketRef(AVPacket *target);

    tMediaOptResult seekTo(int16_t targetPosInMillis);

    void release();
} tMediaSubtitlePktReaderContext;


#endif //TMEDIAPLAYER_TMEDIASUBTITLEPKTREADER_H
