
#ifndef MEDIA_HEADER_H
#define MEDIA_HEADER_H

#include <android/log.h>
#include "android/native_window.h"

extern "C" {
#include "libavformat/avformat.h"
#include "libavcodec/avcodec.h"
#include "libswscale/swscale.h"
#include "libavutil/imgutils.h"
#include <libswresample/swresample.h>
}
#include "SLES/OpenSLES.h"
#include "SLES/OpenSLES_Android.h"

#define LOG_TAG "tMediaPlayerNative"
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

enum PLAYER_OPT_RESULT {
    OPT_SUCCESS,
    OPT_FAIL
};

enum DECODE_FRAME_RESULT {
    DECODE_FRAME_SUCCESS,
    DECODE_FRAME_FINISHED,
    DECODE_FRAME_FAIL
};

typedef struct RenderVideoRawData {
    long pts;
    uint8_t *rgba_frame_buffer = nullptr;
    AVFrame* rgba_frame = nullptr;
} RenderVideoData;

typedef struct AudioBuffer {
    int buffer_size = 0;
    unsigned char *buffer = nullptr;
} AudioBuffer;

typedef struct RenderAudioRawData {
    long pts = 0L;
    int buffer_count = 0;
    AudioBuffer **buffers = nullptr;
} RenderAudioRawData;

typedef struct RenderRawData {
    bool is_video = true;
    RenderVideoData* video_data = nullptr;
    RenderAudioRawData* audio_data = nullptr;
} RenderRawData;

typedef struct MediaPlayerContext {
    const char *media_file = nullptr;
    AVFormatContext *format_ctx = nullptr;
    AVPacket *pkt = nullptr;
    AVFrame *frame = nullptr;
    long duration;

    /**
     * Video
     */
    ANativeWindow *native_window = nullptr;
    ANativeWindow_Buffer *native_window_buffer = nullptr;
    AVStream *video_stream = nullptr;
    AVBufferRef *hw_ctx = nullptr;
    const AVCodec *video_decoder = nullptr;
    SwsContext * sws_ctx = nullptr;

    int video_width;
    int video_height;
    double video_fps;
    double video_base_time;
    int video_time_den;
    AVCodecContext *video_decoder_ctx = nullptr;

    /**
     * Audio
     */
    AVStream *audio_stream = nullptr;
    const AVCodec *audio_decoder = nullptr;
    AVCodecContext *audio_decoder_ctx = nullptr;
    SwrContext *swr_ctx = nullptr;
    int audio_channels;
    int audio_pre_sample_bytes;
    int audio_simple_rate;
    // OpenSL ES
    SLObjectItf sl_engine_object;
    SLEngineItf sl_engine_engine;
    SLObjectItf sl_output_mix_object;
    SLEnvironmentalReverbItf sl_output_mix_rev;
    SLObjectItf sl_player_object;
    SLPlayItf sl_player_play;
    SLAndroidSimpleBufferQueueItf sl_player_buffer_queue;


    PLAYER_OPT_RESULT set_window(ANativeWindow *native_window);

    DECODE_FRAME_RESULT decode_next_frame(RenderRawData* render_data);

    PLAYER_OPT_RESULT render_raw_data(RenderRawData* raw_data);

    PLAYER_OPT_RESULT reset_play_progress();

    void release_render_raw_data(RenderRawData* render_data);

    RenderRawData* new_render_raw_data();

    void release_media_player();

    PLAYER_OPT_RESULT setup_media_player(const char *file_path);
} MediaPlayerContext;

#define AUDIO_OUTPUT_SAMPLE_RATE 44100
#define AUDIO_OUTPUT_CH_LAYOUT (AV_CH_LAYOUT_STEREO)
#define AUDIO_OUTPUT_SAMPLE_FMT AV_SAMPLE_FMT_S32

#endif