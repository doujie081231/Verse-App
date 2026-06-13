#include <jni.h>
#include <string>
#include <android/log.h>
#include <android/native_window.h>
#include <android/native_window_jni.h>
#include <cstring>

#define LOG_TAG "GLFWBridge"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

static ANativeWindow* g_window = nullptr;
static int g_windowWidth = 0;
static int g_windowHeight = 0;
static bool g_windowCreated = false;
static std::string g_basePath;
static std::string g_gameDir;

extern "C" {

JNIEXPORT void JNICALL
Java_com_verse_explorer_launcher_bridge_GLFWBridge_nativeOnWindowCreate(
    JNIEnv *env, jobject thiz, jobject surface, jint width, jint height) {
    ANativeWindow* newWindow = ANativeWindow_fromSurface(env, surface);
    ANativeWindow* oldWindow = g_window;
    g_window = newWindow;
    g_windowWidth = width;
    g_windowHeight = height;
    g_windowCreated = true;
    if (oldWindow) {
        ANativeWindow_release(oldWindow);
    }
    LOGI("GLFW window reference set: %dx%d", width, height);
}

JNIEXPORT void JNICALL
Java_com_verse_explorer_launcher_bridge_GLFWBridge_nativeOnWindowResize(
    JNIEnv *env, jobject thiz, jobject surface, jint width, jint height) {
    g_windowWidth = width;
    g_windowHeight = height;
    ANativeWindow* newWindow = ANativeWindow_fromSurface(env, surface);
    ANativeWindow* oldWindow = g_window;
    g_window = newWindow;
    if (oldWindow) {
        ANativeWindow_release(oldWindow);
    }
    LOGI("GLFW window reference resized: %dx%d", width, height);
}

JNIEXPORT void JNICALL
Java_com_verse_explorer_launcher_bridge_GLFWBridge_nativeOnWindowClose(
    JNIEnv *env, jobject thiz) {
    ANativeWindow* oldWindow = g_window;
    g_window = nullptr;
    g_windowCreated = false;
    if (oldWindow) {
        ANativeWindow_release(oldWindow);
    }
    LOGI("GLFW window reference released");
}

JNIEXPORT void JNICALL
Java_com_verse_explorer_launcher_bridge_GLFWBridge_nativeSetPath(
    JNIEnv *env, jobject thiz, jstring path) {
    const char *p = env->GetStringUTFChars(path, nullptr);
    g_basePath = p;
    env->ReleaseStringUTFChars(path, p);
    LOGI("Base path set: %s", g_basePath.c_str());
}

JNIEXPORT void JNICALL
Java_com_verse_explorer_launcher_bridge_GLFWBridge_nativeSetGameDir(
    JNIEnv *env, jobject thiz, jstring path) {
    const char *p = env->GetStringUTFChars(path, nullptr);
    g_gameDir = p;
    env->ReleaseStringUTFChars(path, p);
    LOGI("Game dir set: %s", g_gameDir.c_str());
}

JNIEXPORT void JNICALL
Java_com_verse_explorer_launcher_bridge_GLFWBridge_nativeSetSurface(
    JNIEnv *env, jobject thiz, jobject surface) {
    ANativeWindow* oldWindow = g_window;
    g_window = nullptr;
    if (oldWindow) {
        ANativeWindow_release(oldWindow);
    }
    if (surface) {
        g_window = ANativeWindow_fromSurface(env, surface);
        if (g_window) {
            g_windowWidth = ANativeWindow_getWidth(g_window);
            g_windowHeight = ANativeWindow_getHeight(g_window);
            LOGI("GLFW surface reference set: %dx%d", g_windowWidth, g_windowHeight);
        }
    }
}

JNIEXPORT jboolean JNICALL
Java_com_verse_explorer_launcher_bridge_GLFWBridge_nativeInitGLFW(
    JNIEnv *env, jobject thiz) {
    LOGI("GLFW initialized (bridge mode - EGL delegated to glfw_hook)");
    return JNI_TRUE;
}

JNIEXPORT void JNICALL
Java_com_verse_explorer_launcher_bridge_GLFWBridge_nativeTerminateGLFW(
    JNIEnv *env, jobject thiz) {
    ANativeWindow* oldWindow = g_window;
    g_window = nullptr;
    g_windowCreated = false;
    if (oldWindow) {
        ANativeWindow_release(oldWindow);
    }
    LOGI("GLFW terminated");
}

}
