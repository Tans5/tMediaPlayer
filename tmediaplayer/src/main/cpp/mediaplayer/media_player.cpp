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
#include "pthread.h"


DECODE_FRAME_RESULT decode_single_video_frame(MediaPlayerContext *media_player_ctx);

PLAYER_OPT_RESULT render_video_frame(MediaPlayerContext *media_player_ctx);

DECODE_FRAME_RESULT decode_single_audio_frame(MediaPlayerContext *media_player_ctx, long decode_start_millis);

PLAYER_OPT_RESULT MediaPlayerContext::setup_media_player( const char *file_path) {
    LOGD("Setup media player file path: %s", file_path);
    // mutex data
    this->id = get_time_millis();
    pthread_mutex_t mutex;
    int mutex_result = pthread_mutex_init(&mutex, nullptr);
    LOGD("Mutex Result: %d", mutex_result);
    this->player_mutex = &mutex;
    pthread_cond_t pause_cond;
    pthread_cond_init(&pause_cond, nullptr);
    this->player_pause_cond = &pause_cond;

    this->media_file = file_path;
    // Find audio and video streams.
    this->format_ctx = avformat_alloc_context();
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
    for (int i = 0; i < format_ctx->nb_streams; i++) {
        auto stream = format_ctx->streams[i];
        auto codec_type = stream->codecpar->codec_type;
        switch (codec_type) {
            case AVMEDIA_TYPE_AUDIO:
                this->audio_stream = stream;
                LOGD("Find Stream: %s", "AVMEDIA_TYPE_AUDIO");
                break;
            case AVMEDIA_TYPE_VIDEO:
                this->video_stream = stream;
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

    this->pkt = av_packet_alloc();
    this->frame = av_frame_alloc();

    // Video decode
    if (this->video_stream != nullptr) {
        auto video_codec = video_stream->codecpar->codec_id;
        this->video_decoder = avcodec_find_decoder(video_codec);
        if (this->video_decoder == nullptr) {
            LOGE("Do not find video decoder");
        } else {
            this->video_decoder_ctx = avcodec_alloc_context3(video_decoder);

            if (avcodec_parameters_to_context(video_decoder_ctx, video_stream->codecpar) < 0) {
                LOGE("Set video stream params fail");
                return FAIL;
            }
            if (avcodec_open2(video_decoder_ctx, video_decoder, nullptr) < 0) {
                LOGE("Open video decoder fail");
                return FAIL;
            }
            this->video_width = video_decoder_ctx->width;
            this->video_height = video_decoder_ctx->height;
            this->video_fps = av_q2d(video_stream->r_frame_rate);
            this->video_base_time = av_q2d(video_stream->time_base);
            this->video_time_den = video_stream->time_base.den;
            LOGD("Width: %d, Height: %d, Fps: %.1f, Base time: %.1f",
                 video_width,
                 video_height, video_fps,
                 video_base_time);

            this->sws_ctx = sws_getContext(
                    video_width, video_height, video_decoder_ctx->pix_fmt,
                    video_width, video_height, AV_PIX_FMT_RGBA,
                    SWS_BICUBIC, nullptr, nullptr, nullptr
            );
            this->rgba_frame = av_frame_alloc();
            int buffer_size = av_image_get_buffer_size(AV_PIX_FMT_RGBA, video_width, video_height,
                                                       1);
            this->rgba_frame_buffer = static_cast<uint8_t *>(av_malloc(
                    buffer_size * sizeof(uint8_t)));
            av_image_fill_arrays(rgba_frame->data, rgba_frame->linesize, rgba_frame_buffer,
                                 AV_PIX_FMT_RGBA, video_width, video_height, 1);
            this->native_window_buffer = new ANativeWindow_Buffer;
        }
    }

    // Audio decode
    if (audio_stream != nullptr) {
        this->audio_decoder = avcodec_find_decoder(audio_stream->codecpar->codec_id);
        if (audio_decoder == nullptr) {
            LOGE("%s", "Do not find audio decoder");
        } else {
            this->audio_decoder = audio_decoder;
            this->audio_decoder_ctx = avcodec_alloc_context3(audio_decoder);
        }
    }
    if (video_stream == nullptr && audio_stream == nullptr) {
        return FAIL;
    } else {
        return SUCCESS;
    }
}

PLAYER_OPT_RESULT MediaPlayerContext::set_window(ANativeWindow *native_window_l) {
    this->native_window = native_window_l;
    return SUCCESS;
}

void *decode_new_thread(void *arg) {
    MediaPlayerContext * media_player_ctx = static_cast<MediaPlayerContext *>(arg);
    if (media_player_ctx != nullptr) {

        // common
        auto fmt_ctx = media_player_ctx->format_ctx;
        auto pkt = media_player_ctx->pkt;
        auto frame = media_player_ctx->frame;

        // video
        auto video_decoder_ctx = media_player_ctx->video_decoder_ctx;
        auto video_decoder = media_player_ctx->video_decoder;
        auto video_stream = media_player_ctx->video_stream;

        // audio
        auto audio_decoder_ctx = media_player_ctx->audio_decoder_ctx;
        auto audio_decoder = media_player_ctx->audio_decoder;
        auto audio_stream = media_player_ctx->audio_stream;

        if (pkt != nullptr &&
            frame != nullptr &&
            fmt_ctx != nullptr) {
            if (video_stream != nullptr) {
                av_seek_frame(fmt_ctx, video_stream->index, 0, AVSEEK_FLAG_BACKWARD);
            }
            if (audio_stream != nullptr) {
                av_seek_frame(fmt_ctx, audio_stream->index, 0, AVSEEK_FLAG_BACKWARD);
            }
            long decode_start_millis = get_time_millis();
            do {
                int lock_result = pthread_mutex_lock(media_player_ctx->player_mutex);
                LOGD("Lock Result: %d", lock_result);
                auto is_paused = media_player_ctx->is_paused;
                auto is_stopped = media_player_ctx->is_stopped;
                auto is_released = media_player_ctx->is_released;
                if (is_stopped || is_released) {
                    LOGD("%s", "Player stopped or released.");
                    pthread_mutex_unlock(media_player_ctx->player_mutex);
                    return nullptr;
                }
                if (is_paused) {
                    LOGD("%s", "Player paused.");
                    pthread_cond_wait(media_player_ctx->player_pause_cond, media_player_ctx->player_mutex);
                    LOGD("%s", "Player playing.");
                }
                av_packet_unref(pkt);
                // 1. read pkt.
                int read_frame_result = av_read_frame(fmt_ctx, pkt);
                if (read_frame_result < 0) {
                    LOGD("%s", "Decode video read frame result.");
                    pthread_mutex_unlock(media_player_ctx->player_mutex);
                    return nullptr;
                }
                DECODE_FRAME_RESULT decode_result = DECODE_FRAME_CONTINUE;

                int64_t pts_millis;
                // decode video
                if (video_decoder_ctx != nullptr &&
                    video_decoder != nullptr &&
                    video_stream != nullptr &&
                    pkt->stream_index == video_stream->index) {
                    decode_result = decode_single_video_frame(media_player_ctx);
                    pts_millis = frame->pts * 1000 / media_player_ctx->video_time_den;
                }

                // decode audio
                if (audio_decoder_ctx != nullptr &&
                    audio_decoder != nullptr &&
                    audio_stream != nullptr &&
                    pkt->stream_index == audio_stream->index) {
                    decode_result = decode_single_audio_frame(media_player_ctx, decode_start_millis);
                }
                pthread_mutex_unlock(media_player_ctx->player_mutex);
                if (decode_result == DECODE_FRAME_FAIL) {
                    break;
                }
                if (decode_result == DECODE_FRAME_CONTINUE) {
                    continue;
                }
                // 5. delay
                long cts_millis = get_time_millis() - decode_start_millis;
                long d = pts_millis - cts_millis;
                if (d > 0) {
                    msleep(d);
                }

            } while (true);
            LOGD("Decode video finish!!!");
        } else {
            LOGE("Decode video fail, video_stream, video_decoder and context is null.");
        }
    } else {
        LOGE("Decode video fail.");
    }
    return nullptr;
}

void MediaPlayerContext::decode() {
    pthread_t decode_thread;
    pthread_create(&decode_thread, nullptr, decode_new_thread, this);
}

DECODE_FRAME_RESULT decode_single_video_frame(MediaPlayerContext *media_player_ctx) {
    long decode_frame_start = get_time_millis();
    auto decoder_ctx = media_player_ctx->video_decoder_ctx;
    auto pkt = media_player_ctx->pkt;
    auto frame = media_player_ctx->frame;
    // 2. send pkt to decoder.
    int send_pkg_result = avcodec_send_packet(decoder_ctx, pkt);
    if (send_pkg_result < 0 && send_pkg_result != AVERROR(EAGAIN)) {
        LOGE("Decode video send pkt fail: %d", send_pkg_result);
        return DECODE_FRAME_FAIL;
    }
    av_frame_unref(frame);

    // 3. receive frame
    int receive_frame_result = avcodec_receive_frame(decoder_ctx, frame);
    if (receive_frame_result == AVERROR(EAGAIN)) {
        return DECODE_FRAME_CONTINUE;
    }
    if (receive_frame_result < 0) {
        LOGE("%s", "Decode video frame fail");
        return DECODE_FRAME_FAIL;
    }

    int64_t pts_millis = frame->pts * 1000 / media_player_ctx->video_time_den;
    LOGD("Decode video frame success: %lld, time cost: %ld", pts_millis, get_time_millis() - decode_frame_start);

    // 4. render_video_frame
    render_video_frame(media_player_ctx);
    return DECODE_FRAME_SUCCESS;
}

PLAYER_OPT_RESULT render_video_frame(MediaPlayerContext *media_player_ctx) {
    auto window = media_player_ctx -> native_window;
    if (window != nullptr) {
        try {
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
        } catch (...) {
            LOGE("%s", "Render frame error.");
            return FAIL;
        }
    } else {
        LOGD("%s", "Native window is null, skip render.");
        return FAIL;
    }
}

DECODE_FRAME_RESULT decode_single_audio_frame(MediaPlayerContext *media_player_ctx, long decode_start_millis) {
    // TODO: decode audio frame.
    return DECODE_FRAME_CONTINUE;
}

void * release_media_player_new_thread(void *arg) {
    MediaPlayerContext *media_player_ctx = static_cast<MediaPlayerContext *>(arg);
    if (media_player_ctx != nullptr) {
        pthread_mutex_lock(media_player_ctx->player_mutex);

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
        media_player_ctx->is_released = true;

        pthread_mutex_unlock(media_player_ctx->player_mutex);
    }
    return nullptr;
}

void MediaPlayerContext::release_media_player() {
    pthread_t release_thread;
    pthread_create(&release_thread, nullptr, release_media_player_new_thread, this);
}