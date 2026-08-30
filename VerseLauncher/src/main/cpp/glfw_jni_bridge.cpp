#include <jni.h>
#include <android/log.h>
#include <cstring>
#include <cstdlib>
#include "glfw_hook.h"
#include "verse_compat.h"

#define LOG_TAG "GLFW_JNI_Bridge"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

extern "C" {

JNIEXPORT jint JNICALL
Java_org_lwjgl_glfw_GLFW_nglfwInit(JNIEnv* env, jclass cls) {
    return glfwInit();
}

JNIEXPORT void JNICALL
Java_org_lwjgl_glfw_GLFW_nglfwTerminate(JNIEnv* env, jclass cls) {
    glfwTerminate();
}

JNIEXPORT jlong JNICALL
Java_org_lwjgl_glfw_GLFW_nglfwCreateWindow(JNIEnv* env, jclass cls,
    jint width, jint height, jlong title, jlong monitor, jlong share) {
    const char* titleStr = title ? (const char*)(intptr_t)title : "Minecraft";
    GLFWmonitor* mon = (GLFWmonitor*)(intptr_t)monitor;
    GLFWwindow* shareWin = (GLFWwindow*)(intptr_t)share;
    GLFWwindow* win = glfwCreateWindow(width, height, titleStr, mon, shareWin);
    return (jlong)(intptr_t)win;
}

JNIEXPORT void JNICALL
Java_org_lwjgl_glfw_GLFW_nglfwDestroyWindow(JNIEnv* env, jclass cls, jlong window) {
    if (window) glfwDestroyWindow((GLFWwindow*)(intptr_t)window);
}

JNIEXPORT jint JNICALL
Java_org_lwjgl_glfw_GLFW_nglfwWindowShouldClose(JNIEnv* env, jclass cls, jlong window) {
    if (!window) return 1;
    return glfwWindowShouldClose((GLFWwindow*)(intptr_t)window);
}

JNIEXPORT void JNICALL
Java_org_lwjgl_glfw_GLFW_nglfwSetWindowShouldClose(JNIEnv* env, jclass cls, jlong window, jint value) {
    if (window) glfwSetWindowShouldClose((GLFWwindow*)(intptr_t)window, value);
}

JNIEXPORT void JNICALL
Java_org_lwjgl_glfw_GLFW_nglfwSetWindowTitle(JNIEnv* env, jclass cls, jlong window, jlong title) {
    if (window && title)
        glfwSetWindowTitle((GLFWwindow*)(intptr_t)window, (const char*)(intptr_t)title);
}

JNIEXPORT void JNICALL
Java_org_lwjgl_glfw_GLFW_nglfwGetWindowPos(JNIEnv* env, jclass cls, jlong window, jlong xpos, jlong ypos) {
    if (window && xpos && ypos) {
        int x = 0, y = 0;
        glfwGetWindowPos((GLFWwindow*)(intptr_t)window, &x, &y);
        *(int*)(intptr_t)xpos = x;
        *(int*)(intptr_t)ypos = y;
    }
}

JNIEXPORT void JNICALL
Java_org_lwjgl_glfw_GLFW_nglfwSetWindowPos(JNIEnv* env, jclass cls, jlong window, jint xpos, jint ypos) {
    if (window) glfwSetWindowPos((GLFWwindow*)(intptr_t)window, xpos, ypos);
}

JNIEXPORT void JNICALL
Java_org_lwjgl_glfw_GLFW_nglfwGetWindowSize(JNIEnv* env, jclass cls, jlong window, jlong width, jlong height) {
    if (window && width && height) {
        int w = 0, h = 0;
        glfwGetWindowSize((GLFWwindow*)(intptr_t)window, &w, &h);
        *(int*)(intptr_t)width = w;
        *(int*)(intptr_t)height = h;
    }
}

JNIEXPORT void JNICALL
Java_org_lwjgl_glfw_GLFW_nglfwSetWindowSize(JNIEnv* env, jclass cls, jlong window, jint width, jint height) {
    if (window) glfwSetWindowSize((GLFWwindow*)(intptr_t)window, width, height);
}

JNIEXPORT void JNICALL
Java_org_lwjgl_glfw_GLFW_nglfwGetFramebufferSize(JNIEnv* env, jclass cls, jlong window, jlong width, jlong height) {
    if (window && width && height) {
        int w = 0, h = 0;
        glfwGetFramebufferSize((GLFWwindow*)(intptr_t)window, &w, &h);
        *(int*)(intptr_t)width = w;
        *(int*)(intptr_t)height = h;
    }
}

JNIEXPORT void JNICALL
Java_org_lwjgl_glfw_GLFW_nglfwIconifyWindow(JNIEnv* env, jclass cls, jlong window) {
    if (window) glfwIconifyWindow((GLFWwindow*)(intptr_t)window);
}

JNIEXPORT void JNICALL
Java_org_lwjgl_glfw_GLFW_nglfwRestoreWindow(JNIEnv* env, jclass cls, jlong window) {
    if (window) glfwRestoreWindow((GLFWwindow*)(intptr_t)window);
}

JNIEXPORT void JNICALL
Java_org_lwjgl_glfw_GLFW_nglfwShowWindow(JNIEnv* env, jclass cls, jlong window) {
    if (window) glfwShowWindow((GLFWwindow*)(intptr_t)window);
}

JNIEXPORT void JNICALL
Java_org_lwjgl_glfw_GLFW_nglfwHideWindow(JNIEnv* env, jclass cls, jlong window) {
    if (window) glfwHideWindow((GLFWwindow*)(intptr_t)window);
}

JNIEXPORT void JNICALL
Java_org_lwjgl_glfw_GLFW_nglfwFocusWindow(JNIEnv* env, jclass cls, jlong window) {
    if (window) glfwFocusWindow((GLFWwindow*)(intptr_t)window);
}

JNIEXPORT jint JNICALL
Java_org_lwjgl_glfw_GLFW_nglfwGetWindowAttrib(JNIEnv* env, jclass cls, jlong window, jint attrib) {
    if (window) return glfwGetWindowAttrib((GLFWwindow*)(intptr_t)window, attrib);
    return 0;
}

JNIEXPORT void JNICALL
Java_org_lwjgl_glfw_GLFW_nglfwSetWindowAttrib(JNIEnv* env, jclass cls, jlong window, jint attrib, jint value) {
    if (window) glfwSetWindowAttrib((GLFWwindow*)(intptr_t)window, attrib, value);
}

JNIEXPORT jlong JNICALL
Java_org_lwjgl_glfw_GLFW_nglfwGetPrimaryMonitor(JNIEnv* env, jclass cls) {
    return (jlong)(intptr_t)glfwGetPrimaryMonitor();
}

JNIEXPORT jlong JNICALL
Java_org_lwjgl_glfw_GLFW_nglfwGetMonitors(JNIEnv* env, jclass cls, jlong count) {
    if (count) {
        int c = 0;
        GLFWmonitor** monitors = glfwGetMonitors(&c);
        *(int*)(intptr_t)count = c;
        return (jlong)(intptr_t)monitors;
    }
    return 0;
}

JNIEXPORT jlong JNICALL
Java_org_lwjgl_glfw_GLFW_nglfwGetVideoMode(JNIEnv* env, jclass cls, jlong monitor) {
    if (monitor) return (jlong)(intptr_t)glfwGetVideoMode((GLFWmonitor*)(intptr_t)monitor);
    return 0;
}

JNIEXPORT jlong JNICALL
Java_org_lwjgl_glfw_GLFW_nglfwGetVideoModes(JNIEnv* env, jclass cls, jlong monitor, jlong count) {
    if (monitor && count) {
        int c = 0;
        const GLFWvidmode* modes = glfwGetVideoModes((GLFWmonitor*)(intptr_t)monitor, &c);
        *(int*)(intptr_t)count = c;
        return (jlong)(intptr_t)modes;
    }
    return 0;
}

JNIEXPORT void JNICALL
Java_org_lwjgl_glfw_GLFW_nglfwGetMonitorPos(JNIEnv* env, jclass cls, jlong monitor, jlong xpos, jlong ypos) {
    if (monitor && xpos && ypos) {
        int x = 0, y = 0;
        glfwGetMonitorPos((GLFWmonitor*)(intptr_t)monitor, &x, &y);
        *(int*)(intptr_t)xpos = x;
        *(int*)(intptr_t)ypos = y;
    }
}

JNIEXPORT void JNICALL
Java_org_lwjgl_glfw_GLFW_nglfwGetMonitorPhysicalSize(JNIEnv* env, jclass cls, jlong monitor, jlong width, jlong height) {
    if (monitor && width && height) {
        int w = 0, h = 0;
        glfwGetMonitorPhysicalSize((GLFWmonitor*)(intptr_t)monitor, &w, &h);
        *(int*)(intptr_t)width = w;
        *(int*)(intptr_t)height = h;
    }
}

JNIEXPORT jlong JNICALL
Java_org_lwjgl_glfw_GLFW_nglfwGetMonitorName(JNIEnv* env, jclass cls, jlong monitor) {
    if (monitor) return (jlong)(intptr_t)glfwGetMonitorName((GLFWmonitor*)(intptr_t)monitor);
    return 0;
}

JNIEXPORT void JNICALL
Java_org_lwjgl_glfw_GLFW_nglfwGetMonitorContentScale(JNIEnv* env, jclass cls, jlong monitor, jlong xscale, jlong yscale) {
    if (monitor && xscale && yscale) {
        float xs = 1.0f, ys = 1.0f;
        glfwGetMonitorContentScale((GLFWmonitor*)(intptr_t)monitor, &xs, &ys);
        *(float*)(intptr_t)xscale = xs;
        *(float*)(intptr_t)yscale = ys;
    }
}

JNIEXPORT void JNICALL
Java_org_lwjgl_glfw_GLFW_nglfwGetMonitorWorkarea(JNIEnv* env, jclass cls, jlong monitor, jlong xpos, jlong ypos, jlong width, jlong height) {
    if (monitor && xpos && ypos && width && height) {
        int x = 0, y = 0, w = 0, h = 0;
        glfwGetMonitorWorkarea((GLFWmonitor*)(intptr_t)monitor, &x, &y, &w, &h);
        *(int*)(intptr_t)xpos = x;
        *(int*)(intptr_t)ypos = y;
        *(int*)(intptr_t)width = w;
        *(int*)(intptr_t)height = h;
    }
}

JNIEXPORT void JNICALL
Java_org_lwjgl_glfw_GLFW_nglfwSetGamma(JNIEnv* env, jclass cls, jlong monitor, jfloat gamma) {
    if (monitor) glfwSetGamma((GLFWmonitor*)(intptr_t)monitor, gamma);
}

JNIEXPORT jlong JNICALL
Java_org_lwjgl_glfw_GLFW_nglfwGetGammaRamp(JNIEnv* env, jclass cls, jlong monitor) {
    if (monitor) return (jlong)(intptr_t)glfwGetGammaRamp((GLFWmonitor*)(intptr_t)monitor);
    return 0;
}

JNIEXPORT void JNICALL
Java_org_lwjgl_glfw_GLFW_nglfwSetGammaRamp(JNIEnv* env, jclass cls, jlong monitor, jlong ramp) {
    if (monitor && ramp) glfwSetGammaRamp((GLFWmonitor*)(intptr_t)monitor, (const GLFWgammaramp*)(intptr_t)ramp);
}

JNIEXPORT void JNICALL
Java_org_lwjgl_glfw_GLFW_nglfwPollEvents(JNIEnv* env, jclass cls) {
    glfwPollEvents();
}

JNIEXPORT void JNICALL
Java_org_lwjgl_glfw_GLFW_nglfwWaitEvents(JNIEnv* env, jclass cls) {
    glfwWaitEvents();
}

JNIEXPORT void JNICALL
Java_org_lwjgl_glfw_GLFW_nglfwWaitEventsTimeout(JNIEnv* env, jclass cls, jdouble timeout) {
    glfwWaitEventsTimeout(timeout);
}

JNIEXPORT void JNICALL
Java_org_lwjgl_glfw_GLFW_nglfwPostEmptyEvent(JNIEnv* env, jclass cls) {
    glfwPostEmptyEvent();
}

JNIEXPORT jint JNICALL
Java_org_lwjgl_glfw_GLFW_nglfwGetInputMode(JNIEnv* env, jclass cls, jlong window, jint mode) {
    if (window) return glfwGetInputMode((GLFWwindow*)(intptr_t)window, mode);
    return 0;
}

JNIEXPORT void JNICALL
Java_org_lwjgl_glfw_GLFW_nglfwSetInputMode(JNIEnv* env, jclass cls, jlong window, jint mode, jint value) {
    if (window) glfwSetInputMode((GLFWwindow*)(intptr_t)window, mode, value);
}

JNIEXPORT jint JNICALL
Java_org_lwjgl_glfw_GLFW_nglfwRawMouseMotionSupported(JNIEnv* env, jclass cls) {
    return glfwRawMouseMotionSupported();
}

JNIEXPORT jlong JNICALL
Java_org_lwjgl_glfw_GLFW_nglfwGetKeyName(JNIEnv* env, jclass cls, jint key, jint scancode) {
    return (jlong)(intptr_t)glfwGetKeyName(key, scancode);
}

JNIEXPORT jint JNICALL
Java_org_lwjgl_glfw_GLFW_nglfwGetKeyScancode(JNIEnv* env, jclass cls, jint key) {
    return glfwGetKeyScancode(key);
}

JNIEXPORT jint JNICALL
Java_org_lwjgl_glfw_GLFW_nglfwGetKey(JNIEnv* env, jclass cls, jlong window, jint key) {
    if (window) return glfwGetKey((GLFWwindow*)(intptr_t)window, key);
    return 0;
}

JNIEXPORT jint JNICALL
Java_org_lwjgl_glfw_GLFW_nglfwGetMouseButton(JNIEnv* env, jclass cls, jlong window, jint button) {
    if (window) return glfwGetMouseButton((GLFWwindow*)(intptr_t)window, button);
    return 0;
}

JNIEXPORT void JNICALL
Java_org_lwjgl_glfw_GLFW_nglfwGetCursorPos(JNIEnv* env, jclass cls, jlong window, jobject xpos, jobject ypos) {
    double x = 0.0, y = 0.0;
    verseGetCursorPos(&x, &y);
    if (xpos) {
        double* p = (double*)env->GetDirectBufferAddress(xpos);
        if (p) *p = x;
    }
    if (ypos) {
        double* p = (double*)env->GetDirectBufferAddress(ypos);
        if (p) *p = y;
    }
}

JNIEXPORT void JNICALL
Java_org_lwjgl_glfw_GLFW_nglfwGetCursorPosA(JNIEnv* env, jclass cls, jlong window, jdoubleArray xpos, jdoubleArray ypos) {
    double x = 0.0, y = 0.0;
    verseGetCursorPos(&x, &y);
    if (xpos) env->SetDoubleArrayRegion(xpos, 0, 1, &x);
    if (ypos) env->SetDoubleArrayRegion(ypos, 0, 1, &y);
}

JNIEXPORT void JNICALL
Java_org_lwjgl_glfw_GLFW_glfwSetCursorPos(JNIEnv* env, jclass cls, jlong window, jdouble xpos, jdouble ypos) {
    verseSetCursorPosValue(xpos, ypos);
}

JNIEXPORT void JNICALL
Java_org_lwjgl_glfw_GLFW_nglfwSetShowingWindow(JNIEnv* env, jclass cls, jlong window) {
    verseSetShowingWindow((long)window);
}

JNIEXPORT void JNICALL
Java_org_lwjgl_glfw_GLFW_nglfwSetCursorPos(JNIEnv* env, jclass cls, jlong window, jdouble xpos, jdouble ypos) {
    if (window) glfwSetCursorPos((GLFWwindow*)(intptr_t)window, xpos, ypos);
}

JNIEXPORT jlong JNICALL
Java_org_lwjgl_glfw_GLFW_nglfwGetClipboardString(JNIEnv* env, jclass cls, jlong window) {
    if (window) return (jlong)(intptr_t)glfwGetClipboardString((GLFWwindow*)(intptr_t)window);
    return 0;
}

JNIEXPORT void JNICALL
Java_org_lwjgl_glfw_GLFW_nglfwSetClipboardString(JNIEnv* env, jclass cls, jlong window, jlong string) {
    if (window && string)
        glfwSetClipboardString((GLFWwindow*)(intptr_t)window, (const char*)(intptr_t)string);
}

JNIEXPORT jdouble JNICALL
Java_org_lwjgl_glfw_GLFW_nglfwGetTime(JNIEnv* env, jclass cls) {
    return glfwGetTime();
}

JNIEXPORT void JNICALL
Java_org_lwjgl_glfw_GLFW_nglfwSetTime(JNIEnv* env, jclass cls, jdouble time) {
    glfwSetTime(time);
}

JNIEXPORT void JNICALL
Java_org_lwjgl_glfw_GLFW_nglfwMakeContextCurrent(JNIEnv* env, jclass cls, jlong window) {
    glfwMakeContextCurrent(window ? (GLFWwindow*)(intptr_t)window : nullptr);
}

JNIEXPORT jlong JNICALL
Java_org_lwjgl_glfw_GLFW_nglfwGetCurrentContext(JNIEnv* env, jclass cls) {
    return (jlong)(intptr_t)glfwGetCurrentContext();
}

JNIEXPORT void JNICALL
Java_org_lwjgl_glfw_GLFW_nglfwSwapBuffers(JNIEnv* env, jclass cls, jlong window) {
    if (window) glfwSwapBuffers((GLFWwindow*)(intptr_t)window);
}

JNIEXPORT void JNICALL
Java_org_lwjgl_glfw_GLFW_nglfwSwapInterval(JNIEnv* env, jclass cls, jint interval) {
    glfwSwapInterval(interval);
}

JNIEXPORT jint JNICALL
Java_org_lwjgl_glfw_GLFW_nglfwExtensionSupported(JNIEnv* env, jclass cls, jlong extension) {
    if (extension)
        return glfwExtensionSupported((const char*)(intptr_t)extension);
    return 0;
}

JNIEXPORT jlong JNICALL
Java_org_lwjgl_glfw_GLFW_nglfwGetProcAddress(JNIEnv* env, jclass cls, jlong procname) {
    if (procname)
        return (jlong)(intptr_t)glfwGetProcAddress((const char*)(intptr_t)procname);
    return 0;
}

JNIEXPORT jint JNICALL
Java_org_lwjgl_glfw_GLFW_nglfwVulkanSupported(JNIEnv* env, jclass cls) {
    return glfwVulkanSupported();
}

JNIEXPORT jlong JNICALL
Java_org_lwjgl_glfw_GLFW_nglfwGetRequiredInstanceExtensions(JNIEnv* env, jclass cls, jlong count) {
    if (count) {
        uint32_t c = 0;
        const char** exts = glfwGetRequiredInstanceExtensions(&c);
        *(uint32_t*)(intptr_t)count = c;
        return (jlong)(intptr_t)exts;
    }
    return 0;
}

JNIEXPORT jint JNICALL
Java_org_lwjgl_glfw_GLFW_nglfwJoystickPresent(JNIEnv* env, jclass cls, jint jid) {
    return glfwJoystickPresent(jid);
}

JNIEXPORT jlong JNICALL
Java_org_lwjgl_glfw_GLFW_nglfwGetJoystickAxes(JNIEnv* env, jclass cls, jint jid, jlong count) {
    if (count) {
        int c = 0;
        const float* axes = glfwGetJoystickAxes(jid, &c);
        *(int*)(intptr_t)count = c;
        return (jlong)(intptr_t)axes;
    }
    return 0;
}

JNIEXPORT jlong JNICALL
Java_org_lwjgl_glfw_GLFW_nglfwGetJoystickButtons(JNIEnv* env, jclass cls, jint jid, jlong count) {
    if (count) {
        int c = 0;
        const unsigned char* btns = glfwGetJoystickButtons(jid, &c);
        *(int*)(intptr_t)count = c;
        return (jlong)(intptr_t)btns;
    }
    return 0;
}

JNIEXPORT jlong JNICALL
Java_org_lwjgl_glfw_GLFW_nglfwGetJoystickName(JNIEnv* env, jclass cls, jint jid) {
    return (jlong)(intptr_t)glfwGetJoystickName(jid);
}

JNIEXPORT jlong JNICALL
Java_org_lwjgl_glfw_GLFW_nglfwGetJoystickGUID(JNIEnv* env, jclass cls, jint jid) {
    return (jlong)(intptr_t)glfwGetJoystickGUID(jid);
}

JNIEXPORT jint JNICALL
Java_org_lwjgl_glfw_GLFW_nglfwGetJoystickHats(JNIEnv* env, jclass cls, jint jid, jlong hats) {
    if (hats) {
        int c = 0;
        glfwGetJoystickHats(jid, &c);
        *(int*)(intptr_t)hats = c;
    }
    return 0;
}

JNIEXPORT jint JNICALL
Java_org_lwjgl_glfw_GLFW_nglfwGetGamepadState(JNIEnv* env, jclass cls, jint jid, jlong state) {
    if (state) return glfwGetGamepadState(jid, (GLFWgamepadstate*)(intptr_t)state);
    return 0;
}

JNIEXPORT jlong JNICALL
Java_org_lwjgl_glfw_GLFW_nglfwSetErrorCallback(JNIEnv* env, jclass cls, jlong callback) {
    glfwSetErrorCallback((GLFWerrorfun)(intptr_t)callback);
    return 0;
}

JNIEXPORT jlong JNICALL
Java_org_lwjgl_glfw_GLFW_nglfwSetWindowPosCallback(JNIEnv* env, jclass cls, jlong window, jlong callback) {
    if (window)
        glfwSetWindowPosCallback((GLFWwindow*)(intptr_t)window, (GLFWwindowposfun)(intptr_t)callback);
    return 0;
}

JNIEXPORT jlong JNICALL
Java_org_lwjgl_glfw_GLFW_nglfwSetWindowSizeCallback(JNIEnv* env, jclass cls, jlong window, jlong callback) {
    return verseSetCallback(VERSE_CB_WINDOW_SIZE, callback);
}

JNIEXPORT jlong JNICALL
Java_org_lwjgl_glfw_GLFW_nglfwSetWindowCloseCallback(JNIEnv* env, jclass cls, jlong window, jlong callback) {
    if (window)
        glfwSetWindowCloseCallback((GLFWwindow*)(intptr_t)window, (GLFWwindowclosefun)(intptr_t)callback);
    return 0;
}

JNIEXPORT jlong JNICALL
Java_org_lwjgl_glfw_GLFW_nglfwSetWindowRefreshCallback(JNIEnv* env, jclass cls, jlong window, jlong callback) {
    if (window)
        glfwSetWindowRefreshCallback((GLFWwindow*)(intptr_t)window, (GLFWwindowrefreshfun)(intptr_t)callback);
    return 0;
}

JNIEXPORT jlong JNICALL
Java_org_lwjgl_glfw_GLFW_nglfwSetWindowFocusCallback(JNIEnv* env, jclass cls, jlong window, jlong callback) {
    if (window)
        glfwSetWindowFocusCallback((GLFWwindow*)(intptr_t)window, (GLFWwindowfocusfun)(intptr_t)callback);
    return 0;
}

JNIEXPORT jlong JNICALL
Java_org_lwjgl_glfw_GLFW_nglfwSetWindowIconifyCallback(JNIEnv* env, jclass cls, jlong window, jlong callback) {
    if (window)
        glfwSetWindowIconifyCallback((GLFWwindow*)(intptr_t)window, (GLFWwindowiconifyfun)(intptr_t)callback);
    return 0;
}

JNIEXPORT jlong JNICALL
Java_org_lwjgl_glfw_GLFW_nglfwSetWindowMaximizeCallback(JNIEnv* env, jclass cls, jlong window, jlong callback) {
    if (window)
        glfwSetWindowMaximizeCallback((GLFWwindow*)(intptr_t)window, (GLFWwindowmaximizefun)(intptr_t)callback);
    return 0;
}

JNIEXPORT jlong JNICALL
Java_org_lwjgl_glfw_GLFW_nglfwSetFramebufferSizeCallback(JNIEnv* env, jclass cls, jlong window, jlong callback) {
    return verseSetCallback(VERSE_CB_FRAMEBUFFER_SIZE, callback);
}

JNIEXPORT jlong JNICALL
Java_org_lwjgl_glfw_GLFW_nglfwSetWindowContentScaleCallback(JNIEnv* env, jclass cls, jlong window, jlong callback) {
    if (window)
        glfwSetWindowContentScaleCallback((GLFWwindow*)(intptr_t)window, (GLFWwindowcontentscalefun)(intptr_t)callback);
    return 0;
}

JNIEXPORT jlong JNICALL
Java_org_lwjgl_glfw_GLFW_nglfwSetKeyCallback(JNIEnv* env, jclass cls, jlong window, jlong callback) {
    return verseSetCallback(VERSE_CB_KEY, callback);
}

JNIEXPORT jlong JNICALL
Java_org_lwjgl_glfw_GLFW_nglfwSetCharCallback(JNIEnv* env, jclass cls, jlong window, jlong callback) {
    return verseSetCallback(VERSE_CB_CHAR, callback);
}

JNIEXPORT jlong JNICALL
Java_org_lwjgl_glfw_GLFW_nglfwSetCharModsCallback(JNIEnv* env, jclass cls, jlong window, jlong callback) {
    return verseSetCallback(VERSE_CB_CHAR_MODS, callback);
}

JNIEXPORT jlong JNICALL
Java_org_lwjgl_glfw_GLFW_nglfwSetMouseButtonCallback(JNIEnv* env, jclass cls, jlong window, jlong callback) {
    return verseSetCallback(VERSE_CB_MOUSE_BUTTON, callback);
}

JNIEXPORT jlong JNICALL
Java_org_lwjgl_glfw_GLFW_nglfwSetCursorPosCallback(JNIEnv* env, jclass cls, jlong window, jlong callback) {
    return verseSetCallback(VERSE_CB_CURSOR_POS, callback);
}

JNIEXPORT jlong JNICALL
Java_org_lwjgl_glfw_GLFW_nglfwSetCursorEnterCallback(JNIEnv* env, jclass cls, jlong window, jlong callback) {
    return verseSetCallback(VERSE_CB_CURSOR_ENTER, callback);
}

JNIEXPORT jlong JNICALL
Java_org_lwjgl_glfw_GLFW_nglfwSetScrollCallback(JNIEnv* env, jclass cls, jlong window, jlong callback) {
    return verseSetCallback(VERSE_CB_SCROLL, callback);
}

JNIEXPORT jlong JNICALL
Java_org_lwjgl_glfw_GLFW_nglfwSetDropCallback(JNIEnv* env, jclass cls, jlong window, jlong callback) {
    if (window)
        glfwSetDropCallback((GLFWwindow*)(intptr_t)window, (GLFWdropfun)(intptr_t)callback);
    return 0;
}

JNIEXPORT jlong JNICALL
Java_org_lwjgl_glfw_GLFW_nglfwSetMonitorCallback(JNIEnv* env, jclass cls, jlong callback) {
    return (jlong)(intptr_t)glfwSetMonitorCallback((GLFWmonitorfun)(intptr_t)callback);
}

JNIEXPORT jlong JNICALL
Java_org_lwjgl_glfw_GLFW_nglfwSetJoystickCallback(JNIEnv* env, jclass cls, jlong callback) {
    glfwSetJoystickCallback((GLFWjoystickfun)(intptr_t)callback);
    return 0;
}

JNIEXPORT void JNICALL
Java_org_lwjgl_glfw_GLFW_nglfwSetWindowIcon(JNIEnv* env, jclass cls, jlong window, jint count, jlong images) {
    if (window)
        glfwSetWindowIcon((GLFWwindow*)(intptr_t)window, count, (const GLFWimage*)(intptr_t)images);
}

JNIEXPORT jlong JNICALL
Java_org_lwjgl_glfw_GLFW_nglfwSetWindowMonitor(JNIEnv* env, jclass cls,
    jlong window, jlong monitor, jint xpos, jint ypos, jint width, jint height, jint refreshRate) {
    if (window)
        return (jlong)(intptr_t)glfwSetWindowMonitor(
            (GLFWwindow*)(intptr_t)window,
            monitor ? (GLFWmonitor*)(intptr_t)monitor : nullptr,
            xpos, ypos, width, height, refreshRate);
    return 0;
}

JNIEXPORT void JNICALL
Java_org_lwjgl_glfw_GLFW_nglfwRequestWindowAttention(JNIEnv* env, jclass cls, jlong window) {
}

JNIEXPORT void JNICALL
Java_org_lwjgl_glfw_GLFW_nglfwGetWindowFrameSize(JNIEnv* env, jclass cls,
    jlong window, jlong left, jlong top, jlong right, jlong bottom) {
    if (window && left && top && right && bottom) {
        int l = 0, t = 0, r = 0, b = 0;
        glfwGetWindowFrameSize((GLFWwindow*)(intptr_t)window, &l, &t, &r, &b);
        *(int*)(intptr_t)left = l;
        *(int*)(intptr_t)top = t;
        *(int*)(intptr_t)right = r;
        *(int*)(intptr_t)bottom = b;
    }
}

JNIEXPORT void JNICALL
Java_org_lwjgl_glfw_GLFW_nglfwInitAllocator(JNIEnv* env, jclass cls, jlong allocator) {
    LOGI("glfwInitAllocator called");
}

JNIEXPORT jlong JNICALL
Java_org_lwjgl_glfw_GLFW_nglfwSetMonitorUserPointer(JNIEnv* env, jclass cls, jlong monitor, jlong pointer) {
    return 0;
}

JNIEXPORT jlong JNICALL
Java_org_lwjgl_glfw_GLFW_nglfwGetMonitorUserPointer(JNIEnv* env, jclass cls, jlong monitor) {
    return 0;
}

JNIEXPORT void JNICALL
Java_org_lwjgl_glfw_GLFW_nglfwSetWindowUserPointer(JNIEnv* env, jclass cls, jlong window, jlong pointer) {
    if (window) glfwSetWindowUserPointer((GLFWwindow*)(intptr_t)window, (void*)(intptr_t)pointer);
}

JNIEXPORT jlong JNICALL
Java_org_lwjgl_glfw_GLFW_nglfwGetWindowUserPointer(JNIEnv* env, jclass cls, jlong window) {
    if (window) return (jlong)(intptr_t)glfwGetWindowUserPointer((GLFWwindow*)(intptr_t)window);
    return 0;
}

JNIEXPORT jint JNICALL
Java_org_lwjgl_glfw_GLFW_nglfwGetError(JNIEnv* env, jclass cls, jlong description) {
    const char* desc = nullptr;
    int code = glfwGetError(&desc);
    if (description) *(const char**)(intptr_t)description = desc;
    return code;
}

JNIEXPORT jlong JNICALL
Java_org_lwjgl_glfw_GLFW_nglfwGetVersionString(JNIEnv* env, jclass cls) {
    return (jlong)(intptr_t)"3.3.8 Verse Android EGL";
}

JNIEXPORT jlong JNICALL
Java_org_lwjgl_glfw_GLFW_nglfwGetJoystickUserPointer(JNIEnv* env, jclass cls, jint jid) {
    return 0;
}

JNIEXPORT void JNICALL
Java_org_lwjgl_glfw_GLFW_nglfwSetJoystickUserPointer(JNIEnv* env, jclass cls, jint jid, jlong pointer) {
}

JNIEXPORT void JNICALL
Java_org_lwjgl_glfw_GLFW_nglfwDefaultWindowHints(JNIEnv* env, jclass cls) {
    glfwDefaultWindowHints();
}

JNIEXPORT void JNICALL
Java_org_lwjgl_glfw_GLFW_nglfwWindowHint(JNIEnv* env, jclass cls, jint hint, jint value) {
    glfwWindowHint(hint, value);
}

JNIEXPORT void JNICALL
Java_org_lwjgl_glfw_GLFW_nglfwWindowHintString(JNIEnv* env, jclass cls, jint hint, jlong value) {
    if (value)
        glfwWindowHintString(hint, (const char*)(intptr_t)value);
}

JNIEXPORT jint JNICALL
Java_org_lwjgl_glfw_GLFW_nglfwGetPlatform(JNIEnv* env, jclass cls) {
    return glfwGetPlatform();
}

JNIEXPORT jlong JNICALL
Java_org_lwjgl_glfw_GLFW_nglfwGetTimerValue(JNIEnv* env, jclass cls) {
    return (jlong)glfwGetTimerValue();
}

JNIEXPORT jlong JNICALL
Java_org_lwjgl_glfw_GLFW_nglfwGetTimerFrequency(JNIEnv* env, jclass cls) {
    return (jlong)glfwGetTimerFrequency();
}

JNIEXPORT void JNICALL
Java_org_lwjgl_glfw_GLFW_nglfwGetWindowContentScale(JNIEnv* env, jclass cls, jlong window, jlong xscale, jlong yscale) {
    if (window) {
        glfwGetWindowContentScale((GLFWwindow*)(intptr_t)window, (float*)(intptr_t)xscale, (float*)(intptr_t)yscale);
    }
}

JNIEXPORT jlong JNICALL
Java_org_lwjgl_glfw_GLFW_nglfwCreateCursor(JNIEnv* env, jclass cls, jlong image, jint xhot, jint yhot) {
    if (image) return (jlong)(intptr_t)glfwCreateCursor((const GLFWimage*)(intptr_t)image, xhot, yhot);
    return 0;
}

JNIEXPORT jlong JNICALL
Java_org_lwjgl_glfw_GLFW_nglfwCreateStandardCursor(JNIEnv* env, jclass cls, jint shape) {
    return (jlong)(intptr_t)glfwCreateStandardCursor(shape);
}

JNIEXPORT void JNICALL
Java_org_lwjgl_glfw_GLFW_nglfwDestroyCursor(JNIEnv* env, jclass cls, jlong cursor) {
    if (cursor) glfwDestroyCursor((GLFWcursor*)(intptr_t)cursor);
}

JNIEXPORT void JNICALL
Java_org_lwjgl_glfw_GLFW_nglfwSetCursor(JNIEnv* env, jclass cls, jlong window, jlong cursor) {
    if (window) glfwSetCursor((GLFWwindow*)(intptr_t)window, cursor ? (GLFWcursor*)(intptr_t)cursor : nullptr);
}

JNIEXPORT void JNICALL
Java_org_lwjgl_glfw_GLFW_nglfwMaximizeWindow(JNIEnv* env, jclass cls, jlong window) {
    if (window) glfwMaximizeWindow((GLFWwindow*)(intptr_t)window);
}

JNIEXPORT void JNICALL
Java_org_lwjgl_glfw_GLFW_nglfwSetWindowSizeLimits(JNIEnv* env, jclass cls, jlong window, jint minW, jint minH, jint maxW, jint maxH) {
    if (window) glfwSetWindowSizeLimits((GLFWwindow*)(intptr_t)window, minW, minH, maxW, maxH);
}

JNIEXPORT void JNICALL
Java_org_lwjgl_glfw_GLFW_nglfwSetWindowAspectRatio(JNIEnv* env, jclass cls, jlong window, jint numer, jint denom) {
    if (window) glfwSetWindowAspectRatio((GLFWwindow*)(intptr_t)window, numer, denom);
}

JNIEXPORT jfloat JNICALL
Java_org_lwjgl_glfw_GLFW_nglfwGetWindowOpacity(JNIEnv* env, jclass cls, jlong window) {
    if (window) return glfwGetWindowOpacity((GLFWwindow*)(intptr_t)window);
    return 1.0f;
}

JNIEXPORT void JNICALL
Java_org_lwjgl_glfw_GLFW_nglfwSetWindowOpacity(JNIEnv* env, jclass cls, jlong window, jfloat opacity) {
    if (window) glfwSetWindowOpacity((GLFWwindow*)(intptr_t)window, opacity);
}

JNIEXPORT void JNICALL
Java_org_lwjgl_glfw_GLFW_nglfwInitHint(JNIEnv* env, jclass cls, jint hint, jint value) {
    glfwInitHint(hint, value);
}

JNIEXPORT void JNICALL
Java_org_lwjgl_glfw_GLFW_nglfwGetVersion(JNIEnv* env, jclass cls, jlong major, jlong minor, jlong rev) {
    glfwGetVersion((int*)(intptr_t)major, (int*)(intptr_t)minor, (int*)(intptr_t)rev);
}

JNIEXPORT jint JNICALL
Java_org_lwjgl_glfw_GLFW_nglfwUpdateGamepadMappings(JNIEnv* env, jclass cls, jlong string) {
    if (string) return glfwUpdateGamepadMappings((const char*)(intptr_t)string);
    return 0;
}

JNIEXPORT jlong JNICALL
Java_org_lwjgl_glfw_GLFW_nglfwGetGamepadName(JNIEnv* env, jclass cls, jint jid) {
    return (jlong)(intptr_t)glfwGetGamepadName(jid);
}

JNIEXPORT jint JNICALL
Java_org_lwjgl_glfw_GLFW_nglfwJoystickIsGamepad(JNIEnv* env, jclass cls, jint jid) {
    return glfwJoystickIsGamepad(jid);
}

} // extern "C"
