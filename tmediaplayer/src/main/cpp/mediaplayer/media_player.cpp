#include <__threading_support>
#include "media_player.h"

extern "C" {
#include "libavformat/avformat.h"
#include "libavcodec/avcodec.h"
#include "libswscale/swscale.h"
#include "libavutil/imgutils.h"
#include "libswresample/swresample.h"
#include "libavcodec/mediacodec.h"
}
#include "android/native_window.h"
#include "android/native_window_jni.h"
#include "media_time.h"
#include "SLES/OpenSLES.h"
#include "SLES/OpenSLES_Android.h"

void* decode_test(void* player_cxt) {
    MediaPlayerContext* ctx = static_cast<MediaPlayerContext *>(player_cxt);
    // ctx->jvm->AttachCurrentThread(&ctx->jniEnv, nullptr);
    ctx->reset_play_progress();
    auto fmt_ctx = ctx->format_ctx;
    auto pkt = ctx->pkt;
    auto video_decoder_ctx = ctx->video_decoder_ctx;
    auto audio_decoder_ctx = ctx->audio_decoder_ctx;
    auto frame = ctx->frame;
    auto audio_stream = ctx->audio_stream;
    auto video_stream = ctx->video_stream;
    auto rgba_frame = av_frame_alloc();
    int buffer_size = av_image_get_buffer_size(AV_PIX_FMT_RGBA, ctx->video_width, ctx->video_height,1);
    auto rgba_frame_buffer = static_cast<uint8_t *>(av_malloc(
            buffer_size * sizeof(uint8_t)));
    av_image_fill_arrays(rgba_frame->data, rgba_frame->linesize, rgba_frame_buffer,
                         AV_PIX_FMT_RGBA, ctx->video_width, ctx->video_height, 1);

    auto sws_ctx = sws_getContext(
            ctx->video_width, ctx->video_height, video_decoder_ctx->pix_fmt,
            ctx->video_width, ctx->video_height, AV_PIX_FMT_RGBA,
            SWS_BICUBIC, nullptr, nullptr, nullptr
    );
    bool skipReadPktFrame = false;
    LOGD("Test decode start");
    int result = 0;
    while (true) {
        long time_start = get_time_millis();

        if (!skipReadPktFrame) {
            av_packet_unref(pkt);
            result = av_read_frame(fmt_ctx, pkt);
            if (result < 0) {
                LOGD("Decode read frame fail: %d", result);
                break;
            }
        }
        if (pkt->stream_index == video_stream->index) {
            result = avcodec_send_packet(video_decoder_ctx, pkt);
            skipReadPktFrame = result == -11;
            if (result != 0 && !skipReadPktFrame) {
                LOGE("Decode video send pkt: %d", result);
                break;
            }
            if (skipReadPktFrame) {
                LOGD("Decode video skip read pkt frame");
            }
            av_frame_unref(frame);
            result = avcodec_receive_frame(video_decoder_ctx, frame);
            if (result == -11) {
                LOGD("Decode video do resend frame");
                continue;
            }
            if (result < 0) {
                LOGD("Decode video receive video frame fail: %d", result);
                break;
            }
            int scale_width = sws_scale(sws_ctx, frame->data, frame->linesize, 0, frame->height, rgba_frame->data, rgba_frame->linesize);
            LOGD("Decode video scale result: %d", scale_width);
            long time_end = get_time_millis();
            LOGD("Decode video cost: %ld ms", time_end - time_start);
            if (scale_width > 0) {
                int size = ctx->video_width * ctx->video_height * 4;
                jint jWidth = ctx->video_width;
                jint jHeight = ctx->video_height;
                jbyteArray jFrame = ctx->jniEnv->NewByteArray(size);
                ctx->jniEnv->SetByteArrayRegion(jFrame, 0, size,reinterpret_cast<const jbyte *>(rgba_frame_buffer));
                ctx->jniEnv->CallVoidMethod(ctx->jplayer,ctx->jniEnv->GetMethodID(ctx->jniEnv->GetObjectClass(ctx->jplayer), "onNewVideoFrame", "(II[B)V"), jWidth, jHeight, jFrame);
                ctx->jniEnv->DeleteLocalRef(jFrame);
            }
            // msleep(100);
        } else {
            skipReadPktFrame = false;
        }

//        if (pkt->stream_index == ctx->audio_stream->index) {
//            result = avcodec_send_packet(audio_decoder_ctx, pkt);
//            if (result < 0) {
//                break;
//            }
//            int count = 0;
//            while (true) {
//                av_frame_unref(frame);
//                result = avcodec_receive_frame(audio_decoder_ctx, frame);
//                if (result < 0) {
//                    break;
//                }
//                long pts_millis = frame->pts * 1000 / audio_stream->time_base.den;
//                LOGD("Audio Pts: %ld ms, nb sample: %d", pts_millis, frame->nb_samples);
//                count ++;
//            }
//            long time_end = get_time_millis();
//            LOGD("Audio decode count: %d, cost: %ld ms", count, time_end - time_start);
//        }
//        msleep(20);
    }
    LOGD("Test decode end.");
    return nullptr;
}

