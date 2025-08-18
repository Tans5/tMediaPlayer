//
// Created by pengcheng.tan on 2024/5/27.
//

#ifndef TMEDIAPLAYER_TMEDIAPLAYER_H
#define TMEDIAPLAYER_TMEDIAPLAYER_H

#include <android/log.h>
#include <jni.h>

extern "C" {
#include "libavformat/avformat.h"
#include "libavcodec/avcodec.h"
#include "libswscale/swscale.h"
#include "libavutil/imgutils.h"
#include "libswresample/swresample.h"
}

#define LOG_TAG "tMediaPlayerNative"
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

#define YUV_ALIGN_SIZE 8

enum ImageRawType {
    Yuv420p,
    Nv12,
    Nv21,
    Rgba,
    HwSurface,
    UnknownImgType
};

typedef struct tMediaVideoBuffer {
    int32_t width = 0;
    int32_t height = 0;
    ImageRawType type = UnknownImgType;
    int32_t rgbaBufferSize = 0;
    int32_t rgbaContentSize = 0;
    uint8_t *rgbaBuffer = nullptr;
    int32_t yBufferSize = 0;
    int32_t yContentSize = 0;
    uint8_t *yBuffer = nullptr;
    int32_t uBufferSize = 0;
    int32_t uContentSize = 0;
    uint8_t  *uBuffer = nullptr;
    int32_t vBufferSize = 0;
    int32_t vContentSize = 0;
    uint8_t  *vBuffer = nullptr;
    int32_t uvBufferSize = 0;
    int32_t uvContentSize = 0;
    uint8_t  *uvBuffer = nullptr;
    int64_t pts = 0L;
    int64_t duration = 0L;
} tMediaVideoBuffer;

typedef struct tMediaAudioBuffer {
    int32_t bufferSize = 0;
    int32_t contentSize = 0;
    uint8_t  *pcmBuffer = nullptr;
    int64_t pts = 0L;
    int64_t duration = 0L;
} tMediaAudioBuffer;

enum tMediaDecodeResult {
    DecodeSuccess,
    DecodeSuccessAndSkipNextPkt,
    DecodeFail,
    DecodeFailAndNeedMorePkt,
    DecodeEnd,
};

enum tMediaReadPktResult {
    ReadVideoSuccess,
    ReadVideoAttachmentSuccess,
    ReadAudioSuccess,
    ReadSubtitleSuccess,
    ReadFail,
    ReadEof,
    UnknownPkt
};

enum tMediaOptResult {
    OptSuccess,
    OptFail
};

typedef struct Metadata {
    int32_t metadataCount = 0;
    char ** metadata = nullptr;
} Metadata;

typedef struct SubtitleStream {
    AVStream *stream = nullptr;
    Metadata streamMetadata;
} SubtitleStream;

typedef struct VideoDecoder {
    const AVCodec *video_decoder = nullptr;
    char *videoDecoderName = nullptr;
    AVBufferRef *hardware_ctx = nullptr;
    AVCodecContext *video_decoder_ctx = nullptr;
    SwsContext * video_sws_ctx = nullptr;
    AVPixelFormat video_pixel_format = AV_PIX_FMT_NONE;
    AVPacket *video_pkt = nullptr;
    AVFrame *video_frame = nullptr;
} VideoDecoder;

typedef struct AudioDecoder {
    const AVCodec *audio_decoder = nullptr;
    char *audioDecoderName = nullptr;
    AVCodecContext *audio_decoder_ctx = nullptr;
    SwrContext *audio_swr_ctx = nullptr;
    int32_t audio_output_sample_rate = 48000;
    AVSampleFormat audio_output_sample_fmt = AV_SAMPLE_FMT_S16;
    AVChannelLayout audio_output_ch_layout = AV_CHANNEL_LAYOUT_STEREO;
    int32_t audio_output_channels = 2;
    AVPacket *audio_pkt = nullptr;
    AVFrame *audio_frame = nullptr;
} AudioDecoder;

typedef struct tMediaPlayerContext {
    /**
     * Format
     */
    AVFormatContext *format_ctx = nullptr;
    bool isRealTime = false;
    bool interruptReadPkt = false;
    int64_t startTime = -1L;
    int64_t duration = -1L;
    char *containerName = nullptr;
    Metadata *fileMetadata = nullptr;
    // buffer
    AVPacket *pkt = nullptr;

    /**
     * Java
     */
    JavaVM* jvm = nullptr;

    /**
     * Video
     */
     // Video Stream
    AVStream *video_stream = nullptr;
    // Video info
    int32_t video_width = 0;
    int32_t video_height = 0;
    int32_t video_bits_per_raw_sample = 0;
    int32_t video_bitrate = 0;
    double video_fps = 0.0;
    int64_t video_duration = 0;
    AVCodecID video_codec_id = AV_CODEC_ID_NONE;
    bool videoIsAttachPic = false;
    Metadata *videoMetaData = nullptr;
    // Video decoder
    VideoDecoder *videoDecoder = nullptr;
    bool requestHwVideoDecoder = false;

    /**
     * Audio
     */
     // Audio stream
    AVStream *audio_stream = nullptr;
    // Audio info
    int32_t audio_channels = 0;
    int32_t audio_bits_per_raw_sample = 0;
    AVSampleFormat audio_sample_format = AV_SAMPLE_FMT_NONE;
    AVCodecID audio_codec_id = AV_CODEC_ID_NONE;
    int32_t audio_bitrate = 0;
    int32_t audio_per_sample_bytes = 0;
    int32_t audio_simple_rate = 0;
    int64_t audio_duration = 0;
    Metadata *audioMetadata = nullptr;
    // Audio decoder
    AudioDecoder *audioDecoder = nullptr;

    /**
     * Subtitle
     */
    int subtitleStreamCount = 0;
    SubtitleStream **subtitleStreams = nullptr;

    tMediaOptResult prepare(
            const char * media_file,
            bool is_request_hw,
            int target_audio_channels,
            int target_audio_sample_rate,
            int target_audio_sample_bit_depth);

    tMediaReadPktResult readPacket() const;

    tMediaOptResult pauseReadPacket() const;

    tMediaOptResult resumeReadPacket() const;

    void movePacketRef(AVPacket *target) const;

    tMediaOptResult seekTo(int64_t targetPosInMillis) const;

    tMediaDecodeResult decodeVideo(AVPacket *targetPkt) const;

    tMediaOptResult moveDecodedVideoFrameToBuffer(tMediaVideoBuffer* buffer);

    void flushVideoCodecBuffer() const;

    tMediaDecodeResult decodeAudio(AVPacket *targetPkt) const;

    tMediaOptResult moveDecodedAudioFrameToBuffer(tMediaAudioBuffer* buffer) const;

    void flushAudioCodecBuffer() const;

    tMediaOptResult setHwSurface(jobject surface);

    void requestInterruptReadPkt();

    void release();
} tMediaPlayerContext;

#endif //TMEDIAPLAYER_TMEDIAPLAYER_H
