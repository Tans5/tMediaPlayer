//
// Created by pengcheng.tan on 2023/7/13.
//
#include "tmediaplayer.h"


AVPixelFormat hw_pix_fmt_i = AV_PIX_FMT_NONE;

static enum AVPixelFormat get_hw_format(AVCodecContext *ctx,
                                        const enum AVPixelFormat *pix_fmts) {
    const enum AVPixelFormat *p;

    for (p = pix_fmts; *p != -1; p++) {
        if (*p == hw_pix_fmt_i) {
            return *p;
        }
    }
    LOGE("Failed to get HW surface format");
    return AV_PIX_FMT_NONE;
}

tMediaOptResult tMediaPlayerContext::prepare(const char *media_file_p, bool is_request_hw,int target_audio_channels) {
    this->media_file = media_file_p;
    LOGD("Prepare media file: %s", media_file_p);
    this->format_ctx = avformat_alloc_context();
    int result = avformat_open_input(&format_ctx, media_file, nullptr, nullptr);
    if (result < 0) {
        LOGE("Avformat open file fail: %d", result);
        return OptFail;
    }
    result = avformat_find_stream_info(format_ctx, nullptr);
    if (result < 0) {
        LOGE("Avformat find stream info fail: %d", result);
        return OptFail;
    }
    for (int i = 0; i < format_ctx->nb_streams; i ++) {
        auto s = format_ctx->streams[i];
        auto codec_type = s->codecpar->codec_type;
        switch (codec_type) {
            case AVMEDIA_TYPE_VIDEO:
                LOGD("Find video stream.");
                this->video_stream = s;
                this->video_duration = (long) (s->duration / s->time_base.den * 1000L);
                break;
            case AVMEDIA_TYPE_AUDIO:
                LOGD("Find audio stream.");
                this->audio_stream = s;
                this->audio_duration = (long) (s->duration / s->time_base.den * 1000L);
                break;
            default:
                break;
        }
    }
    if (video_stream == nullptr && audio_stream == nullptr) {
        LOGE("Didn't find video stream or audio stream");
        return OptFail;
    }
    this->pkt = av_packet_alloc();
    this->frame = av_frame_alloc();
    if (video_duration > audio_duration) {
        this->duration = video_duration;
    } else {
        this->duration = audio_duration;
    }

    // Video
    if (video_stream != nullptr) {
        AVCodecParameters *params = video_stream->codecpar;
        this->video_width = params->width;
        this->video_height = params->height;
        this->video_fps = av_q2d(video_stream->avg_frame_rate);

        if (is_request_hw) {
            bool useHwDecoder = true;
            const char * hwCodecName;
            switch (params->codec_id) {
                case AV_CODEC_ID_H264:
                    hwCodecName = "h264_mediacodec";
                    break;
                case AV_CODEC_ID_HEVC:
                    hwCodecName = "hevc_mediacodec";
                    break;
                default:
                    useHwDecoder = false;
                    break;
            }
            if (useHwDecoder) {
                AVHWDeviceType hwDeviceType = av_hwdevice_find_type_by_name("mediacodec");
                if (hwDeviceType == AV_HWDEVICE_TYPE_NONE) {
                    while ((hwDeviceType = av_hwdevice_iterate_types(hwDeviceType)) != AV_HWDEVICE_TYPE_NONE) {
                    }
                }
                const AVCodec *hwDecoder = avcodec_find_decoder_by_name(hwCodecName);
                if (hwDecoder) {
                    for (int i = 0; ; ++i) {
                        const AVCodecHWConfig *config = avcodec_get_hw_config(hwDecoder, i);
                        if (!config) {
                            break;
                        }
                        if (config->methods & AV_CODEC_HW_CONFIG_METHOD_HW_DEVICE_CTX && config->device_type == hwDeviceType) {
                            hw_pix_fmt_i = config->pix_fmt;
                            break;
                        }
                    }
                    if (hw_pix_fmt_i != AV_PIX_FMT_NONE) {
                        this->video_decoder = hwDecoder;
                        result = av_hwdevice_ctx_create(&hardware_ctx, hwDeviceType, nullptr,
                                                        nullptr, 0);
                        if (result < 0) {
                            LOGE("Create hw device ctx fail: %d", result);
                            return OptFail;
                        }
                    } else {
                        LOGE("Don't find hw decoder config");
                        this->video_decoder = avcodec_find_decoder(params->codec_id);
                    }
                } else {
                    LOGE("Don't find hw decoder: %s", hwCodecName);
                    this->video_decoder = avcodec_find_decoder(params->codec_id);
                }
            } else {
                this->video_decoder = avcodec_find_decoder(params->codec_id);
            }
        } else {
            this->video_decoder = avcodec_find_decoder(params->codec_id);
        }

        if (video_decoder == nullptr) {
            LOGE("Didn't find video decoder.");
            return OptFail;
        }
        this->video_decoder_ctx = avcodec_alloc_context3(video_decoder);
        if (!video_decoder_ctx) {
            LOGE("Create video decoder ctx fail.");
            return OptFail;
        }
        result = avcodec_parameters_to_context(video_decoder_ctx, params);
        if (result < 0) {
            LOGE("Attach video params to ctx fail: %d", result);
            return OptFail;
        }
        if (hardware_ctx) {
            video_decoder_ctx->get_format = get_hw_format;
            video_decoder_ctx->hw_device_ctx = av_buffer_ref(hardware_ctx);
            LOGD("Set up hw device ctx.");
        }
        result = avcodec_open2(video_decoder_ctx, video_decoder, nullptr);
        if (result < 0) {
            LOGE("Open video decoder ctx fail: %d", result);
            return OptFail;
        }
        LOGD("Prepare video decoder success.");
    }

    // Audio
    if (audio_stream != nullptr) {
        auto params = audio_stream->codecpar;
        this->audio_decoder = avcodec_find_decoder(params->codec_id);
        if (!audio_decoder) {
            LOGE("Didn't find audio decoder.");
            return OptFail;
        }
        this->audio_decoder_ctx = avcodec_alloc_context3(audio_decoder);
        if (!audio_decoder_ctx) {
            LOGE("Create audio decoder ctx fail");
            return OptFail;
        }
        result = avcodec_parameters_to_context(audio_decoder_ctx, params);
        if (result < 0) {
            LOGE("Attach params to audio ctx fail: %d", result);
            return OptFail;
        }
        result = avcodec_open2(audio_decoder_ctx, audio_decoder, nullptr);
        if (result < 0) {
            LOGE("Open audio ctx fail: %d", result);
            return OptFail;
        }
        this->audio_channels = av_get_channel_layout_nb_channels(audio_decoder_ctx->channel_layout);
        this->audio_pre_sample_bytes = av_get_bytes_per_sample(audio_decoder_ctx->sample_fmt);
        this->audio_simple_rate = audio_decoder_ctx->sample_rate;
        if (target_audio_channels >= 2) {
            this->audio_output_channels = 2;
            this->audio_output_ch_layout = AV_CH_LAYOUT_STEREO;
        } else {
            this->audio_output_channels = 1;
            this->audio_output_ch_layout = AV_CH_LAYOUT_MONO;
        }
        this->swr_ctx = swr_alloc();
        swr_alloc_set_opts(swr_ctx, audio_output_ch_layout, audio_output_sample_fmt,audio_output_sample_rate,
                           audio_decoder_ctx->channel_layout,audio_decoder_ctx->sample_fmt, audio_decoder_ctx->sample_rate,
                           0,nullptr);
        result = swr_init(swr_ctx);
        if (result < 0) {
            LOGE("Init swr ctx fail: %d", result);
            return OptFail;
        }
        LOGD("Prepare audio decoder success.");
    }
    return OptSuccess;
}

