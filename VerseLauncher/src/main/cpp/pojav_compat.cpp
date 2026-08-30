#include <jni.h>
#include <android/log.h>
#include <android/native_window.h>
#include <atomic>
#include <cmath>
#include <cstdlib>
#include <cstdio>
#include <cstring>

#include "glfw_hook.h"
#include "verse_compat.h"

#define LOG_TAG "VerseCompat"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN, LOG_TAG, __VA_ARGS__)

#define EVENT_WINDOW_SIZE 8000

#define EVENT_TYPE_CHAR 1000
#define EVENT_TYPE_CHAR_MODS 1001
#define EVENT_TYPE_CURSOR_ENTER 1002
#define EVENT_TYPE_CURSOR_POS 1003
#define EVENT_TYPE_FRAMEBUFFER_SIZE 1004
#define EVENT_TYPE_KEY 1005
#define EVENT_TYPE_MOUSE_BUTTON 1006
#define EVENT_TYPE_SCROLL 1007
#define EVENT_TYPE_WINDOW_SIZE 1008
#define EVENT_TYPE_CURSOR_DELTA 1009

typedef struct {
    int type;
    int i1;
    int i2;
    int i3;
    int i4;
} VerseInputEvent;

typedef void (*VerseInvokeChar)(void*, unsigned int);
typedef void (*VerseInvokeCharMods)(void*, unsigned int, int);
typedef void (*VerseInvokeCursorEnter)(void*, int);
typedef void (*VerseInvokeCursorPos)(void*, double, double);
typedef void (*VerseInvokeFramebufferSize)(void*, int, int);
typedef void (*VerseInvokeKey)(void*, int, int, int, int);
typedef void (*VerseInvokeMouseButton)(void*, int, int, int);
typedef void (*VerseInvokeScroll)(void*, double, double);
typedef void (*VerseInvokeWindowSize)(void*, int, int);

// 应用进程内会加载两份本库：应用侧（ART）与游戏侧（JVM）各一份，静态变量互不共享。
// 同一进程内环境变量全局可见，因此首次加载时分配共享结构并把指针写入环境变量，
// 第二份副本通过环境变量取回同一块内存，实现输入状态、事件队列与回调指针的跨命名空间共享。
struct VerseEnviron {
    JavaVM* appVm;
    JavaVM* gameVm;
    jclass appBridgeClass;
    jmethodID mAppSetInputReady;
    jmethodID mAppSetGrabbing;
    jmethodID mAppClipboard;
    jclass gameGlfwClass;
    jmethodID mGameWindowSizeChanged;

    ANativeWindow* window;
    int savedWidth;
    int savedHeight;

    bool inputReady;
    bool grabbing;
    bool stackQueue;

    VerseInputEvent events[EVENT_WINDOW_SIZE];
    std::atomic<size_t> eventCounter;
    size_t inEventIndex;
    size_t outEventIndex;
    size_t outTargetIndex;
    size_t inEventCount;
    double cursorX;
    double cursorY;
    double cLastX;
    double cLastY;
    bool shouldUpdateMouse;
    long showingWindow;
    void* injectorCallback;
    int hitResultType;

    void* callbacks[VERSE_CB_COUNT];
};

static VerseEnviron* g_env = nullptr;

static const char* kEnvVarName = "VERSE_SHARED_ENVIRON";

__attribute__((constructor)) static void verseEnvironInit() {
    const char* strptr = getenv(kEnvVarName);
    if (strptr == nullptr) {
        g_env = (VerseEnviron*)calloc(1, sizeof(VerseEnviron));
        if (g_env == nullptr) abort();
        char buf[32];
        snprintf(buf, sizeof(buf), "%p", (void*)g_env);
        setenv(kEnvVarName, buf, 1);
        LOGI("Created shared environ %p", (void*)g_env);
    } else {
        g_env = (VerseEnviron*)strtoull(strptr, nullptr, 16);
        LOGI("Reusing shared environ %p", (void*)g_env);
    }
}

