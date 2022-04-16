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
    char * result;
    if (context == nullptr) {
        result = "Result is Nullptr";
    } else {
        result = "Result is not null";
    }
    return env->NewStringUTF(result);
}