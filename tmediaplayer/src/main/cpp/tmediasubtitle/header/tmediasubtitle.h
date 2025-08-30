//
// Created by pengcheng.tan on 2024/6/27.
//


#ifndef TMEDIAPLAYER_TMEDIASUBTITLE_H
#define TMEDIAPLAYER_TMEDIASUBTITLE_H
extern "C" {
#include "ass/ass.h"
}
#include "tmediaplayer.h"

typedef struct tMediaSubtitleBuffer {
    AVSubtitle *subtitle_frame = nullptr;
} tMediaSubtitleBuffer;

typedef struct tMediaSubtitleContext {
    AVStream *subtitle_stream = nullptr;
    AVCodecContext *subtitle_decoder_ctx = nullptr;
    AVPacket *subtitle_pkt = nullptr;

    int32_t frame_width = 0;
    int32_t frame_height = 0;

    // Ass
    ASS_Library *ass_library = nullptr;
    ASS_Renderer *ass_renderer = nullptr;
    ASS_Track *ass_track = nullptr;

    tMediaOptResult setupNewSubtitleStream(AVStream *stream, int32_t frame_width, int32_t frame_height);

    tMediaDecodeResult decodeSubtitle(AVPacket* pkt, AVSubtitle *subtitleFrame);

    void flushDecoder() const;

    void releaseLastSubtitleStream();

    void release();

} tMediaSubtitleContext;

#endif //TMEDIAPLAYER_TMEDIASUBTITLE_H