static void callGameWindowSizeChanged(long window, int w, int h) {
    if (g_env == nullptr || g_env->gameVm == nullptr ||
        g_env->gameGlfwClass == nullptr || g_env->mGameWindowSizeChanged == nullptr) {
        return;
    }
    JNIEnv* env = nullptr;
    jint st = g_env->gameVm->GetEnv((void**)&env, JNI_VERSION_1_4);
    if (st == JNI_EDETACHED) {
        if (g_env->gameVm->AttachCurrentThread(&env, nullptr) != JNI_OK) return;
        env->CallStaticVoidMethod(g_env->gameGlfwClass, g_env->mGameWindowSizeChanged,
                                  (jlong)window, (jint)w, (jint)h);
        g_env->gameVm->DetachCurrentThread();
        return;
    }
    if (st != JNI_OK || env == nullptr) return;
    env->CallStaticVoidMethod(g_env->gameGlfwClass, g_env->mGameWindowSizeChanged,
                              (jlong)window, (jint)w, (jint)h);
}

static JNIEnv* attachAppEnv() {
    if (g_env == nullptr || g_env->appVm == nullptr || g_env->appBridgeClass == nullptr) {
        return nullptr;
    }
    JNIEnv* env = nullptr;
    if (g_env->appVm->AttachCurrentThread(&env, nullptr) != JNI_OK) return nullptr;
    return env;
}

static void detachAppEnv() {
    if (g_env != nullptr && g_env->appVm != nullptr) {
        g_env->appVm->DetachCurrentThread();
    }
}

void verseOnVmLoad(JavaVM* vm) {
    if (g_env == nullptr) return;
    if (g_env->appVm == nullptr) {
        g_env->appVm = vm;
        JNIEnv* env = nullptr;
        if (vm->GetEnv((void**)&env, JNI_VERSION_1_4) != JNI_OK || env == nullptr) return;
        jclass local = env->FindClass("com/verse/explorer/launcher/bridge/CallbackBridge");
        if (local == nullptr) {
            env->ExceptionClear();
            LOGW("verseOnVmLoad: app CallbackBridge class not found");
            return;
        }
        g_env->appBridgeClass = (jclass)env->NewGlobalRef(local);
        env->DeleteLocalRef(local);
        if (g_env->appBridgeClass == nullptr) return;
        g_env->mAppSetInputReady = env->GetStaticMethodID(g_env->appBridgeClass,
            "nativeSetInputReady", "(Z)Z");
        if (env->ExceptionCheck()) env->ExceptionClear();
        g_env->mAppSetGrabbing = env->GetStaticMethodID(g_env->appBridgeClass,
            "nativeSetGrabbing", "(Z)V");
        if (env->ExceptionCheck()) env->ExceptionClear();
        g_env->mAppClipboard = env->GetStaticMethodID(g_env->appBridgeClass,
            "nativeClipboard", "(I[B)Ljava/lang/String;");
        if (env->ExceptionCheck()) env->ExceptionClear();
        LOGI("App VM registered (bridge class=%p)", (void*)g_env->appBridgeClass);
    } else if (g_env->gameVm == nullptr && g_env->appVm != vm) {
        g_env->gameVm = vm;
        JNIEnv* env = nullptr;
        if (vm->GetEnv((void**)&env, JNI_VERSION_1_4) != JNI_OK || env == nullptr) return;
        jclass local = env->FindClass("org/lwjgl/glfw/GLFW");
        if (local == nullptr) {
            env->ExceptionClear();
            LOGW("verseOnVmLoad: GLFW class not found");
            return;
        }
        g_env->gameGlfwClass = (jclass)env->NewGlobalRef(local);
        env->DeleteLocalRef(local);
        if (g_env->gameGlfwClass != nullptr) {
            g_env->mGameWindowSizeChanged = env->GetStaticMethodID(g_env->gameGlfwClass,
                "internalWindowSizeChanged", "(JII)V");
            if (env->ExceptionCheck()) env->ExceptionClear();
        }
        LOGI("Game VM registered (GLFW class=%p)", (void*)g_env->gameGlfwClass);
    }
}

long verseSetCallback(int id, long callback) {
    if (g_env == nullptr || id < 0 || id >= VERSE_CB_COUNT) return 0;
    long old = (long)(intptr_t)g_env->callbacks[id];
    g_env->callbacks[id] = (void*)(intptr_t)callback;
    return old;
}

void verseGetCursorPos(double* x, double* y) {
    if (g_env == nullptr) {
        if (x) *x = 0.0;
        if (y) *y = 0.0;
        return;
    }
    if (x) *x = g_env->cursorX;
    if (y) *y = g_env->cursorY;
}

