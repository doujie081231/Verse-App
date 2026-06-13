#include <jni.h>
#include <android/log.h>

#define LOG_TAG "LWJGLoader"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)

extern "C" {

JNIEXPORT jint JNICALL
JNI_OnLoad(JavaVM* vm, void* reserved) {
    (void)vm;
    (void)reserved;
    LOGI("lwjgl stub loaded (methods are in pojavexec)");
    return JNI_VERSION_1_6;
}

}
