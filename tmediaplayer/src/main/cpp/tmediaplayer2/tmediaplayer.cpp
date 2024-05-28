//
// Created by pengcheng.tan on 2024/5/27.
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

tMediaOptResult tMediaPlayerContext::prepare(
        const char *media_file_p,
        bool is_request_hw,
        int target_audio_channels,
        int target_audio_sample_rate,
        int target_audio_sample_bit_depth) {

    this->media_file = media_file_p;
    LOGD("Prepare media file: %s", media_file_p);
    this->format_ctx = avformat_alloc_context();
    int result = avformat_open_input(&format_ctx, media_file, nullptr, nullptr);
    if (result < 0) {
        LOGE("Avformat open file fail: %d", result);
        return OptFail;
    }
    LOGD("Input format: %s", format_ctx->iformat->name);

    // audio channels
    if (target_audio_channels == 1) {
        audio_output_channels = 1;
        audio_output_ch_layout = AV_CHANNEL_LAYOUT_MONO;
    } else {
        audio_output_channels = 2;
        audio_output_ch_layout = AV_CHANNEL_LAYOUT_STEREO;
    }

    // audio sample rate.
    if (target_audio_sample_rate == 44100) {
        audio_output_sample_rate = 44100;
    } else if (target_audio_sample_rate == 48000) {
        audio_output_sample_rate = 48000;
    } else if (target_audio_sample_rate == 96000) {
        audio_output_sample_rate = 96000;
    } else if (target_audio_sample_rate == 192000) {
        audio_output_sample_rate = 192000;
    } else {
        audio_output_sample_rate = 44100;
    }

    // audio output sample depth
    if (target_audio_sample_bit_depth == 8) {
        audio_output_sample_fmt = AV_SAMPLE_FMT_U8;
    } else if (target_audio_sample_bit_depth == 16) {
        audio_output_sample_fmt = AV_SAMPLE_FMT_S16;
    } else if (target_audio_sample_bit_depth == 32) {
        audio_output_sample_fmt = AV_SAMPLE_FMT_S32;
    } else {
        audio_output_sample_fmt = AV_SAMPLE_FMT_U8;
    }

    // Read metadata
    AVDictionaryEntry *metadataLocal = nullptr;
    int metadataCountLocal = 0;
    while ((metadataLocal = av_dict_get(format_ctx->metadata, "", metadataLocal, AV_DICT_IGNORE_SUFFIX)) != nullptr) {
        LOGD("Metadata %s=%s", metadataLocal->key, metadataLocal->value);
        metadataCountLocal ++;
    }
    LOGD("Metadata count: %d", metadataCountLocal);
    this->metadataCount = metadataCountLocal;
    if (metadataCount > 0) {
        this->metadata = static_cast<char **>(malloc(metadataCount * 2 * sizeof(char *)));
        metadataLocal = nullptr;
        for (int i = 0; i < metadataCount; i ++) {
            metadataLocal = av_dict_get(format_ctx->metadata, "", metadataLocal, AV_DICT_IGNORE_SUFFIX);
            int keyLen = strlen(metadataLocal->key);
            int valueLen = strlen(metadataLocal->value);
            char *key = static_cast<char *>(malloc((keyLen + 1) * sizeof(char)));
            char *value = static_cast<char *>(malloc((valueLen + 1) * sizeof(char)));
            memcpy(key, metadataLocal->key, keyLen);
            key[keyLen] = '\0';
            memcpy(value, metadataLocal->value, valueLen);
            value[valueLen] = '\0';
            metadata[i * 2] = key;
            metadata[i * 2 + 1] = value;
        }
    }

    // Find out first audio stream and video stream.
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
                if (this->video_stream != nullptr) {
                    LOGE("Find multiple video stream, skip it.");
                } else {
                    this->video_stream = s;
                    videoIsAttachPic = s->disposition & AV_DISPOSITION_ATTACHED_PIC; // Is music files' picture, only have one frame.
                    this->video_duration = 0L;
                    if (s->time_base.den > 0 && s->duration > 0 && !videoIsAttachPic && s->duration != AV_NOPTS_VALUE) {
                        this->video_duration = ((double)s->duration) * av_q2d(s->time_base) * 1000.0;
                    }
                    LOGD("Find video stream: duration=%ld, isAttachPic=%d", video_duration, videoIsAttachPic);
                }
                break;
            case AVMEDIA_TYPE_AUDIO:
                if (this->audio_stream != nullptr) {
                    LOGE("Find multiple audio stream, skip it.");
                } else {
                    this->audio_stream = s;
                    this->audio_duration = 0L;
                    if (s->duration != AV_NOPTS_VALUE && s->time_base.den > 0 && s->duration > 0) {
                        this->audio_duration = ((double)s->duration) * av_q2d(s->time_base) * 1000.0;
                    }
                    LOGD("Find audio stream: duration=%ld", audio_duration);
                }
                break;
            default:
                break;
        }
    }

    // No video stream and audio stream.
    if (video_stream == nullptr && audio_stream == nullptr) {
        LOGE("Didn't find video stream or audio stream");
        return OptFail;
    }

    // Video
    if (video_stream != nullptr) {
        AVCodecParameters *params = video_stream->codecpar;
        this->video_width = params->width;
        this->video_height = params->height;
        this->video_bits_per_raw_sample = params->bits_per_raw_sample;
        this->video_bitrate = params->bit_rate;
        auto frameRate = av_guess_frame_rate(format_ctx, video_stream, nullptr);
        this->video_fps = 0.0;
        if (frameRate.den > 0 && frameRate.num > 0 && !videoIsAttachPic) {
            this->video_fps = av_q2d(frameRate);
        }
        this->video_codec_id = params->codec_id;
        if (is_request_hw) {
            // Find android hardware codec.
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
                        this->video_codec_id = hwDecoder->id;
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
        // // set decode pixel size half
        // video_decoder_ctx->lowres = 1;
        // // set decode thread count
        // video_decoder_ctx->thread_count = 1;
        this->video_pixel_format = video_decoder_ctx->pix_fmt;
//        this->video_sws_ctx = sws_getContext(
//                video_width,
//                video_height,
//                video_decoder_ctx->pix_fmt,
//                video_width,
//                video_height,
//                AV_PIX_FMT_RGBA,
//                SWS_BICUBIC,
//                nullptr,
//                nullptr,
//                nullptr);
//        if (video_sws_ctx == nullptr) {
//            LOGE("Open video decoder get sws ctx fail.");
//            return OptFail;
//        }
        LOGD("Prepare video decoder success.");
    }

    // Audio
    if (audio_stream != nullptr) {
        auto params = audio_stream->codecpar;
        this->audio_codec_id = params->codec_id;
        this->audio_bits_per_raw_sample = params->bits_per_raw_sample;
        this->audio_bitrate = params->bit_rate;
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
        this->audio_channels = audio_decoder_ctx->ch_layout.nb_channels;
        this->audio_per_sample_bytes = av_get_bytes_per_sample(audio_decoder_ctx->sample_fmt);
        this->audio_sample_format = audio_decoder_ctx->sample_fmt;
        this->audio_simple_rate = audio_decoder_ctx->sample_rate;
        this->audio_swr_ctx = swr_alloc();

        swr_alloc_set_opts2(&audio_swr_ctx, &audio_output_ch_layout, audio_output_sample_fmt,audio_output_sample_rate,
                            &audio_decoder_ctx->ch_layout, audio_decoder_ctx->sample_fmt, audio_decoder_ctx->sample_rate,
                            0,nullptr);
        result = swr_init(audio_swr_ctx);
        if (result < 0) {
            LOGE("Init swr ctx fail: %d", result);
            return OptFail;
        }
        LOGD("Prepare audio decoder success.");
    }

    // decode need buffers.
    this->pkt = av_packet_alloc();
    this->frame = av_frame_alloc();

    this->duration = 0L;
    if (format_ctx->duration != AV_NOPTS_VALUE) {
        this->duration = ((double) format_ctx->duration) * av_q2d(AV_TIME_BASE_Q) * 1000.0;
    }

    return OptSuccess;
}


