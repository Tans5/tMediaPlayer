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
    stream_index = stream->index;
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
    subtitle_frame = reinterpret_cast<AVSubtitle *>(av_mallocz(sizeof(AVSubtitle)));
    return OptSuccess;
}

tMediaDecodeResult tMediaSubtitleContext::decodeSubtitle(AVPacket *pkt) const {
    if (stream_index == -1 || subtitle_decoder_ctx == nullptr) {
        LOGE("Subtitle stream is null.");
        return DecodeFail;
    }
    if (pkt != nullptr) {
        if (pkt->stream_index != stream_index) {
            LOGE("Wrong subtitle stream index");
            return DecodeFail;
        }
    }
    av_packet_unref(subtitle_pkt);
    if (pkt != nullptr) {
        av_packet_move_ref(subtitle_pkt, pkt);
    }
    int got_frame = 0;
    avsubtitle_free(subtitle_frame);
    int ret = avcodec_decode_subtitle2(subtitle_decoder_ctx, subtitle_frame, &got_frame, subtitle_pkt);
    if (ret < 0) {
        LOGE("Decode subtitle fail: %d", ret);
        return DecodeFail;
    }
    if (got_frame) {
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

tMediaOptResult tMediaSubtitleContext::moveDecodedSubtitleFrameToBuffer(tMediaSubtitleBuffer *buffer) {

    double ptsStart = 0;
    double ptsEnd = 0;
    // TODO: Fix ptsStart and ptsEnd
    if (subtitle_frame->start_display_time > 0) {
        ptsStart = subtitle_frame->start_display_time;
    } else if (subtitle_pkt->time_base.den != 0) {
        ptsStart = (double) subtitle_pkt->pts * av_q2d(subtitle_pkt->time_base) * 1000.0;
    }
    if (subtitle_frame->end_display_time > 0) {
        ptsEnd = subtitle_frame->end_display_time;
    } else if(subtitle_pkt->time_base.den != 0) {
        ptsEnd = (double) subtitle_pkt->duration * av_q2d(subtitle_pkt->time_base) * 1000.0 + ptsStart;
    }
    LOGD("Subtitle pts=%lld, startDisplay=%lld, endDisplay=%lld", subtitle_frame->pts, subtitle_frame->start_display_time, subtitle_frame->end_display_time);
    LOGD("Pkt pts=%lld, duration=%lld", subtitle_pkt->pts, subtitle_pkt->duration);
    buffer->start_pts = (int64_t) ptsStart;
    buffer->end_pts = (int64_t) ptsEnd;
    buffer->width = frame_width;
    buffer->height = frame_height;
    int contentSize = buffer->width * buffer->height * 4;
    if (contentSize > buffer->bufferSize) {
        if (buffer->rgbaBuffer != nullptr) {
            free(buffer->rgbaBuffer);
        }
        buffer->bufferSize = contentSize;
        buffer->rgbaBuffer = static_cast<uint8_t *>(malloc(contentSize));
        LOGD("Create new subtitle rgba buffer, contentSize=%d, width=%d, height=%d", buffer->bufferSize, buffer->width, buffer->height);
    }
    memset(buffer->rgbaBuffer, 0, frame_width * frame_height * 4);
    auto outputBitmap = buffer->rgbaBuffer;

    if (subtitle_frame->format != 0) { // text
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

        for (int i = 0; i < subtitle_frame->num_rects; i ++) {
            auto rect = subtitle_frame->rects[i];
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
        int write_image = 0;
        while (img != nullptr) {
            uint32_t color = img->color;
            uint8_t r = (color >> 24) & 0xFF;
            uint8_t g = (color >> 16) & 0xFF;
            uint8_t b = (color >> 8) & 0xFF;
            uint8_t global_alpha = 255 - (color & 255);
            LOGD("ASS Rect width=%d, height=%d, pos_x=%d, pos_y=%d, r=%d, g=%d, b=%d, a=%d", img->w, img->h, img->dst_x, img->dst_y, r, g, b, global_alpha);

            if (global_alpha > 0) {
                write_image ++;
                for (int y = 0; y < img->h; y ++) {
                    for (int x = 0; x < img->w; x ++) {
                        uint8_t alpha = img->bitmap[y * img->stride + x];
                        if (alpha == 0) {
                            // skip
                            continue;
                        }
                        uint8_t final_alpha = (global_alpha * alpha) / 255;
                        if (final_alpha == 0) {
                            // skip
                            continue;
                        }
                        int target_x = img->dst_x + x;
                        int target_y = img->dst_y + y;
                        if (target_x < 0 || target_x >= frame_width || target_y < 0 || target_y >= frame_height) {
                            continue;
                        }
                        uint8_t *pixel = &outputBitmap[(target_y * frame_width * 4) + (target_x * 4)];
                        uint8_t bg_alpha = pixel[3];
                        if (bg_alpha == 0) {
                            pixel[0] = r;
                            pixel[1] = g;
                            pixel[2] = b;
                            pixel[3] = final_alpha;
                        } else {
                            // mix
                            uint8_t out_alpha = final_alpha + (bg_alpha * (255 - final_alpha) / 255);
                            float blend = final_alpha / 255.0f;
                            pixel[0] = (r * blend) + (pixel[0] * (1.0f - blend));
                            pixel[1] = (g * blend) + (pixel[1] * (1.0f - blend));
                            pixel[2] = (b * blend) + (pixel[2] * (1.0f - blend));
                            pixel[3] = out_alpha;
                        }
                    }
                }
            }
            img = img->next;
        }
        ass_flush_events(ass_track);
        return write_image > 0 ? OptSuccess : OptFail;
    } else { // bitmap
        LOGD("FF subtitle rect size: %d", subtitle_frame->num_rects);
        for (int i = 0; i < subtitle_frame->num_rects; i ++) {
            auto rect = subtitle_frame->rects[i];
            uint8_t *pixel_indices = rect->data[0];
            uint32_t  *palette = (uint32_t *)rect->data[1];

            LOGD("FF subtitle rect: width=%d, height=%d, x=%d, y=%d, indexlinesize=%d", rect->w, rect->h, rect->x, rect->y, rect->linesize[0]);

            for (int y = 0; y < rect->h; y++) {
                for (int x = 0; x < rect->w; x++) {
                    uint8_t index = pixel_indices[y * rect->linesize[0] + x];
                    int target_x = rect->x + x;
                    int target_y = rect->y + y;
                    uint32_t color = palette[index];
                    uint8_t r = (color >> 24) & 0xFF;
                    uint8_t g = (color >> 16) & 0xFF;
                    uint8_t b = (color >> 8) & 0xFF;
                    uint8_t a = color & 0xFF;

                    if (target_x < 0 || target_x >= frame_width || target_y < 0 || target_y >= frame_height || a <= 0) {
                        continue;
                    }
                    uint8_t *pixel = &outputBitmap[(target_y * frame_width * 4) + (target_x * 4)];
                    uint8_t bg_alpha = pixel[3];
                    if (bg_alpha == 0) {
                        pixel[0] = r;
                        pixel[1] = g;
                        pixel[2] = b;
                        pixel[3] = a;
                    } else {
                        // mix
                        uint8_t out_alpha = a + (bg_alpha * (255 - a) / 255);
                        float blend = a / 255.0f;
                        pixel[0] = (r * blend) + (pixel[0] * (1.0f - blend));
                        pixel[1] = (g * blend) + (pixel[1] * (1.0f - blend));
                        pixel[2] = (b * blend) + (pixel[2] * (1.0f - blend));
                        pixel[3] = out_alpha;
                    }
                }
            }
        }
        return subtitle_frame->num_rects > 0 ? OptSuccess : OptFail;
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
    stream_index = -1;

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
    if (subtitle_frame != nullptr) {
        avsubtitle_free(subtitle_frame);
        av_free(subtitle_frame);
        subtitle_frame = nullptr;
    }
    LOGD("tMediaSubtitle released.");
}

