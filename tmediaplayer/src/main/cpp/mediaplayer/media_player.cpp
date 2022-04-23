#include "media_player.h"

extern "C" {
#include "libavformat/avformat.h"
#include "libavcodec/avcodec.h"
#include "libswscale/swscale.h"
#include "libavutil/imgutils.h"
}
#include "android/native_window.h"
#include "android/native_window_jni.h"
#include "media_time.h"

PLAYER_OPT_RESULT render_frame(MediaPlayerContext *media_player_ctx);

PLAYER_OPT_RESULT setup_media_player(MediaPlayerContext *media_player_ctx, const char *file_path) {
    LOGD("Setup media player file path: %s", file_path);
    media_player_ctx->media_file = file_path;
    // Find audio and video streams.
    auto format_ctx = avformat_alloc_context();
    media_player_ctx->format_ctx = format_ctx;
    int format_open_result = avformat_open_input(&format_ctx, file_path, nullptr, nullptr);
    if (format_open_result != 0) {
        LOGE("Format open file fail: %d", format_open_result);
        return FAIL;
    }
    int stream_find_result = avformat_find_stream_info(format_ctx, nullptr);
    if (stream_find_result < 0) {
        LOGE("Format find stream error: %d", stream_find_result);
        return FAIL;
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
    media_player_ctx->audio_stream = audio_stream;
    media_player_ctx->video_stream = video_stream;


    auto pkt = av_packet_alloc();
    media_player_ctx->pkt = pkt;
    auto frame = av_frame_alloc();
    media_player_ctx->frame = frame;

    // Video decode
    if (video_stream != nullptr) {
        auto video_codec = video_stream->codecpar->codec_id;
        auto video_decoder = avcodec_find_decoder(video_codec);
        if (video_decoder == nullptr) {
            LOGE("%s", "Do not find video decoder");
        } else {
            media_player_ctx->video_decoder = video_decoder;
            auto video_decoder_ctx = avcodec_alloc_context3(video_decoder);
            media_player_ctx->video_decoder_ctx = video_decoder_ctx;

            if (avcodec_parameters_to_context(video_decoder_ctx, video_stream->codecpar) < 0) {
                LOGE("%s", "Set video stream params fail");
                return FAIL;
            }
            if (avcodec_open2(video_decoder_ctx, video_decoder, nullptr) < 0) {
                LOGE("%s", "Open video decoder fail");
                return FAIL;
            }
            media_player_ctx->video_width = video_decoder_ctx->width;
            media_player_ctx->video_height = video_decoder_ctx->height;
            media_player_ctx->video_fps = av_q2d(video_stream->r_frame_rate);
            media_player_ctx->video_base_time = av_q2d(video_stream->time_base);
            media_player_ctx->video_time_den = video_stream->time_base.den;
            LOGD("Width: %d, Height: %d, Fps: %.1f, Base time: %.1f",
                 media_player_ctx->video_width,
                 media_player_ctx->video_height, media_player_ctx->video_fps,
                 media_player_ctx->video_base_time);

            int video_width = video_decoder_ctx->width, video_height = video_decoder_ctx->height;

            auto sws_ctx = sws_getContext(
                    video_width, video_height, video_decoder_ctx->pix_fmt,
                    video_width, video_height, AV_PIX_FMT_RGBA,
                    SWS_BICUBIC, nullptr, nullptr, nullptr
            );
            media_player_ctx->sws_ctx = sws_ctx;
            auto rgba_frame = av_frame_alloc();
            int buffer_size = av_image_get_buffer_size(AV_PIX_FMT_RGBA, video_width, video_height,
                                                       1);
            auto *rgba_frame_buffer = static_cast<uint8_t *>(av_malloc(
                    buffer_size * sizeof(uint8_t)));
            av_image_fill_arrays(rgba_frame->data, rgba_frame->linesize, rgba_frame_buffer,
                                 AV_PIX_FMT_RGBA, video_width, video_height, 1);
            media_player_ctx->rgba_frame = rgba_frame;
            media_player_ctx->rgba_frame_buffer = rgba_frame_buffer;
            auto *native_window_buffer = new ANativeWindow_Buffer;
            media_player_ctx->native_window_buffer = native_window_buffer;
        }
    }

    // Audio decode
//    if (audio_stream != nullptr) {
//        auto audio_decoder = avcodec_find_decoder(audio_stream->codecpar->codec_id);
//        if (audio_decoder == nullptr) {
//            LOGE("%s", "Do not find audio decoder");
//        } else {
//            media_player_ctx->audio_decoder = audio_decoder;
//            auto audio_decoder_ctx = avcodec_alloc_context3(audio_decoder);
//            media_player_ctx->audio_decoder_ctx = audio_decoder_ctx;
//        }
//    }
    if (video_stream == nullptr && audio_stream == nullptr) {
        return FAIL;
    } else {
        return SUCCESS;
    }
}

PLAYER_OPT_RESULT set_window(MediaPlayerContext *media_player_data, ANativeWindow *native_window) {
    media_player_data->native_window = native_window;
    return SUCCESS;
}

PLAYER_DECODE_RESULT decode(MediaPlayerContext *media_player_ctx) {
    if (media_player_ctx != nullptr) {
        auto fmt_ctx = media_player_ctx->format_ctx;
        auto pkt = media_player_ctx->pkt;

        auto decoder_ctx = media_player_ctx->video_decoder_ctx;
        auto decoder = media_player_ctx->video_decoder;
        auto stream = media_player_ctx->video_stream;
        auto frame = media_player_ctx->frame;
        if (decoder_ctx != nullptr &&
            decoder != nullptr &&
            stream != nullptr &&
            pkt != nullptr &&
            frame != nullptr &&
            fmt_ctx != nullptr) {

            long decode_start_millis = get_time_millis();
            do {
                long decode_frame_start = get_time_millis();
                av_packet_unref(pkt);
                // 1. read pkt.
                int read_frame_result = av_read_frame(fmt_ctx, pkt);
                if (read_frame_result < 0) {
                    LOGD("%s", "Decode video read frame result.");
                    return FINISHED;
                }

                if (pkt->stream_index == stream->index) {

                    // 2. send pkt to decoder.
                    int send_pkg_result = avcodec_send_packet(decoder_ctx, pkt);
                    if (send_pkg_result < 0 && send_pkg_result != AVERROR(EAGAIN)) {
                        LOGE("Decode video send pkt fail: %d", send_pkg_result);
                        return ERROR;
                    }
                    av_frame_unref(frame);

                    // 3. receive frame
                    int receive_frame_result = avcodec_receive_frame(decoder_ctx, frame);
                    if (receive_frame_result == AVERROR(EAGAIN)) {
                        continue;
                    }
                    if (receive_frame_result < 0) {
                        LOGE("%s", "Decode video frame fail");
                        return ERROR;
                    }

                    int64_t pts_millis = frame->pts * 1000 / media_player_ctx->video_time_den;
                    LOGD("Decode video frame success: %lld, time cost: %ld", pts_millis, get_time_millis() - decode_frame_start);

                    // render_frame
                    render_frame(media_player_ctx);

                    // delay
                    long cts_millis = get_time_millis() - decode_start_millis;
                    long d = pts_millis - cts_millis;
                    if (d > 0) {
                        msleep(d);
                    }
                }
            } while (true);
            LOGD("%s", "Decode video finish!!!");
        } else {
            LOGE("%s", "Decode video fail, stream, decoder and context is null.");
            return ERROR;
        }
    } else {
        LOGE("%s", "Decode video fail.");
        return ERROR;
    }
}

PLAYER_OPT_RESULT render_frame(MediaPlayerContext *media_player_ctx) {
    auto window = media_player_ctx -> native_window;
    if (window != nullptr) {
        auto frame = media_player_ctx -> frame;
        auto sws_ctx = media_player_ctx->sws_ctx;
        auto rgba_frame = media_player_ctx->rgba_frame;
        unsigned long scale_start = get_time_millis();
        sws_scale(sws_ctx, frame->data, frame->linesize, 0, frame->height, rgba_frame->data, rgba_frame->linesize);
        LOGD("Scale time cost: %ld", get_time_millis() - scale_start);
        unsigned long render_start = get_time_millis();
        if (ANativeWindow_setBuffersGeometry(window, frame->width,frame->height, WINDOW_FORMAT_RGBA_8888) != 0) {
            return FAIL;
        }
        auto window_buffer = media_player_ctx->native_window_buffer;
        auto rgba_buffer = media_player_ctx->rgba_frame_buffer;
        if (ANativeWindow_lock(window, window_buffer, nullptr) != 0) {
            return FAIL;
        }
        auto bits = (uint8_t*) window_buffer->bits;
        for (int h = 0; h < frame->height; h++) {
            memcpy(bits + h * window_buffer->stride * 4,
                   rgba_buffer + h * rgba_frame->linesize[0],
                   rgba_frame->linesize[0]);
        }
        if (ANativeWindow_unlockAndPost(window) < 0) {
            return FAIL;
        }
        LOGD("Render time cost: %ld", get_time_millis() - render_start);
        return SUCCESS;
    } else {
        LOGD("%s", "Native window is null, skip render.");
        return FAIL;
    }
}

void release_media_player(MediaPlayerContext * media_player_ctx) {
    if (media_player_ctx != nullptr) {
        LOGD("%s", "Release media player");

        // Common release.
        auto pkt = media_player_ctx->pkt;
        if (pkt != nullptr) {
            av_packet_unref(pkt);
            av_packet_free(&pkt);
        }

        auto format_ctx = media_player_ctx->format_ctx;
        if (format_ctx != nullptr) {
            avformat_close_input(&format_ctx);
            avformat_free_context(format_ctx);
        }

        // Video Release.
        auto video_frame = media_player_ctx->frame;
        if (video_frame != nullptr) {
            av_frame_free(&video_frame);
        }

        auto video_decoder_ctx = media_player_ctx->video_decoder_ctx;
        if (video_decoder_ctx != nullptr) {
            avcodec_close(video_decoder_ctx);
            avcodec_free_context(&video_decoder_ctx);
        }

        auto native_window = media_player_ctx->native_window;
        if (native_window != nullptr) {
            ANativeWindow_release(native_window);
        }

        auto native_window_buffer = media_player_ctx->native_window_buffer;
        if (native_window_buffer != nullptr) {
            free(native_window_buffer);
        }

        auto rgba_buffer = media_player_ctx->rgba_frame_buffer;
        if (rgba_buffer != nullptr) {
            av_free(rgba_buffer);
        }

        auto rgba_frame = media_player_ctx->rgba_frame;
        if (rgba_frame != nullptr) {
            av_frame_free(&rgba_frame);
        }

        auto sws_ctx = media_player_ctx->sws_ctx;
        if (sws_ctx != nullptr) {
            sws_freeContext(sws_ctx);
        }

        // Audio free.
        auto audio_decoder_ctx = media_player_ctx->audio_decoder_ctx;
        if (audio_decoder_ctx != nullptr) {
            avcodec_close(audio_decoder_ctx);
            avcodec_free_context(&audio_decoder_ctx);
        }

        free(media_player_ctx);
    }
}