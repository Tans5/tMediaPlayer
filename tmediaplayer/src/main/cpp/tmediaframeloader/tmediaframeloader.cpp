//
// Created by pengcheng.tan on 2024/4/23.
//
#include "tmediaframeloader.h"
#include "tmediaplayer.h"

tMediaOptResult tMediaFrameLoaderContext::prepare(const char *media_file_p) {
    LOGD("Prepare media file: %s", media_file_p);
    this->media_file = media_file_p;
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

    // Find out first video stream.
    for (int i = 0; i < format_ctx->nb_streams; i ++) {
        auto s = format_ctx->streams[i];
        auto codec_type = s->codecpar->codec_type;
        switch (codec_type) {
            case AVMEDIA_TYPE_VIDEO:
                if (this->video_stream != nullptr) {
                    LOGE("Find multiple video stream, skip it.");
                } else {
                    this->video_stream = s;
                    LOGD("Find video stream");
                }
                break;
            default:
                break;
        }
    }

    // No video stream.
    if (video_stream == nullptr) {
        LOGE("Didn't find video stream.");
        return OptFail;
    }
    AVCodecParameters *params = video_stream->codecpar;
    this->video_width = params->width;
    this->video_height = params->height;
    this->video_decoder = avcodec_find_decoder(params->codec_id);
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
    result = avcodec_open2(video_decoder_ctx, video_decoder, nullptr);
    if (result < 0) {
        LOGE("Open video decoder ctx fail: %d", result);
        return OptFail;
    }
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

    // decode need buffers.
    this->pkt = av_packet_alloc();
    this->frame = av_frame_alloc();
    this->videoBuffer = new tMediaVideoBuffer;

    this->duration = format_ctx->duration * av_q2d(AV_TIME_BASE_Q) * 1000L;
    return OptSuccess;
}

tMediaOptResult tMediaFrameLoaderContext::getFrame(long framePosition, bool needRealTime) {
    if (format_ctx != nullptr) {
        if (video_stream == nullptr) {
            return OptFail;
        } else {
            if (framePosition > duration || framePosition < 0) {
                LOGE("Wrong frame position: %ld, duration: %ld", framePosition, duration);
                return OptFail;
            }
            if (video_stream != nullptr && framePosition > 0) {
                avcodec_flush_buffers(video_decoder_ctx);
                int64_t seekTimestamp = av_rescale_q(framePosition * AV_TIME_BASE / 1000, AV_TIME_BASE_Q, video_stream->time_base);
                int video_reset_result = avformat_seek_file(format_ctx, video_stream->index, INT64_MIN, seekTimestamp, INT64_MAX, AVSEEK_FLAG_BACKWARD);
                if (video_reset_result < 0) {
                    LOGE("Seek video progress fail: %d", video_reset_result);
                    return OptFail;
                }
            }

            for (int i = 0; i < format_ctx->nb_streams; i++) {
                auto s = format_ctx->streams[i];
                if (s == video_stream) {
                    continue;
                } else {
                    int64_t seekTimestamp = av_rescale_q(framePosition * AV_TIME_BASE / 1000,
                                                         AV_TIME_BASE_Q, s->time_base);
                    avformat_seek_file(format_ctx, s->index, INT64_MIN, seekTimestamp, INT64_MAX,AVSEEK_FLAG_BACKWARD);
                }
            }

            return decodeForGetFrame(framePosition, 300.0, needRealTime, 30);
        }
    } else {
        return OptFail;
    }
}

tMediaOptResult tMediaFrameLoaderContext::decodeForGetFrame(long framePosition, double minStepInMillis, bool needRealTime, int maxVideoDoCircleTimes) {
    LOGD("Seek max video circle times: %d", maxVideoDoCircleTimes);
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
                LOGE("Seek decode media end");
                return OptFail;
            }
        }
        skipPktRead = false;

        if (video_stream != nullptr &&
            pkt->stream_index == video_stream->index &&
            video_decoder_ctx != nullptr) {
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
                return decodeForGetFrame(framePosition, minStepInMillis, needRealTime, maxVideoDoCircleTimes);
            }
            av_frame_unref(frame);
            // Decode video frame.
            result = avcodec_receive_frame(video_decoder_ctx, frame);
            if (result == AVERROR(EAGAIN)) {
                LOGD("Seek decode video reload frame");
                // Need more pkt data to decode current frame.
                return decodeForGetFrame(framePosition, minStepInMillis, needRealTime, maxVideoDoCircleTimes);
            }
            if (result < 0) {
                // Decode video frame fail.
                LOGE("Seek decode video receive frame fail: %d", result);
                return decodeForGetFrame(framePosition, minStepInMillis, needRealTime, maxVideoDoCircleTimes);
            }
            long ptsInMillis = (long) ((double)frame->pts * av_q2d(video_stream->time_base) * 1000L);
            auto parseResult = parseDecodeVideoFrameToBuffer();
            if (parseResult == OptFail) {
                return OptFail;
            }
            if (needRealTime) {
                if ((framePosition - ptsInMillis < minStepInMillis) || maxVideoDoCircleTimes <= 0) {
                    // Already seek to target pts.
                    return OptSuccess;
                } else {
                    // Need do more decode to target pts.
                    return decodeForGetFrame(framePosition, minStepInMillis, needRealTime, --maxVideoDoCircleTimes);
                }
            } else {
                return OptSuccess;
            }
        } else {
            return decodeForGetFrame(framePosition, minStepInMillis, needRealTime, maxVideoDoCircleTimes);
        }
    }
    return OptFail;
}

tMediaOptResult tMediaFrameLoaderContext::parseDecodeVideoFrameToBuffer() {
    int w = frame->width;
    int h = frame->height;

    if (w != video_width ||
        h != video_height ||
        sws_ctx == nullptr) {
        LOGE("Decode video change rgbaSize, recreate sws ctx.");
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
            return OptFail;
        }
        video_width = w;
        video_height = h;
    }

    // Alloc new RGBA frame and buffer if need.
    if (w != videoBuffer->width ||
        h != videoBuffer->height ||
        videoBuffer->rgbaBuffer == nullptr ||
        videoBuffer->rgbaFrame == nullptr) {
        LOGE("Decode video create new buffer.");
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
        return OptFail;
    }
    videoBuffer->type = Rgba;
    return OptSuccess;
}

void tMediaFrameLoaderContext::release() {
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
    if (sws_ctx != nullptr) {
        sws_freeContext(sws_ctx);
    }

    // VideoBuffer
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
        videoBuffer = nullptr;
    }

    free(this);
    LOGD("Release media frame loader.");
}