tMediaDecodeResult tMediaPlayerContext::decode(tMediaDecodeBuffer* buffer) {
    if (pkt != nullptr &&
        frame != nullptr &&
        format_ctx != nullptr &&
        buffer != nullptr) {
        int result;
        if (!skipPktRead) {
            av_packet_unref(pkt);
            result = av_read_frame(format_ctx, pkt);
            if (result < 0) {
                LOGD("Decode media end.");
                return DecodeEnd;
            }
        }
        skipPktRead = false;
        if (video_stream != nullptr &&
            pkt->stream_index == video_stream->index &&
            video_decoder_ctx != nullptr) {
            result = avcodec_send_packet(video_decoder_ctx, pkt);
            if (result == AVERROR(EAGAIN)) {
                LOGD("Decode video skip read pkt");
                skipPktRead = true;
            } else {
                skipPktRead = false;
            }
            if (result < 0 && !skipPktRead) {
                LOGE("Decode video send pkt fail: %d", result);
                return DecodeFail;
            }
            av_frame_unref(frame);
            result = avcodec_receive_frame(video_decoder_ctx, frame);
            if (result == AVERROR(EAGAIN)) {
                LOGD("Decode video reload frame");
                return decode(buffer);
            }
            if (result < 0) {
                LOGE("Decode video receive frame fail: %d", result);
                return DecodeFail;
            }

            int w = frame->width;
            int h = frame->height;
            auto sws_ctx = sws_getContext(
                    w,
                    h,
                    (AVPixelFormat) frame->format,
                    w,
                    h,
                    AV_PIX_FMT_RGBA,
                    SWS_BICUBIC,
                    nullptr,
                    nullptr,
                    nullptr);
            if (sws_ctx == nullptr) {
                LOGE("Decode video fail, sws ctx create fail.");
                return DecodeFail;
            }
            auto videoBuffer = buffer->videoBuffer;
            if (w != videoBuffer->width ||
                h != videoBuffer->height) {
                LOGE("Decode video change size.");
                videoBuffer->width = w;
                videoBuffer->height = h;
                videoBuffer->size = av_image_get_buffer_size(AV_PIX_FMT_RGBA, w, h,1);
                free(videoBuffer->rgbaBuffer);
                videoBuffer->rgbaBuffer = static_cast<uint8_t *>(av_malloc(
                        videoBuffer->size * sizeof(uint8_t)));
                av_image_fill_arrays(videoBuffer->rgbaFrame->data, videoBuffer->rgbaFrame->linesize, videoBuffer->rgbaBuffer,
                                     AV_PIX_FMT_RGBA, w, h, 1);
            }
            buffer->is_video = true;
            result = sws_scale(sws_ctx, frame->data, frame->linesize, 0, frame->height, videoBuffer->rgbaFrame->data, videoBuffer->rgbaFrame->linesize);
            sws_freeContext(sws_ctx);
            if (result < 0) {
                LOGE("Decode video sws scale fail: %d", result);
                return DecodeFail;
            }
//            if (videoBuffer->jByteArray != nullptr) {
//                jniEnv->DeleteLocalRef(videoBuffer->jByteArray);
//            }
//            auto jByteArray = jniEnv->NewByteArray(videoBuffer->size);
//            jniEnv->SetByteArrayRegion(jByteArray, 0, videoBuffer->size, reinterpret_cast<const jbyte *>(videoBuffer->rgbaBuffer));
//            videoBuffer->jByteArray = jByteArray;
            videoBuffer->pts = (long) (frame->pts * 1000L / video_stream->time_base.den);
            LOGD("Decode video success: %ld", videoBuffer->pts);
            return DecodeSuccess;
        }
        LOGE("Decode unknown pkt");
        return DecodeFail;
    } else {
        LOGE("Decode wrong player context.");
        return DecodeFail;
    }
}

