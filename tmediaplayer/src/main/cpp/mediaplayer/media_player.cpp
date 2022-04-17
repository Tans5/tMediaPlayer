#include "media_player.h"
extern "C" {
#include "libavformat/avformat.h"
}

typedef struct MediaPlayerData {
    const char * media_file;
    AVFormatContext *format_ctx;
    AVStream *video_stream;
    AVStream *audio_stream;
    AVCodec *video_decoder;
    AVCodecContext *video_decoder_ctx;
    AVCodec *audio_decoder;
    AVCodecContext *audio_decoder_ctx;
} MediaPlayerData;

MediaPlayerData *media_player_data = nullptr;

void set_media_file_path(const char * file_path) {
    LOGD("File path: %s", file_path);
//    media_player_data = {};
//    media_player_data->media_file = file_path;
    // Find audio and video streams.
    auto format_ctx = avformat_alloc_context();
    // media_player_data->format_ctx = format_ctx;
    int format_open_result = avformat_open_input(&format_ctx, file_path, nullptr, nullptr);
    if (format_open_result != 0) {
        LOGE("Format open file fail: %d", format_open_result);
        return;
    }
    int stream_find_result = avformat_find_stream_info(format_ctx, nullptr);
    if (stream_find_result < 0) {
        LOGE("Format find stream error: %d", stream_find_result);
        return;
    }
    AVStream *audio_stream = nullptr;
    AVStream *video_stream = nullptr;
    for (int i = 0; i < format_ctx -> nb_streams; i ++) {
        auto stream = format_ctx -> streams[i];
        auto codec_type = stream -> codecpar -> codec_type;
        switch (codec_type) {
            case AVMEDIA_TYPE_AUDIO:
                audio_stream = stream;
                LOGD("Find Stream: %s", "AVMEDIA_TYPE_AUDIO");
                break;
            case AVMEDIA_TYPE_VIDEO:
                video_stream = stream;
                LOGD("Find Stream: %s", "AVMEDIA_TYPE_VIDEO");
                break;
            case AVMEDIA_TYPE_UNKNOWN:
                LOGD("Find Stream: %s", "AVMEDIA_TYPE_UNKNOWN");
                break;
            case AVMEDIA_TYPE_DATA:
                LOGD("Find Stream: %s", "AVMEDIA_TYPE_DATA");
                break;
            case AVMEDIA_TYPE_SUBTITLE:
                LOGD("Find Stream: %s", "AVMEDIA_TYPE_SUBTITLE");
                break;
            case AVMEDIA_TYPE_ATTACHMENT:
                LOGD("Find Stream: %s", "AVMEDIA_TYPE_ATTACHMENT");
                break;
            case AVMEDIA_TYPE_NB:
                LOGD("Find Stream: %s", "AVMEDIA_TYPE_NB");
                break;
        }
    }
//    media_player_data->audio_stream = audio_stream;
//    media_player_data->video_stream = video_stream;

    // Video decode
    if (video_stream != nullptr) {
        auto video_codec = video_stream->codecpar->codec_id;
        auto video_decoder = avcodec_find_decoder(video_codec);
        if (video_decoder == nullptr) {
            LOGE("%s", "Do not find video decoder");
            return;
        }
//        media_player_data->video_decoder = video_decoder;
        auto video_decoder_ctx = avcodec_alloc_context3(video_decoder);
//        media_player_data->video_decoder_ctx = video_decoder_ctx;
    }

}