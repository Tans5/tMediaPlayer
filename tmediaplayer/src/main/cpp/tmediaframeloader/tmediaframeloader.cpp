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
                    LOGD("Find video stream.");
                    this->video_stream = s;
                    this->video_duration = (long) ((double) s->duration * av_q2d(s->time_base) *1000L);
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