AVPixelFormat hw_pix_fmt = AV_PIX_FMT_NONE;

static enum AVPixelFormat get_hw_format(AVCodecContext *ctx,
                                        const enum AVPixelFormat *pix_fmts) {
    const enum AVPixelFormat *p;

    for (p = pix_fmts; *p != -1; p++) {
        if (*p == hw_pix_fmt) {
            LOGE("get HW surface format: %d", *p);
            return *p;
        }
    }

    LOGE("Failed to get HW surface format");
    return AV_PIX_FMT_NONE;
}

PLAYER_OPT_RESULT MediaPlayerContext::setup_media_player( const char *file_path) {
    LOGD("Setup media player file path: %s", file_path);

    this->media_file = file_path;
    // Find audio and video streams.
    this->format_ctx = avformat_alloc_context();
    int format_open_result = avformat_open_input(&format_ctx, file_path, nullptr, nullptr);
    if (format_open_result != 0) {
        LOGE("Format open file fail: %d", format_open_result);
        return OPT_FAIL;
    }
    int stream_find_result = avformat_find_stream_info(format_ctx, nullptr);
    if (stream_find_result < 0) {
        LOGE("Format find stream error: %d", stream_find_result);
        return OPT_FAIL;
    }
    // const AVCodec *videoCodec;
//    int videoStreamId = av_find_best_stream(format_ctx, AVMEDIA_TYPE_VIDEO, -1, -1, &videoCodec, 0);
//    LOGD("Find best video stream id: %d", videoStreamId);
//    const AVCodec *audioCodec;
//    int audioStreamId = av_find_best_stream(format_ctx, AVMEDIA_TYPE_AUDIO, -1, -1, &audioCodec, 0);
//    LOGD("Find best audio stream id: %d", audioStreamId);
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
    if (video_stream != nullptr) {
        AVStream *stream = video_stream;

        AVCodecParameters *params = stream->codecpar;
        video_width = params->width;
        video_height = params->height;

        // find decoder
        bool useHwDecoder = true;
        const char * mediacodecName;
        switch (params->codec_id) {
            case AV_CODEC_ID_H264:
                mediacodecName = "h264_mediacodec";
                break;
            case AV_CODEC_ID_HEVC:
                mediacodecName = "hevc_mediacodec";
                break;
            default:
                useHwDecoder = false;
                LOGE("format(%d) not support hw decode, maybe rebuild ffmpeg so", params->codec_id);
                break;
        }

        const AVCodec * mVideoCodec = nullptr;
        AVBufferRef *mHwDeviceCtx = nullptr;
        if (useHwDecoder) {
            AVHWDeviceType type = av_hwdevice_find_type_by_name("mediacodec");
            if (type == AV_HWDEVICE_TYPE_NONE) {
                while ((type = av_hwdevice_iterate_types(type)) != AV_HWDEVICE_TYPE_NONE) {
                    LOGD("av_hwdevice_iterate_types: %d", type);
                }
            }

            const AVCodec *mediacodec = avcodec_find_decoder_by_name(mediacodecName);
            if (mediacodec) {
                LOGD("Find %s", mediacodecName);
                for (int i = 0; ; ++i) {
                    const AVCodecHWConfig *config = avcodec_get_hw_config(mediacodec, i);
                    if (!config) {
                        LOGE("Decoder: %s does not support device type: %s", mediacodec->name,
                             av_hwdevice_get_type_name(type));
                        break;
                    }
                    if (config->methods & AV_CODEC_HW_CONFIG_METHOD_HW_DEVICE_CTX && config->device_type == type) {
                        // AV_PIX_FMT_MEDIACODEC(165)
                        hw_pix_fmt = config->pix_fmt;
                        LOGE("Decoder: %s support device type: %s, hw_pix_fmt: %d, AV_PIX_FMT_MEDIACODEC: %d", mediacodec->name,
                             av_hwdevice_get_type_name(type), hw_pix_fmt, AV_PIX_FMT_MEDIACODEC);
                        break;
                    }
                }

                if (hw_pix_fmt == AV_PIX_FMT_NONE) {
                    LOGE("not use surface decoding");
                    mVideoCodec = avcodec_find_decoder(params->codec_id);
                } else {
                    mVideoCodec = mediacodec;
                    int ret = av_hwdevice_ctx_create(&mHwDeviceCtx, type, nullptr, nullptr, 0);
                    if (ret != 0) {
                        LOGE("av_hwdevice_ctx_create err: %d", ret);
                    }
                }
            } else {
                LOGE("not find %s", mediacodecName);
                mVideoCodec = avcodec_find_decoder(params->codec_id);
            }
        } else {
            mVideoCodec = avcodec_find_decoder(params->codec_id);
        }

        if (mVideoCodec == nullptr) {
//        std::string msg = "not find decoder";
//        if (mErrorMsgListener) {
//            mErrorMsgListener(-1000, msg);
//        }
            return OPT_FAIL;
        }

        // init codec context
        video_decoder_ctx = avcodec_alloc_context3(mVideoCodec);
        if (!video_decoder_ctx) {
//        std::string msg = "codec context alloc failed";
//        if (mErrorMsgListener) {
//            mErrorMsgListener(-2000, msg);
//        }
            return OPT_FAIL;
        }
        avcodec_parameters_to_context(video_decoder_ctx, params);

        if (mHwDeviceCtx) {
            video_decoder_ctx->get_format = get_hw_format;
            video_decoder_ctx->hw_device_ctx = av_buffer_ref(mHwDeviceCtx);

//        if (mSurface != nullptr) {
//            mMediaCodecContext = av_mediacodec_alloc_context();
//            av_mediacodec_default_init(mCodecContext, mMediaCodecContext, mSurface);
//        }
        }

        // open codec
        int ret = avcodec_open2(video_decoder_ctx, mVideoCodec, nullptr);
        if (ret != 0) {
//        std::string msg = "codec open failed";
//        if (mErrorMsgListener) {
//            mErrorMsgListener(-3000, msg);
//        }
            return OPT_FAIL;
        }
        this->sws_ctx = sws_getContext(
                video_width, video_height, video_decoder_ctx->pix_fmt,
                video_width, video_height, AV_PIX_FMT_RGBA,
                SWS_BICUBIC, nullptr, nullptr, nullptr
        );
        this->video_decoder = mVideoCodec;
        this->native_window_buffer = new ANativeWindow_Buffer;
    }

    // Audio decode
    if (audio_stream != nullptr) {
        this->audio_decoder = avcodec_find_decoder(audio_stream->codecpar->codec_id);
        if (audio_decoder == nullptr) {
            LOGE("%s", "Do not find audio decoder");
            return OPT_FAIL;
        } else {
            this->audio_decoder_ctx = avcodec_alloc_context3(audio_decoder);
        }

        if (avcodec_parameters_to_context(audio_decoder_ctx, audio_stream->codecpar) < 0) {
            LOGE("Set audio stream params fail");
            return OPT_FAIL;
        }
        if (avcodec_open2(audio_decoder_ctx, audio_decoder, nullptr) < 0) {
            LOGE("Open audio decoder fail");
            return OPT_FAIL;
        }

        long a_duration = audio_stream->duration * 1000 / audio_stream->time_base.den;
        if (a_duration > duration) {
            this->duration = a_duration;
        }

        audio_channels = av_get_channel_layout_nb_channels(audio_decoder_ctx->channel_layout);
        audio_pre_sample_bytes = av_get_bytes_per_sample(audio_decoder_ctx->sample_fmt);
        audio_simple_rate = audio_decoder_ctx->sample_rate;

        LOGD("Audio channel size: %d, simple size: %d, simple rate: %d", audio_channels, audio_pre_sample_bytes, audio_simple_rate);
        swr_ctx = swr_alloc();
        swr_alloc_set_opts(swr_ctx, AUDIO_OUTPUT_CH_LAYOUT, AUDIO_OUTPUT_SAMPLE_FMT, AUDIO_OUTPUT_SAMPLE_RATE,
                           audio_decoder_ctx->channel_layout, audio_decoder_ctx->sample_fmt,
                           audio_decoder_ctx->sample_rate, 0,nullptr);
        if (0 > swr_init(swr_ctx)) {
            return OPT_FAIL;
        }

        // OpenSL ES
        // Cte engine
        SLresult sl_result;
        sl_result = slCreateEngine(&sl_engine_object, 0, NULL, 0, NULL, NULL);
        if (sl_result != SL_RESULT_SUCCESS) {
            return OPT_FAIL;
        }
        sl_result = (*sl_engine_object)->Realize(sl_engine_object, SL_BOOLEAN_FALSE);
        if (sl_result != SL_RESULT_SUCCESS) {
            return OPT_FAIL;
        }
        sl_result = (*sl_engine_object)->GetInterface(sl_engine_object, SL_IID_ENGINE, &sl_engine_engine);
        if (sl_result != SL_RESULT_SUCCESS) {
            return OPT_FAIL;
        }

        // Create Mix
        const SLInterfaceID ids[1] = {SL_IID_ENVIRONMENTALREVERB};
        const SLboolean req[1] = {SL_BOOLEAN_FALSE};
        sl_result = (*sl_engine_engine)->CreateOutputMix(sl_engine_engine, &sl_output_mix_object, 1, ids, req);
        if (sl_result != SL_RESULT_SUCCESS) {
            return OPT_FAIL;
        }
        sl_result = (*sl_output_mix_object)->Realize(sl_output_mix_object, SL_BOOLEAN_FALSE);
        if (sl_result != SL_RESULT_SUCCESS) {
            return OPT_FAIL;
        }
//        sl_result = (*sl_output_mix_object)->GetInterface(sl_output_mix_object, SL_IID_ENVIRONMENTALREVERB,
//                                                  &sl_output_mix_rev);
//        if (SL_RESULT_SUCCESS == sl_result) {
//            SLEnvironmentalReverbSettings reverbSettings =
//                    SL_I3DL2_ENVIRONMENT_PRESET_STONECORRIDOR;
//            (*sl_output_mix_rev)->SetEnvironmentalReverbProperties(
//                    sl_output_mix_rev, &reverbSettings);
//        }

        // Create DataSrc/DataSink
        SLDataLocator_AndroidSimpleBufferQueue sl_buffer_queue = {SL_DATALOCATOR_ANDROIDSIMPLEBUFFERQUEUE, 10};
        SLDataFormat_PCM format_pcm = {SL_DATAFORMAT_PCM, 2, SL_SAMPLINGRATE_44_1,
                                       SL_PCMSAMPLEFORMAT_FIXED_32, SL_PCMSAMPLEFORMAT_FIXED_32,
                                       SL_SPEAKER_FRONT_LEFT | SL_SPEAKER_FRONT_RIGHT, SL_BYTEORDER_LITTLEENDIAN};

        SLDataSource audioSrc = {&sl_buffer_queue, &format_pcm};
        SLDataLocator_OutputMix loc_outmix = {SL_DATALOCATOR_OUTPUTMIX, sl_output_mix_object};
        SLDataSink audioSnk = {&loc_outmix, NULL};


        const SLInterfaceID sl_ids[3] = {SL_IID_BUFFERQUEUE, SL_IID_VOLUME, SL_IID_EFFECTSEND,
                /*SL_IID_MUTESOLO,*/};
        const SLboolean sl_req[3] = {SL_BOOLEAN_TRUE, SL_BOOLEAN_TRUE, SL_BOOLEAN_TRUE,
                /*SL_BOOLEAN_TRUE,*/ };

        // Create Player
        sl_result = (*sl_engine_engine)->CreateAudioPlayer(sl_engine_engine, &sl_player_object, &audioSrc, &audioSnk, 2, sl_ids, sl_req);
        if (sl_result != SL_RESULT_SUCCESS) {
            return OPT_FAIL;
        }
        sl_result = (*sl_player_object)->Realize(sl_player_object, SL_BOOLEAN_FALSE);
        if (sl_result != SL_RESULT_SUCCESS) {
            return OPT_FAIL;
        }
        sl_result = (*sl_player_object)->GetInterface(sl_player_object, SL_IID_PLAY, &sl_player_play);
        if (sl_result != SL_RESULT_SUCCESS) {
            return OPT_FAIL;
        }
        sl_result = (*sl_player_object)->GetInterface(sl_player_object, SL_IID_BUFFERQUEUE,
                                                 &sl_player_buffer_queue);
        (*sl_player_play)->SetPlayState(sl_player_play, SL_PLAYSTATE_PLAYING);
        LOGD("Create SL success.");
        if (sl_result != SL_RESULT_SUCCESS) {
            return OPT_FAIL;
        }

    }
    if (video_stream == nullptr && audio_stream == nullptr) {
        return OPT_FAIL;
    } else {
//        pthread_t t;
//        pthread_create(&t, nullptr, decode_test, this);
//        decode_test(this);
        return OPT_SUCCESS;
    }
}

