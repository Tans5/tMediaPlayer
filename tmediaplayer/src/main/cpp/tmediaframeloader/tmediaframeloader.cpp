//
// Created by pengcheng.tan on 2024/4/23.
//
#include "tmediaframeloader.h"
#include "tmediaplayer.h"

tMediaOptResult tMediaFrameLoaderContext::prepare(const char *media_file_p) {
    LOGD("Prepare media file: %s", media_file_p);
    this->format_ctx = avformat_alloc_context();
    int result = avformat_open_input(&format_ctx, media_file_p, nullptr, nullptr);
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

    // decode need buffers.
    this->pkt = av_packet_alloc();
    this->frame = av_frame_alloc();
    this->videoBuffer = new tMediaVideoBuffer;

    this->duration = 0L;
    if (format_ctx->duration != AV_NOPTS_VALUE) {
        this->duration = (int32_t) (((double) format_ctx->duration) * av_q2d(AV_TIME_BASE_Q) * 1000.0);
    }
    return OptSuccess;
}

tMediaOptResult tMediaFrameLoaderContext::getFrame(int64_t framePosition) {
    if (format_ctx != nullptr) {
        if (video_stream == nullptr) {
            return OptFail;
        } else {
            if (framePosition > duration || framePosition < 0) {
                LOGE("Wrong frame position: %lld, duration: %lld", framePosition, duration);
                return OptFail;
            }
            int64_t fixedPosition = framePosition;
            if (video_stream->disposition & AV_DISPOSITION_ATTACHED_PIC) {
                fixedPosition = 0L;
            }
            int64_t seekTs = fixedPosition * AV_TIME_BASE / 1000L;
            int result = avformat_seek_file(format_ctx, -1, INT64_MIN, seekTs, INT64_MAX, AVSEEK_FLAG_BACKWARD);
            if (result < 0) {
                LOGE("Seek file fail: %d", result);
                return OptFail;
            }
            return decodeForGetFrame();
        }
    } else {
        return OptFail;
    }
}

tMediaOptResult tMediaFrameLoaderContext::decodeForGetFrame() {
    if (pkt != nullptr &&
        frame != nullptr &&
        format_ctx != nullptr &&
        video_stream != nullptr &&
        video_decoder_ctx != nullptr) {
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

        if (pkt->stream_index == video_stream->index) {
            // Send pkt data to video decoder ctx.
            result = avcodec_send_packet(video_decoder_ctx, pkt);
            if (result == AVERROR(EAGAIN)) {
                // Next time decode no need to read pkt.
                LOGD("Seek decode video skip read pkt");
                skipPktRead = true;
            } else {
                av_packet_unref(pkt);
            }
            if (result < 0 && !skipPktRead) {
                // Send pkt to video decoder ctx fail, do next frame seek.
                LOGE("Seek decode video send pkt fail: %d", result);
                return OptFail;
            }
            av_frame_unref(frame);
            // Decode video frame.
            result = avcodec_receive_frame(video_decoder_ctx, frame);
            if (result == AVERROR(EAGAIN)) {
                LOGD("Seek decode video need more pkt.");
                // Need more pkt data to decode current frame.
                return decodeForGetFrame();
            }
            if (result < 0) {
                // Decode video frame fail.
                LOGE("Seek decode video receive frame fail: %d", result);
                return OptFail;
            }
            return parseDecodeVideoFrameToBuffer();
        } else {
            return decodeForGetFrame();
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

    videoBuffer->width = w;
    videoBuffer->height = h;
    // Alloc new RGBA frame and buffer if need.
    int rgbaContentSize = av_image_get_buffer_size(AV_PIX_FMT_RGBA, videoBuffer->width, videoBuffer->height, 1);
    if (rgbaContentSize > videoBuffer->rgbaBufferSize ||
        videoBuffer->rgbaBuffer == nullptr) {
        videoBuffer->rgbaBufferSize = rgbaContentSize;
        if (videoBuffer->rgbaBuffer != nullptr) {
            free(videoBuffer->rgbaBuffer);
        }
        videoBuffer->rgbaBuffer = static_cast<uint8_t *>(av_malloc(rgbaContentSize * sizeof(uint8_t)));

    }
    videoBuffer->rgbaContentSize = rgbaContentSize;
    // Convert to rgba.
    uint8_t* data[AV_NUM_DATA_POINTERS] = {videoBuffer->rgbaBuffer};
    int lineSize[AV_NUM_DATA_POINTERS];
    av_image_fill_linesizes(lineSize, AV_PIX_FMT_RGBA, videoBuffer->width);
    int result = sws_scale(sws_ctx, frame->data, frame->linesize, 0, frame->height, data, lineSize);
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
        av_frame_unref(frame);
        av_frame_free(&frame);
    }

    // Video Release.
    if (video_decoder_ctx != nullptr) {
        avcodec_free_context(&video_decoder_ctx);
    }
    if (sws_ctx != nullptr) {
        sws_freeContext(sws_ctx);
    }

    // VideoBuffer
    if (videoBuffer != nullptr) {
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

    video_stream = nullptr;

    free(this);
    LOGD("Release media frame loader.");
}
