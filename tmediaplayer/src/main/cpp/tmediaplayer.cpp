#include <jni.h>
#include <string>
extern "C" {
#include <libavformat/avformat.h>
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_tans_tmediaplayer_NativeLib_stringFromJNI(
        JNIEnv* env,
        jobject /* this */) {
    AVFormatContext* context = avformat_alloc_context();
    std::string hello = context->codec_whitelist;
    return env->NewStringUTF(hello.c_str());
}