PLAYER_OPT_RESULT MediaPlayerContext::set_window(ANativeWindow *native_window_l) {
    this->native_window = native_window_l;
    return OPT_SUCCESS;
}

PLAYER_OPT_RESULT MediaPlayerContext::reset_play_progress() {
    if (video_stream != nullptr) {
        av_seek_frame(format_ctx, video_stream->index, 0, AVSEEK_FLAG_BACKWARD);
    }
    if (audio_stream != nullptr) {
        av_seek_frame(format_ctx, audio_stream->index, 0, AVSEEK_FLAG_BACKWARD);
    }
    return OPT_SUCCESS;
}

bool skipReadPkt = false;
DECODE_FRAME_RESULT MediaPlayerContext::decode_next_frame(JNIEnv* jniEnv, jobject jplayer, RenderRawData* render_data) {

    if (pkt != nullptr &&
        frame != nullptr &&
        format_ctx != nullptr &&
        render_data != nullptr) {

        int result;

        if (!skipReadPkt) {
            // 1. read pkt.
            av_packet_unref(pkt);
            int read_frame_result = av_read_frame(format_ctx, pkt);
            if (read_frame_result < 0) {
                LOGD("%s", "Decode video read frame result.");
                return DECODE_FRAME_FINISHED;
            }
        }
        skipReadPkt = false;

        // decode video
        if (video_decoder_ctx != nullptr &&
            video_decoder != nullptr &&
            video_stream != nullptr &&
            pkt->stream_index == video_stream->index) {

            long decode_frame_start = get_time_millis();
            // 2. send pkt to decoder.
            result = avcodec_send_packet(video_decoder_ctx, pkt);
            if (result == AVERROR(EAGAIN)) {
                LOGD("Decode video skip read pkt");
                skipReadPkt = true;
            } else {
                skipReadPkt = false;
            }
            if (result != 0 && !skipReadPkt) {
                return DECODE_FRAME_FAIL;
            }

            av_frame_unref(frame);

            // 3. receive frame
            result = avcodec_receive_frame(video_decoder_ctx, frame);
            if (result == AVERROR(EAGAIN)) {
                return decode_next_frame(jniEnv, jplayer, render_data);
            }
            if (result < 0) {
                LOGE("%s", "Decode video frame fail");
                return DECODE_FRAME_FAIL;
            }
            // 4.scale
            int scale_width = sws_scale(sws_ctx, frame->data, frame->linesize, 0, frame->height, render_data->video_data->rgba_frame->data, render_data->video_data->rgba_frame->linesize);
            if (scale_width == frame->height) {
//                int size = frame->width * frame->height * 4;
//                jint jWidth = frame->width;
//                jint jHeight = frame->height;
//                jbyteArray jFrame = jniEnv->NewByteArray(size);
//                jniEnv->SetByteArrayRegion(jFrame, 0, size,reinterpret_cast<const jbyte *>(render_data->video_data->rgba_frame_buffer));
//                jniEnv->CallVoidMethod(jplayer,jniEnv->GetMethodID(jniEnv->GetObjectClass(jplayer), "onNewVideoFrame", "(II[B)V"), jWidth, jHeight, jFrame);
            } else {
                LOGE("Decode video scale fail: %d", scale_width);
            }
            int64_t pts_millis = frame->pts * 1000 / video_time_den;
            LOGD("Decode video frame success: %lld, time cost: %ld", pts_millis, get_time_millis() - decode_frame_start);
            render_data->video_data->pts = pts_millis;
            render_data->is_video = true;
            return DECODE_FRAME_SUCCESS;
        }

        // decode audio
        if (audio_decoder_ctx != nullptr &&
            audio_decoder != nullptr &&
            audio_stream != nullptr &&
            pkt->stream_index == audio_stream->index) {
            render_data->is_video = false;

            long decode_frame_start = get_time_millis();

            int send_pkg_result = avcodec_send_packet(audio_decoder_ctx, pkt);
            if (send_pkg_result < 0 && send_pkg_result != AVERROR(EAGAIN)) {
                LOGE("Decode video send pkt fail: %d", send_pkg_result);
                return DECODE_FRAME_FAIL;
            }

            int buffer_count = 0;
            AudioBuffer **audio_buffers = nullptr;
            int64_t pts_millis = 0;
            while (true) {
                int receive_frame_result = avcodec_receive_frame(audio_decoder_ctx, frame);
                if (receive_frame_result < 0) {
                    break;
                }
                int output_nb_samples = av_rescale_rnd(frame->nb_samples, AUDIO_OUTPUT_SAMPLE_RATE, audio_decoder_ctx->sample_rate, AV_ROUND_UP);
                int audio_buffer_size = av_samples_get_buffer_size(nullptr, 2, output_nb_samples, AUDIO_OUTPUT_SAMPLE_FMT, 1);
                unsigned char *buffer = static_cast<unsigned char *>(malloc(audio_buffer_size));
                int convert_result = swr_convert(swr_ctx, &buffer, output_nb_samples,(const uint8_t **) frame->data, frame->nb_samples);
                if (convert_result < 0) {
                    free(buffer);
                    break;
                }
                AudioBuffer *audio_buffer = new AudioBuffer;
                audio_buffer->buffer_size = audio_buffer_size;
                audio_buffer->buffer = buffer;
                if (audio_buffers == nullptr) {
                    audio_buffers = static_cast<AudioBuffer **>(malloc(sizeof(audio_buffer)));
                    pts_millis = frame->pts * 1000 / audio_stream->time_base.den;
                } else {
                    audio_buffers = static_cast<AudioBuffer **>(realloc(audio_buffers,
                                                                        (buffer_count + 1) *
                                                                        sizeof(audio_buffer)));
                }
                audio_buffers[buffer_count] = audio_buffer;
                buffer_count ++;
            }
            if (buffer_count <= 0) {
                return DECODE_FRAME_FAIL;
            }
            auto audio_data = render_data->audio_data;
            audio_data->pts = pts_millis;
            audio_data->buffer_count = buffer_count;
            audio_data->buffers = audio_buffers;
            LOGD("Decode audio -> pts: %lld , time cost: %ld ms, buffer count: %d", pts_millis, get_time_millis() - decode_frame_start, buffer_count);
            return DECODE_FRAME_SUCCESS;
        }
        LOGD("Unknown pkt.");
        return DECODE_FRAME_FAIL;
    } else {
        LOGE("Decode video fail, video_stream, video_decoder and context is null.");
        return DECODE_FRAME_FAIL;
    }

}

