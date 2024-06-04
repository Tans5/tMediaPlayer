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
        this->video_frame = av_frame_alloc();
        this->video_pkt = av_packet_alloc();
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
        this->audio_frame = av_frame_alloc();
        this->audio_pkt = av_packet_alloc();
    }

    // decode need buffers.
    this->pkt = av_packet_alloc();

    this->duration = 0L;
    if (format_ctx->duration != AV_NOPTS_VALUE) {
        this->duration = ((double) format_ctx->duration) * av_q2d(AV_TIME_BASE_Q) * 1000.0;
    }

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
            // video
            if (videoIsAttachPic) {
                return ReadVideoAttachmentSuccess;
            } else {
                return ReadVideoSuccess;
            }
        }
        if (audio_stream && pkt->stream_index == audio_stream->index) {
            // audio
            return ReadAudioSuccess;
        }
        return UnknownPkt;
    }
}

void tMediaPlayerContext::movePacketRef(AVPacket *target) {
    av_packet_move_ref(target, pkt);
    if (video_stream && video_stream->index == pkt->stream_index) {
        target->time_base = video_stream->time_base;
    }
    if (audio_stream && audio_stream->index == pkt->stream_index) {
        target->time_base = audio_stream->time_base;
    }
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
    bool skipNextReadPkt = false;
    int ret;
    if (pkt != nullptr) {
        ret = avcodec_send_packet(codec_ctx, pkt);
        if (ret != AVERROR(EAGAIN)) {
            av_packet_unref(pkt);
        }
        if (ret < 0) {
            if (ret == AVERROR(EAGAIN)) {
                skipNextReadPkt = true;
            } else {
                return DecodeFail;
            }
        }
    }
    ret = avcodec_receive_frame(codec_ctx, frame);
    if (ret < 0) {
        if (ret == AVERROR(EAGAIN)) {
            return DecodeFailAndNeedMorePkt;
        }
        if (ret == AVERROR_EOF) {
            return DecodeEnd;
        }
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
    AVPacket *v_pkt = nullptr;
    if (targetPkt != nullptr) {
        v_pkt = video_pkt;
        av_packet_move_ref(v_pkt, targetPkt);
    }
    return decode(video_decoder_ctx, video_frame, v_pkt);
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
        videoBuffer->width = w;
        videoBuffer->height = h;
        int ySize = w * h;
        int uSize = ySize / 4;
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
        // Copy yuv data to video frame.
        av_image_copy_plane(yBuffer, w, video_frame->data[0], video_frame->linesize[0], w, h);
        av_image_copy_plane(uBuffer, w / 2, video_frame->data[1], video_frame->linesize[1], w / 2, h / 2);
        av_image_copy_plane(vBuffer, w / 2, video_frame->data[2], video_frame->linesize[2], w / 2, h / 2);
        videoBuffer->yContentSize = ySize;
        videoBuffer->uContentSize = uSize;
        videoBuffer->vContentSize = vSize;
        videoBuffer->type = Yuv420p;
    } else if ((format == AV_PIX_FMT_NV12 || format == AV_PIX_FMT_NV21)) {
        videoBuffer->width = w;
        videoBuffer->height = h;
        int ySize = w * h;
        int uvSize = ySize / 2;
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
        // Copy Y buffer.
        av_image_copy_plane(yBuffer, w, video_frame->data[0], video_frame->linesize[0], w, h);
        // copyFrameData(yBuffer, frame->data[0], w, h, frame->linesize[0], 1);
        // Copy UV buffer.
        av_image_copy_plane(uvBuffer, w, video_frame->data[1], video_frame->linesize[1], w, h / 2);
        // copyFrameData(uvBuffer, frame->data[1], w / 2, h / 2, frame->linesize[1], 2);
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
        int rgbaSize = w * h * 4;
        // alloc rgba data if need.
        if (videoBuffer->rgbaBufferSize < rgbaSize || videoBuffer->rgbaBuffer == nullptr) {
            if (videoBuffer->rgbaBuffer != nullptr) {
                free(videoBuffer->rgbaBuffer);
            }
            videoBuffer->rgbaBuffer = static_cast<uint8_t *>(av_malloc(rgbaSize * sizeof(uint8_t)));
            videoBuffer->rgbaBufferSize = rgbaSize;
        }
        uint8_t *rgbaBuffer = videoBuffer->rgbaBuffer;
        av_image_copy_plane(rgbaBuffer, w * 4, video_frame->data[0], video_frame->linesize[0], w * 4, h);
        // Copy RGBA data.
        // copyFrameData(rgbaBuffer, frame->data[0], w, h, frame->linesize[0], 4);
        videoBuffer->rgbaContentSize = rgbaSize;
        videoBuffer->type = Rgba;
    } else {
        // Others format need to convert to RGBA.
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
                    AV_PIX_FMT_RGBA,
                    SWS_BICUBIC,
                    nullptr,
                    nullptr,
                    nullptr);
            if (video_sws_ctx == nullptr) {
                LOGE("Decode video fail, sws ctx create fail.");
                return OptFail;
            }
        }

        int rgbaSize = w * h * 4;
        // Alloc new RGBA frame and buffer if need.
        if (videoBuffer->rgbaBufferSize < rgbaSize ||
            videoBuffer->rgbaBuffer == nullptr ||
            videoBuffer->rgbaFrame == nullptr) {
            LOGD("Decode video create new buffer.");
            if (videoBuffer->rgbaBuffer != nullptr) {
                free(videoBuffer->rgbaBuffer);
            }
            if (videoBuffer->rgbaFrame != nullptr) {
                av_frame_free(&(videoBuffer->rgbaFrame));
                videoBuffer->rgbaFrame = nullptr;
            }
            videoBuffer->rgbaFrame = av_frame_alloc();
            videoBuffer->rgbaBuffer = static_cast<uint8_t *>(av_malloc(rgbaSize * sizeof(uint8_t)));
            videoBuffer->rgbaBufferSize = rgbaSize;
            videoBuffer->rgbaContentSize = rgbaSize;
            // Fill rgbaBuffer to rgbaFrame
            av_image_fill_arrays(videoBuffer->rgbaFrame->data, videoBuffer->rgbaFrame->linesize, videoBuffer->rgbaBuffer,
                                 AV_PIX_FMT_RGBA, w, h, 1);
            videoBuffer->width = w;
            videoBuffer->height = h;
        }
        // Convert to rgba.
        int result = sws_scale(video_sws_ctx, video_frame->data, video_frame->linesize, 0, video_frame->height, videoBuffer->rgbaFrame->data, videoBuffer->rgbaFrame->linesize);
        if (result < 0) {
            // Convert fail.
            LOGE("Decode video sws scale fail: %d", result);
            return OptFail;
        }
        videoBuffer->type = Rgba;
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
    AVPacket *a_pkt = nullptr;
    if (targetPkt != nullptr) {
        a_pkt = audio_pkt;
        av_packet_move_ref(a_pkt, targetPkt);
    }
    return decode(audio_decoder_ctx, audio_frame, a_pkt);
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
    free(this);
    LOGD("Release media player");
}