tMediaDecodeBuffer * tMediaPlayerContext::allocDecodeBuffer() {
    auto buffer = new tMediaDecodeBuffer;
    auto audioBuffer = new tMediaAudioBuffer;
    buffer->audioBuffer = audioBuffer;
    auto videoBuffer = new tMediaVideoBuffer;

    videoBuffer->width = video_width;
    videoBuffer->height = video_height;
    videoBuffer->rgbaFrame = av_frame_alloc();
    videoBuffer->size = av_image_get_buffer_size(AV_PIX_FMT_RGBA, video_width, video_height,1);
    videoBuffer->rgbaBuffer = static_cast<uint8_t *>(av_malloc(
            videoBuffer->size * sizeof(uint8_t)));
    av_image_fill_arrays(videoBuffer->rgbaFrame->data, videoBuffer->rgbaFrame->linesize, videoBuffer->rgbaBuffer,
                         AV_PIX_FMT_RGBA, video_width, video_height, 1);
    buffer->videoBuffer = videoBuffer;
    return buffer;
}

void tMediaPlayerContext::freeDecodeBuffer(tMediaDecodeBuffer *b) {
    auto audioBuffer = b->audioBuffer;
    if (audioBuffer != nullptr) {
        if (audioBuffer->pcmBuffer != nullptr) {
            free(audioBuffer->pcmBuffer);
        }
        if (audioBuffer->jByteArray != nullptr) {
            jniEnv->DeleteLocalRef(audioBuffer->jByteArray);
            free(audioBuffer->jByteArray);
        }
        free(audioBuffer);
        b->audioBuffer = nullptr;
    }
    auto videoBuffer = b->videoBuffer;
    if (videoBuffer != nullptr) {
        if (videoBuffer->rgbaFrame != nullptr) {
            av_frame_unref(videoBuffer->rgbaFrame);
            av_frame_free(&videoBuffer->rgbaFrame);
        }
        if (videoBuffer->rgbaBuffer != nullptr) {
            free(videoBuffer->rgbaBuffer);
        }
        if (videoBuffer->jByteArray != nullptr) {
            jniEnv->DeleteLocalRef(videoBuffer->jByteArray);
            free(videoBuffer->jByteArray);
        }
        free(videoBuffer);
        b->videoBuffer = nullptr;
    }
    free(b);
}

void tMediaPlayerContext::release() {
    if (pkt != nullptr) {
        av_packet_unref(pkt);
        av_packet_free(&pkt);
    }
    if (format_ctx != nullptr) {
        avformat_close_input(&format_ctx);
        avformat_free_context(format_ctx);
    }
    if (frame != nullptr) {
        av_frame_free(&frame);
    }

    // Video Release.
    if (video_decoder_ctx != nullptr) {
        avcodec_close(video_decoder_ctx);
        avcodec_free_context(&video_decoder_ctx);
    }

//    if (sws_ctx != nullptr) {
//        sws_freeContext(sws_ctx);
//    }

    // Audio free.
    if (audio_decoder_ctx != nullptr) {
        avcodec_close(audio_decoder_ctx);
        avcodec_free_context(&audio_decoder_ctx);
    }

    if (swr_ctx != nullptr) {
        swr_free(&swr_ctx);
    }
    free(this);
    LOGD("Release media player");
}
