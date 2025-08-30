//
// Created by pengcheng.tan on 2024/6/27.
//
#include "tmediasubtitle.h"


tMediaOptResult tMediaSubtitleContext::setupNewSubtitleStream(AVStream *stream, int32_t width, int32_t height) {
    releaseLastSubtitleStream();
    if (subtitle_pkt == nullptr) {
        subtitle_pkt = av_packet_alloc();
    }
    releaseLastSubtitleStream();
    this->frame_width = width;
    this->frame_height = height;
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


//char *ff_ass_get_dialog(int readorder, int layer, const char *style,
//                        const char *speaker, const char *text)
//{
//    return av_asprintf("%d,%d,%s,%s,0,0,0,,%s",
//                       readorder, layer, style ? style : "Default",
//                       speaker ? speaker : "", text);
//}

/**
 * FFmpeg ASS: 3,0,Default,,0,0,0,,Hello World.
 * Standard ASS: Dialogue: 0,0:00:00.00,0:00:05.00,Default,,0,0,0,,Hello World.
 */
 static int ffAssToStandardAss(const char* inputFfAss, int inputFfAssSize, bool inputIsText, char *outputStandard, int maxOutputSize) {
     if (!inputIsText) {
         int firstCommaIndex = -1;
         int secondCommaIndex = -1;
         for (int i = 0; i < inputFfAssSize; i ++) {
             char c = inputFfAss[i];
             if (c == ',' ) {
                 if (firstCommaIndex == -1) {
                     firstCommaIndex = i;
                     continue;
                 }
                 secondCommaIndex = i;
                 break;
             }
         }
         if (firstCommaIndex == -1 || secondCommaIndex == -1) {
             return 0;
         }
         int layerSize = secondCommaIndex - firstCommaIndex - 1;
         char layer[layerSize + 1];
         memcpy(layer, inputFfAss + firstCommaIndex + 1, layerSize);
         return snprintf(outputStandard, maxOutputSize, "Dialogue: %s,0:00:00.00,0:00:05.00%s", layer, inputFfAss + secondCommaIndex);
     } else {
         return snprintf(outputStandard, maxOutputSize, "Dialogue: 0,0:00:00.00,0:00:05.00,Default,,0,0,0,,%s", inputFfAss);
     }
 }

const static char* defaultAssHeaderFormat =
        "[Script Info]\n"
        "ScriptType: v4.00+\n"
        "PlayResX: %d\n"
        "PlayResY: %d\n"
        "\n"
        "[V4+ Styles]\n"
        "Format: Name, Fontname, Fontsize, PrimaryColour, SecondaryColour, OutlineColour, BackColour, Bold, Italic, Underline, StrikeOut, ScaleX, ScaleY, Spacing, Angle, BorderStyle, Outline, Shadow, Alignment, MarginL, MarginR, MarginV, Encoding\n"
        "Style: Default,Arial,20,&H00FFFFFF,&H000000FF,&H00000000,&H80000000,0,0,0,0,100,100,0,0,1,2,1,2,10,10,10,1\n"
        "\n"
        "[Events]\n"
        "Format: Layer, Start, End, Style, Name, MarginL, MarginR, MarginV, Effect, Text\n";

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
    double ptsStart = 0;
    double ptsEnd = 0;
    if (subtitle_pkt->time_base.den != 0) {
        ptsStart = (double) subtitle_pkt->pts * av_q2d(subtitle_pkt->time_base) * 1000.0;
        ptsEnd = (double) subtitle_pkt->duration * av_q2d(subtitle_pkt->time_base) * 1000.0 + ptsStart;
    }
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
//                ass_set_message_cb(ass_library, [](int level, const char* fmt, va_list va, void* data) {
//                    char buf[1024];
//                    vsnprintf(buf, sizeof(buf), fmt, va);
//                    LOGD("[libass] %s", buf);
//                }, nullptr);
                LOGD("Create new ass library.");
            }
            if (ass_renderer == nullptr) {
                ass_renderer = ass_renderer_init(ass_library);
                ass_set_frame_size(ass_renderer, frame_width, frame_height);
                ass_set_fonts(ass_renderer, nullptr, nullptr, ASS_FONTPROVIDER_AUTODETECT, nullptr, 1);
                LOGD("Create new ass renderer.");
            }
            if (ass_track == nullptr) {
                ass_track = ass_new_track(ass_library);
                LOGD("Create new ass track.");
            }

            if (subtitle_decoder_ctx->subtitle_header_size > 0) {
                ass_process_data(ass_track, (const char *)subtitle_decoder_ctx->subtitle_header, subtitle_decoder_ctx->subtitle_header_size);
            } else {
                char defaultHeader[strlen(defaultAssHeaderFormat) + 16];
                sprintf(defaultHeader, defaultAssHeaderFormat, frame_width, frame_height);
                ass_process_data(ass_track, defaultHeader, strlen(defaultHeader));
            }

            for (int i = 0; i < subtitleFrame->num_rects; i ++) {
                auto rect = subtitleFrame->rects[i];
                char buffer[256];
                int size = 0;
                if (rect->type == SUBTITLE_ASS) {
                    if (rect->ass != nullptr) {
                        size = ffAssToStandardAss(rect->ass, strlen(rect->ass), false, buffer, 256);
                    }
                } else {
                    if (rect->text != nullptr) {
                        size = ffAssToStandardAss(rect->text, strlen(rect->text), true, buffer, 256);
                    }
                }
                ass_process_data(ass_track, buffer, size);
            }
            LOGD("ASS event size: %d", ass_track->n_events);
            auto img = ass_render_frame(ass_renderer, ass_track, 1000, nullptr);
            if (img != nullptr) {
                // TODO:
                LOGD("ASS width=%d, height=%d, pos_x=%d, pos_y=%d", img->w, img->h, img->dst_x, img->dst_y);
            } else {
                LOGE("ASS image is null.");
            }

            ass_flush_events(ass_track);
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

    if (ass_track != nullptr) {
        ass_free_track(ass_track);
        ass_track = nullptr;
    }
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