PLAYER_OPT_RESULT MediaPlayerContext::render_raw_data(RenderRawData* raw_data) {
    if (raw_data->is_video) {
        auto window = native_window;
        if (window != nullptr) {
            try {
                if (ANativeWindow_setBuffersGeometry(window, video_width, video_height, WINDOW_FORMAT_RGBA_8888) != 0) {
                    return OPT_FAIL;
                }
                auto window_buffer = native_window_buffer;
                auto rgba_buffer = raw_data->video_data->rgba_frame_buffer;
                auto rgba_frame = raw_data->video_data->rgba_frame;
                if (ANativeWindow_lock(window, window_buffer, nullptr) != 0) {
                    return OPT_FAIL;
                }
                auto bits = (uint8_t*) window_buffer->bits;
                for (int h = 0; h < video_height; h++) {
                    memcpy(bits + h * window_buffer->stride * 4,
                           rgba_buffer + h * rgba_frame->linesize[0],
                           rgba_frame->linesize[0]);
                }
                if (ANativeWindow_unlockAndPost(window) < 0) {
                    return OPT_FAIL;
                }
                return OPT_SUCCESS;
            } catch (...) {
                LOGE("%s", "Render frame error.");
                return OPT_FAIL;
            }
        } else {
            LOGD("%s", "Native window is null, skip render.");
            return OPT_SUCCESS;
        }
    } else {
        if (raw_data->audio_data != nullptr && raw_data->audio_data->buffer_count > 0 && raw_data->audio_data->buffers != nullptr) {
            for (int i = 0; i < raw_data->audio_data->buffer_count; i ++) {
                auto buffer = raw_data->audio_data->buffers[i];
                SLresult result = (*sl_player_buffer_queue)->Enqueue(sl_player_buffer_queue, buffer->buffer, buffer->buffer_size);
                if (result != SL_RESULT_SUCCESS) {
                    LOGE("Render audio error: %d", result);
                }
                free(buffer);
            }
            free(raw_data->audio_data->buffers);
            raw_data->audio_data->buffer_count = 0;
            raw_data->audio_data->buffers = nullptr;
        }
        return OPT_SUCCESS;
    }
}

