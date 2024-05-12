//
// Created by pengcheng.tan on 2023/7/13.
//
#include "tmediaplayer.h"
#include "media_time.h"


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

tMediaOptResult tMediaPlayerContext::prepare(const char *media_file_p, bool is_request_hw, int target_audio_channels, int target_audio_sample_rate, int target_audio_sample_bit_depth) {
    this->media_file = media_file_p;
    LOGD("Prepare media file: %s", media_file_p);
    this->format_ctx = avformat_alloc_context();
    int result = avformat_open_input(&format_ctx, media_file, nullptr, nullptr);
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

    // audio sample depth
    if (target_audio_sample_bit_depth == 8) {
        audio_output_sample_fmt = AV_SAMPLE_FMT_U8;
    } else if (target_audio_sample_bit_depth == 16) {
        audio_output_sample_fmt = AV_SAMPLE_FMT_S16;
    } else if (target_audio_sample_bit_depth == 32) {
        audio_output_sample_fmt = AV_SAMPLE_FMT_S32;
    } else {
        audio_output_sample_fmt = AV_SAMPLE_FMT_U8;
    }

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

    result = avformat_find_stream_info(format_ctx, nullptr);
    if (result < 0) {
        LOGE("Avformat find stream info fail: %d", result);
        return OptFail;
    }

    // Find out first audio stream and video stream.
    for (int i = 0; i < format_ctx->nb_streams; i ++) {
        auto s = format_ctx->streams[i];
        auto codec_type = s->codecpar->codec_type;
        switch (codec_type) {
            case AVMEDIA_TYPE_VIDEO:
                if (this->video_stream != nullptr) {
                    LOGE("Find multiple video stream, skip it.");
                } else {
                    this->video_stream = s;
                    if (s->time_base.den > 0 && s->duration > 0) {
                        this->video_duration = (long) ((double)s->duration * av_q2d(s->time_base) * 1000L);
                    } else {
                        this->video_duration = 0L;
                    }
                    LOGD("Find video stream: duration=%ld", video_duration);
                }
                break;
            case AVMEDIA_TYPE_AUDIO:
                if (this->audio_stream != nullptr) {
                    LOGE("Find multiple audio stream, skip it.");
                } else {
                    this->audio_stream = s;
                    if (s->time_base.den > 0 && s->duration > 0) {
                        this->audio_duration = (long) ((double)s->duration * av_q2d(s->time_base) * 1000L);
                    } else {
                        this->audio_duration = 0L;
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
        auto frameRate = video_stream->avg_frame_rate;
        this->video_fps = 0.0;
        if (frameRate.den > 0 && frameRate.num > 0) {
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
        this->video_pixel_format = video_decoder_ctx->pix_fmt;
        this->sws_ctx = sws_getContext(
                video_width,
                video_height,
                video_decoder_ctx->pix_fmt,
                video_width,
                video_height,
                AV_PIX_FMT_RGBA,
                SWS_BICUBIC,
                nullptr,
                nullptr,
                nullptr);
        if (sws_ctx == nullptr) {
            LOGE("Open video decoder get sws ctx fail.");
            return OptFail;
        }
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
        this->swr_ctx = swr_alloc();

        swr_alloc_set_opts2(&swr_ctx, &audio_output_ch_layout, audio_output_sample_fmt,audio_output_sample_rate,
                           &audio_decoder_ctx->ch_layout, audio_decoder_ctx->sample_fmt, audio_decoder_ctx->sample_rate,
                           0,nullptr);
        result = swr_init(swr_ctx);
        if (result < 0) {
            LOGE("Init swr ctx fail: %d", result);
            return OptFail;
        }
        LOGD("Prepare audio decoder success.");
    }

    // decode need buffers.
    this->pkt = av_packet_alloc();
    this->frame = av_frame_alloc();

    this->duration = format_ctx->duration * av_q2d(AV_TIME_BASE_Q) * 1000L;

    return OptSuccess;
}

tMediaOptResult tMediaPlayerContext::resetDecodeProgress() {
    return seekTo(0, nullptr, nullptr, false);
}

/**
 * Only video frame would write to videoBuffer
 * @param targetPtsInMillis
 * @param videoBuffer
 * @param needDecode
 * @return
 */
tMediaOptResult tMediaPlayerContext::seekTo(long targetPtsInMillis, tMediaDecodeBuffer* videoBuffer, tMediaDecodeBuffer* audioBuffer, bool needDecode) {
    if (format_ctx != nullptr) {
        if (video_stream == nullptr && audio_stream == nullptr) {
            return OptFail;
        } else {
            if (targetPtsInMillis > duration || targetPtsInMillis < 0) {
                LOGE("Wrong seek pts: %ld, duration: %ld", targetPtsInMillis, duration);
                return OptFail;
            }
            int video_reset_result = -1, audio_reset_result = -1;
            if (video_stream != nullptr && video_fps > 0.0f) {
                avcodec_flush_buffers(video_decoder_ctx);
                int64_t seekTimestamp = av_rescale_q(targetPtsInMillis * AV_TIME_BASE / 1000, AV_TIME_BASE_Q, video_stream->time_base);
                video_reset_result = avformat_seek_file(format_ctx, video_stream->index, INT64_MIN, seekTimestamp, INT64_MAX, AVSEEK_FLAG_BACKWARD);
                if (video_reset_result < 0) {
                    LOGE("Seek video progress fail: %d", video_reset_result);
                }
            }
            if (audio_stream != nullptr) {
                avcodec_flush_buffers(audio_decoder_ctx);
                int64_t seekTimestamp = av_rescale_q(targetPtsInMillis * AV_TIME_BASE / 1000, AV_TIME_BASE_Q, audio_stream->time_base);
                audio_reset_result = avformat_seek_file(format_ctx, audio_stream->index, INT64_MIN, seekTimestamp, INT64_MAX, AVSEEK_FLAG_BACKWARD);
                if (audio_reset_result < 0) {
                    LOGE("Seek audio progress fail: %d", audio_reset_result);
                }
            }
            for (int i = 0; i < format_ctx->nb_streams; i ++) {
                auto s = format_ctx->streams[i];
                if ((video_stream != nullptr && s->index == video_stream->index) || (audio_stream != nullptr && s->index == audio_stream->index)) {
                    continue;
                } else {
                    int64_t seekTimestamp = av_rescale_q(targetPtsInMillis * AV_TIME_BASE / 1000, AV_TIME_BASE_Q, s->time_base);
                    avformat_seek_file(format_ctx, s->index, INT64_MIN, seekTimestamp, INT64_MAX, AVSEEK_FLAG_BACKWARD);
                }
            }
            skipPktRead = false;
            if (video_reset_result >=0 || audio_reset_result >= 0) {
                if (!needDecode) {
                    return OptSuccess;
                } else {
                    videoBuffer->decodeResult = DecodeFail;
                    audioBuffer->decodeResult = DecodeFail;
                    videoBuffer->is_last_frame = false;
                    audioBuffer->is_last_frame = false;
                    return decodeForSeek(targetPtsInMillis, videoBuffer, audioBuffer, 300.0, audio_reset_result < 0, video_reset_result < 0, 1000);
                }
            } else {
                return OptFail;
            }
        }
    } else {
        return OptFail;
    }
}

tMediaOptResult tMediaPlayerContext::decodeForSeek(long targetPtsInMillis, tMediaDecodeBuffer* videoDecodeBuffer, tMediaDecodeBuffer* audioDecodeBuffer, double minStepInMillis, bool skipAudio, bool skipVideo, int maxVideoDoCircleTimes) {
    LOGD("Seek max circle times: %d", maxVideoDoCircleTimes);
    if (pkt != nullptr &&
        frame != nullptr &&
        format_ctx != nullptr) {
        int result;
        if (!skipPktRead) {
            // If need read data to pkt from file.
            av_packet_unref(pkt);
            result = av_read_frame(format_ctx, pkt);
            if (result < 0) {
                // No data to read, end of file.
                audioDecodeBuffer->is_last_frame = true;
                audioDecodeBuffer->pts = duration;
                LOGE("Seek decode media end");
                return OptSuccess;
            }
        }
        skipPktRead = false;

        if (video_stream != nullptr &&
            pkt->stream_index == video_stream->index &&
            video_decoder_ctx != nullptr &&
            !skipVideo) {
            // Send pkt data to video decoder ctx.
            result = avcodec_send_packet(video_decoder_ctx, pkt);
            if (result == AVERROR(EAGAIN)) {
                // Next time decode no need to read pkt.
                LOGD("Seek decode video skip read pkt");
                skipPktRead = true;
            } else {
                skipPktRead = false;
            }
            if (result < 0 && !skipPktRead) {
                // Send pkt to video decoder ctx fail, do next frame seek.
                LOGE("Seek decode video send pkt fail: %d", result);
                return decodeForSeek(targetPtsInMillis, videoDecodeBuffer, audioDecodeBuffer, minStepInMillis, skipAudio, skipVideo, maxVideoDoCircleTimes);
            }
            av_frame_unref(frame);
            // Decode video frame.
            result = avcodec_receive_frame(video_decoder_ctx, frame);
            if (result == AVERROR(EAGAIN)) {
                LOGD("Seek decode video reload frame");
                // Need more pkt data to decode current frame.
                return decodeForSeek(targetPtsInMillis, videoDecodeBuffer, audioDecodeBuffer, minStepInMillis, skipAudio, skipVideo, maxVideoDoCircleTimes);
            }
            if (result < 0) {
                // Decode video frame fail.
                LOGE("Seek decode video receive frame fail: %d", result);
                return decodeForSeek(targetPtsInMillis, videoDecodeBuffer, audioDecodeBuffer, minStepInMillis, skipAudio, skipVideo, maxVideoDoCircleTimes);
            }
            long ptsInMillis = (long) ((double)frame->pts * av_q2d(video_stream->time_base) * 1000L);

            // Copy frame data to video decode buffer.
            auto parseResult = parseDecodeVideoFrameToBuffer(videoDecodeBuffer);
            if (parseResult == DecodeSuccess) {
                LOGD("Seek decode video success: %ld", ptsInMillis);
            }
            videoDecodeBuffer->decodeResult = parseResult;

            if (targetPtsInMillis - ptsInMillis < minStepInMillis) {
                // Already seek to target pts.
                if (skipAudio) {
                    return OptSuccess;
                } else {
                    if (audioDecodeBuffer->decodeResult == DecodeSuccess || maxVideoDoCircleTimes <= 0) {
                        return OptSuccess;
                    } else {
                        return decodeForSeek(targetPtsInMillis, videoDecodeBuffer, audioDecodeBuffer, minStepInMillis, skipAudio, skipVideo, --maxVideoDoCircleTimes);
                    }
                }
            } else {
                if (maxVideoDoCircleTimes <= 0) {
                    return OptSuccess;
                } else {
                    // Need do more decode to target pts.
                    return decodeForSeek(targetPtsInMillis, videoDecodeBuffer, audioDecodeBuffer, minStepInMillis, skipAudio, skipVideo, --maxVideoDoCircleTimes);
                }
            }
        }

        if (audio_stream != nullptr &&
            pkt->stream_index == audio_stream->index &&
            audio_decoder_ctx != nullptr &&
            !skipAudio) {
            // Send pkt data to audio decoder ctx.
            result = avcodec_send_packet(audio_decoder_ctx, pkt);
            skipPktRead = result == AVERROR(EAGAIN);
            if (skipPktRead) {
                LOGD("Seek decode audio skip read pkt");
            }
            if (result < 0 && !skipPktRead) {
                // Send pkt data to audio decoder ctx fail.
                LOGE("Seek decode audio send pkt fail: %d", result);
                return decodeForSeek(targetPtsInMillis, videoDecodeBuffer, audioDecodeBuffer, minStepInMillis, skipAudio, skipVideo, maxVideoDoCircleTimes);
            }
            av_frame_unref(frame);
            // Decode audio frame.
            result = avcodec_receive_frame(audio_decoder_ctx, frame);
            if (result < 0) {
                // Decode audio frame fail.
                LOGE("Seek decode audio receive frame fail: %d", result);
                return decodeForSeek(targetPtsInMillis, videoDecodeBuffer, audioDecodeBuffer, minStepInMillis, skipAudio, skipVideo, maxVideoDoCircleTimes);
            }
            long ptsInMillis = (long) ((double)frame->pts * av_q2d(audio_stream->time_base) * 1000L);
            auto parseResult = parseDecodeAudioFrameToBuffer(audioDecodeBuffer);
            audioDecodeBuffer->decodeResult = parseResult;
            if (targetPtsInMillis - ptsInMillis < minStepInMillis) {
                // Already seek to target pts.
                if (skipVideo) {
                    return OptSuccess;
                } else {
                    if (videoDecodeBuffer->decodeResult == DecodeSuccess || maxVideoDoCircleTimes <= 0) {
                        return OptSuccess;
                    } else {
                        return decodeForSeek(targetPtsInMillis, videoDecodeBuffer, audioDecodeBuffer, minStepInMillis, skipAudio, skipVideo, --maxVideoDoCircleTimes);
                    }
                }
            } else {
                if (maxVideoDoCircleTimes <= 0) {
                    return OptSuccess;
                } else {
                    // Need do more decode to target pts.
                    return decodeForSeek(targetPtsInMillis, videoDecodeBuffer, audioDecodeBuffer, minStepInMillis, skipAudio, skipVideo, --maxVideoDoCircleTimes);
                }
            }
        }
        return decodeForSeek(targetPtsInMillis, videoDecodeBuffer, audioDecodeBuffer, minStepInMillis, skipAudio, skipVideo, maxVideoDoCircleTimes);
    }
    return OptFail;
}

tMediaDecodeBuffer* tMediaPlayerContext::decode(tMediaDecodeBuffer *lastBuffer) {
    if (pkt != nullptr &&
        frame != nullptr &&
        format_ctx != nullptr) {
        long start_time = get_time_millis();
        int result;
        if (!skipPktRead) {
            // If need read data to pkt from file.
            av_packet_unref(pkt);
            result = av_read_frame(format_ctx, pkt);
            if (result < 0) {
                // No data to read, end of file.
                tMediaDecodeBuffer * buffer = nullptr;
                if (lastBuffer != nullptr) {
                    if (lastBuffer->type == BufferTypeAudio) {
                        buffer = lastBuffer;
                    } else {
                        enqueueVideoEncodeBufferFromJava(lastBuffer);
                        buffer = requestAudioDecodeBufferFromJava();
                    }
                } else {
                    buffer = requestAudioDecodeBufferFromJava();
                }
                buffer->decodeResult = DecodeEnd;
                buffer->is_last_frame = true;
                LOGD("Decode media end.");
                return buffer;
            }
        }
        skipPktRead = false;
        if (video_stream != nullptr &&
            pkt->stream_index == video_stream->index &&
            video_decoder_ctx != nullptr) {
            tMediaDecodeBuffer * buffer = nullptr;
            if (lastBuffer != nullptr) {
                if (lastBuffer->type == BufferTypeVideo) {
                    buffer = lastBuffer;
                } else {
                    enqueueAudioEncodeBufferFromJava(lastBuffer);
                    buffer = requestVideoDecodeBufferFromJava();
                }
            } else {
                buffer = requestVideoDecodeBufferFromJava();
            }
            buffer->is_last_frame = false;
            // Send pkt data to video decoder ctx.
            result = avcodec_send_packet(video_decoder_ctx, pkt);
            if (result == AVERROR(EAGAIN)) {
                // Next time decode no need to read pkt.
                LOGD("Decode video skip read pkt");
                skipPktRead = true;
            } else {
                skipPktRead = false;
            }
            if (result < 0 && !skipPktRead) {
                // Send pkt to video decoder ctx fail, do next frame seek.
                LOGE("Decode video send pkt fail: %d", result);
                buffer->decodeResult = DecodeFail;
                return buffer;
            }
            av_frame_unref(frame);
            // Decode video frame.
            result = avcodec_receive_frame(video_decoder_ctx, frame);
            if (result == AVERROR(EAGAIN)) {
                // Need more pkt data to decode current frame.
                LOGD("Decode video reload frame");
                return decode(buffer);
            }
            if (result < 0) {
                // Decode video frame fail.
                LOGE("Decode video receive frame fail: %d", result);
                buffer->decodeResult = DecodeFail;
                return buffer;
            }
            // Copy frame data to video decode buffer.
            auto parseResult = parseDecodeVideoFrameToBuffer(buffer);
            if (parseResult == DecodeSuccess) {
                LOGD("Decode video success: %ld, cost: %ld ms, type: %d, colorRange: %d, colorPrimaries: %d, colorSpace: %d", buffer->pts, get_time_millis() - start_time, buffer->videoBuffer->type, frame->color_range, frame->color_primaries, frame->colorspace);
            }
            buffer->decodeResult = parseResult;
            return buffer;
        }
        if (audio_stream != nullptr &&
            pkt->stream_index == audio_stream->index &&
            audio_decoder_ctx != nullptr) {
            tMediaDecodeBuffer * buffer = nullptr;
            if (lastBuffer != nullptr) {
                if (lastBuffer->type == BufferTypeAudio) {
                    buffer = lastBuffer;
                } else {
                    enqueueVideoEncodeBufferFromJava(lastBuffer);
                    buffer = requestAudioDecodeBufferFromJava();
                }
            } else {
                buffer = requestAudioDecodeBufferFromJava();
            }
            buffer->is_last_frame = false;
            // Send pkt data to audio decoder ctx.
            result = avcodec_send_packet(audio_decoder_ctx, pkt);
            skipPktRead = result == AVERROR(EAGAIN);
            if (skipPktRead) {
                LOGD("Decode audio skip read pkt");
            }
            if (result < 0 && !skipPktRead) {
                LOGE("Decode audio send pkt fail: %d", result);
                buffer->decodeResult = DecodeFail;
                return buffer;
            }
            av_frame_unref(frame);
            result = avcodec_receive_frame(audio_decoder_ctx, frame);
            if (result == AVERROR(EAGAIN)) {
                // Need more pkt data to decode current audio frame.
                LOGD("Decode audio reload frame");
                return decode(buffer);
            }
            if (result < 0) {
                // Decode audio frame fail.
                LOGE("Decode audio receive frame fail: %d", result);
                buffer->decodeResult = DecodeFail;
                return buffer;
            }
            // Copy frame data to audio decode buffer.
            auto parseResult = parseDecodeAudioFrameToBuffer(buffer);
            if (parseResult == DecodeSuccess) {
                LOGD("Decode audio success: %ld, contentSize: %d, cost: %ld ms", buffer->pts, buffer->audioBuffer->contentSize, get_time_millis() - start_time);
            }
            buffer->decodeResult = parseResult;
            return buffer;
        }
        LOGE("Decode unknown pkt");
        tMediaDecodeBuffer * buffer = nullptr;
        if (lastBuffer != nullptr) {
            if (lastBuffer->type == BufferTypeAudio) {
                buffer = lastBuffer;
            } else {
                enqueueVideoEncodeBufferFromJava(lastBuffer);
                buffer = requestAudioDecodeBufferFromJava();
            }
        } else {
            buffer = requestAudioDecodeBufferFromJava();
        }
        buffer->decodeResult = DecodeFail;
        return buffer;
    } else {
        LOGE("Decode wrong player context.");
        tMediaDecodeBuffer * buffer = nullptr;
        if (lastBuffer != nullptr) {
            if (lastBuffer->type == BufferTypeAudio) {
                buffer = lastBuffer;
            } else {
                enqueueVideoEncodeBufferFromJava(lastBuffer);
                buffer = requestAudioDecodeBufferFromJava();
            }
        } else {
            buffer = requestAudioDecodeBufferFromJava();
        }
        buffer->decodeResult = DecodeFail;
        buffer->is_last_frame = false;
        return buffer;
    }
}

/**
 * Copy image pixel data.
 * @param dst
 * @param src
 * @param image_width
 * @param image_height
 * @param line_stride
 * @param pixel_stride
 */
void copyFrameData(uint8_t * dst, uint8_t * src, int image_width, int image_height, int line_stride, int pixel_stride) {
    if ((image_width * pixel_stride) >= line_stride) {
        // pixel row no empty data.
        memcpy(dst, src, image_width * image_height * pixel_stride);
    } else {
        // pixel contain empty data.
        uint8_t *dst_offset = dst;
        uint8_t *src_offset = src;
        int image_line_len = image_width * pixel_stride;
        for (int i = 0; i < image_height; i ++) {
            memcpy(dst_offset, src_offset, image_line_len);
            dst_offset += image_line_len;
            src_offset += line_stride;
        }
    }
}

/**
 * Convert decoded video frame buffer to tMediaDecodeBuffer
 * @param buffer
 * @return
 */
tMediaDecodeResult tMediaPlayerContext::parseDecodeVideoFrameToBuffer(tMediaDecodeBuffer *buffer) {

    int w = frame->width;
    int h = frame->height;
    auto videoBuffer = buffer->videoBuffer;
    auto format = frame->format;
//    auto colorRange = frame->color_range;
//    auto colorPrimaries = frame->color_primaries;
    auto colorSpace = frame->colorspace;
    if (format == AV_PIX_FMT_YUV420P && colorSpace == AVCOL_SPC_BT470BG) {
        videoBuffer->width = w;
        videoBuffer->height = h;
        int ySize = w * h;
        int uSize = ySize / 4;
        int vSize = uSize;

        // alloc Y buffer if need.
        if (videoBuffer->ySize != ySize || videoBuffer->yBuffer == nullptr) {
            if (videoBuffer->yBuffer != nullptr) {
                free(videoBuffer->yBuffer);
            }
            videoBuffer->yBuffer = static_cast<uint8_t *>(av_malloc(ySize * sizeof(uint8_t)));
            videoBuffer->ySize = ySize;
        }

        // alloc U buffer if need.
        if (videoBuffer->uSize != uSize || videoBuffer->uBuffer == nullptr) {
            if (videoBuffer->uBuffer != nullptr) {
                free(videoBuffer->uBuffer);
            }
            videoBuffer->uBuffer = static_cast<uint8_t *>(av_malloc(uSize * sizeof(uint8_t)));
            videoBuffer->uSize = uSize;
        }

        // // alloc V buffer if need.
        if (videoBuffer->vSize != vSize || videoBuffer->vBuffer == nullptr) {
            if (videoBuffer->vBuffer != nullptr) {
                free(videoBuffer->vBuffer);
            }
            videoBuffer->vBuffer = static_cast<uint8_t *>(av_malloc(vSize * sizeof(uint8_t)));
            videoBuffer->vSize = vSize;
        }
        uint8_t *yBuffer = videoBuffer->yBuffer;
        uint8_t *uBuffer = videoBuffer->uBuffer;
        uint8_t *vBuffer = videoBuffer->vBuffer;
        // Copy yuv data to video frame.
        copyFrameData(yBuffer, frame->data[0], w, h, frame->linesize[0], 1);
        copyFrameData(uBuffer, frame->data[1], w / 2, h / 2, frame->linesize[1], 1);
        copyFrameData(vBuffer, frame->data[2], w / 2, h / 2, frame->linesize[2], 1);
        videoBuffer->type = Yuv420p;
    } else if ((format == AV_PIX_FMT_NV12 || format == AV_PIX_FMT_NV21) && colorSpace == AVCOL_SPC_BT470BG) {
        videoBuffer->width = w;
        videoBuffer->height = h;
        int ySize = w * h;
        int uvSize = ySize / 2;
        // alloc Y buffer if need.
        if (videoBuffer->ySize != ySize || videoBuffer->yBuffer == nullptr) {
            if (videoBuffer->yBuffer != nullptr) {
                free(videoBuffer->yBuffer);
            }
            videoBuffer->yBuffer = static_cast<uint8_t *>(av_malloc(ySize * sizeof(uint8_t)));
            videoBuffer->ySize = ySize;
        }

        // alloc UV buffer if need.
        if (videoBuffer->uvSize != uvSize || videoBuffer->uvBuffer == nullptr) {
            if (videoBuffer->uvBuffer != nullptr) {
                free(videoBuffer->uvBuffer);
            }
            videoBuffer->uvBuffer = static_cast<uint8_t *>(av_malloc(uvSize * sizeof(uint8_t)));
            videoBuffer->uvSize = uvSize;
        }
        uint8_t *yBuffer = videoBuffer->yBuffer;
        uint8_t *uvBuffer = videoBuffer->uvBuffer;
        // Copy Y buffer.
        copyFrameData(yBuffer, frame->data[0], w, h, frame->linesize[0], 1);
        // Copy UV buffer.
        copyFrameData(uvBuffer, frame->data[1], w / 2, h / 2, frame->linesize[1], 2);
        if (frame->format == AV_PIX_FMT_NV12) {
            videoBuffer->type = Nv12;
        } else {
            videoBuffer->type = Nv21;
        }
    } else if (format == AV_PIX_FMT_RGBA && colorSpace == AVCOL_SPC_BT470BG) {
        videoBuffer->width = w;
        videoBuffer->height = h;
        int rgbaSize = w * h * 4;
        // alloc rgba data if need.
        if (videoBuffer->rgbaSize != rgbaSize || videoBuffer->rgbaBuffer == nullptr) {
            if (videoBuffer->rgbaBuffer != nullptr) {
                free(videoBuffer->rgbaBuffer);
            }
            videoBuffer->rgbaBuffer = static_cast<uint8_t *>(av_malloc(rgbaSize * sizeof(uint8_t)));
            videoBuffer->rgbaSize = rgbaSize;
        }
        uint8_t *rgbaBuffer = videoBuffer->rgbaBuffer;
        // Copy RGBA data.
        copyFrameData(rgbaBuffer, frame->data[0], w, h, frame->linesize[0], 4);
        videoBuffer->type = Rgba;
    } else {
        // Others format need to convert to RGBA.
        if (w != video_width ||
            h != video_height ||
            sws_ctx == nullptr) {
            LOGD("Decode video change rgbaSize, recreate sws ctx.");
            if (sws_ctx != nullptr) {
                sws_freeContext(sws_ctx);
            }

            this->sws_ctx = sws_getContext(
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
        }

        // Alloc new RGBA frame and buffer if need.
        if (w != videoBuffer->width ||
            h != videoBuffer->height ||
            videoBuffer->rgbaBuffer == nullptr ||
            videoBuffer->rgbaFrame == nullptr) {
            LOGD("Decode video create new buffer.");
            videoBuffer->rgbaSize = av_image_get_buffer_size(AV_PIX_FMT_RGBA, w, h, 1);
            if (videoBuffer->rgbaBuffer != nullptr) {
                free(videoBuffer->rgbaBuffer);
            }
            if (videoBuffer->rgbaFrame != nullptr) {
                av_frame_free(&(videoBuffer->rgbaFrame));
                videoBuffer->rgbaFrame = nullptr;
            }
            videoBuffer->rgbaFrame = av_frame_alloc();
            videoBuffer->rgbaBuffer = static_cast<uint8_t *>(av_malloc(videoBuffer->rgbaSize * sizeof(uint8_t)));
            // Fill rgbaBuffer to rgbaFrame
            av_image_fill_arrays(videoBuffer->rgbaFrame->data, videoBuffer->rgbaFrame->linesize, videoBuffer->rgbaBuffer,
                                 AV_PIX_FMT_RGBA, w, h, 1);
            videoBuffer->width = w;
            videoBuffer->height = h;
        }
        // Convert to rgba.
        int result = sws_scale(sws_ctx, frame->data, frame->linesize, 0, frame->height, videoBuffer->rgbaFrame->data, videoBuffer->rgbaFrame->linesize);
        if (result < 0) {
            // Convert fail.
            LOGE("Decode video sws scale fail: %d", result);
            return DecodeFail;
        }
        videoBuffer->type = Rgba;
    }
    auto time_base = video_stream->time_base;
    if (time_base.den > 0 && frame->pts > 0) {
        buffer->pts = (long) ((double)frame->pts * av_q2d(time_base) * 1000L);
    } else {
        buffer->pts = 0L;
    }
    if (w != video_width || h != video_height) {
        video_width = w;
        video_height = h;
    }
    buffer->type = BufferTypeVideo;
    return DecodeSuccess;
}

/**
 * Convert decoded audio frame buffer to tMediaDecodeBuffer
 * @param buffer
 * @return
 */
tMediaDecodeResult tMediaPlayerContext::parseDecodeAudioFrameToBuffer(tMediaDecodeBuffer *buffer) {

    int in_nb_samples = frame->nb_samples;

    // Get current output frame contains sample bufferSize per channel.
    int out_nb_samples = (int) av_rescale_rnd( swr_get_delay(swr_ctx, frame->sample_rate) + in_nb_samples, audio_output_sample_rate, audio_decoder_ctx->sample_rate, AV_ROUND_UP); // swr_get_out_samples(swr_ctx, in_nb_samples);

    if (out_nb_samples <= 0) {
        LOGE("Get out put nb samples fail: %d", out_nb_samples);
        return DecodeFail;
    }
    // Get current output audio frame need buffer bufferSize.
    int lineSize = 0;
    int out_audio_buffer_size = av_samples_get_buffer_size(&lineSize, audio_output_channels, out_nb_samples, audio_output_sample_fmt, 1);
    auto audioBuffer = buffer->audioBuffer;
    // Alloc pcm buffer if need.
    if (audioBuffer->bufferSize < out_audio_buffer_size || audioBuffer->pcmBuffer == nullptr) {
        int in_audio_buffer_size = av_samples_get_buffer_size(frame->linesize, audio_channels, in_nb_samples, audio_decoder_ctx->sample_fmt, 1);
        LOGD("Decode audio change bufferSize, outBufferSize=%d, need bufferSize=%d, inBufferSize=%d", audioBuffer->bufferSize, out_audio_buffer_size, in_audio_buffer_size);
        if (audioBuffer->pcmBuffer != nullptr) {
            free(audioBuffer->pcmBuffer);
        }
        audioBuffer->pcmBuffer = static_cast<uint8_t *>(malloc(out_audio_buffer_size));
    }
    audioBuffer->bufferSize = out_audio_buffer_size;
    auto time_base = audio_stream->time_base;
    if (time_base.den > 0 && frame->pts > 0) {
        buffer->pts = (long) ((double)frame->pts * av_q2d(time_base) * 1000L);
    } else {
        buffer->pts = 0L;
    }
    // Convert to target output pcm format data.
    int real_out_nb_samples = swr_convert(swr_ctx, &(audioBuffer->pcmBuffer), out_nb_samples, (const uint8_t **)(frame->data), in_nb_samples);
    if (real_out_nb_samples < 0) {
        LOGE("Decode audio swr convert fail: %d", real_out_nb_samples);
        return DecodeFail;
    }
    int contentBufferSize = av_samples_get_buffer_size(&lineSize, audio_output_channels, real_out_nb_samples, audio_output_sample_fmt, 1);
    audioBuffer->contentSize = lineSize;
    if (contentBufferSize != lineSize) {
        LOGE("output lineSize=%d, contentBufferSize=%d", lineSize, contentBufferSize);
    }
    buffer->type = BufferTypeAudio;
    return DecodeSuccess;
}

tMediaDecodeBuffer* tMediaPlayerContext::requestVideoDecodeBufferFromJava() {
    JNIEnv *jniEnv;
    jvm->GetEnv((void **)&jniEnv, JNI_VERSION_1_6);
    jlong bufferPointer = jniEnv->CallLongMethod(jplayer, callRequestVideoBufferMethodId);
    return reinterpret_cast<tMediaDecodeBuffer *>(bufferPointer);
}

tMediaDecodeBuffer* tMediaPlayerContext::requestAudioDecodeBufferFromJava() {
    JNIEnv *jniEnv;
    jvm->GetEnv((void **)&jniEnv, JNI_VERSION_1_6);
    jlong bufferPointer = jniEnv->CallLongMethod(jplayer, callRequestAudioBufferMethodId);
    return reinterpret_cast<tMediaDecodeBuffer *>(bufferPointer);
}

void tMediaPlayerContext::enqueueVideoEncodeBufferFromJava(tMediaDecodeBuffer *buffer) {
    JNIEnv *jniEnv;
    jvm->GetEnv((void **)&jniEnv, JNI_VERSION_1_6);
    jniEnv->CallVoidMethod(jplayer, callEnqueueVideoBufferMethodId, reinterpret_cast<jlong>(buffer));
}

void tMediaPlayerContext::enqueueAudioEncodeBufferFromJava(tMediaDecodeBuffer *buffer) {
    JNIEnv *jniEnv;
    jvm->GetEnv((void **)&jniEnv, JNI_VERSION_1_6);
    jniEnv->CallVoidMethod(jplayer, callEnqueueAudioBufferMethodId, reinterpret_cast<jlong>(buffer));
}

tMediaDecodeBuffer * allocVideoDecodeBuffer() {
    auto buffer = new tMediaDecodeBuffer;
    buffer->type = BufferTypeVideo;
    buffer->videoBuffer = new tMediaVideoBuffer;
    return buffer;
}

tMediaDecodeBuffer * allocAudioDecodeBuffer() {
    auto buffer = new tMediaDecodeBuffer;
    buffer->type = BufferTypeAudio;
    buffer->audioBuffer = new tMediaAudioBuffer;
    return buffer;
}

void freeDecodeBuffer(tMediaDecodeBuffer *b) {
    // Free audio buffer.
    auto audioBuffer = b->audioBuffer;
    if (audioBuffer != nullptr) {
        if (audioBuffer->pcmBuffer != nullptr) {
            free(audioBuffer->pcmBuffer);
        }
        free(audioBuffer);
        b->audioBuffer = nullptr;
    }

    // Free video buffer.
    auto videoBuffer = b->videoBuffer;
    if (videoBuffer != nullptr) {
        // Free rgba.
        if (videoBuffer->rgbaFrame != nullptr) {
            av_frame_unref(videoBuffer->rgbaFrame);
            av_frame_free(&videoBuffer->rgbaFrame);
        }
        if (videoBuffer->rgbaBuffer != nullptr) {
            free(videoBuffer->rgbaBuffer);
        }

        // Free yuv420
        if (videoBuffer->yBuffer != nullptr) {
            free(videoBuffer->yBuffer);
        }
        if (videoBuffer->uBuffer != nullptr) {
            free(videoBuffer->uBuffer);
        }
        if (videoBuffer->vBuffer != nullptr) {
            free(videoBuffer->vBuffer);
        }
        if (videoBuffer->uvBuffer != nullptr) {
            free(videoBuffer->uvBuffer);
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

    if (sws_ctx != nullptr) {
        sws_freeContext(sws_ctx);
        sws_ctx = nullptr;
    }

    // Audio free.
    if (audio_decoder_ctx != nullptr) {
        avcodec_close(audio_decoder_ctx);
        avcodec_free_context(&audio_decoder_ctx);
        audio_decoder_ctx = nullptr;
    }

    if (swr_ctx != nullptr) {
        swr_free(&swr_ctx);
        swr_ctx = nullptr;
    }
    free(this);
    LOGD("Release media player");
}