void verseSetCursorPosValue(double x, double y) {
    if (g_env == nullptr) return;
    g_env->cLastX = g_env->cursorX = x;
    g_env->cLastY = g_env->cursorY = y;
}

void verseSetShowingWindow(long window) {
    if (g_env == nullptr) return;
    g_env->showingWindow = window;
}

void verseSetStackQueue(bool use) {
    if (g_env == nullptr) return;
    g_env->stackQueue = use;
}

static void enqueueEvent(int type, int i1, int i2, int i3, int i4) {
    VerseInputEvent* event = &g_env->events[g_env->inEventIndex];
    event->type = type;
    event->i1 = i1;
    event->i2 = i2;
    event->i3 = i3;
    event->i4 = i4;
    if (++g_env->inEventIndex >= EVENT_WINDOW_SIZE) {
        g_env->inEventIndex -= EVENT_WINDOW_SIZE;
    }
    g_env->eventCounter.fetch_add(1, std::memory_order_release);
}

void verseDispatchInput(int type, int i1, int i2, int i3, int i4) {
    if (g_env == nullptr || !g_env->inputReady) return;
    void* window = (void*)(intptr_t)g_env->showingWindow;

    switch (type) {
        case EVENT_TYPE_CURSOR_POS: {
            g_env->cursorX = (double)i1;
            g_env->cursorY = (double)i2;
            if (!g_env->stackQueue) {
                VerseInvokeCursorPos cb = (VerseInvokeCursorPos)g_env->callbacks[VERSE_CB_CURSOR_POS];
                if (cb) cb(window, (double)i1, (double)i2);
            }
            break;
        }
        case EVENT_TYPE_CURSOR_DELTA: {
            g_env->cursorX += (double)i1;
            g_env->cursorY += (double)i2;
            if (!g_env->stackQueue) {
                VerseInvokeCursorPos cb = (VerseInvokeCursorPos)g_env->callbacks[VERSE_CB_CURSOR_POS];
                if (cb) cb(window, g_env->cursorX, g_env->cursorY);
            }
            break;
        }
        case EVENT_TYPE_KEY: {
            // i1=keyChar, i2=keycode, i3=action(1=press), i4=mods
            int key = i2;
            int action = i3;
            int mods = i4;
            if (g_env->stackQueue) {
                enqueueEvent(EVENT_TYPE_KEY, key, 0, action, mods);
                if (action == 1 && i1 > 0) {
                    enqueueEvent(EVENT_TYPE_CHAR, i1, 0, 0, 0);
                }
            } else {
                VerseInvokeKey cb = (VerseInvokeKey)g_env->callbacks[VERSE_CB_KEY];
                if (cb) cb(window, key, 0, action, mods);
                if (action == 1 && i1 > 0) {
                    VerseInvokeChar ccb = (VerseInvokeChar)g_env->callbacks[VERSE_CB_CHAR];
                    if (ccb) ccb(window, (unsigned int)i1);
                }
            }
            break;
        }
        case EVENT_TYPE_CHAR: {
            if (g_env->stackQueue) {
                enqueueEvent(EVENT_TYPE_CHAR, i1, 0, 0, 0);
            } else {
                VerseInvokeChar cb = (VerseInvokeChar)g_env->callbacks[VERSE_CB_CHAR];
                if (cb) cb(window, (unsigned int)i1);
            }
            break;
        }
        case EVENT_TYPE_MOUSE_BUTTON: {
            int button = i1;
            int action = (i2 == 1) ? 1 : 0;
            if (g_env->stackQueue) {
                enqueueEvent(EVENT_TYPE_MOUSE_BUTTON, button, action, 0, 0);
            } else {
                VerseInvokeMouseButton cb = (VerseInvokeMouseButton)g_env->callbacks[VERSE_CB_MOUSE_BUTTON];
                if (cb) cb(window, button, action, 0);
            }
            break;
        }
        case EVENT_TYPE_SCROLL: {
            double xo = (double)i1 / 1000.0;
            double yo = (double)i2 / 1000.0;
            if (g_env->stackQueue) {
                enqueueEvent(EVENT_TYPE_SCROLL, (int)xo, (int)yo, 0, 0);
            } else {
                VerseInvokeScroll cb = (VerseInvokeScroll)g_env->callbacks[VERSE_CB_SCROLL];
                if (cb) cb(window, xo, yo);
            }
            break;
        }
        case EVENT_TYPE_WINDOW_SIZE:
        case EVENT_TYPE_FRAMEBUFFER_SIZE: {
            if (g_env->stackQueue) {
                enqueueEvent(type, i1, i2, 0, 0);
            } else {
                callGameWindowSizeChanged(g_env->showingWindow, i1, i2);
                if (type == EVENT_TYPE_WINDOW_SIZE) {
                    VerseInvokeWindowSize cb = (VerseInvokeWindowSize)g_env->callbacks[VERSE_CB_WINDOW_SIZE];
                    if (cb) cb(window, i1, i2);
                } else {
                    VerseInvokeFramebufferSize cb = (VerseInvokeFramebufferSize)g_env->callbacks[VERSE_CB_FRAMEBUFFER_SIZE];
                    if (cb) cb(window, i1, i2);
                }
            }
            break;
        }
        default:
            break;
    }
}

