
#ifndef MEDIA_HEADER_H
#define MEDIA_HEADER_H

#include <android/log.h>
#include "android/native_window.h"

extern "C" {
#include "libavformat/avformat.h"
#include "libavcodec/avcodec.h"
#include "libswscale/swscale.h"
#include "libavutil/imgutils.h"
}
#include "pthread.h"

#define LOG_TAG "tMediaPlayerNative"
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

enum PLAYER_OPT_RESULT {
    SUCCESS,
    FAIL
};

enum DECODE_FRAME_RESULT {
    DECODE_FRAME_SUCCESS,
    DECODE_FRAME_CONTINUE,
    DECODE_FRAME_FAIL
};

typedef struct MediaPlayerContext {
    const char *media_file;
    AVFormatContext *format_ctx;
    AVPacket *pkt;
    AVFrame *frame;

    /**
     * Video
     */
    ANativeWindow *native_window;
    ANativeWindow_Buffer *native_window_buffer;
    AVStream *video_stream;
    AVCodec *video_decoder;
    SwsContext * sws_ctx;
    AVFrame *rgba_frame;
    uint8_t *rgba_frame_buffer;

    int video_width;
    int video_height;
    double video_fps;
    double video_base_time;
    int video_time_den;
    AVCodecContext *video_decoder_ctx;

    /**
     * Audio
     */
    AVStream *audio_stream;
    AVCodec *audio_decoder;
    AVCodecContext *audio_decoder_ctx;


    /**
     * Player State
     */
    bool is_paused = false;
    bool is_stopped = false;
    bool is_released = false;

    /**
     * sync
     */
    long id;
    pthread_mutex_t *player_mutex;
    pthread_cond_t *player_pause_cond;

    PLAYER_OPT_RESULT set_window(ANativeWindow *native_window);

    void decode();

    void release_media_player();

    PLAYER_OPT_RESULT setup_media_player(const char *file_path);
} MediaPlayerContext;
#endif