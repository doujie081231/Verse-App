#include <jni.h>
#include <string>
#include <android/log.h>
#include <android/native_window.h>
#include <android/native_window_jni.h>
#include <cstring>

#define LOG_TAG "VerseBridge"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

static ANativeWindow* g_bridgeWindow = nullptr;
static int g_bridgeWidth = 0;
static int g_bridgeHeight = 0;

extern "C" {

JNIEXPORT void JNICALL
Java_com_verse_explorer_launcher_bridge_ZLBridge_nativeSetupBridgeWindow(
    JNIEnv *env, jobject thiz, jobject surface, jint width, jint height) {
    if (g_bridgeWindow) {
        ANativeWindow_release(g_bridgeWindow);
        g_bridgeWindow = nullptr;
    }

    g_bridgeWindow = ANativeWindow_fromSurface(env, surface);
    g_bridgeWidth = width;
    g_bridgeHeight = height;

    if (g_bridgeWindow) {
        ANativeWindow_setBuffersGeometry(g_bridgeWindow, width, height, AHARDWAREBUFFER_FORMAT_R8G8B8A8_UNORM);
        LOGI("Bridge window created: %dx%d", width, height);
    } else {
        LOGE("Failed to create bridge window");
    }
}

JNIEXPORT void JNICALL
Java_com_verse_explorer_launcher_bridge_ZLBridge_nativeDestroyBridgeWindow(
    JNIEnv *env, jobject thiz) {
    if (g_bridgeWindow) {
        ANativeWindow_release(g_bridgeWindow);
        g_bridgeWindow = nullptr;
    }
    LOGI("Bridge window destroyed");
}

JNIEXPORT void JNICALL
Java_com_verse_explorer_launcher_bridge_ZLBridge_nativeSetWindowVisible(
    JNIEnv *env, jobject thiz, jboolean visible) {
    LOGI("Window visible: %s", visible ? "true" : "false");
}

JNIEXPORT void JNICALL
Java_com_verse_explorer_launcher_bridge_ZLBridge_nativeSetInputReady(
    JNIEnv *env, jobject thiz, jboolean ready) {
    LOGI("Input ready: %s", ready ? "true" : "false");
}

JNIEXPORT void JNICALL
Java_com_verse_explorer_launcher_bridge_ZLBridge_nativeRegisterInputCallbacks(
    JNIEnv *env, jobject thiz, jobject callback) {
}

JNIEXPORT void JNICALL
Java_com_verse_explorer_launcher_bridge_ZLBridge_nativeRequestScreenshot(
    JNIEnv *env, jobject thiz) {
}

}
