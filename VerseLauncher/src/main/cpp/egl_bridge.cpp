#include <jni.h>
#include <android/log.h>
#include <EGL/egl.h>
#include <GLES3/gl3.h>

#define LOG_TAG "EGLBridge"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

extern "C" {

JNIEXPORT jboolean JNICALL
Java_com_verse_explorer_launcher_bridge_EGLBridge_nativeInitialize(
    JNIEnv* env, jobject thiz, jobject surfaceObj, jint width, jint height) {
    
    LOGI("EGL Bridge native init: %dx%d", width, height);
    return JNI_TRUE;
}

JNIEXPORT void JNICALL
Java_com_verse_explorer_launcher_bridge_EGLBridge_nativeShutdown(
    JNIEnv* env, jobject thiz) {
    
    LOGI("EGL Bridge native shutdown");
}

JNIEXPORT jboolean JNICALL
Java_com_verse_explorer_launcher_bridge_EGLBridge_nativeSwapBuffers(
    JNIEnv* env, jobject thiz) {
    return JNI_TRUE;
}

JNIEXPORT jboolean JNICALL
Java_com_verse_explorer_launcher_bridge_EGLBridge_nativeMakeCurrent(
    JNIEnv* env, jobject thiz) {
    return JNI_TRUE;
}

}
