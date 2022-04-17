#include "media_player.h"

extern "C" {
#include "libavformat/avformat.h"
#include "libavcodec/avcodec.h"
}

typedef struct MediaPlayerData {
    const char *media_file;
    AVFormatContext *format_ctx;
    AVStream *video_stream;
    AVStream *audio_stream;
    AVCodec *video_decoder;
    AVPacket *video_pkg;
    AVFrame *video_frame;
    AVCodecContext *video_decoder_ctx;
    AVCodec *audio_decoder;
    AVCodecContext *audio_decoder_ctx;
} MediaPlayerData;

MediaPlayerData *media_player_data = nullptr;

void decode_video();

void setup_media_player(const char *file_path) {
    release_media_player();
    LOGD("Setup media player file path: %s", file_path);
    media_player_data = new MediaPlayerData;
    media_player_data->media_file = file_path;
    // Find audio and video streams.
    auto format_ctx = avformat_alloc_context();
    media_player_data->format_ctx = format_ctx;
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
    for (int i = 0; i < format_ctx->nb_streams; i++) {
        auto stream = format_ctx->streams[i];
        auto codec_type = stream->codecpar->codec_type;
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
    media_player_data->audio_stream = audio_stream;
    media_player_data->video_stream = video_stream;

    // Video decode
    if (video_stream != nullptr) {
        auto video_codec = video_stream->codecpar->codec_id;
        auto video_decoder = avcodec_find_decoder(video_codec);
        if (video_decoder == nullptr) {
            LOGE("%s", "Do not find video decoder");
        } else {
            media_player_data->video_decoder = video_decoder;
            auto video_decoder_ctx = avcodec_alloc_context3(video_decoder);
            media_player_data->video_decoder_ctx = video_decoder_ctx;
            auto video_pkg = av_packet_alloc();
            media_player_data->video_pkg = video_pkg;
            auto video_frame = av_frame_alloc();
            media_player_data->video_frame = video_frame;
        }
    }

    // Audio decode
    if (audio_stream != nullptr) {
        auto audio_decoder = avcodec_find_decoder(audio_stream->codecpar->codec_id);
        if (audio_decoder == nullptr) {
            LOGE("%s", "Do not find audio decoder");
        } else {
            media_player_data->audio_decoder = audio_decoder;
            auto audio_decoder_ctx = avcodec_alloc_context3(audio_decoder);
            media_player_data->audio_decoder_ctx = audio_decoder_ctx;
        }
    }
    decode_video();
}

void decode_video() {
    if (media_player_data != nullptr) {
        auto fmt_ctx = media_player_data->format_ctx;
        auto decoder_ctx = media_player_data->video_decoder_ctx;
        auto decoder = media_player_data->video_decoder;
        auto stream = media_player_data->video_stream;
        auto pkg = media_player_data->video_pkg;
        auto frame = media_player_data->video_frame;
        if (decoder_ctx != nullptr &&
            decoder != nullptr &&
            stream != nullptr &&
            pkg != nullptr &&
            frame != nullptr &&
            fmt_ctx != nullptr) {
            auto p_result = avcodec_parameters_to_context(decoder_ctx, stream->codecpar);
            if (p_result >= 0) {
                auto c_result = avcodec_open2(decoder_ctx, decoder, nullptr);
                if (c_result >= 0) {
                    do {
                        int read_frame_result = av_read_frame(fmt_ctx, pkg);
                        if (read_frame_result < 0) {
                            LOGD("%s", "Decode video read frame result.");
                        }
                        int send_pkg_result = avcodec_send_packet(decoder_ctx, pkg);
                        if (send_pkg_result < 0) {
                            LOGE("Decode video send pkg fail: %d", send_pkg_result);
                            return;
                        }
                        int receive_frame_result = avcodec_receive_frame(decoder_ctx, frame);
                        if (receive_frame_result >= 0) {
                            // TODO: handle decoded frame.
                            LOGD("Decode video frame success: %lld", frame->pts);
                        } else {
                            LOGE("%s", "Decode video frame fail");
                            break;
                        }
                    } while (true);
                    LOGD("%s", "Decode video finish!!!");
                } else {
                    LOGE("Decode video fail, open codec fail: %d", c_result);
                }
            } else {
                LOGE("Decode video fail, p result: %d", p_result);
            }
        } else {
            LOGE("%s", "Decode video fail, stream, decoder and context is null.");
        }
    } else {
        LOGE("%s", "Decode video fail.");
    }
}

void release_media_player() {
    if (media_player_data != nullptr) {
        LOGD("%s", "Release media player");
        auto audio_decoder_ctx = media_player_data->audio_decoder_ctx;
        if (audio_decoder_ctx != nullptr) {
            avcodec_close(audio_decoder_ctx);
            avcodec_free_context(&audio_decoder_ctx);
        }

        auto video_pkg = media_player_data->video_pkg;
        if (video_pkg != nullptr) {
            av_packet_free(&video_pkg);
        }

        auto video_frame = media_player_data->video_frame;
        if (video_frame != nullptr) {
            av_frame_free(&video_frame);
        }

        auto video_decoder_ctx = media_player_data->video_decoder_ctx;
        if (video_decoder_ctx != nullptr) {
            avcodec_close(video_decoder_ctx);
            avcodec_free_context(&video_decoder_ctx);
        }

        auto format_ctx = media_player_data->format_ctx;
        if (format_ctx != nullptr) {
            avformat_close_input(&format_ctx);
            avformat_free_context(format_ctx);
        }

        free(media_player_data);
        media_player_data = nullptr;
    }
}