extern "C" {

__attribute__((visibility("default"))) int pojavInit() {
    if (g_env == nullptr) return 0;
    LOGI("pojavInit: begin");
    ANativeWindow* w = getNativeWindowSafe();
    if (w == nullptr) {
        LOGE("pojavInit: no native window available");
        return 0;
    }
    if (g_env->window != nullptr && g_env->window != w) {
        ANativeWindow_release(g_env->window);
    }
    g_env->window = w;
    g_env->savedWidth = ANativeWindow_getWidth(w);
    g_env->savedHeight = ANativeWindow_getHeight(w);
    LOGI("pojavInit: window=%p size=%dx%d", (void*)w, g_env->savedWidth, g_env->savedHeight);

    glfwInit();

    // 把 Surface 实际尺寸同步给游戏侧的 GLFW 窗口记录，避免其沿用默认 1280x720
    callGameWindowSizeChanged(0, g_env->savedWidth, g_env->savedHeight);
    return 1;
}

__attribute__((visibility("default"))) void* pojavCreateContext(void* contextSrc) {
    if (g_env == nullptr) return nullptr;
    int w = g_env->savedWidth > 0 ? g_env->savedWidth : 1280;
    int h = g_env->savedHeight > 0 ? g_env->savedHeight : 720;
    LOGI("pojavCreateContext: share=%p size=%dx%d", contextSrc, w, h);
    GLFWwindow* win = glfwCreateWindow(w, h, "Minecraft", nullptr, (GLFWwindow*)contextSrc);
    if (win == nullptr) {
        LOGE("pojavCreateContext: glfwCreateWindow failed");
        return nullptr;
    }
    return win;
}

__attribute__((visibility("default"))) void* pojavGetCurrentContext() {
    return glfwGetCurrentContext();
}

__attribute__((visibility("default"))) void pojavMakeCurrent(void* context) {
    glfwMakeContextCurrent((GLFWwindow*)context);
}

__attribute__((visibility("default"))) void pojavTerminate() {
    LOGI("pojavTerminate");
    glfwTerminate();
    if (g_env != nullptr && g_env->window != nullptr) {
        ANativeWindow_release(g_env->window);
        g_env->window = nullptr;
    }
}

__attribute__((visibility("default"))) void pojavSetWindowHint(int hint, int value) {
    glfwWindowHint(hint, value);
}

__attribute__((visibility("default"))) void pojavSwapBuffers(void* context) {
    glfwSwapBuffers((GLFWwindow*)context);
}

__attribute__((visibility("default"))) void pojavSwapInterval(int interval) {
    glfwSwapInterval(interval);
}

__attribute__((visibility("default"))) void pojavStartPumping() {
    if (g_env == nullptr) return;
    size_t counter = g_env->eventCounter.load(std::memory_order_acquire);
    size_t index = g_env->outEventIndex;
    unsigned targetIndex = index + counter;
    if (targetIndex >= EVENT_WINDOW_SIZE) {
        targetIndex -= EVENT_WINDOW_SIZE;
    }
    g_env->inEventCount = counter;
    g_env->outTargetIndex = targetIndex;

    if ((g_env->cLastX != g_env->cursorX || g_env->cLastY != g_env->cursorY) &&
        g_env->callbacks[VERSE_CB_CURSOR_POS] != nullptr) {
        g_env->cLastX = g_env->cursorX;
        g_env->cLastY = g_env->cursorY;
        g_env->shouldUpdateMouse = true;
    }
}

__attribute__((visibility("default"))) void pojavPumpEvents(void* window) {
    if (g_env == nullptr) return;
    if (g_env->shouldUpdateMouse) {
        VerseInvokeCursorPos cb = (VerseInvokeCursorPos)g_env->callbacks[VERSE_CB_CURSOR_POS];
        if (cb) cb(window, floor(g_env->cursorX), floor(g_env->cursorY));
    }

    size_t index = g_env->outEventIndex;
    size_t targetIndex = g_env->outTargetIndex;

    while (targetIndex != index) {
        VerseInputEvent event = g_env->events[index];
        switch (event.type) {
            case EVENT_TYPE_CHAR: {
                VerseInvokeChar cb = (VerseInvokeChar)g_env->callbacks[VERSE_CB_CHAR];
                if (cb) cb(window, (unsigned int)event.i1);
                break;
            }
            case EVENT_TYPE_CHAR_MODS: {
                VerseInvokeCharMods cb = (VerseInvokeCharMods)g_env->callbacks[VERSE_CB_CHAR_MODS];
                if (cb) cb(window, (unsigned int)event.i1, event.i2);
                break;
            }
            case EVENT_TYPE_KEY: {
                VerseInvokeKey cb = (VerseInvokeKey)g_env->callbacks[VERSE_CB_KEY];
                if (cb) cb(window, event.i1, event.i2, event.i3, event.i4);
                break;
            }
            case EVENT_TYPE_MOUSE_BUTTON: {
                VerseInvokeMouseButton cb = (VerseInvokeMouseButton)g_env->callbacks[VERSE_CB_MOUSE_BUTTON];
                if (cb) cb(window, event.i1, event.i2, event.i3);
                break;
            }
            case EVENT_TYPE_CURSOR_ENTER: {
                VerseInvokeCursorEnter cb = (VerseInvokeCursorEnter)g_env->callbacks[VERSE_CB_CURSOR_ENTER];
                if (cb) cb(window, event.i1);
                break;
            }
            case EVENT_TYPE_SCROLL: {
                VerseInvokeScroll cb = (VerseInvokeScroll)g_env->callbacks[VERSE_CB_SCROLL];
                if (cb) cb(window, (double)event.i1, (double)event.i2);
                break;
            }
            case EVENT_TYPE_FRAMEBUFFER_SIZE: {
                callGameWindowSizeChanged(g_env->showingWindow, event.i1, event.i2);
                VerseInvokeFramebufferSize cb = (VerseInvokeFramebufferSize)g_env->callbacks[VERSE_CB_FRAMEBUFFER_SIZE];
                if (cb) cb(window, event.i1, event.i2);
                break;
            }
            case EVENT_TYPE_WINDOW_SIZE: {
                callGameWindowSizeChanged(g_env->showingWindow, event.i1, event.i2);
                VerseInvokeWindowSize cb = (VerseInvokeWindowSize)g_env->callbacks[VERSE_CB_WINDOW_SIZE];
                if (cb) cb(window, event.i1, event.i2);
                break;
            }
            default:
                break;
        }

        index++;
        if (index >= EVENT_WINDOW_SIZE) {
            index -= EVENT_WINDOW_SIZE;
        }
    }
}

__attribute__((visibility("default"))) void pojavStopPumping() {
    if (g_env == nullptr) return;
    g_env->outEventIndex = g_env->outTargetIndex;
    g_env->eventCounter.fetch_sub(g_env->inEventCount, std::memory_order_acquire);
    g_env->shouldUpdateMouse = false;
}

// ---- org.lwjgl.glfw.CallbackBridge（游戏侧 JVM）要求的 native 方法 ----

JNIEXPORT jboolean JNICALL
Java_org_lwjgl_glfw_CallbackBridge_nativeSetInputReady(JNIEnv* env, jclass clazz, jboolean ready) {
    if (g_env == nullptr) return JNI_TRUE;
    LOGI("nativeSetInputReady: %d", (int)ready);
    g_env->inputReady = (ready == JNI_TRUE);
    if (g_env->appVm != nullptr && g_env->appBridgeClass != nullptr && g_env->mAppSetInputReady != nullptr) {
        JNIEnv* appEnv = attachAppEnv();
        if (appEnv != nullptr) {
            appEnv->CallStaticBooleanMethod(g_env->appBridgeClass, g_env->mAppSetInputReady, ready);
            detachAppEnv();
        }
    }
    return JNI_TRUE;
}

JNIEXPORT void JNICALL
Java_org_lwjgl_glfw_CallbackBridge_nativeSetGrabbing(JNIEnv* env, jclass clazz, jboolean grab) {
    if (g_env == nullptr) return;
    LOGI("nativeSetGrabbing: %d", (int)grab);
    g_env->grabbing = (grab == JNI_TRUE);
    if (g_env->appVm != nullptr && g_env->appBridgeClass != nullptr && g_env->mAppSetGrabbing != nullptr) {
        JNIEnv* appEnv = attachAppEnv();
        if (appEnv != nullptr) {
            appEnv->CallStaticVoidMethod(g_env->appBridgeClass, g_env->mAppSetGrabbing, grab);
            detachAppEnv();
        }
    }
}

JNIEXPORT jstring JNICALL
Java_org_lwjgl_glfw_CallbackBridge_nativeClipboard(JNIEnv* env, jclass clazz, jint action, jbyteArray copySrc) {
    if (g_env == nullptr) return nullptr;
    if (g_env->appVm == nullptr || g_env->appBridgeClass == nullptr || g_env->mAppClipboard == nullptr) {
        return env->NewStringUTF("");
    }
    JNIEnv* appEnv = attachAppEnv();
    if (appEnv == nullptr) return env->NewStringUTF("");

    jbyteArray appCopy = nullptr;
    if (copySrc != nullptr) {
        jsize len = env->GetArrayLength(copySrc);
        jbyte* buf = env->GetByteArrayElements(copySrc, nullptr);
        if (buf != nullptr) {
            appCopy = appEnv->NewByteArray(len);
            if (appCopy != nullptr) {
                appEnv->SetByteArrayRegion(appCopy, 0, len, buf);
            }
            env->ReleaseByteArrayElements(copySrc, buf, JNI_ABORT);
        }
    }

    jobject result = appEnv->CallStaticObjectMethod(g_env->appBridgeClass, g_env->mAppClipboard, action, appCopy);
    if (appCopy != nullptr) {
        appEnv->DeleteLocalRef(appCopy);
    }

    jstring ret = nullptr;
    if (result != nullptr) {
        const char* utf = appEnv->GetStringUTFChars((jstring)result, nullptr);
        if (utf != nullptr) {
            ret = env->NewStringUTF(utf);
            appEnv->ReleaseStringUTFChars((jstring)result, utf);
        }
        appEnv->DeleteLocalRef(result);
    }
    detachAppEnv();
    return ret != nullptr ? ret : env->NewStringUTF("");
}

JNIEXPORT void JNICALL
Java_org_lwjgl_glfw_CallbackBridge_nativeSendData(JNIEnv* env, jclass clazz,
        jboolean isAndroid, jint type, jstring data) {
    if (g_env == nullptr || data == nullptr) return;
    const char* str = env->GetStringUTFChars(data, nullptr);
    if (str == nullptr) return;
    int vals[4] = {0, 0, 0, 0};
    int count = 0;
    const char* p = str;
    while (count < 4 && *p != '\0') {
        char* end = nullptr;
        long v = strtol(p, &end, 10);
        if (end == p) break;
        vals[count++] = (int)v;
        p = end;
        while (*p == ',' || *p == ' ') p++;
    }
    env->ReleaseStringUTFChars(data, str);
    verseDispatchInput(type, vals[0], vals[1], vals[2], vals[3]);
}

JNIEXPORT void JNICALL
Java_org_lwjgl_glfw_CallbackBridge_nativeSetWindowAttrib(JNIEnv* env, jclass clazz,
        jint attrib, jint value) {
}

__attribute__((visibility("default"))) void pojavSetInjectorCallback(long callback) {
    if (g_env == nullptr) return;
    g_env->injectorCallback = (void*)(intptr_t)callback;
    g_env->hitResultType = 0;
}

__attribute__((visibility("default"))) void pojavSetHitResultType(int type) {
    if (g_env == nullptr) return;
    g_env->hitResultType = type;
}

JNIEXPORT jint JNICALL
Java_org_lwjgl_glfw_CallbackBridge_nativeGetFps(JNIEnv* env, jclass clazz) {
    return 0;
}

} // extern "C"
