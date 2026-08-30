#include <jni.h>
#include <string>
#include <android/log.h>

#include "verse_compat.h"

#define LOG_TAG "VerseLauncher"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

extern "C" {

// 本库会被应用侧（ART）与游戏侧（JVM）各加载一份，借此区分两个 VM 并登记各自的类引用
JNIEXPORT jint JNI_OnLoad(JavaVM* vm, void* reserved) {
    LOGI("VerseLauncher native library loaded");
    verseOnVmLoad(vm);
    return JNI_VERSION_1_6;
}

JNIEXPORT void JNI_OnUnload(JavaVM* vm, void* reserved) {
    LOGI("VerseLauncher native library unloaded");
}

}
