#include <android/log.h>
#include "android/native_window.h"

#define LOG_TAG "tMediaPlayerNative"
#define LOGD(fmt, ...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, fmt, __VA_ARGS__)
#define LOGE(fmt, ...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, fmt, __VA_ARGS__)

#define INVALUE_INT -1

void setup_media_player(const char * file_path);

void set_window(ANativeWindow* native_window);

void release_media_player();