void MediaPlayerContext::release_media_player() {
    // Common release.
    if (pkt != nullptr) {
        av_packet_unref(pkt);
        av_packet_free(&pkt);
    }
    if (format_ctx != nullptr) {
        avformat_close_input(&format_ctx);
        avformat_free_context(format_ctx);
    }
    if (frame != nullptr) {
        av_frame_free(&frame);
    }

    // Video Release.

    if (video_decoder_ctx != nullptr) {
        avcodec_close(video_decoder_ctx);
        avcodec_free_context(&video_decoder_ctx);
    }

    if (native_window != nullptr) {
        ANativeWindow_release(native_window);
    }

    if (native_window_buffer != nullptr) {
        free(native_window_buffer);
    }

    if (sws_ctx != nullptr) {
        sws_freeContext(sws_ctx);
    }

    // Audio free.
    if (audio_decoder_ctx != nullptr) {
        avcodec_close(audio_decoder_ctx);
        avcodec_free_context(&audio_decoder_ctx);
    }

    if (swr_ctx != nullptr) {
        swr_free(&swr_ctx);
    }
    (*sl_player_buffer_queue)->Clear(sl_player_buffer_queue);
    (*sl_player_object)->Destroy(sl_player_object);
    (*sl_output_mix_object)->Destroy(sl_output_mix_object);
    (*sl_engine_object)->Destroy(sl_engine_object);
    free(this);
    LOGD("%s", "Release media player");
}


