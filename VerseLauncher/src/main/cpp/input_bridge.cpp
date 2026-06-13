#include <jni.h>
#include <android/log.h>

#define LOG_TAG "InputBridge"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)

extern "C" {

JNIEXPORT void JNICALL
Java_com_verse_explorer_launcher_bridge_InputBridge_nativeSendKeyEvent(
    JNIEnv* env, jobject thiz, jint keyCode, jboolean pressed) {
    
    LOGD("Key event: %d, pressed: %d", keyCode, pressed);
}

JNIEXPORT void JNICALL
Java_com_verse_explorer_launcher_bridge_InputBridge_nativeSendMouseEvent(
    JNIEnv* env, jobject thiz, jint button, jboolean pressed, jfloat x, jfloat y) {
    
    LOGD("Mouse event: button=%d, pressed=%d, x=%.1f, y=%.1f", button, pressed, x, y);
}

JNIEXPORT void JNICALL
Java_com_verse_explorer_launcher_bridge_InputBridge_nativeSendMouseDelta(
    JNIEnv* env, jobject thiz, jfloat dx, jfloat dy) {
    
    LOGD("Mouse delta: dx=%.1f, dy=%.1f", dx, dy);
}

JNIEXPORT void JNICALL
Java_com_verse_explorer_launcher_bridge_InputBridge_nativeSendTouchEvent(
    JNIEnv* env, jobject thiz, jint action, jfloat x, jfloat y, jint pointerId, jlong timestamp) {
    
    LOGD("Touch event: action=%d, x=%.1f, y=%.1f, pointer=%d", action, x, y, pointerId);
}

}
