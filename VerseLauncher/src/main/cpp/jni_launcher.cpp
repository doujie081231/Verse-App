#include <jni.h>
#include <string>
#include <android/log.h>

#define LOG_TAG "VerseLauncher"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

extern "C" {

JNIEXPORT jint JNI_OnLoad(JavaVM* vm, void* reserved) {
    LOGI("VerseLauncher native library loaded");
    return JNI_VERSION_1_6;
}

JNIEXPORT void JNI_OnUnload(JavaVM* vm, void* reserved) {
    LOGI("VerseLauncher native library unloaded");
}

}