void tMediaPlayerContext::release() {
    if (pkt != nullptr) {
        av_packet_unref(pkt);
        av_packet_free(&pkt);
        pkt = nullptr;
    }
    if (format_ctx != nullptr) {
        avformat_close_input(&format_ctx);
        avformat_free_context(format_ctx);
        format_ctx = nullptr;
    }
    if (frame != nullptr) {
        av_frame_free(&frame);
        frame = nullptr;
    }

    // metadata
    if (metadata != nullptr) {
        for (int i = 0; i < metadataCount; i ++) {
            char *key = metadata[i * 2];
            char *value = metadata[i * 2 + 1];
            free(key);
            free(value);
            metadata[i * 2] = nullptr;
            metadata[i * 2 + 1] = nullptr;
        }
        free(metadata);
    }

    // Video Release.
    if (video_decoder_ctx != nullptr) {
        avcodec_close(video_decoder_ctx);
        avcodec_free_context(&video_decoder_ctx);
        video_decoder_ctx = nullptr;
    }
    if (hardware_ctx != nullptr) {
        av_buffer_unref(&hardware_ctx);
        hardware_ctx = nullptr;
    }
    if (video_sws_ctx != nullptr) {
        sws_freeContext(video_sws_ctx);
        video_sws_ctx = nullptr;
    }

    // Audio free.
    if (audio_decoder_ctx != nullptr) {
        avcodec_close(audio_decoder_ctx);
        avcodec_free_context(&audio_decoder_ctx);
        audio_decoder_ctx = nullptr;
    }
    if (audio_swr_ctx != nullptr) {
        swr_free(&audio_swr_ctx);
        audio_swr_ctx = nullptr;
    }
    free(this);
    LOGD("Release media player");
}
