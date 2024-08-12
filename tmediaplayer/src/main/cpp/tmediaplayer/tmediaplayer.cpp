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

void readMetadata(AVDictionary *src, Metadata *dst) {
    AVDictionaryEntry *metadataLocal = nullptr;
    int metadataCountLocal = 0;
    while ((metadataLocal = av_dict_get(src, "", metadataLocal, AV_DICT_IGNORE_SUFFIX)) != nullptr) {
        LOGD("Metadata %s=%s", metadataLocal->key, metadataLocal->value);
        metadataCountLocal ++;
    }
    dst->metadataCount = metadataCountLocal;
    if (metadataCountLocal > 0) {
        dst->metadata = static_cast<char **>(malloc(metadataCountLocal * 2 * sizeof(char *)));
        metadataLocal = nullptr;
        for (int i = 0; i < metadataCountLocal; i ++) {
            metadataLocal = av_dict_get(src, "", metadataLocal, AV_DICT_IGNORE_SUFFIX);
            int keyLen = strlen(metadataLocal->key);
            int valueLen = strlen(metadataLocal->value);
            char *key = static_cast<char *>(malloc((keyLen + 1) * sizeof(char)));
            char *value = static_cast<char *>(malloc((valueLen + 1) * sizeof(char)));
            memcpy(key, metadataLocal->key, keyLen);
            key[keyLen] = '\0';
            memcpy(value, metadataLocal->value, valueLen);
            value[valueLen] = '\0';
            dst->metadata[i * 2] = key;
            dst->metadata[i * 2 + 1] = value;
        }
    }
}

/**
 * Do not support bitmap subtitle.
 * @param s
 * @return
 */
bool isSupportSubtitleStream(AVStream * s) {
    auto codecId = s->codecpar->codec_id;
    auto type = s->codecpar->codec_type;
    return (type == AVMEDIA_TYPE_SUBTITLE &&
           codecId != AV_CODEC_ID_DVD_SUBTITLE &&
           codecId != AV_CODEC_ID_XSUB &&
           codecId != AV_CODEC_ID_HDMV_PGS_SUBTITLE
           // && codecId != AV_CODEC_ID_HDMV_TEXT_SUBTITLE

           );
}

static int decode_interrupt_cb(void *ctx)
{
    auto *player_ctx = static_cast<tMediaPlayerContext *>(ctx);
    return player_ctx->interruptReadPkt;
}