RenderRawData* MediaPlayerContext::new_render_raw_data() {
    auto raw_data = new RenderRawData;
    auto video_data = new RenderVideoRawData;

    // Video
    video_data->rgba_frame = av_frame_alloc();
    int buffer_size = av_image_get_buffer_size(AV_PIX_FMT_RGBA, video_width, video_height,1);
    video_data->rgba_frame_buffer = static_cast<uint8_t *>(av_malloc(
            buffer_size * sizeof(uint8_t)));
    av_image_fill_arrays(video_data->rgba_frame->data, video_data->rgba_frame->linesize, video_data->rgba_frame_buffer,
                         AV_PIX_FMT_RGBA, video_width, video_height, 1);
    raw_data->video_data = video_data;

    // Audio
    raw_data->audio_data = new RenderAudioRawData;
    LOGD("Create RAW Data: %ld", raw_data);
    return raw_data;
}

void MediaPlayerContext::release_render_raw_data(RenderRawData* render_data) {
    auto video = render_data->video_data;
    if (video != nullptr) {
        av_frame_free(&video->rgba_frame);
        av_free(video->rgba_frame_buffer);
        free(video);
    }
    auto audio = render_data->audio_data;
    if (audio != nullptr) {
        free(audio);
    }
    LOGD("Release RAW Data: %ld", render_data);
    free(render_data);
}