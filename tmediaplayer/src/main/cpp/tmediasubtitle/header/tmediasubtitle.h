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
    int32_t width = 0;
    int32_t height = 0;
    uint8_t *rgbaBuffer = nullptr;
    int32_t bufferSize = 0;
    int64_t start_pts = 0;
    int64_t end_pts = 0;
} tMediaSubtitleBuffer;

typedef struct tMediaSubtitleContext {
    int stream_index = -1;
    AVCodecContext *subtitle_decoder_ctx = nullptr;
    AVPacket *subtitle_pkt = nullptr;
    AVSubtitle *subtitle_frame = nullptr;

    int32_t frame_width = 0;
    int32_t frame_height = 0;

    // Ass
    ASS_Library *ass_library = nullptr;
    ASS_Renderer *ass_renderer = nullptr;
    ASS_Track *ass_track = nullptr;

    tMediaOptResult setupNewSubtitleStream(AVStream *stream, int32_t width, int32_t height);

    tMediaDecodeResult decodeSubtitle(AVPacket* pkt) const;

    tMediaOptResult moveDecodedSubtitleFrameToBuffer(tMediaSubtitleBuffer* buffer);

    void flushDecoder() const;

    void releaseLastSubtitleStream();

    void release();

} tMediaSubtitleContext;



#endif //TMEDIAPLAYER_TMEDIASUBTITLE_H
