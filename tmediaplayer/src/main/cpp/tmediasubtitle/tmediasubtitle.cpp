//
// Created by pengcheng.tan on 2024/6/27.
//
#include "tmediasubtitle.h"


tMediaOptResult tMediaSubtitleContext::setupNewSubtitleStream(AVStream *stream, int32_t frame_width, int32_t frame_height) {
    releaseLastSubtitleStream();
    if (subtitle_pkt == nullptr) {
        subtitle_pkt = av_packet_alloc();
    }
    releaseLastSubtitleStream();
    this->frame_width = frame_width;
    this->frame_height = frame_height;
    if (stream->codecpar->codec_type != AVMEDIA_TYPE_SUBTITLE) {
        LOGE("Wrong stream type: %d", stream->codecpar->codec_type);
        return OptFail;
    }
    subtitle_stream = stream;
    auto codec = avcodec_find_decoder(stream->codecpar->codec_id);
    if (codec == nullptr) {
        LOGE("Don't find subtitle codec.");
        return OptFail;
    }
    subtitle_decoder_ctx = avcodec_alloc_context3(codec);
    int ret = avcodec_parameters_to_context(subtitle_decoder_ctx, stream->codecpar);
    if (ret < 0) {
        LOGE("Attach params to ctx fail: %d", ret);
        return OptFail;
    }
    ret = avcodec_open2(subtitle_decoder_ctx, codec, nullptr);
    if (ret < 0) {
        LOGE("Open decoder fail: %d", ret);
        return OptFail;
    }
    return OptSuccess;
}

/**
 * FFmpeg ASS: 3,0,Default,,0,0,0,,Hello World.
 * Standard ASS: Dialogue: 0,0:00:00.00,0:00:05.00,Default,,0,0,0,,Hello World.
 */


tMediaDecodeResult tMediaSubtitleContext::decodeSubtitle(AVPacket *pkt, AVSubtitle *subtitleFrame) {
    if (subtitle_stream == nullptr || subtitle_decoder_ctx == nullptr) {
        LOGE("Subtitle stream is null.");
        return DecodeFail;
    }
    if (pkt != nullptr) {
        if (pkt->stream_index != subtitle_stream->index) {
            LOGE("Wrong subtitle stream index");
            return DecodeFail;
        }
    }
    av_packet_unref(subtitle_pkt);
    if (pkt != nullptr) {
        av_packet_move_ref(subtitle_pkt, pkt);
    }
    int got_frame = 0;
    int ret = avcodec_decode_subtitle2(subtitle_decoder_ctx, subtitleFrame, &got_frame, subtitle_pkt);
    double ptsStart = (double) subtitle_pkt->pts * av_q2d(subtitle_pkt->time_base) * 1000.0;
    double ptsEnd = (double) subtitle_pkt->duration * av_q2d(subtitle_pkt->time_base) * 1000.0 + ptsStart;
    subtitleFrame->start_display_time = (int) ptsStart;
    subtitleFrame->end_display_time = (int) ptsEnd;
    if (ret < 0) {
        LOGE("Decode subtitle fail: %d", ret);
        return DecodeFail;
    }
    if (got_frame) {
        if (subtitleFrame->format != 0) { // text
            // FixMe
            if (ass_library == nullptr) {
                ass_library = ass_library_init();
                ass_set_message_cb(ass_library, [](int level, const char* fmt, va_list va, void* data) {
                    char buf[1024];
                    vsnprintf(buf, sizeof(buf), fmt, va);
                    LOGD("[libass] %s", buf);
                }, nullptr);
                LOGD("Create new ass library.");
            }
            if (ass_renderer == nullptr) {
                ass_renderer = ass_renderer_init(ass_library);
                ass_set_frame_size(ass_renderer, frame_width, frame_height);
                ass_set_fonts(ass_renderer, nullptr, nullptr, ASS_FONTPROVIDER_AUTODETECT, nullptr, 1);
                LOGD("Create new ass renderer.");
            }
            ASS_Track *ass_track = ass_new_track(ass_library);
            ass_process_data(ass_track, (const char *)subtitle_decoder_ctx->subtitle_header, subtitle_decoder_ctx->subtitle_header_size);

            const char* testAss = "Dialogue: 0,0:00:00.00,0:00:05.00,Default,,0,0,0,,Hello World";
            for (int i = 0; i < subtitleFrame->num_rects; i ++) {
                auto rect = subtitleFrame->rects[i];
                ass_process_data(ass_track, testAss, strlen(testAss));
                LOGD("Text subtitle: ass=%s, text=%s", rect->ass, rect->text);
            }
            LOGD("ASS event size: %d", ass_track->n_events);
            auto img = ass_render_frame(ass_renderer, ass_track, 1000, nullptr);
            if (img != nullptr) {
                // TODO:
                LOGD("ASS width=%d, height=%d", img->w, img->h);
            } else {
                LOGE("ASS image is null.");
            }

            ass_free_track(ass_track);
        } else { // bitmap
            // TODO: Handle bitmap
        }

        return !subtitle_pkt->data ? DecodeSuccessAndSkipNextPkt : DecodeSuccess;
    } else {
        if (subtitle_pkt->data) {
            LOGE("Decode subtitle fail: %d", ret);
            return DecodeFail;
        } else {
            LOGD("Decode subtitle end.");
            return DecodeEnd;
        }
    }
}

void tMediaSubtitleContext::flushDecoder() const {
    if (subtitle_decoder_ctx != nullptr) {
        avcodec_flush_buffers(subtitle_decoder_ctx);
    }
}

void tMediaSubtitleContext::releaseLastSubtitleStream() {
    if (subtitle_decoder_ctx != nullptr) {
        avcodec_free_context(&subtitle_decoder_ctx);
        subtitle_decoder_ctx = nullptr;
    }
    subtitle_stream = nullptr;
    if (ass_renderer != nullptr) {
        ass_renderer_done(ass_renderer);
        ass_renderer = nullptr;
    }
    if (ass_library != nullptr) {
        ass_library_done(ass_library);
        ass_library = nullptr;
    }
}

void tMediaSubtitleContext::release() {
    releaseLastSubtitleStream();
    if (subtitle_pkt != nullptr) {
        av_packet_unref(subtitle_pkt);
        av_packet_free(&subtitle_pkt);
        subtitle_pkt = nullptr;
    }
}