tMediaOptResult tMediaPlayerContext::prepare(
        const char *media_file_p,
        bool is_request_hw,
        int target_audio_channels,
        int target_audio_sample_rate,
        int target_audio_sample_bit_depth) {

    LOGD("Prepare media file: %s", media_file_p);
    this->format_ctx = avformat_alloc_context();
    this->format_ctx->interrupt_callback.callback = decode_interrupt_cb;
    this->format_ctx->interrupt_callback.opaque = this;
    AVDictionary * fmt_opts = nullptr;
    av_dict_set(&fmt_opts, "scan_all_pmts", "1", AV_DICT_DONT_OVERWRITE);
    // Timeout 5 seconds.
    av_dict_set(&fmt_opts, "rw_timeout", "5000000", AV_DICT_DONT_OVERWRITE);
    int result = avformat_open_input(&format_ctx, media_file_p, nullptr, &fmt_opts);
    av_dict_free(&fmt_opts);
    if (result < 0) {
        LOGE("Avformat open file fail: %d", result);
        return OptFail;
    }

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

    // Find stream info.
    result = avformat_find_stream_info(format_ctx, nullptr);
    if (result < 0) {
        LOGE("Avformat find stream info fail: %d", result);
        return OptFail;
    }

    // Format
    if (!strcmp(format_ctx->iformat->name, "rtp")
        || !strcmp(format_ctx->iformat->name, "rtsp")
        || !strcmp(format_ctx->iformat->name, "sdp")) {
        isRealTime = true;
    } else if (format_ctx->pb && (!strncmp(format_ctx->url, "rtp:", 4)|| !strncmp(format_ctx->url, "udp:", 4))) {
        isRealTime = true;
    } else {
        isRealTime = false;
    }
    int fmt_flags = format_ctx->iformat->flags;
    if (fmt_flags & AVFMT_NOFILE) {
        isNoFile = true;
    } else {
        isNoFile = false;
    }

    if (format_ctx->start_time != AV_NOPTS_VALUE) {
        startTime = (long) (((double) format_ctx->start_time) * av_q2d(AV_TIME_BASE_Q) * 1000.0);
    } else {
        startTime = -1L;
    }
    if (format_ctx->duration != AV_NOPTS_VALUE) {
        this->duration = (long) (((double) format_ctx->duration) * av_q2d(AV_TIME_BASE_Q) * 1000.0);
    } else {
        this->duration = -1L;
    }
    LOGD("Format=%s, isRealTime=%d, startTime=%ld, duration=%ld, isNoFile=%d", format_ctx->iformat->name, isRealTime, startTime, duration, isNoFile);

    // Read metadata
    fileMetadata = new Metadata;
    readMetadata(format_ctx->metadata, fileMetadata);

    // Container name
    const char *containerNameLocal = nullptr;
    if (format_ctx->iformat->long_name) {
        containerNameLocal = format_ctx->iformat->long_name;
    } else {
        containerNameLocal = format_ctx->iformat->name;
    }
    int containerNameLen = 0;
    if (containerNameLocal) {
        containerNameLen = strlen(containerNameLocal);
    }
    if (containerName != nullptr) {
        free(containerName);
    }
    containerName = static_cast<char *>(malloc((containerNameLen + 1) * sizeof(char)));
    if (containerNameLocal) {
        memcpy(containerName, containerNameLocal, containerNameLen);
    }
    containerName[containerNameLen] = '\0';
    LOGD("Container name: %s", containerName);

    // Find out first audio stream, video stream and all subtitle streams.
    int subtitleStreamCountLocal = 0;
    for (int i = 0; i < format_ctx->nb_streams; i ++) {
        auto s = format_ctx->streams[i];
        auto codec_type = s->codecpar->codec_type;
        switch (codec_type) {
            case AVMEDIA_TYPE_VIDEO:
                if (this->video_stream != nullptr && !videoIsAttachPic) {
                    LOGE("Find multiple video stream, skip it.");
                } else {
                    this->video_stream = s;
                    videoIsAttachPic = s->disposition & AV_DISPOSITION_ATTACHED_PIC; // Is music files' picture, only have one frame.
                    this->video_duration = 0L;
                    if (s->time_base.den > 0 && s->duration > 0 && !videoIsAttachPic && s->duration != AV_NOPTS_VALUE) {
                        this->video_duration = (long) (((double)s->duration) * av_q2d(s->time_base) * 1000.0);
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
                        this->audio_duration = (long) (((double)s->duration) * av_q2d(s->time_base) * 1000.0);
                    }
                    LOGD("Find audio stream: duration=%ld", audio_duration);
                }
                break;

            case AVMEDIA_TYPE_SUBTITLE:
                if (isSupportSubtitleStream(s)) {
                    subtitleStreamCountLocal ++;
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

    // Read subtitle streams
    this->subtitleStreamCount = subtitleStreamCountLocal;
    LOGD("Find %d subtitle streams", subtitleStreamCountLocal);
    if (subtitleStreamCountLocal > 0) {
        int subtitleIndex = 0;
        this->subtitleStreams = static_cast<SubtitleStream **>(malloc(sizeof(SubtitleStream *) * subtitleStreamCountLocal));
        for (int i = 0; i < format_ctx->nb_streams; i ++) {
            auto s = format_ctx->streams[i];
            auto codec_type = s->codecpar->codec_type;
            if (codec_type == AVMEDIA_TYPE_SUBTITLE) {
                // Do not support bitmap subtitle.
                if (isSupportSubtitleStream(s)) {
                    LOGD("SubtitleStream: %d", s->index);
                    auto* ts = new SubtitleStream;
                    subtitleStreams[subtitleIndex] = ts;
                    ts->stream = s;
                    readMetadata(s->metadata, &ts->streamMetadata);
                    subtitleIndex ++;
                } else {
                    auto codecId = s->codecpar->codec_id;
                    LOGE("Do not support subtitle stream: streamIndex=%d, codecId=%d", s->index, codecId);
                }
            }
        }
    }

    // Video
    if (video_stream != nullptr) {
        AVCodecParameters *params = video_stream->codecpar;
        this->video_width = params->width;
        this->video_height = params->height;
        this->video_bits_per_raw_sample = params->bits_per_raw_sample;
        this->video_bitrate = (int) params->bit_rate;
        auto frameRate = av_guess_frame_rate(format_ctx, video_stream, nullptr);
        this->video_fps = 0.0;
        if (frameRate.den > 0 && frameRate.num > 0 && !videoIsAttachPic) {
            this->video_fps = av_q2d(frameRate);
        }
        this->video_codec_id = params->codec_id;

        //region Hardware Decoder
        if (is_request_hw) {
            // Find android hardware codec.
            bool isFindHwDecoder = true;
            const char * hwCodecName;
            switch (params->codec_id) {
                case AV_CODEC_ID_H264:
                    hwCodecName = "h264_mediacodec";
                    break;
                case AV_CODEC_ID_HEVC:
                    hwCodecName = "hevc_mediacodec";
                    break;
                case AV_CODEC_ID_AV1:
                    hwCodecName = "av1_mediacodec";
                    break;
                case AV_CODEC_ID_VP8:
                    hwCodecName = "vp8_mediacodec";
                    break;
                case AV_CODEC_ID_VP9:
                    hwCodecName = "vp9_mediacodec";
                    break;
                default:
                    isFindHwDecoder = false;
                    break;
            }
            if (isFindHwDecoder) {
                AVHWDeviceType hwDeviceType = av_hwdevice_find_type_by_name("mediacodec");
                if (hwDeviceType == AV_HWDEVICE_TYPE_NONE) {
                    while ((hwDeviceType = av_hwdevice_iterate_types(hwDeviceType)) != AV_HWDEVICE_TYPE_NONE) {}
                }
                const AVCodec *hwDecoder = avcodec_find_decoder_by_name(hwCodecName);
                if (hwDecoder) {
                    // find pixel format.
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
                        if (result >= 0) {
                            LOGD("Set up %s hw device ctx.", hwCodecName);
                            this->video_decoder_ctx = avcodec_alloc_context3(video_decoder);
                            if (video_decoder_ctx) {
                                result = avcodec_parameters_to_context(video_decoder_ctx, params);
                                if (result >= 0) {
                                    video_decoder_ctx->get_format = get_hw_format;
                                    video_decoder_ctx->hw_device_ctx = av_buffer_ref(hardware_ctx);
                                    result = avcodec_open2(video_decoder_ctx, video_decoder, nullptr);
                                    if (result >= 0) {
                                        LOGD("Open %s video hw decoder ctx success.", hwCodecName);
                                        goto decoder_open_success;
                                    } else {
                                        avcodec_free_context(&video_decoder_ctx);
                                        video_decoder_ctx = nullptr;
                                        LOGE("Open %s video hw decoder ctx fail.", hwCodecName);
                                    }
                                } else {
                                    avcodec_free_context(&video_decoder_ctx);
                                    video_decoder_ctx = nullptr;
                                    LOGE("Attach video params to %s hw ctx fail: %d", hwCodecName, result);
                                }
                            } else {
                                LOGE("Create %s hw video decoder ctx fail.", hwCodecName);
                            }
                        } else {
                            LOGE("Create %s hw device ctx fail: %d", hwCodecName, result);
                        }
                    } else {
                        LOGE("Don't find %s hw decoder pix format", hwCodecName);
                    }
                } else {
                    LOGE("Don't find hw decoder: %s", hwCodecName);
                }
            }
        }
        //endregion

        //region Software Decoder
        this->video_decoder = avcodec_find_decoder(params->codec_id);
        if (video_decoder == nullptr) {
            LOGE("Didn't find sw video decoder.");
            return OptFail;
        }
        this->video_decoder_ctx = avcodec_alloc_context3(video_decoder);
        if (!video_decoder_ctx) {
            LOGE("Create sw video decoder ctx fail.");
            return OptFail;
        }
        result = avcodec_parameters_to_context(video_decoder_ctx, params);
        if (result < 0) {
            LOGE("Attach video params to sw decoder ctx fail: %d", result);
            return OptFail;
        }
        result = avcodec_open2(video_decoder_ctx, video_decoder, nullptr);
        if (result < 0) {
            LOGE("Open video sw decoder ctx fail: %d", result);
            return OptFail;
        } else {
            LOGD("Open video sw decoder ctx success.");
        }
        // endregion

        // // set decode pixel size half
        // video_decoder_ctx->lowres = 1;
        // // set decode thread count
        // video_decoder_ctx->thread_count = 1;
        decoder_open_success:
        this->video_pixel_format = video_decoder_ctx->pix_fmt;
        const char *codecName = nullptr;
        if (video_decoder->long_name) {
            codecName = video_decoder->long_name;
        } else {
            codecName = video_decoder->name;
        }
        int codecNameLen = 0;
        if (codecName) {
            codecNameLen = strlen(codecName);
        }
        if (videoDecoderName != nullptr) {
            free(videoDecoderName);
        }
        videoDecoderName = static_cast<char *>(malloc((codecNameLen + 1) * sizeof(char)));
        if (codecName) {
            memcpy(videoDecoderName, codecName, codecNameLen);
        }
        videoDecoderName[codecNameLen] = '\0';
        videoMetaData = new Metadata;
        readMetadata(video_stream->metadata, videoMetaData);
        LOGD("Prepare video decoder success: %s", videoDecoderName);
        this->video_frame = av_frame_alloc();
        this->video_pkt = av_packet_alloc();
    }

    // Audio
    if (audio_stream != nullptr) {
        auto params = audio_stream->codecpar;
        this->audio_codec_id = params->codec_id;
        this->audio_bits_per_raw_sample = params->bits_per_raw_sample;
        this->audio_bitrate = (int) params->bit_rate;
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
        const char *codecName = nullptr;
        if (audio_decoder->long_name) {
            codecName = audio_decoder->long_name;
        } else {
            codecName = audio_decoder->name;
        }
        int codecNameLen = 0;
        if (codecName) {
            codecNameLen = strlen(codecName);
        }
        if (audioDecoderName != nullptr) {
            free(audioDecoderName);
        }
        audioDecoderName = static_cast<char *>(malloc((codecNameLen + 1) * sizeof(char)));
        if (codecName) {
            memcpy(audioDecoderName, codecName, codecNameLen);
        }
        audioDecoderName[codecNameLen] = '\0';
        audioMetadata = new Metadata;
        readMetadata(audio_stream->metadata, audioMetadata);
        LOGD("Prepare audio decoder success: %s", codecName);
        this->audio_frame = av_frame_alloc();
        this->audio_pkt = av_packet_alloc();
    }

    // decode need buffers.
    this->pkt = av_packet_alloc();

    return OptSuccess;
}

tMediaReadPktResult tMediaPlayerContext::readPacket() {
    int ret = av_read_frame(format_ctx, pkt);
    if (ret < 0) {
        if (ret == AVERROR_EOF || avio_feof(format_ctx->pb)) {
            return ReadEof;
        } else {
            return ReadFail;
        }
    } else {
        if (video_stream && pkt->stream_index == video_stream->index) {
            pkt->time_base = video_stream->time_base;
            // video
            if (videoIsAttachPic) {
                return ReadVideoAttachmentSuccess;
            } else {
                return ReadVideoSuccess;
            }
        }
        if (audio_stream && pkt->stream_index == audio_stream->index) {
            pkt->time_base = audio_stream->time_base;
            // audio
            return ReadAudioSuccess;
        }
        if (subtitleStreams != nullptr && subtitleStreamCount > 0) {
            for (int i = 0; i < subtitleStreamCount; i ++) {
                auto s = subtitleStreams[i];
                if (s->stream->index == pkt->stream_index) {
                    pkt->time_base = s->stream->time_base;
                    return ReadSubtitleSuccess;
                }
            }
        }
        av_packet_unref(pkt);
        return UnknownPkt;
    }
}

void tMediaPlayerContext::movePacketRef(AVPacket *target) {
    av_packet_move_ref(target, pkt);
}

tMediaOptResult tMediaPlayerContext::pauseReadPacket() {
    av_read_pause(format_ctx);
    return OptSuccess;
}

tMediaOptResult tMediaPlayerContext::resumeReadPacket() {
    av_read_play(format_ctx);
    return OptSuccess;
}

tMediaOptResult tMediaPlayerContext::seekTo(int64_t targetPosInMillis) {
    int64_t seekTs = targetPosInMillis * AV_TIME_BASE / 1000L;
    int ret = avformat_seek_file(format_ctx, -1, INT64_MIN, seekTs, INT64_MAX, AVSEEK_FLAG_BACKWARD);
    if (ret < 0) {
        return OptFail;
    } else {
        return OptSuccess;
    }
}

tMediaDecodeResult decode(AVCodecContext *codec_ctx, AVFrame* frame, AVPacket *pkt) {

    int ret;
    ret = avcodec_send_packet(codec_ctx, pkt);
    if (ret != AVERROR(EAGAIN)) {
        av_packet_unref(pkt);
    }
    bool skipNextReadPkt = ret == AVERROR(EAGAIN);
    if (ret < 0 && ret != AVERROR(EAGAIN)) {
        LOGE("%s send packet fail: %d", codec_ctx->codec->name ,ret);
        return DecodeFail;
    }
    av_frame_unref(frame);
    ret = avcodec_receive_frame(codec_ctx, frame);
    if (ret < 0) {
        if (ret == AVERROR(EAGAIN)) {
            if (skipNextReadPkt) {
                return decode(codec_ctx, frame, pkt);
            } else {
                return DecodeFailAndNeedMorePkt;
            }
        }
        if (ret == AVERROR_EOF) {
            return DecodeEnd;
        }
        LOGE("%s receive frame fail: %d", codec_ctx->codec->name, ret);
        return DecodeFail;
    } else {
        if (skipNextReadPkt) {
            return DecodeSuccessAndSkipNextPkt;
        } else {
            return DecodeSuccess;
        }
    }
}

tMediaDecodeResult tMediaPlayerContext::decodeVideo(AVPacket *targetPkt) {
    if (targetPkt != nullptr) {
        av_packet_move_ref(video_pkt, targetPkt);
    }
    return decode(video_decoder_ctx, video_frame, video_pkt);
}

void tMediaPlayerContext::flushVideoCodecBuffer() {
    avcodec_flush_buffers(video_decoder_ctx);
}

tMediaOptResult tMediaPlayerContext::moveDecodedVideoFrameToBuffer(tMediaVideoBuffer *videoBuffer) {
    int w = video_frame->width;
    int h = video_frame->height;
    auto format = video_frame->format;
//    auto colorRange = frame->color_range;
//    auto colorPrimaries = frame->color_primaries;
//    auto colorSpace = frame->colorspace;
    if (format == AV_PIX_FMT_YUV420P) {
        if (w % YUV_ALIGN_SIZE == 0) {
            videoBuffer->width = w;
        } else {
            videoBuffer->width = w + (YUV_ALIGN_SIZE - (w % YUV_ALIGN_SIZE));
        }
        videoBuffer->height = h;
        int yuvSize = av_image_get_buffer_size(AV_PIX_FMT_YUV420P, videoBuffer->width, videoBuffer->height, 1);
        int ySize = av_image_get_buffer_size(AV_PIX_FMT_GRAY8, videoBuffer->width, videoBuffer->height, 1);
        int uSize = (yuvSize - ySize) / 2;
        int vSize = uSize;

        // alloc Y buffer if need.
        if (videoBuffer->yBufferSize < ySize || videoBuffer->yBuffer == nullptr) {
            if (videoBuffer->yBuffer != nullptr) {
                free(videoBuffer->yBuffer);
            }
            videoBuffer->yBuffer = static_cast<uint8_t *>(av_malloc(ySize * sizeof(uint8_t)));
            videoBuffer->yBufferSize = ySize;
        }

        // alloc U buffer if need.
        if (videoBuffer->uBufferSize < uSize || videoBuffer->uBuffer == nullptr) {
            if (videoBuffer->uBuffer != nullptr) {
                free(videoBuffer->uBuffer);
            }
            videoBuffer->uBuffer = static_cast<uint8_t *>(av_malloc(uSize * sizeof(uint8_t)));
            videoBuffer->uBufferSize = uSize;
        }

        // // alloc V buffer if need.
        if (videoBuffer->vBufferSize < vSize || videoBuffer->vBuffer == nullptr) {
            if (videoBuffer->vBuffer != nullptr) {
                free(videoBuffer->vBuffer);
            }
            videoBuffer->vBuffer = static_cast<uint8_t *>(av_malloc(vSize * sizeof(uint8_t)));
            videoBuffer->vBufferSize = vSize;
        }
        uint8_t *yBuffer = videoBuffer->yBuffer;
        uint8_t *uBuffer = videoBuffer->uBuffer;
        uint8_t *vBuffer = videoBuffer->vBuffer;
        int lineSize[AV_NUM_DATA_POINTERS];
        av_image_fill_linesizes(lineSize, AV_PIX_FMT_YUV420P, videoBuffer->width);
        // Copy yuv data to video frame.
        av_image_copy_plane(yBuffer, lineSize[0], video_frame->data[0], video_frame->linesize[0], lineSize[0], h);
        av_image_copy_plane(uBuffer, lineSize[1], video_frame->data[1], video_frame->linesize[1], lineSize[1], h / 2);
        av_image_copy_plane(vBuffer, lineSize[2], video_frame->data[2], video_frame->linesize[2], lineSize[2], h / 2);
        videoBuffer->yContentSize = ySize;
        videoBuffer->uContentSize = uSize;
        videoBuffer->vContentSize = vSize;
        videoBuffer->type = Yuv420p;
    } else if ((format == AV_PIX_FMT_NV12 || format == AV_PIX_FMT_NV21)) {
        if (w % YUV_ALIGN_SIZE == 0) {
            videoBuffer->width = w;
        } else {
            videoBuffer->width = w + (YUV_ALIGN_SIZE - (w % YUV_ALIGN_SIZE));
        }
        videoBuffer->height = h;
        int yuvSize;
        if (format == AV_PIX_FMT_NV12) {
            yuvSize = av_image_get_buffer_size(AV_PIX_FMT_NV12, videoBuffer->width, videoBuffer->height, 1);
        } else {
            yuvSize = av_image_get_buffer_size(AV_PIX_FMT_NV21, videoBuffer->width, videoBuffer->height, 1);
        }
        int ySize =  av_image_get_buffer_size(AV_PIX_FMT_GRAY8, videoBuffer->width, videoBuffer->height, 1);
        int uvSize = yuvSize - ySize;
        // alloc Y buffer if need.
        if (videoBuffer->yBufferSize < ySize || videoBuffer->yBuffer == nullptr) {
            if (videoBuffer->yBuffer != nullptr) {
                free(videoBuffer->yBuffer);
            }
            videoBuffer->yBuffer = static_cast<uint8_t *>(av_malloc(ySize * sizeof(uint8_t)));
            videoBuffer->yBufferSize = ySize;
        }

        // alloc UV buffer if need.
        if (videoBuffer->uvBufferSize < uvSize || videoBuffer->uvBuffer == nullptr) {
            if (videoBuffer->uvBuffer != nullptr) {
                free(videoBuffer->uvBuffer);
            }
            videoBuffer->uvBuffer = static_cast<uint8_t *>(av_malloc(uvSize * sizeof(uint8_t)));
            videoBuffer->uvBufferSize = uvSize;
        }
        uint8_t *yBuffer = videoBuffer->yBuffer;
        uint8_t *uvBuffer = videoBuffer->uvBuffer;
        int lineSize[AV_NUM_DATA_POINTERS];
        if (format == AV_PIX_FMT_NV12) {
            av_image_fill_linesizes(lineSize, AV_PIX_FMT_NV12, videoBuffer->width);
        } else {
            av_image_fill_linesizes(lineSize, AV_PIX_FMT_NV21, videoBuffer->width);
        }
        // Copy Y buffer.
        av_image_copy_plane(yBuffer, lineSize[0], video_frame->data[0], video_frame->linesize[0], lineSize[0], h);
        // Copy UV buffer.
        av_image_copy_plane(uvBuffer, lineSize[1], video_frame->data[1], video_frame->linesize[1], lineSize[1], h / 2);
        videoBuffer->yContentSize = ySize;
        videoBuffer->uvContentSize = uvSize;
        if (video_frame->format == AV_PIX_FMT_NV12) {
            videoBuffer->type = Nv12;
        } else {
            videoBuffer->type = Nv21;
        }
    } else if (format == AV_PIX_FMT_RGBA) {
        videoBuffer->width = w;
        videoBuffer->height = h;
        int rgbaSize = av_image_get_buffer_size(AV_PIX_FMT_RGBA, w, h, 1);
        // alloc rgba data if need.
        if (videoBuffer->rgbaBufferSize < rgbaSize || videoBuffer->rgbaBuffer == nullptr) {
            if (videoBuffer->rgbaBuffer != nullptr) {
                free(videoBuffer->rgbaBuffer);
            }
            videoBuffer->rgbaBuffer = static_cast<uint8_t *>(av_malloc(rgbaSize * sizeof(uint8_t)));
            videoBuffer->rgbaBufferSize = rgbaSize;
        }
        uint8_t *rgbaBuffer = videoBuffer->rgbaBuffer;
        int lineSize[AV_NUM_DATA_POINTERS];
        av_image_fill_linesizes(lineSize, AV_PIX_FMT_RGBA, videoBuffer->width);
        av_image_copy_plane(rgbaBuffer, lineSize[0], video_frame->data[0], video_frame->linesize[0], lineSize[0], h);
        // Copy RGBA data.
        // copyFrameData(rgbaBuffer, frame->data[0], w, h, frame->linesize[0], 4);
        videoBuffer->rgbaContentSize = rgbaSize;
        videoBuffer->type = Rgba;
    } else {
        // Others format need to convert to Yuv420p.
        if (w != video_width ||
            h != video_height ||
            video_sws_ctx == nullptr) {
            LOGD("Decode video change rgbaSize, recreate sws ctx.");
            if (video_sws_ctx != nullptr) {
                sws_freeContext(video_sws_ctx);
            }

            this->video_sws_ctx = sws_getContext(
                    w,
                    h,
                    (AVPixelFormat) video_frame->format,
                    w,
                    h,
                    AV_PIX_FMT_YUV420P,
                    SWS_BICUBIC,
                    nullptr,
                    nullptr,
                    nullptr);
            if (video_sws_ctx == nullptr) {
                LOGE("Decode video fail, sws ctx create fail.");
                return OptFail;
            }
        }

        if (w % YUV_ALIGN_SIZE == 0) {
            videoBuffer->width = w;
        } else {
            videoBuffer->width = w + (YUV_ALIGN_SIZE - (w % YUV_ALIGN_SIZE));
        }
        videoBuffer->height = h;
        int yuvSize = av_image_get_buffer_size(AV_PIX_FMT_YUV420P, videoBuffer->width, videoBuffer->height, 1);
        int ySize = av_image_get_buffer_size(AV_PIX_FMT_GRAY8, videoBuffer->width, videoBuffer->height, 1);
        int uSize = (yuvSize - ySize) / 2;
        int vSize = uSize;

        // alloc Y buffer if need.
        if (videoBuffer->yBufferSize < ySize || videoBuffer->yBuffer == nullptr) {
            if (videoBuffer->yBuffer != nullptr) {
                free(videoBuffer->yBuffer);
            }
            videoBuffer->yBuffer = static_cast<uint8_t *>(av_malloc(ySize * sizeof(uint8_t)));
            videoBuffer->yBufferSize = ySize;
        }

        // alloc U buffer if need.
        if (videoBuffer->uBufferSize < uSize || videoBuffer->uBuffer == nullptr) {
            if (videoBuffer->uBuffer != nullptr) {
                free(videoBuffer->uBuffer);
            }
            videoBuffer->uBuffer = static_cast<uint8_t *>(av_malloc(uSize * sizeof(uint8_t)));
            videoBuffer->uBufferSize = uSize;
        }

        // // alloc V buffer if need.
        if (videoBuffer->vBufferSize < vSize || videoBuffer->vBuffer == nullptr) {
            if (videoBuffer->vBuffer != nullptr) {
                free(videoBuffer->vBuffer);
            }
            videoBuffer->vBuffer = static_cast<uint8_t *>(av_malloc(vSize * sizeof(uint8_t)));
            videoBuffer->vBufferSize = vSize;
        }

        uint8_t *data[AV_NUM_DATA_POINTERS] = {videoBuffer->yBuffer, videoBuffer->uBuffer, videoBuffer->vBuffer};
        int lineSize[AV_NUM_DATA_POINTERS];
        av_image_fill_linesizes(lineSize, AV_PIX_FMT_YUV420P, videoBuffer->width);
        // Convert to yuv420p.
        int result = sws_scale(video_sws_ctx, video_frame->data, video_frame->linesize, 0, video_frame->height, data, lineSize);
        if (result < 0) {
            videoBuffer->type = UnknownImgType;
            // Convert fail.
            LOGE("Decode video sws scale fail: %d", result);
            return OptFail;
        }
        videoBuffer->yContentSize = ySize;
        videoBuffer->uContentSize = uSize;
        videoBuffer->vContentSize = vSize;
        videoBuffer->type = Yuv420p;
    }
    auto time_base = video_stream->time_base;
    if (time_base.den > 0 && video_frame->pts != AV_NOPTS_VALUE) {
        videoBuffer->pts = (long) ((double)video_frame->pts * av_q2d(time_base) * 1000.0);
    } else {
        videoBuffer->pts = 0L;
    }
    if (time_base.den > 0) {
        videoBuffer->duration = (long) ((double)video_frame->duration * av_q2d(time_base) * 1000.0);
    } else {
        videoBuffer->duration = 0L;
    }
    if (w != video_width || h != video_height) {
        video_width = w;
        video_height = h;
    }
    av_frame_unref(video_frame);
    return OptSuccess;
}

tMediaDecodeResult tMediaPlayerContext::decodeAudio(AVPacket *targetPkt) {
    if (targetPkt != nullptr) {
        av_packet_move_ref(audio_pkt, targetPkt);
    }
    return decode(audio_decoder_ctx, audio_frame, audio_pkt);
}

void tMediaPlayerContext::flushAudioCodecBuffer() {
    avcodec_flush_buffers(audio_decoder_ctx);
}

tMediaOptResult tMediaPlayerContext::moveDecodedAudioFrameToBuffer(tMediaAudioBuffer *audioBuffer) {
    int in_nb_samples = audio_frame->nb_samples;

    // Get current output frame contains sample bufferSize per channel.
    int out_nb_samples = (int) av_rescale_rnd( swr_get_delay(audio_swr_ctx, audio_frame->sample_rate) + in_nb_samples, audio_output_sample_rate, audio_decoder_ctx->sample_rate, AV_ROUND_UP); // swr_get_out_samples(swr_ctx, in_nb_samples);

    if (out_nb_samples <= 0) {
        LOGE("Get out put nb samples fail: %d", out_nb_samples);
        return OptFail;
    }
    // Get current output audio frame need buffer bufferSize.
    int lineSize = 0;
    int out_audio_buffer_size = av_samples_get_buffer_size(&lineSize, audio_output_channels, out_nb_samples, audio_output_sample_fmt, 1);
    // Alloc pcm buffer if need.
    if (audioBuffer->bufferSize < out_audio_buffer_size || audioBuffer->pcmBuffer == nullptr) {
        int in_audio_buffer_size = av_samples_get_buffer_size(audio_frame->linesize, audio_channels, in_nb_samples, audio_decoder_ctx->sample_fmt, 1);
        LOGD("Decode audio change bufferSize, outBufferSize=%d, need bufferSize=%d, inBufferSize=%d", audioBuffer->bufferSize, out_audio_buffer_size, in_audio_buffer_size);
        if (audioBuffer->pcmBuffer != nullptr) {
            free(audioBuffer->pcmBuffer);
        }
        audioBuffer->pcmBuffer = static_cast<uint8_t *>(malloc(out_audio_buffer_size));
        audioBuffer->bufferSize = out_audio_buffer_size;
    }
    // Convert to target output pcm format data.
    int real_out_nb_samples = swr_convert(audio_swr_ctx, &(audioBuffer->pcmBuffer), out_nb_samples, (const uint8_t **)(audio_frame->data), in_nb_samples);
    if (real_out_nb_samples < 0) {
        LOGE("Decode audio swr convert fail: %d", real_out_nb_samples);
        return OptFail;
    }
    auto time_base = audio_stream->time_base;
    if (time_base.den > 0 && audio_frame->pts != AV_NOPTS_VALUE) {
        audioBuffer->pts = (long) ((double)audio_frame->pts * av_q2d(time_base) * 1000.0);
    } else {
        audioBuffer->pts = 0L;
    }
    if (time_base.den > 0) {
        audioBuffer->duration = (long) ((double)audio_frame->duration * av_q2d(time_base) * 1000.0);
    } else {
        audioBuffer->duration = 0L;
    }
    int contentBufferSize = av_samples_get_buffer_size(&lineSize, audio_output_channels, real_out_nb_samples, audio_output_sample_fmt, 1);
    audioBuffer->contentSize = lineSize;
    if (contentBufferSize != lineSize) {
        LOGE("output lineSize=%d, contentBufferSize=%d", lineSize, contentBufferSize);
    }
    av_frame_unref(audio_frame);
    return OptSuccess;
}

void releaseMetadata(Metadata *src) {
    for (int i = 0; i < src->metadataCount; i ++) {
        char *key = src->metadata[i * 2];
        char *value = src->metadata[i * 2 + 1];
        free(key);
        free(value);
        src->metadata[i * 2] = nullptr;
        src->metadata[i * 2 + 1] = nullptr;
    }
    src->metadataCount = 0;
    free(src->metadata);
    src->metadata = nullptr;
}

void tMediaPlayerContext::requestInterruptReadPkt() {
    this->interruptReadPkt = true;
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

    // File Metadata
    releaseMetadata(fileMetadata);

    // Container name
    if (containerName != nullptr) {
        free(containerName);
        containerName = nullptr;
    }

    // Video Release.
    if (video_decoder_ctx != nullptr) {
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
    if (video_frame != nullptr) {
        av_frame_unref(video_frame);
        av_frame_free(&video_frame);
        video_frame = nullptr;
    }
    if (video_pkt != nullptr) {
        av_packet_unref(video_pkt);
        av_packet_free(&video_pkt);
        video_pkt = nullptr;
    }
    if (videoDecoderName != nullptr) {
        free(videoDecoderName);
        videoDecoderName = nullptr;
    }
    if (videoMetaData != nullptr) {
        releaseMetadata(videoMetaData);
        free(videoMetaData);
        videoMetaData = nullptr;
    }

    // Audio free.
    if (audio_decoder_ctx != nullptr) {
        avcodec_free_context(&audio_decoder_ctx);
        audio_decoder_ctx = nullptr;
    }
    if (audio_swr_ctx != nullptr) {
        swr_free(&audio_swr_ctx);
        audio_swr_ctx = nullptr;
    }
    if (audio_frame != nullptr) {
        av_frame_unref(audio_frame);
        av_frame_free(&audio_frame);
        audio_frame = nullptr;
    }
    if (audio_pkt != nullptr) {
        av_packet_unref(audio_pkt);
        av_packet_free(&audio_pkt);
        audio_pkt = nullptr;
    }
    if (audioDecoderName != nullptr) {
        free(audioDecoderName);
        audioDecoderName = nullptr;
    }
    if (audioMetadata != nullptr) {
        releaseMetadata(audioMetadata);
        free(audioMetadata);
        audioMetadata = nullptr;
    }

    // Subtitle free
    if (subtitleStreams != nullptr) {
        for (int i = 0; i < subtitleStreamCount; i ++) {
            auto s = subtitleStreams[i];
            releaseMetadata(&s->streamMetadata);
            free(s);
        }
        free(subtitleStreams);
        subtitleStreamCount = 0;
        subtitleStreams = nullptr;
    }

    free(this);
    LOGD("Release media player");
}
