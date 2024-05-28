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

enum ImageRawType {
    Yuv420p,
    Nv12,
    Nv21,
    Rgba,
    Unknown
};

typedef struct tMediaVideoBuffer {
    int width = 0;
    int height = 0;
    ImageRawType type = Unknown;
    int rgbaBufferSize = 0;
    int rgbaContentSize = 0;
    AVFrame *rgbaFrame = nullptr;
    uint8_t *rgbaBuffer = nullptr;
    int yBufferSize = 0;
    int yContentSize = 0;
    uint8_t *yBuffer = nullptr;
    int uBufferSize = 0;
    int uContentSize = 0;
    uint8_t  *uBuffer = nullptr;
    int vBufferSize = 0;
    int vContentSize = 0;
    uint8_t  *vBuffer = nullptr;
    int uvBufferSize = 0;
    int uvContentSize = 0;
    uint8_t  *uvBuffer = nullptr;
} tMediaVideoBuffer;

typedef struct tMediaAudioBuffer {
    int bufferSize = 0;
    int contentSize = 0;
    uint8_t  *pcmBuffer = nullptr;
} tMediaAudioBuffer;

enum tMediaDecodeResult {
    DecodeSuccess,
    DecodeSuccessAndSkipNextPkt,
    DecodeFail,
    DecodeFailAndNeedMorePkt,
    DecodeEnd,
};

enum tMediaOptResult {
    OptSuccess,
    OptFail
};

typedef struct tMediaPlayerContext {
    const char *media_file = nullptr;

    AVFormatContext *format_ctx = nullptr;
    AVPacket *pkt = nullptr;
    AVFrame *frame = nullptr;
    long duration = 0;

    int metadataCount = 0;
    char ** metadata = nullptr;

    /**
     * Java
     */
    JavaVM* jvm = nullptr;
    jobject jplayer = nullptr;
    jclass jplayerClazz = nullptr;

    /**
     * Video
     */
    AVStream *video_stream = nullptr;
    AVBufferRef *hardware_ctx = nullptr;
    const AVCodec *video_decoder = nullptr;
    SwsContext * video_sws_ctx = nullptr;
    int video_width = 0;
    int video_height = 0;
    int video_bits_per_raw_sample = 0;
    AVPixelFormat video_pixel_format = AV_PIX_FMT_NONE;
    int video_bitrate = 0;
    double video_fps = 0.0;
    long video_duration = 0;
    bool videoIsAttachPic = false;
    AVCodecID video_codec_id = AV_CODEC_ID_NONE;
    AVCodecContext *video_decoder_ctx = nullptr;

    /**
     * Audio
     */
    AVStream *audio_stream = nullptr;
    const AVCodec *audio_decoder = nullptr;
    AVCodecContext *audio_decoder_ctx = nullptr;
    SwrContext *audio_swr_ctx = nullptr;
    int audio_channels = 0;
    int audio_bits_per_raw_sample = 0;
    AVSampleFormat audio_sample_format = AV_SAMPLE_FMT_NONE;
    int audio_bitrate = 0;
    int audio_per_sample_bytes = 0;
    int audio_simple_rate = 0;
    long audio_duration = 0;
    long audio_output_sample_rate = 48000;
    AVSampleFormat audio_output_sample_fmt = AV_SAMPLE_FMT_S16;
    AVChannelLayout audio_output_ch_layout = AV_CHANNEL_LAYOUT_STEREO;
    int audio_output_channels = 2;
    AVCodecID audio_codec_id = AV_CODEC_ID_NONE;


    tMediaOptResult prepare(
            const char * media_file,
            bool is_request_hw,
            int target_audio_channels,
            int target_audio_sample_rate,
            int target_audio_sample_bit_depth);


    void release();
} tMediaPlayerContext;

#endif //TMEDIAPLAYER_TMEDIAPLAYER_H
