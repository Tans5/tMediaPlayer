//
// Created by pengcheng.tan on 2024/7/3.
//
#include "tmediasubtitlepktreader.h"

static bool isSupportSubtitleStream(AVStream * s) {
    auto codecId = s->codecpar->codec_id;
    auto type = s->codecpar->codec_type;
    return (type == AVMEDIA_TYPE_SUBTITLE &&
            codecId != AV_CODEC_ID_DVD_SUBTITLE &&
            codecId != AV_CODEC_ID_XSUB &&
            codecId != AV_CODEC_ID_HDMV_PGS_SUBTITLE
            // && codecId != AV_CODEC_ID_HDMV_TEXT_SUBTITLE

    );
}

tMediaOptResult tMediaSubtitlePktReaderContext::prepare(const char *subtitle_file) {
    if (subtitle_stream != nullptr) {
        subtitle_stream = nullptr;
    }
    if (format_ctx != nullptr) {
        avformat_close_input(&format_ctx);
        avformat_free_context(format_ctx);
        format_ctx = nullptr;
    }
    this->format_ctx = avformat_alloc_context();
    LOGD("Prepare subtitle file: %s", subtitle_file);
    int ret = avformat_open_input(&format_ctx, subtitle_file, nullptr, nullptr);
    if (ret < 0) {
        LOGE("Open subtitle file fail: %s", subtitle_file);
        return OptFail;
    }
    LOGD("Input subtitle file format: %s", format_ctx->iformat->long_name);

    for (int i = 0; i < format_ctx->nb_streams; i ++) {
        auto s = format_ctx->streams[i];
        if (isSupportSubtitleStream(s)) {
            subtitle_stream = s;
            break;
        }
    }
    if (subtitle_stream == nullptr) {
        LOGE("Do not find support subtitle stream.");
        return OptFail;
    }
    if (pkt == nullptr) {
        pkt = av_packet_alloc();
    }
    return OptSuccess;
}

tMediaReadPktResult tMediaSubtitlePktReaderContext::readPacket() const {
    if (format_ctx == nullptr || pkt == nullptr) {
        return ReadFail;
    }
    int ret = av_read_frame(format_ctx, pkt);
    if (ret < 0) {
        if (ret == AVERROR_EOF || avio_feof(format_ctx->pb)) {
            return ReadEof;
        } else {
            return ReadFail;
        }
    } else {
        if (subtitle_stream && pkt->stream_index == subtitle_stream->index) {
            pkt->time_base = subtitle_stream->time_base;
            return ReadSubtitleSuccess;
        }
        av_packet_unref(pkt);
        return UnknownPkt;
    }
}

tMediaOptResult tMediaSubtitlePktReaderContext::seekTo(int64_t targetPosInMillis) const {
    if (format_ctx == nullptr) {
        return OptFail;
    }
    int64_t seekTs = targetPosInMillis * AV_TIME_BASE / 1000L;
    int ret = avformat_seek_file(format_ctx, -1, INT64_MIN, seekTs, INT64_MAX, AVSEEK_FLAG_BACKWARD);
    if (ret < 0) {
        return OptFail;
    } else {
        return OptSuccess;
    }
}

void tMediaSubtitlePktReaderContext::movePacketRef(AVPacket *target) const {
    av_packet_move_ref(target, pkt);
}

void tMediaSubtitlePktReaderContext::release() {
    if (subtitle_stream != nullptr) {
        subtitle_stream = nullptr;
    }
    if (format_ctx != nullptr) {
        avformat_close_input(&format_ctx);
        avformat_free_context(format_ctx);
        format_ctx = nullptr;
    }
    if (pkt != nullptr) {
        av_packet_unref(pkt);
        av_packet_free(&pkt);
        pkt = nullptr;
    }
    free(this);
}