#include <jni.h>
#include <android/log.h>
#include <android/native_window.h>
#include <android/native_window_jni.h>
#include <cstring>

#define LOG_TAG "LWJGLBridge"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)

static ANativeWindow* window = nullptr;
static int windowWidth = 0;
static int windowHeight = 0;
static bool initialized = false;

extern "C" {

JNIEXPORT void JNICALL
Java_com_verse_explorer_launcher_bridge_LWJGLBridge_nativeInitialize(
    JNIEnv* env, jobject thiz, jobject surfaceObj, jint width, jint height) {

    ANativeWindow* newWindow = ANativeWindow_fromSurface(env, surfaceObj);
    ANativeWindow* oldWindow = window;
    window = newWindow;
    windowWidth = width;
    windowHeight = height;
    initialized = true;
    if (oldWindow) {
        ANativeWindow_release(oldWindow);
    }
    LOGI("LWJGL Bridge window reference set: %dx%d", width, height);
}

JNIEXPORT void JNICALL
Java_com_verse_explorer_launcher_bridge_LWJGLBridge_nativeShutdown(
    JNIEnv* env, jobject thiz) {

    ANativeWindow* oldWindow = window;
    window = nullptr;
    initialized = false;
    if (oldWindow) {
        ANativeWindow_release(oldWindow);
    }
    LOGI("LWJGL Bridge shutdown");
}

JNIEXPORT void JNICALL
Java_com_verse_explorer_launcher_bridge_LWJGLBridge_nativeSwapBuffers(
    JNIEnv* env, jobject thiz) {
}

JNIEXPORT void JNICALL
Java_com_verse_explorer_launcher_bridge_LWJGLBridge_nativePollEvents(
    JNIEnv* env, jobject thiz) {
}

JNIEXPORT void JNICALL
Java_com_verse_explorer_launcher_bridge_LWJGLBridge_nativeSetWindowTitle(
    JNIEnv* env, jobject thiz, jstring title) {
    const char* titleStr = env->GetStringUTFChars(title, nullptr);
    LOGI("Window title: %s", titleStr);
    env->ReleaseStringUTFChars(title, titleStr);
}

JNIEXPORT jintArray JNICALL
Java_com_verse_explorer_launcher_bridge_LWJGLBridge_nativeGetWindowSize(
    JNIEnv* env, jobject thiz) {
    jintArray result = env->NewIntArray(2);
    jint size[2] = {windowWidth, windowHeight};
    env->SetIntArrayRegion(result, 0, 2, size);
    return result;
}

}
