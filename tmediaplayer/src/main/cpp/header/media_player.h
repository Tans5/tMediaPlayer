#include <android/log.h>
#include "android/native_window.h"

extern "C" {
#include "libavformat/avformat.h"
#include "libavcodec/avcodec.h"
#include "libswscale/swscale.h"
#include "libavutil/imgutils.h"
}

#define LOG_TAG "tMediaPlayerNative"
#define LOGD(fmt, ...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, fmt, __VA_ARGS__)
#define LOGE(fmt, ...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, fmt, __VA_ARGS__)

enum PLAYER_OPT_RESULT {
    SUCCESS,
    FAIL
};

enum PLAYER_DECODE_RESULT {
    FINISHED,
    STOPPED,
    ERROR
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
} MediaPlayerContext;

PLAYER_OPT_RESULT setup_media_player(MediaPlayerContext *media_player_ctx, const char * file_path);

PLAYER_OPT_RESULT set_window(MediaPlayerContext *media_player_data, ANativeWindow* native_window);

PLAYER_DECODE_RESULT decode(MediaPlayerContext *media_player_ctx);

void release_media_player(MediaPlayerContext *media_player_ctx);