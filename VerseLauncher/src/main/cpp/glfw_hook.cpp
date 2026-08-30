#include <jni.h>
#include <android/log.h>
#include <android/native_window.h>
#include <android/native_window_jni.h>
#include <EGL/egl.h>
#include <GLES3/gl3.h>
#include <dlfcn.h>
#include <cstdlib>
#include <cstring>
#include <cstdio>
#include <pthread.h>

#include "verse_compat.h"

#define LOG_TAG "GLFWHook"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN, LOG_TAG, __VA_ARGS__)

// EGL 动态加载层：默认使用系统 libEGL.so；当环境变量 POJAVEXEC_EGL 指定
// （如 Zink 渲染器的 libEGL_mesa.so）时加载指定实现，使桌面 GL 上下文
// 可由 Mesa EGL 创建，同时 libcutils.so 需预加载供 Kopper 后端使用。
typedef EGLBoolean (*VerseEglBooleanDisp)(EGLDisplay);
typedef EGLBoolean (*VerseEglMakeCurrent)(EGLDisplay, EGLSurface, EGLSurface, EGLContext);
typedef EGLBoolean (*VerseEglDestroyCtx)(EGLDisplay, EGLContext);
typedef EGLBoolean (*VerseEglDestroySurf)(EGLDisplay, EGLSurface);
typedef EGLDisplay (*VerseEglGetDisplay)(EGLNativeDisplayType);
typedef EGLBoolean (*VerseEglInitialize)(EGLDisplay, EGLint*, EGLint*);
typedef EGLBoolean (*VerseEglChooseConfig)(EGLDisplay, const EGLint*, EGLConfig*, EGLint, EGLint*);
typedef EGLBoolean (*VerseEglBindAPI)(EGLenum);
typedef EGLSurface (*VerseEglCreateWindowSurface)(EGLDisplay, EGLConfig, EGLNativeWindowType, const EGLint*);
typedef EGLBoolean (*VerseEglSwapBuffers)(EGLDisplay, EGLSurface);
typedef EGLContext (*VerseEglCreateContext)(EGLDisplay, EGLConfig, EGLContext, const EGLint*);
typedef EGLBoolean (*VerseEglSwapInterval)(EGLDisplay, EGLint);
typedef const char* (*VerseEglQueryString)(EGLDisplay, EGLint);
typedef void* (*VerseEglGetProcAddress)(const char*);
typedef EGLint (*VerseEglGetError)(void);

static struct {
    VerseEglGetDisplay eglGetDisplay;
    VerseEglInitialize eglInitialize;
    VerseEglChooseConfig eglChooseConfig;
    VerseEglBindAPI eglBindAPI;
    VerseEglCreateContext eglCreateContext;
    VerseEglCreateWindowSurface eglCreateWindowSurface;
    VerseEglMakeCurrent eglMakeCurrent;
    VerseEglDestroyCtx eglDestroyContext;
    VerseEglDestroySurf eglDestroySurface;
    VerseEglSwapInterval eglSwapInterval;
    VerseEglSwapBuffers eglSwapBuffers;
    VerseEglQueryString eglQueryString;
    VerseEglGetProcAddress eglGetProcAddress;
    VerseEglGetError eglGetError;
} g_egl;

static bool g_eglLoaded = false;
static pthread_mutex_t g_eglLoadMutex = PTHREAD_MUTEX_INITIALIZER;

static bool verseRendererIsDesktopGL() {
    const char* r = getenv("POJAV_RENDERER");
    return r != nullptr && strncmp(r, "opengles3_desktopgl", 19) == 0;
}

static void* verseDlopenRendererLib(const char* name) {
    void* h = dlopen(name, RTLD_GLOBAL | RTLD_LAZY);
    if (h != nullptr) return h;
    const char* dirs[] = {getenv("POJAV_NATIVEDIR"), getenv("FCL_NATIVEDIR"), nullptr};
    for (int i = 0; dirs[i] != nullptr; i++) {
        char path[512];
        snprintf(path, sizeof(path), "%s/%s", dirs[i], name);
        h = dlopen(path, RTLD_GLOBAL | RTLD_LAZY);
        if (h != nullptr) return h;
    }
    return nullptr;
}

static bool verseEglLoad() {
    pthread_mutex_lock(&g_eglLoadMutex);
    if (g_eglLoaded) {
        pthread_mutex_unlock(&g_eglLoadMutex);
        return g_egl.eglGetProcAddress != nullptr;
    }
    g_eglLoaded = true;

    const char* eglName = getenv("POJAVEXEC_EGL");
    if (eglName == nullptr || !*eglName) eglName = "libEGL.so";
    if (strncmp(eglName, "libEGL_mesa.so", 14) == 0) {
        dlopen("libcutils.so", RTLD_GLOBAL | RTLD_NOW);
    }
    void* h = verseDlopenRendererLib(eglName);
    if (h == nullptr) {
        LOGW("verseEglLoad: dlopen(%s) failed: %s, fallback to system libEGL.so", eglName, dlerror());
        h = dlopen("libEGL.so", RTLD_GLOBAL | RTLD_LAZY);
    }
    if (h == nullptr) {
        LOGE("verseEglLoad: cannot load any EGL implementation");
        pthread_mutex_unlock(&g_eglLoadMutex);
        return false;
    }
    LOGI("verseEglLoad: using %s (%p)", eglName, h);

    g_egl.eglGetProcAddress = (VerseEglGetProcAddress)dlsym(h, "eglGetProcAddress");
    if (g_egl.eglGetProcAddress == nullptr) {
        LOGE("verseEglLoad: eglGetProcAddress missing: %s", dlerror());
        pthread_mutex_unlock(&g_eglLoadMutex);
        return false;
    }
    g_egl.eglGetDisplay = (VerseEglGetDisplay)dlsym(h, "eglGetDisplay");
    g_egl.eglInitialize = (VerseEglInitialize)dlsym(h, "eglInitialize");
    g_egl.eglChooseConfig = (VerseEglChooseConfig)dlsym(h, "eglChooseConfig");
    g_egl.eglBindAPI = (VerseEglBindAPI)dlsym(h, "eglBindAPI");
    g_egl.eglCreateContext = (VerseEglCreateContext)dlsym(h, "eglCreateContext");
    g_egl.eglCreateWindowSurface = (VerseEglCreateWindowSurface)dlsym(h, "eglCreateWindowSurface");
    g_egl.eglMakeCurrent = (VerseEglMakeCurrent)dlsym(h, "eglMakeCurrent");
    g_egl.eglDestroyContext = (VerseEglDestroyCtx)dlsym(h, "eglDestroyContext");
    g_egl.eglDestroySurface = (VerseEglDestroySurf)dlsym(h, "eglDestroySurface");
    g_egl.eglSwapInterval = (VerseEglSwapInterval)dlsym(h, "eglSwapInterval");
    g_egl.eglSwapBuffers = (VerseEglSwapBuffers)dlsym(h, "eglSwapBuffers");
    g_egl.eglQueryString = (VerseEglQueryString)dlsym(h, "eglQueryString");
    g_egl.eglGetError = (VerseEglGetError)dlsym(h, "eglGetError");

    bool ok = g_egl.eglGetDisplay && g_egl.eglInitialize && g_egl.eglChooseConfig &&
              g_egl.eglBindAPI && g_egl.eglCreateContext && g_egl.eglCreateWindowSurface &&
              g_egl.eglMakeCurrent && g_egl.eglSwapBuffers;
    if (!ok) {
        LOGE("verseEglLoad: incomplete EGL symbols in %s", eglName);
    } else {
        if (verseRendererIsDesktopGL()) {
            LOGI("verseEglLoad: desktop OpenGL renderer, binding EGL_OPENGL_API");
            g_egl.eglBindAPI(EGL_OPENGL_API);
        } else {
            g_egl.eglBindAPI(EGL_OPENGL_ES_API);
        }
    }
    pthread_mutex_unlock(&g_eglLoadMutex);
    return ok;
}

#define eglGetDisplay g_egl.eglGetDisplay
#define eglInitialize g_egl.eglInitialize
#define eglChooseConfig g_egl.eglChooseConfig
#define eglBindAPI g_egl.eglBindAPI
#define eglCreateContext g_egl.eglCreateContext
#define eglCreateWindowSurface g_egl.eglCreateWindowSurface
#define eglMakeCurrent g_egl.eglMakeCurrent
#define eglDestroyContext g_egl.eglDestroyContext
#define eglDestroySurface g_egl.eglDestroySurface
#define eglSwapInterval g_egl.eglSwapInterval
#define eglSwapBuffers g_egl.eglSwapBuffers
#define eglQueryString g_egl.eglQueryString
#define eglGetProcAddress g_egl.eglGetProcAddress
#define eglGetError g_egl.eglGetError

static void verseSetupZinkEnvironment() {
    const char* r = getenv("POJAV_RENDERER");
    if (r == nullptr) return;
    if (strcmp(r, "opengles3_desktopgl_zink_kopper") == 0 || strcmp(r, "vulkan_zink") == 0) {
        setenv("GALLIUM_DRIVER", "zink", 1);
        setenv("MESA_ANDROID_NO_KMS_SWRAST", "1", 1);
        void* vk = dlopen("libvulkan.so", RTLD_LAZY | RTLD_LOCAL);
        LOGI("verseSetupZinkEnvironment: GALLIUM_DRIVER=zink, libvulkan=%p", vk);
    }
}

#define GLFW_TRUE 1
#define GLFW_FALSE 0

#define GLFW_RELEASE 0
#define GLFW_PRESS 1
#define GLFW_REPEAT 2

#define GLFW_KEY_UNKNOWN -1
#define GLFW_KEY_SPACE 32
#define GLFW_KEY_APOSTROPHE 39
#define GLFW_KEY_COMMA 44
#define GLFW_KEY_MINUS 45
#define GLFW_KEY_PERIOD 46
#define GLFW_KEY_SLASH 47
#define GLFW_KEY_0 48
#define GLFW_KEY_1 49
#define GLFW_KEY_2 50
#define GLFW_KEY_3 51
#define GLFW_KEY_4 52
#define GLFW_KEY_5 53
#define GLFW_KEY_6 54
#define GLFW_KEY_7 55
#define GLFW_KEY_8 56
#define GLFW_KEY_9 57
#define GLFW_KEY_SEMICOLON 59
#define GLFW_KEY_EQUAL 61
#define GLFW_KEY_A 65
#define GLFW_KEY_B 66
#define GLFW_KEY_C 67
#define GLFW_KEY_D 68
#define GLFW_KEY_E 69
#define GLFW_KEY_F 70
#define GLFW_KEY_G 71
#define GLFW_KEY_H 72
#define GLFW_KEY_I 73
#define GLFW_KEY_J 74
#define GLFW_KEY_K 75
#define GLFW_KEY_L 76
#define GLFW_KEY_M 77
#define GLFW_KEY_N 78
#define GLFW_KEY_O 79
#define GLFW_KEY_P 80
#define GLFW_KEY_Q 81
#define GLFW_KEY_R 82
#define GLFW_KEY_S 83
#define GLFW_KEY_T 84
#define GLFW_KEY_U 85
#define GLFW_KEY_V 86
#define GLFW_KEY_W 87
#define GLFW_KEY_X 88
#define GLFW_KEY_Y 89
#define GLFW_KEY_Z 90
#define GLFW_KEY_LEFT_BRACKET 91
#define GLFW_KEY_BACKSLASH 92
#define GLFW_KEY_RIGHT_BRACKET 93
#define GLFW_KEY_GRAVE_ACCENT 96
#define GLFW_KEY_WORLD_1 161
#define GLFW_KEY_WORLD_2 162
#define GLFW_KEY_ESCAPE 256
#define GLFW_KEY_ENTER 257
#define GLFW_KEY_TAB 258
#define GLFW_KEY_BACKSPACE 259
#define GLFW_KEY_INSERT 260
#define GLFW_KEY_DELETE 261
#define GLFW_KEY_RIGHT 262
#define GLFW_KEY_LEFT 263
#define GLFW_KEY_DOWN 264
#define GLFW_KEY_UP 265
#define GLFW_KEY_PAGE_UP 266
#define GLFW_KEY_PAGE_DOWN 267
#define GLFW_KEY_HOME 268
#define GLFW_KEY_END 269
#define GLFW_KEY_CAPS_LOCK 280
#define GLFW_KEY_SCROLL_LOCK 281
#define GLFW_KEY_NUM_LOCK 282
#define GLFW_KEY_PRINT_SCREEN 283
#define GLFW_KEY_PAUSE 284
#define GLFW_KEY_F1 290
#define GLFW_KEY_F2 291
#define GLFW_KEY_F3 292
#define GLFW_KEY_F4 293
#define GLFW_KEY_F5 294
#define GLFW_KEY_F6 295
#define GLFW_KEY_F7 296
#define GLFW_KEY_F8 297
#define GLFW_KEY_F9 298
#define GLFW_KEY_F10 299
#define GLFW_KEY_F11 300
#define GLFW_KEY_F12 301
#define GLFW_KEY_F13 302
#define GLFW_KEY_F14 303
#define GLFW_KEY_F15 304
#define GLFW_KEY_F16 305
#define GLFW_KEY_F17 306
#define GLFW_KEY_F18 307
#define GLFW_KEY_F19 308
#define GLFW_KEY_F20 309
#define GLFW_KEY_F21 310
#define GLFW_KEY_F22 311
#define GLFW_KEY_F23 312
#define GLFW_KEY_F24 313
#define GLFW_KEY_F25 314
#define GLFW_KEY_KP_0 320
#define GLFW_KEY_KP_1 321
#define GLFW_KEY_KP_2 322
#define GLFW_KEY_KP_3 323
#define GLFW_KEY_KP_4 324
#define GLFW_KEY_KP_5 325
#define GLFW_KEY_KP_6 326
#define GLFW_KEY_KP_7 327
#define GLFW_KEY_KP_8 328
#define GLFW_KEY_KP_9 329
#define GLFW_KEY_KP_DECIMAL 330
#define GLFW_KEY_KP_DIVIDE 331
#define GLFW_KEY_KP_MULTIPLY 332
#define GLFW_KEY_KP_SUBTRACT 333
#define GLFW_KEY_KP_ADD 334
#define GLFW_KEY_KP_ENTER 335
#define GLFW_KEY_KP_EQUAL 336
#define GLFW_KEY_LEFT_SHIFT 340
#define GLFW_KEY_LEFT_CONTROL 341
#define GLFW_KEY_LEFT_ALT 342
#define GLFW_KEY_LEFT_SUPER 343
#define GLFW_KEY_RIGHT_SHIFT 344
#define GLFW_KEY_RIGHT_CONTROL 345
#define GLFW_KEY_RIGHT_ALT 346
#define GLFW_KEY_RIGHT_SUPER 347
#define GLFW_KEY_MENU 348
#define GLFW_KEY_LAST GLFW_KEY_MENU

#define GLFW_MOD_SHIFT 0x0001
#define GLFW_MOD_CONTROL 0x0002
#define GLFW_MOD_ALT 0x0004
#define GLFW_MOD_SUPER 0x0008
#define GLFW_MOD_CAPS_LOCK 0x0010
#define GLFW_MOD_NUM_LOCK 0x0020

#define GLFW_MOUSE_BUTTON_1 0
#define GLFW_MOUSE_BUTTON_2 1
#define GLFW_MOUSE_BUTTON_3 2
#define GLFW_MOUSE_BUTTON_4 3
#define GLFW_MOUSE_BUTTON_5 4
#define GLFW_MOUSE_BUTTON_6 5
#define GLFW_MOUSE_BUTTON_7 6
#define GLFW_MOUSE_BUTTON_8 7
#define GLFW_MOUSE_BUTTON_LAST GLFW_MOUSE_BUTTON_8
#define GLFW_MOUSE_BUTTON_LEFT GLFW_MOUSE_BUTTON_1
#define GLFW_MOUSE_BUTTON_RIGHT GLFW_MOUSE_BUTTON_2
#define GLFW_MOUSE_BUTTON_MIDDLE GLFW_MOUSE_BUTTON_3

#define GLFW_JOYSTICK_1 0
#define GLFW_JOYSTICK_2 1
#define GLFW_JOYSTICK_3 2
#define GLFW_JOYSTICK_4 3
#define GLFW_JOYSTICK_5 4
#define GLFW_JOYSTICK_6 5
#define GLFW_JOYSTICK_7 6
#define GLFW_JOYSTICK_8 7
#define GLFW_JOYSTICK_9 8
#define GLFW_JOYSTICK_10 9
#define GLFW_JOYSTICK_11 10
#define GLFW_JOYSTICK_12 11
#define GLFW_JOYSTICK_13 12
#define GLFW_JOYSTICK_14 13
#define GLFW_JOYSTICK_15 14
#define GLFW_JOYSTICK_16 15
#define GLFW_JOYSTICK_LAST GLFW_JOYSTICK_16

#define GLFW_NOT_INITIALIZED 0x00010001
#define GLFW_NO_CURRENT_CONTEXT 0x00010002
#define GLFW_INVALID_ENUM 0x00010003
#define GLFW_INVALID_VALUE 0x00010004
#define GLFW_OUT_OF_MEMORY 0x00010005
#define GLFW_API_UNAVAILABLE 0x00010006
#define GLFW_VERSION_UNAVAILABLE 0x00010007
#define GLFW_PLATFORM_ERROR 0x00010008
#define GLFW_FORMAT_UNAVAILABLE 0x00010009
#define GLFW_NO_WINDOW_CONTEXT 0x0001000A

#define GLFW_FOCUSED 0x00020001
#define GLFW_ICONIFIED 0x00020002
#define GLFW_RESIZABLE 0x00020003
#define GLFW_VISIBLE 0x00020004
#define GLFW_DECORATED 0x00020005
#define GLFW_AUTO_ICONIFY 0x00020006
#define GLFW_FLOATING 0x00020007
#define GLFW_MAXIMIZED 0x00020008
#define GLFW_CENTER_CURSOR 0x00020009
#define GLFW_TRANSPARENT_FRAMEBUFFER 0x0002000A
#define GLFW_HOVERED 0x0002000B
#define GLFW_FOCUS_ON_SHOW 0x0002000C

#define GLFW_RED_BITS 0x00021001
#define GLFW_GREEN_BITS 0x00021002
#define GLFW_BLUE_BITS 0x00021003
#define GLFW_ALPHA_BITS 0x00021004
#define GLFW_DEPTH_BITS 0x00021005
#define GLFW_STENCIL_BITS 0x00021006
#define GLFW_ACCUM_RED_BITS 0x00021007
#define GLFW_ACCUM_GREEN_BITS 0x00021008
#define GLFW_ACCUM_BLUE_BITS 0x00021009
#define GLFW_ACCUM_ALPHA_BITS 0x0002100A
#define GLFW_AUX_BUFFERS 0x0002100B
#define GLFW_STEREO 0x0002100C
#define GLFW_SAMPLES 0x0002100D
#define GLFW_SRGB_CAPABLE 0x0002100E
#define GLFW_REFRESH_RATE 0x0002100F
#define GLFW_DOUBLEBUFFER 0x00021010

#define GLFW_CLIENT_API 0x00022001
#define GLFW_CONTEXT_VERSION_MAJOR 0x00022002
#define GLFW_CONTEXT_VERSION_MINOR 0x00022003
#define GLFW_CONTEXT_REVISION 0x00022004
#define GLFW_CONTEXT_ROBUSTNESS 0x00022005
#define GLFW_OPENGL_FORWARD_COMPAT 0x00022006
#define GLFW_OPENGL_DEBUG_CONTEXT 0x00022007
#define GLFW_OPENGL_PROFILE 0x00022008
#define GLFW_CONTEXT_RELEASE_BEHAVIOR 0x00022009
#define GLFW_CONTEXT_NO_ERROR 0x0002200A
#define GLFW_CONTEXT_CREATION_API 0x0002200B
#define GLFW_SCALE_TO_MONITOR 0x0002200C

#define GLFW_NO_API 0
#define GLFW_OPENGL_API 0x00030001
#define GLFW_OPENGL_ES_API 0x00030002

#define GLFW_NO_ROBUSTNESS 0
#define GLFW_NO_RESET_NOTIFICATION 0x00031001
#define GLFW_LOSE_CONTEXT_ON_RESET 0x00031002

#define GLFW_OPENGL_ANY_PROFILE 0
#define GLFW_OPENGL_CORE_PROFILE 0x00032001
#define GLFW_OPENGL_COMPAT_PROFILE 0x00032002

#define GLFW_CURSOR 0x00033001
#define GLFW_STICKY_KEYS 0x00033002
#define GLFW_STICKY_MOUSE_BUTTONS 0x00033003
#define GLFW_LOCK_KEY_MODS 0x00033004
#define GLFW_RAW_MOUSE_MOTION 0x00033005

#define GLFW_CURSOR_NORMAL 0x00034001
#define GLFW_CURSOR_HIDDEN 0x00034002
#define GLFW_CURSOR_DISABLED 0x00034003

#define GLFW_ANY_RELEASE_BEHAVIOR 0
#define GLFW_RELEASE_BEHAVIOR_FLUSH 0x00035001
#define GLFW_RELEASE_BEHAVIOR_NONE 0x00035002

#define GLFW_NATIVE_CONTEXT_API 0x00036001
#define GLFW_EGL_CONTEXT_API 0x00036002
#define GLFW_OSMESA_CONTEXT_API 0x00036003

#define GLFW_CONNECTED 0x00040001
#define GLFW_DISCONNECTED 0x00040002

#define GLFW_DONT_CARE -1

#ifndef EGL_OPENGL_ES3_BIT_KHR
#define EGL_OPENGL_ES3_BIT_KHR EGL_OPENGL_ES2_BIT
#endif

struct GLFWmonitor {
    char name[64];
    int width;
    int height;
    int physicalWidth;
    int physicalHeight;
};

struct GLFWvidmode {
    int width;
    int height;
    int redBits;
    int greenBits;
    int blueBits;
    int refreshRate;
};

struct GLFWgammaramp {
    unsigned short* red;
    unsigned short* green;
    unsigned short* blue;
    unsigned short size;
};

struct GLFWwindow;
struct GLFWmonitor;
struct GLFWcursor;

typedef void (*GLFWerrorfun)(int, const char*);
typedef void (*GLFWwindowposfun)(GLFWwindow*, int, int);
typedef void (*GLFWwindowsizefun)(GLFWwindow*, int, int);
typedef void (*GLFWwindowclosefun)(GLFWwindow*);
typedef void (*GLFWwindowrefreshfun)(GLFWwindow*);
typedef void (*GLFWwindowfocusfun)(GLFWwindow*, int);
typedef void (*GLFWwindowiconifyfun)(GLFWwindow*, int);
typedef void (*GLFWwindowmaximizefun)(GLFWwindow*, int);
typedef void (*GLFWframebuffersizefun)(GLFWwindow*, int, int);
typedef void (*GLFWwindowcontentscalefun)(GLFWwindow*, float, float);
typedef void (*GLFWkeyfun)(GLFWwindow*, int, int, int, int);
typedef void (*GLFWcharfun)(GLFWwindow*, unsigned int);
typedef void (*GLFWcharmodsfun)(GLFWwindow*, unsigned int, int);
typedef void (*GLFWmousebuttonfun)(GLFWwindow*, int, int, int);
typedef void (*GLFWcursorposfun)(GLFWwindow*, double, double);
typedef void (*GLFWcursorenterfun)(GLFWwindow*, int);
typedef void (*GLFWscrollfun)(GLFWwindow*, double, double);
typedef void (*GLFWdropfun)(GLFWwindow*, int, const char**);
typedef void (*GLFWjoystickfun)(int, int);
typedef void (*GLFWmonitorfun)(GLFWmonitor*, int);

typedef void (*GLFWglproc)(void);
typedef void (*GLFWvkproc)(void);

struct GLFWwindow {
    EGLDisplay eglDisplay;
    EGLContext eglContext;
    EGLSurface eglSurface;
    EGLConfig eglConfig;
    ANativeWindow* nativeWindow;
    
    int width;
    int height;
    int fbWidth;
    int fbHeight;
    char title[256];
    
    bool shouldClose;
    bool iconified;
    bool maximized;
    bool focused;
    bool visible;
    bool resizable;
    bool decorated;
    bool floating;
    
    int swapInterval;
    
    void* userPointer;
    
    int cursorMode;
    int inputMode;
    bool stickyKeys;
    bool stickyMouseButtons;
    bool lockKeyMods;
    bool rawMouseMotion;
    
    double cursorX;
    double cursorY;
    
    bool keyState[512];
    bool mouseButtonState[8];
    
    GLFWwindowposfun posCallback;
    GLFWwindowsizefun sizeCallback;
    GLFWwindowclosefun closeCallback;
    GLFWwindowrefreshfun refreshCallback;
    GLFWwindowfocusfun focusCallback;
    GLFWwindowiconifyfun iconifyCallback;
    GLFWwindowmaximizefun maximizeCallback;
    GLFWframebuffersizefun fbSizeCallback;
    GLFWwindowcontentscalefun scaleCallback;
    
    GLFWkeyfun keyCallback;
    GLFWcharfun charCallback;
    GLFWcharmodsfun charModsCallback;
    GLFWmousebuttonfun mouseButtonCallback;
    GLFWcursorposfun cursorPosCallback;
    GLFWcursorenterfun cursorEnterCallback;
    GLFWscrollfun scrollCallback;
    GLFWdropfun dropCallback;
};

struct GLFWimage {
    int width;
    int height;
    unsigned char* pixels;
};

struct GLFWgamepadstate {
    unsigned char buttons[15];
    float axes[6];
};

struct GLFWallocator {
    void* (*allocate)(size_t size, void* user);
    void* (*deallocate)(void* block, void* user);
    void* (*reallocate)(void* block, size_t newSize, void* user);
    void* user;
};

#ifdef GLFW_INCLUDE_VULKAN
#include <vulkan/vulkan.h>
#endif

#define GLFW_HAS_CURSOR_POS_X_LIMIT 1
#define GLFW_HAS_CURSOR_POS_Y_LIMIT 1

static GLFWerrorfun g_errorCallback = nullptr;
static bool g_initialized = false;
static GLFWwindow* g_currentWindow = nullptr;
static GLFWwindow* g_focusedWindow = nullptr;
static EGLContext g_currentEGLContext = EGL_NO_CONTEXT;
static EGLDisplay g_currentEGLDisplay = EGL_NO_DISPLAY;
static EGLSurface g_currentEGLSurface = EGL_NO_SURFACE;
static double g_timeBase = 0.0;
static int g_errorCode = 0;
static char g_errorDescription[512] = {0};
static GLFWallocator g_allocator = {nullptr, nullptr, nullptr, nullptr};

static EGLDisplay g_sharedDisplay = EGL_NO_DISPLAY;
static bool g_sharedDisplayInitialized = false;
static pthread_mutex_t g_eglInitMutex = PTHREAD_MUTEX_INITIALIZER;

static void* allocAlloc(size_t size, void* user) {
    (void)user;
    return malloc(size);
}

static void allocFree(void* block, void* user) {
    (void)user;
    free(block);
}

static void* allocRealloc(void* block, size_t newSize, void* user) {
    (void)user;
    return realloc(block, newSize);
}

static GLFWmonitor g_primaryMonitor = {"Android Display", 1920, 1080, 200, 150};
static GLFWmonitor* g_monitors[1] = {&g_primaryMonitor};

#define GLFW_HINTS_MAX 64
static int g_windowHints[GLFW_HINTS_MAX][2];
static int g_windowHintCount = 0;

static ANativeWindow* (*g_getNativeWindow)(void) = nullptr;

// 由 Java 侧 surfaceCreated 回调通过 nativeSetupBridgeWindow 直接注入的原生窗口。
// 拥有权：本库持有该 ANativeWindow 的引用，glfwCreateWindow 使用时需自行 acquire。
static ANativeWindow* g_ownedNativeWindow = nullptr;
static int g_ownedNativeWindowReady = 0;

static void resolveNativeWindowFunc() {
    // 已直接注入窗口时优先使用，无需跨命名空间查 versejvm 符号
    if (g_ownedNativeWindowReady && g_ownedNativeWindow) return;
    if (g_getNativeWindow) return;
    
    dlopen("libversejvm.so", RTLD_NOLOAD | RTLD_GLOBAL);
    
    g_getNativeWindow = (ANativeWindow* (*)(void))dlsym(RTLD_DEFAULT, "getFCLNativeWindow");
    if (!g_getNativeWindow) {
        g_getNativeWindow = (ANativeWindow* (*)(void))dlsym(RTLD_DEFAULT, "pojav_getNativeWindow");
    }
    if (g_getNativeWindow) {
        LOGI("Resolved getFCLNativeWindow/pojav_getNativeWindow: %p", g_getNativeWindow);
    } else {
        LOGW("Failed to resolve getFCLNativeWindow: %s", dlerror());
    }
}

// 跨命名空间窗口指针共享的文件路径由环境变量指定（Java 侧在 setupBridgeWindow 时 setenv）。
// 应用命名空间与 JVM 各持一份 libpojavexec.so，静态变量不共享，但 ANativeWindow 是 framework
// 引用计数对象（堆地址），指针跨命名空间有效，故用文件作为跨命名空间传递通道。
static const char* kWndFileEnv = "VERSE_NATIVE_WINDOW_FILE";

static void writePointerFile(ANativeWindow* w, const char* path) {
    if (!w || !path || !*path) return;
    FILE* f = fopen(path, "w");
    if (!f) {
        LOGW("writePointerFile: cannot open %s", path);
        return;
    }
    fprintf(f, "%llx\n", (unsigned long long)(uintptr_t)w);
    fclose(f);
    LOGI("writePointerFile: wrote %p to %s", w, path);
}

static ANativeWindow* readPointerFile() {
    const char* path = getenv(kWndFileEnv);
    if (!path || !*path) return nullptr;
    FILE* f = fopen(path, "r");
    if (!f) return nullptr;
    unsigned long long ptr = 0;
    int matched = fscanf(f, "%llx", &ptr);
    fclose(f);
    if (matched != 1 || !ptr) return nullptr;
    ANativeWindow* w = (ANativeWindow*)(uintptr_t)ptr;
    ANativeWindow_acquire(w);
    return w;
}

// 返回 glfwCreateWindow 应使用的 ANativeWindow。
// 返回值已持有引用（owned 窗口会 acquire，dlsym 路径本身会 acquire），调用方负责在销毁时 release。
ANativeWindow* getNativeWindowSafe() {
    resolveNativeWindowFunc();
    if (g_ownedNativeWindowReady && g_ownedNativeWindow) {
        ANativeWindow* w = g_ownedNativeWindow;
        ANativeWindow_acquire(w);
        return w;
    }
    if (g_getNativeWindow) {
        return g_getNativeWindow();
    }
    // 跨命名空间兜底：应用命名空间与 JVM 通过 dlopen 加载的是两份 libpojavexec.so，
    // 静态变量 g_ownedNativeWindow 不共享，但 ANativeWindow 是 framework 引用计数对象（堆地址），
    // 指针跨命名空间依然有效，可从 Java 侧写入的文件里恢复。
    ANativeWindow* w = readPointerFile();
    if (w) {
        g_ownedNativeWindow = w;
        g_ownedNativeWindowReady = 1;
        LOGI("getNativeWindowSafe: recovered owned window from file %p", w);
        ANativeWindow_acquire(w);
        return w;
    }
    return nullptr;
}

static void setError(int code, const char* description) {
    g_errorCode = code;
    if (description) {
        strncpy(g_errorDescription, description, sizeof(g_errorDescription) - 1);
        g_errorDescription[sizeof(g_errorDescription) - 1] = '\0';
    } else {
        g_errorDescription[0] = '\0';
    }
    if (g_errorCallback) {
        g_errorCallback(code, description);
    }
}

static EGLDisplay getSharedEGLDisplay() {
    pthread_mutex_lock(&g_eglInitMutex);
    if (g_sharedDisplay != EGL_NO_DISPLAY && g_sharedDisplayInitialized) {
        pthread_mutex_unlock(&g_eglInitMutex);
        return g_sharedDisplay;
    }
    if (!verseEglLoad()) {
        pthread_mutex_unlock(&g_eglInitMutex);
        LOGE("getSharedEGLDisplay: verseEglLoad failed");
        return EGL_NO_DISPLAY;
    }
    g_sharedDisplay = eglGetDisplay(EGL_DEFAULT_DISPLAY);
    if (g_sharedDisplay == EGL_NO_DISPLAY) {
        pthread_mutex_unlock(&g_eglInitMutex);
        LOGE("getSharedEGLDisplay: eglGetDisplay failed");
        return EGL_NO_DISPLAY;
    }
    if (!g_sharedDisplayInitialized) {
        EGLint major, minor;
        if (!eglInitialize(g_sharedDisplay, &major, &minor)) {
            pthread_mutex_unlock(&g_eglInitMutex);
            LOGE("getSharedEGLDisplay: eglInitialize failed: 0x%04x", eglGetError());
            g_sharedDisplay = EGL_NO_DISPLAY;
            return EGL_NO_DISPLAY;
        }
        g_sharedDisplayInitialized = true;
        LOGI("getSharedEGLDisplay: EGL initialized v%d.%d", major, minor);
    }
    pthread_mutex_unlock(&g_eglInitMutex);
    return g_sharedDisplay;
}

static EGLConfig selectEGLConfig(EGLDisplay display, GLFWwindow* window) {
    EGLint configAttribs[] = {
        EGL_RED_SIZE, 8,
        EGL_GREEN_SIZE, 8,
        EGL_BLUE_SIZE, 8,
        EGL_ALPHA_SIZE, 8,
        EGL_DEPTH_SIZE, 24,
        EGL_STENCIL_SIZE, 8,
        EGL_RENDERABLE_TYPE, EGL_OPENGL_ES3_BIT_KHR,
        EGL_SURFACE_TYPE, EGL_WINDOW_BIT,
        EGL_NONE
    };

    if (verseRendererIsDesktopGL()) {
        configAttribs[13] = EGL_OPENGL_ES2_BIT;
        configAttribs[15] = EGL_WINDOW_BIT | EGL_PBUFFER_BIT;
    }

    EGLConfig config = nullptr;
    EGLint numConfigs = 0;

    if (!eglChooseConfig(display, configAttribs, &config, 1, &numConfigs) || numConfigs == 0) {
        configAttribs[13] = EGL_OPENGL_ES2_BIT;
        if (verseRendererIsDesktopGL()) {
            configAttribs[15] = EGL_WINDOW_BIT | EGL_PBUFFER_BIT;
        }
        if (!eglChooseConfig(display, configAttribs, &config, 1, &numConfigs) || numConfigs == 0) {
            LOGE("Failed to choose EGL config");
            return nullptr;
        }
    }

    return config;
}

static EGLContext createEGLContext(EGLDisplay display, EGLConfig config, EGLContext share) {
    int clientVersion = 3;
    const char* es = getenv("LIBGL_ES");
    if (es != nullptr && *es) {
        int v = atoi(es);
        if (v >= 1 && v <= 3) clientVersion = v;
    }
    EGLint contextAttribs[] = {
        EGL_CONTEXT_CLIENT_VERSION, clientVersion,
        EGL_NONE
    };

    EGLContext context = eglCreateContext(display, config, share, contextAttribs);
    if (context == EGL_NO_CONTEXT) {
        contextAttribs[1] = 2;
        context = eglCreateContext(display, config, share, contextAttribs);
    }
    if (context == EGL_NO_CONTEXT) {
        LOGE("Failed to create EGL context: 0x%04x", eglGetError());
        return nullptr;
    }
    
    return context;
}

static EGLSurface createEGLSurface(EGLDisplay display, EGLConfig config, ANativeWindow* nativeWindow) {
    EGLint surfaceAttribs[] = {
        EGL_NONE
    };
    
    EGLSurface surface = eglCreateWindowSurface(display, config, nativeWindow, surfaceAttribs);
    if (surface == EGL_NO_SURFACE) {
        LOGE("Failed to create EGL surface: 0x%04x", eglGetError());
    }
    return surface;
}

#ifdef GLFW_INCLUDE_VULKAN
static GLFWvkproc g_vkGetInstanceProcAddr = nullptr;
#endif

static double getCurrentTime() {
    struct timespec ts;
    clock_gettime(CLOCK_MONOTONIC, &ts);
    return (double)ts.tv_sec + (double)ts.tv_nsec / 1e9;
}

static uint64_t getCurrentTimerValue() {
    struct timespec ts;
    clock_gettime(CLOCK_MONOTONIC, &ts);
    return (uint64_t)ts.tv_sec * 1000000000ULL + (uint64_t)ts.tv_nsec;
}

extern "C" {

void glfwDestroyWindow(GLFWwindow* window);

int glfwInit(void) {
    if (g_initialized) return GLFW_TRUE;

    LOGI("glfwInit: Custom Android EGL GLFW");

    verseSetupZinkEnvironment();
    resolveNativeWindowFunc();
    
    g_timeBase = getCurrentTime();
    g_initialized = true;
    g_errorCode = 0;
    g_errorDescription[0] = '\0';
    
    LOGI("glfwInit: Success");
    return GLFW_TRUE;
}

void glfwTerminate(void) {
    LOGI("glfwTerminate");
    g_initialized = false;
    
    if (g_currentWindow) {
        glfwDestroyWindow(g_currentWindow);
        g_currentWindow = nullptr;
    }
    
    g_focusedWindow = nullptr;
    g_currentEGLContext = EGL_NO_CONTEXT;
    g_currentEGLDisplay = EGL_NO_DISPLAY;
    g_currentEGLSurface = EGL_NO_SURFACE;
}

void glfwDefaultWindowHints(void) {
    g_windowHintCount = 0;
}

void glfwWindowHint(int hint, int value) {
    for (int i = 0; i < g_windowHintCount; i++) {
        if (g_windowHints[i][0] == hint) {
            g_windowHints[i][1] = value;
            return;
        }
    }
    if (g_windowHintCount < GLFW_HINTS_MAX) {
        g_windowHints[g_windowHintCount][0] = hint;
        g_windowHints[g_windowHintCount][1] = value;
        g_windowHintCount++;
    }
}

void glfwWindowHintString(int hint, const char* value) {
}

GLFWwindow* glfwCreateWindow(int width, int height, const char* title, GLFWmonitor* monitor, GLFWwindow* share) {
    LOGI("glfwCreateWindow: %dx%d title='%s' monitor=%d", width, height, title ? title : "null", monitor ? 1 : 0);
    
    if (!g_initialized) {
        setError(GLFW_NOT_INITIALIZED, "GLFW not initialized");
        return nullptr;
    }

    if (g_currentWindow) {
        LOGI("glfwCreateWindow: destroying previous window before creating new one");
        glfwDestroyWindow(g_currentWindow);
        g_currentWindow = nullptr;
        g_focusedWindow = nullptr;
    }
    
    ANativeWindow* nativeWindow = getNativeWindowSafe();
    
    LOGI("glfwCreateWindow: ANativeWindow=%p", nativeWindow);
    
    if (!nativeWindow) {
        LOGE("glfwCreateWindow: No native window available! getFCLNativeWindow/owned returned null");
        setError(GLFW_PLATFORM_ERROR, "No native window available. SurfaceView not initialized yet.");
        return nullptr;
    }
    
    GLFWwindow* window = (GLFWwindow*)calloc(1, sizeof(GLFWwindow));
    if (!window) {
        ANativeWindow_release(nativeWindow);
        setError(GLFW_OUT_OF_MEMORY, "Out of memory allocating window");
        return nullptr;
    }
    
    window->nativeWindow = nativeWindow;
    window->width = width;
    window->height = height;
    window->fbWidth = width;
    window->fbHeight = height;
    if (title) strncpy(window->title, title, sizeof(window->title) - 1);
    window->shouldClose = false;
    window->visible = true;
    window->focused = true;
    window->resizable = true;
    window->decorated = true;
    window->swapInterval = 1;
    window->cursorMode = GLFW_CURSOR_NORMAL;
    window->cursorX = width / 2.0;
    window->cursorY = height / 2.0;
    
    window->eglDisplay = getSharedEGLDisplay();
    if (window->eglDisplay == EGL_NO_DISPLAY) {
        LOGE("glfwCreateWindow: Failed to get shared EGL display");
        ANativeWindow_release(nativeWindow);
        free(window);
        setError(GLFW_PLATFORM_ERROR, "Failed to get shared EGL display");
        return nullptr;
    }

    if (verseRendererIsDesktopGL()) {
        eglBindAPI(EGL_OPENGL_API);
    } else {
        eglBindAPI(EGL_OPENGL_ES_API);
    }
    
    window->eglConfig = selectEGLConfig(window->eglDisplay, window);
    if (!window->eglConfig) {
        LOGE("glfwCreateWindow: Failed to select EGL config");
        ANativeWindow_release(nativeWindow);
        free(window);
        setError(GLFW_FORMAT_UNAVAILABLE, "Failed to select EGL config");
        return nullptr;
    }
    
    EGLContext shareContext = EGL_NO_CONTEXT;
    if (share && share->eglContext != EGL_NO_CONTEXT) {
        shareContext = share->eglContext;
    }
    window->eglContext = createEGLContext(window->eglDisplay, window->eglConfig, shareContext);
    if (window->eglContext == EGL_NO_CONTEXT) {
        LOGE("glfwCreateWindow: Failed to create EGL context");
        ANativeWindow_release(nativeWindow);
        free(window);
        setError(GLFW_VERSION_UNAVAILABLE, "Failed to create EGL context");
        return nullptr;
    }
    LOGI("glfwCreateWindow: EGL context created");
    
    ANativeWindow_setBuffersGeometry(nativeWindow, width, height, AHARDWAREBUFFER_FORMAT_R8G8B8A8_UNORM);
    
    window->eglSurface = createEGLSurface(window->eglDisplay, window->eglConfig, nativeWindow);
    if (window->eglSurface == EGL_NO_SURFACE) {
        LOGE("glfwCreateWindow: Failed to create EGL surface");
        eglDestroyContext(window->eglDisplay, window->eglContext);
        ANativeWindow_release(nativeWindow);
        free(window);
        setError(GLFW_PLATFORM_ERROR, "Failed to create EGL surface");
        return nullptr;
    }
    LOGI("glfwCreateWindow: EGL surface created");
    
    if (!eglMakeCurrent(window->eglDisplay, window->eglSurface, window->eglSurface, window->eglContext)) {
        LOGE("glfwCreateWindow: Failed to make EGL context current: 0x%04x", eglGetError());
        eglDestroySurface(window->eglDisplay, window->eglSurface);
        eglDestroyContext(window->eglDisplay, window->eglContext);
        ANativeWindow_release(nativeWindow);
        free(window);
        setError(GLFW_PLATFORM_ERROR, "Failed to make EGL context current");
        return nullptr;
    }
    LOGI("glfwCreateWindow: EGL makeCurrent success");
    
    eglSwapInterval(window->eglDisplay, window->swapInterval);
    
    g_currentWindow = window;
    g_focusedWindow = window;
    g_currentEGLDisplay = window->eglDisplay;
    g_currentEGLContext = window->eglContext;
    g_currentEGLSurface = window->eglSurface;
    
    int actualWidth = ANativeWindow_getWidth(nativeWindow);
    int actualHeight = ANativeWindow_getHeight(nativeWindow);
    if (actualWidth > 0 && actualHeight > 0) {
        window->width = actualWidth;
        window->height = actualHeight;
        window->fbWidth = actualWidth;
        window->fbHeight = actualHeight;
    }
    
    if (window->fbSizeCallback) {
        window->fbSizeCallback(window, window->fbWidth, window->fbHeight);
    }
    if (window->sizeCallback) {
        window->sizeCallback(window, window->width, window->height);
    }
    if (window->focusCallback) {
        window->focusCallback(window, GLFW_TRUE);
    }
    
    LOGI("glfwCreateWindow: SUCCESS %dx%d (fb: %dx%d)", 
         window->width, window->height, window->fbWidth, window->fbHeight);
    
    return window;
}

void glfwDestroyWindow(GLFWwindow* window) {
    LOGI("glfwDestroyWindow: %p", window);
    if (!window) return;
    
    if (g_currentWindow == window) {
        g_currentWindow = nullptr;
        g_currentEGLDisplay = EGL_NO_DISPLAY;
        g_currentEGLContext = EGL_NO_CONTEXT;
        g_currentEGLSurface = EGL_NO_SURFACE;
    }
    if (g_focusedWindow == window) {
        g_focusedWindow = nullptr;
    }
    
    if (window->eglSurface != EGL_NO_SURFACE || window->eglContext != EGL_NO_CONTEXT) {
        eglMakeCurrent(window->eglDisplay, EGL_NO_SURFACE, EGL_NO_SURFACE, EGL_NO_CONTEXT);
    }
    if (window->eglSurface != EGL_NO_SURFACE) {
        eglDestroySurface(window->eglDisplay, window->eglSurface);
    }
    if (window->eglContext != EGL_NO_CONTEXT) {
        eglDestroyContext(window->eglDisplay, window->eglContext);
    }
    if (window->nativeWindow) {
        ANativeWindow_release(window->nativeWindow);
        window->nativeWindow = nullptr;
    }
    
    free(window);
}

int glfwWindowShouldClose(GLFWwindow* window) {
    if (!window) return GLFW_TRUE;
    return window->shouldClose ? GLFW_TRUE : GLFW_FALSE;
}

void glfwSetWindowShouldClose(GLFWwindow* window, int value) {
    if (window) window->shouldClose = (value != GLFW_FALSE);
}

void glfwSetWindowTitle(GLFWwindow* window, const char* title) {
    if (window && title) {
        strncpy(window->title, title, sizeof(window->title) - 1);
        window->title[sizeof(window->title) - 1] = '\0';
        LOGI("glfwSetWindowTitle: %s", title);
    }
}

void glfwSetWindowIcon(GLFWwindow* window, int count, const GLFWimage* images) {
}

void glfwGetWindowPos(GLFWwindow* window, int* xpos, int* ypos) {
    if (xpos) *xpos = 0;
    if (ypos) *ypos = 0;
}

void glfwSetWindowPos(GLFWwindow* window, int xpos, int ypos) {
}

void glfwGetWindowSize(GLFWwindow* window, int* width, int* height) {
    if (window) {
        if (width) *width = window->width;
        if (height) *height = window->height;
    }
}

void glfwSetWindowSize(GLFWwindow* window, int width, int height) {
    if (!window) return;
    window->width = width;
    window->height = height;
    window->fbWidth = width;
    window->fbHeight = height;
    
    if (window->nativeWindow) {
        ANativeWindow_setBuffersGeometry(window->nativeWindow, width, height, AHARDWAREBUFFER_FORMAT_R8G8B8A8_UNORM);
    }
    if (window->sizeCallback) {
        window->sizeCallback(window, width, height);
    }
}

void glfwSetWindowSizeLimits(GLFWwindow* window, int minWidth, int minHeight, int maxWidth, int maxHeight) {
}

void glfwSetWindowAspectRatio(GLFWwindow* window, int numer, int denom) {
}

void glfwGetFramebufferSize(GLFWwindow* window, int* width, int* height) {
    if (window) {
        if (width) *width = window->fbWidth;
        if (height) *height = window->fbHeight;
    }
}

void glfwGetWindowFrameSize(GLFWwindow* window, int* left, int* top, int* right, int* bottom) {
    if (left) *left = 0;
    if (top) *top = 0;
    if (right) *right = 0;
    if (bottom) *bottom = 0;
}

void glfwGetWindowContentScale(GLFWwindow* window, float* xscale, float* yscale) {
    if (xscale) *xscale = 1.0f;
    if (yscale) *yscale = 1.0f;
}

float glfwGetWindowOpacity(GLFWwindow* window) {
    return 1.0f;
}

void glfwSetWindowOpacity(GLFWwindow* window, float opacity) {
}

void glfwIconifyWindow(GLFWwindow* window) {
    if (window) window->iconified = true;
}

void glfwRestoreWindow(GLFWwindow* window) {
    if (window) window->iconified = false;
}

void glfwMaximizeWindow(GLFWwindow* window) {
    if (window) window->maximized = true;
}

void glfwShowWindow(GLFWwindow* window) {
    if (window) window->visible = true;
}

void glfwHideWindow(GLFWwindow* window) {
    if (window) window->visible = false;
}

void glfwFocusWindow(GLFWwindow* window) {
    if (window) window->focused = true;
    if (g_focusedWindow && g_focusedWindow != window && g_focusedWindow->focusCallback) {
        g_focusedWindow->focusCallback(g_focusedWindow, GLFW_FALSE);
    }
    g_focusedWindow = window;
    if (window && window->focusCallback) {
        window->focusCallback(window, GLFW_TRUE);
    }
}

void glfwRequestWindowAttention(GLFWwindow* window) {
}

int glfwGetWindowAttrib(GLFWwindow* window, int attrib) {
    if (!window) return 0;
    switch (attrib) {
        case GLFW_FOCUSED: return window->focused;
        case GLFW_ICONIFIED: return window->iconified;
        case GLFW_MAXIMIZED: return window->maximized;
        case GLFW_VISIBLE: return window->visible;
        case GLFW_RESIZABLE: return window->resizable;
        case GLFW_DECORATED: return window->decorated;
        case GLFW_CLIENT_API: return GLFW_OPENGL_ES_API;
        case GLFW_CONTEXT_VERSION_MAJOR: return 3;
        case GLFW_CONTEXT_VERSION_MINOR: return 0;
        case GLFW_CONTEXT_ROBUSTNESS: return 0;
        case GLFW_OPENGL_FORWARD_COMPAT: return GLFW_FALSE;
        case GLFW_OPENGL_DEBUG_CONTEXT: return GLFW_FALSE;
        case GLFW_OPENGL_PROFILE: return 0;
        case GLFW_CONTEXT_RELEASE_BEHAVIOR: return 0;
        case GLFW_CONTEXT_NO_ERROR: return GLFW_FALSE;
        case GLFW_CONTEXT_CREATION_API: return GLFW_NATIVE_CONTEXT_API;
        case GLFW_SCALE_TO_MONITOR: return GLFW_FALSE;
        default: return 0;
    }
}

void glfwSetWindowAttrib(GLFWwindow* window, int attrib, int value) {
    if (!window) return;
    switch (attrib) {
        case GLFW_RESIZABLE: window->resizable = value != 0; break;
        case GLFW_DECORATED: window->decorated = value != 0; break;
        case GLFW_AUTO_ICONIFY: break;
        case GLFW_FLOATING: break;
        case GLFW_FOCUS_ON_SHOW: break;
    }
}

void glfwSetWindowUserPointer(GLFWwindow* window, void* pointer) {
    if (window) window->userPointer = pointer;
}

void* glfwGetWindowUserPointer(GLFWwindow* window) {
    return window ? window->userPointer : nullptr;
}

void glfwSetWindowPosCallback(GLFWwindow* window, GLFWwindowposfun callback) {
    if (window) window->posCallback = callback;
}

void glfwSetWindowSizeCallback(GLFWwindow* window, GLFWwindowsizefun callback) {
    if (window) window->sizeCallback = callback;
}

void glfwSetWindowCloseCallback(GLFWwindow* window, GLFWwindowclosefun callback) {
    if (window) window->closeCallback = callback;
}

void glfwSetWindowRefreshCallback(GLFWwindow* window, GLFWwindowrefreshfun callback) {
    if (window) window->refreshCallback = callback;
}

void glfwSetWindowFocusCallback(GLFWwindow* window, GLFWwindowfocusfun callback) {
    if (window) window->focusCallback = callback;
}

void glfwSetWindowIconifyCallback(GLFWwindow* window, GLFWwindowiconifyfun callback) {
    if (window) window->iconifyCallback = callback;
}

void glfwSetWindowMaximizeCallback(GLFWwindow* window, GLFWwindowmaximizefun callback) {
    if (window) window->maximizeCallback = callback;
}

void glfwSetFramebufferSizeCallback(GLFWwindow* window, GLFWframebuffersizefun callback) {
    if (window) {
        window->fbSizeCallback = callback;
    }
}

void glfwSetWindowContentScaleCallback(GLFWwindow* window, GLFWwindowcontentscalefun callback) {
    if (window) {
        window->scaleCallback = callback;
    }
}

void glfwPollEvents(void) {
    if (!g_focusedWindow) return;
}

void glfwWaitEvents(void) {
}

void glfwWaitEventsTimeout(double timeout) {
}

void glfwPostEmptyEvent(void) {
}

int glfwGetInputMode(GLFWwindow* window, int mode) {
    if (!window) return 0;
    if (mode == GLFW_CURSOR) return window->cursorMode;
    if (mode == GLFW_STICKY_KEYS) return window->stickyKeys;
    if (mode == GLFW_STICKY_MOUSE_BUTTONS) return window->stickyMouseButtons;
    if (mode == GLFW_LOCK_KEY_MODS) return window->lockKeyMods;
    if (mode == GLFW_RAW_MOUSE_MOTION) return window->rawMouseMotion;
    return 0;
}

void glfwSetInputMode(GLFWwindow* window, int mode, int value) {
    if (!window) return;
    if (mode == GLFW_CURSOR) window->cursorMode = value;
    if (mode == GLFW_STICKY_KEYS) window->stickyKeys = value;
    if (mode == GLFW_STICKY_MOUSE_BUTTONS) window->stickyMouseButtons = value;
}

int glfwRawMouseMotionSupported(void) {
    return GLFW_FALSE;
}

const char* glfwGetKeyName(int key, int scancode) {
    (void)scancode;
    switch (key) {
        case GLFW_KEY_SPACE: return "SPACE";
        case GLFW_KEY_APOSTROPHE: return "APOSTROPHE";
        case GLFW_KEY_COMMA: return "COMMA";
        case GLFW_KEY_MINUS: return "MINUS";
        case GLFW_KEY_PERIOD: return "PERIOD";
        case GLFW_KEY_SLASH: return "SLASH";
    }
    return nullptr;
}

int glfwGetKeyScancode(int key) {
    return key;
}

int glfwGetKey(GLFWwindow* window, int key) {
    if (!window) return GLFW_RELEASE;
    if (key < 0 || key > GLFW_KEY_LAST) return GLFW_RELEASE;
    return window->keyState[key] ? GLFW_PRESS : GLFW_RELEASE;
}

int glfwGetMouseButton(GLFWwindow* window, int button) {
    if (!window) return GLFW_RELEASE;
    if (button < 0 || button > GLFW_MOUSE_BUTTON_LAST) return GLFW_RELEASE;
    return window->mouseButtonState[button] ? GLFW_PRESS : GLFW_RELEASE;
}

void glfwGetCursorPos(GLFWwindow* window, double* xpos, double* ypos) {
    if (window) {
        if (xpos) *xpos = window->cursorX;
        if (ypos) *ypos = window->cursorY;
    }
}

void glfwSetCursorPos(GLFWwindow* window, double xpos, double ypos) {
    if (window) {
        window->cursorX = xpos;
        window->cursorY = ypos;
    }
}

GLFWcursor* glfwCreateCursor(const GLFWimage* image, int xhot, int yhot) {
    return nullptr;
}

GLFWcursor* glfwCreateStandardCursor(int shape) {
    return nullptr;
}

void glfwDestroyCursor(GLFWcursor* cursor) {
}

void glfwSetCursor(GLFWwindow* window, GLFWcursor* cursor) {
}

void glfwSetKeyCallback(GLFWwindow* window, GLFWkeyfun callback) {
    if (window) window->keyCallback = callback;
}

void glfwSetCharCallback(GLFWwindow* window, GLFWcharfun callback) {
    if (window) window->charCallback = callback;
}

void glfwSetCharModsCallback(GLFWwindow* window, GLFWcharmodsfun callback) {
    if (window) window->charModsCallback = callback;
}

void glfwSetMouseButtonCallback(GLFWwindow* window, GLFWmousebuttonfun callback) {
    if (window) window->mouseButtonCallback = callback;
}

void glfwSetCursorPosCallback(GLFWwindow* window, GLFWcursorposfun callback) {
    if (window) window->cursorPosCallback = callback;
}

void glfwSetCursorEnterCallback(GLFWwindow* window, GLFWcursorenterfun callback) {
    if (window) window->cursorEnterCallback = callback;
}

void glfwSetScrollCallback(GLFWwindow* window, GLFWscrollfun callback) {
    if (window) window->scrollCallback = callback;
}

void glfwSetDropCallback(GLFWwindow* window, GLFWdropfun callback) {
    if (window) window->dropCallback = callback;
}

int glfwJoystickPresent(int jid) {
    return GLFW_FALSE;
}

const float* glfwGetJoystickAxes(int jid, int* count) {
    if (count) *count = 0;
    return nullptr;
}

const unsigned char* glfwGetJoystickButtons(int jid, int* count) {
    if (count) *count = 0;
    return nullptr;
}

const unsigned char* glfwGetJoystickHats(int jid, int* count) {
    if (count) *count = 0;
    return nullptr;
}

const char* glfwGetJoystickName(int jid) {
    return nullptr;
}

const char* glfwGetJoystickGUID(int jid) {
    return nullptr;
}

void glfwSetJoystickUserPointer(int jid, void* pointer) {
}

void* glfwGetJoystickUserPointer(int jid) {
    return nullptr;
}

int glfwJoystickIsGamepad(int jid) {
    return GLFW_FALSE;
}

int glfwUpdateGamepadMappings(const char* string) {
    return GLFW_FALSE;
}

const char* glfwGetGamepadName(int jid) {
    return nullptr;
}

int glfwGetGamepadState(int jid, GLFWgamepadstate* state) {
    return GLFW_FALSE;
}

void glfwSetClipboardString(GLFWwindow* window, const char* string) {
}

const char* glfwGetClipboardString(GLFWwindow* window) {
    return "";
}

double glfwGetTime(void) {
    return getCurrentTime() - g_timeBase;
}

void glfwSetTime(double time) {
    g_timeBase = getCurrentTime() - time;
}

uint64_t glfwGetTimerValue(void) {
    return getCurrentTimerValue();
}

uint64_t glfwGetTimerFrequency(void) {
    return 1000000000ULL;
}

void glfwMakeContextCurrent(GLFWwindow* window) {
    if (window) {
        EGLBoolean result = eglMakeCurrent(window->eglDisplay, window->eglSurface, window->eglSurface, window->eglContext);
        if (!result) {
            LOGE("glfwMakeContextCurrent: eglMakeCurrent failed: 0x%04x", eglGetError());
        }
        g_currentEGLDisplay = window->eglDisplay;
        g_currentEGLContext = window->eglContext;
        g_currentEGLSurface = window->eglSurface;
        g_currentWindow = window;
    } else {
        if (g_currentEGLDisplay != EGL_NO_DISPLAY) {
            eglMakeCurrent(g_currentEGLDisplay, EGL_NO_SURFACE, EGL_NO_SURFACE, EGL_NO_CONTEXT);
        }
        g_currentEGLDisplay = EGL_NO_DISPLAY;
        g_currentEGLContext = EGL_NO_CONTEXT;
        g_currentEGLSurface = EGL_NO_SURFACE;
        g_currentWindow = nullptr;
    }
}

GLFWwindow* glfwGetCurrentContext(void) {
    return g_currentWindow;
}

void glfwSwapBuffers(GLFWwindow* window) {
    if (window && window->eglDisplay != EGL_NO_DISPLAY && window->eglSurface != EGL_NO_SURFACE) {
        eglSwapBuffers(window->eglDisplay, window->eglSurface);
    }
}

void glfwSwapInterval(int interval) {
    if (g_currentWindow) {
        g_currentWindow->swapInterval = interval;
        if (g_currentWindow->eglDisplay != EGL_NO_DISPLAY) {
            eglSwapInterval(g_currentWindow->eglDisplay, interval);
        }
    }
}

int glfwExtensionSupported(const char* extension) {
    if (!extension) return GLFW_FALSE;
    if (g_currentEGLDisplay == EGL_NO_DISPLAY) return GLFW_FALSE;
    const char* extensions = (const char*)eglQueryString(g_currentEGLDisplay, EGL_EXTENSIONS);
    if (!extensions) return GLFW_FALSE;
    const char* start = extensions;
    size_t extLen = strlen(extension);
    while (*start) {
        const char* end = start;
        while (*end && *end != ' ') end++;
        size_t len = end - start;
        if (len == extLen && strncmp(start, extension, extLen) == 0) {
            return GLFW_TRUE;
        }
        start = end;
        while (*start == ' ') start++;
    }
    return GLFW_FALSE;
}

GLFWglproc glfwGetProcAddress(const char* procname) {
    return (GLFWglproc)eglGetProcAddress(procname);
}

int glfwVulkanSupported(void) {
    return GLFW_FALSE;
}

#ifndef GLFW_INCLUDE_VULKAN

const char** glfwGetRequiredInstanceExtensions(uint32_t* count) {
    if (count) *count = 0;
    return nullptr;
}

#ifdef __cplusplus
extern "C" {
#endif
typedef void* VkInstance;
typedef void* VkPhysicalDevice;
typedef void* VkSurfaceKHR;
typedef unsigned int VkResult;
typedef int VkFlags;
struct VkAllocationCallbacks { void* dummy; };
#define VK_ERROR_EXTENSION_NOT_PRESENT (-1000000001)
#ifdef __cplusplus
}
#endif

GLFWvkproc glfwGetInstanceProcAddress(VkInstance instance, const char* procname) {
    (void)instance;
    (void)procname;
    return nullptr;
}

int glfwGetPhysicalDevicePresentationSupport(VkInstance instance, VkPhysicalDevice device, uint32_t queuefamily) {
    (void)instance;
    (void)device;
    (void)queuefamily;
    return GLFW_FALSE;
}

VkResult glfwCreateWindowSurface(VkInstance instance, GLFWwindow* window, const VkAllocationCallbacks* allocator, VkSurfaceKHR* surface) {
    (void)instance;
    (void)window;
    (void)allocator;
    (void)surface;
    return VK_ERROR_EXTENSION_NOT_PRESENT;
}

#else
#include <vulkan/vulkan.h>

GLFWvkproc glfwGetInstanceProcAddress(VkInstance instance, const char* procname) {
    if (g_vkGetInstanceProcAddr) {
        return g_vkGetInstanceProcAddr(instance, procname);
    }
    return nullptr;
}

#endif

GLFWmonitor** glfwGetMonitors(int* count) {
    if (count) *count = 1;
    return g_monitors;
}

GLFWmonitor* glfwGetPrimaryMonitor(void) {
    return &g_primaryMonitor;
}

GLFWmonitor* glfwGetWindowMonitor(GLFWwindow* window) {
    // 模拟器/Android 单屏场景：窗口始终依附于主显示器
    return &g_primaryMonitor;
}

void glfwGetMonitorPos(GLFWmonitor* monitor, int* xpos, int* ypos) {
    if (xpos) *xpos = 0;
    if (ypos) *ypos = 0;
}

void glfwGetMonitorWorkarea(GLFWmonitor* monitor, int* xpos, int* ypos, int* width, int* height) {
    if (xpos) *xpos = 0;
    if (ypos) *ypos = 0;
    if (width) *width = g_focusedWindow ? g_focusedWindow->width : 1920;
    if (height) *height = g_focusedWindow ? g_focusedWindow->height : 1080;
}

void glfwGetMonitorPhysicalSize(GLFWmonitor* monitor, int* widthMM, int* heightMM) {
    if (widthMM) *widthMM = 200;
    if (heightMM) *heightMM = 150;
}

void glfwGetMonitorContentScale(GLFWmonitor* monitor, float* xscale, float* yscale) {
    if (xscale) *xscale = 1.0f;
    if (yscale) *yscale = 1.0f;
}

const char* glfwGetMonitorName(GLFWmonitor* monitor) {
    return "Android Display";
}

void glfwSetMonitorUserPointer(GLFWmonitor* monitor, void* pointer) {
}

void* glfwGetMonitorUserPointer(GLFWmonitor* monitor) {
    return nullptr;
}

GLFWmonitorfun glfwSetMonitorCallback(GLFWmonitorfun callback) {
    return nullptr;
}

const GLFWvidmode* glfwGetVideoModes(GLFWmonitor* monitor, int* count) {
    static GLFWvidmode modes[1];
    if (g_focusedWindow) {
        modes[0].width = g_focusedWindow->width;
        modes[0].height = g_focusedWindow->height;
    } else {
        modes[0].width = 1920;
        modes[0].height = 1080;
    }
    modes[0].redBits = 8;
    modes[0].greenBits = 8;
    modes[0].blueBits = 8;
    modes[0].refreshRate = 60;
    if (count) *count = 1;
    return modes;
}

const GLFWvidmode* glfwGetVideoMode(GLFWmonitor* monitor) {
    static GLFWvidmode mode;
    if (g_focusedWindow) {
        mode.width = g_focusedWindow->width;
        mode.height = g_focusedWindow->height;
    } else {
        mode.width = 1920;
        mode.height = 1080;
    }
    mode.redBits = 8;
    mode.greenBits = 8;
    mode.blueBits = 8;
    mode.refreshRate = 60;
    return &mode;
}

void glfwSetGamma(GLFWmonitor* monitor, float gamma) {
}

const GLFWgammaramp* glfwGetGammaRamp(GLFWmonitor* monitor) {
    static unsigned short g_rampIdentity[256];
    static bool g_rampInit = false;
    if (!g_rampInit) {
        for (int i = 0; i < 256; i++) {
            g_rampIdentity[i] = (unsigned short)(i * 257);
        }
        g_rampInit = true;
    }
    static GLFWgammaramp ramp;
    ramp.red = g_rampIdentity;
    ramp.green = g_rampIdentity;
    ramp.blue = g_rampIdentity;
    ramp.size = 256;
    return &ramp;
}

void glfwSetGammaRamp(GLFWmonitor* monitor, const GLFWgammaramp* ramp) {
}

GLFWmonitor* glfwSetWindowMonitor(GLFWwindow* window, GLFWmonitor* monitor, int xpos, int ypos, int width, int height, int refreshRate) {
    if (window) {
        window->width = width;
        window->height = height;
        window->fbWidth = width;
        window->fbHeight = height;
    }
    return nullptr;
}

GLFWerrorfun glfwSetErrorCallback(GLFWerrorfun callback) {
    GLFWerrorfun previous = g_errorCallback;
    g_errorCallback = callback;
    return previous;
}

int glfwGetError(const char** description) {
    int code = g_errorCode;
    if (description) *description = g_errorDescription;
    g_errorCode = 0;
    return code;
}

void glfwSetJoystickCallback(GLFWjoystickfun callback) {
}

const char* glfwGetVersionString(void) {
    return "3.3.8 Verse Android EGL";
}

void glfwGetVersion(int* major, int* minor, int* rev) {
    if (major) *major = 3;
    if (minor) *minor = 3;
    if (rev) *rev = 8;
}

int glfwPlatformSupported(int platform) {
    return GLFW_FALSE;
}

void* glfwGetNativeWindowHandle(GLFWwindow* window) {
    return window ? window->nativeWindow : nullptr;
}

void glfwInitHint(int hint, int value) {
}

void glfwInitAllocator(const GLFWallocator* allocator) {
    if (allocator) {
        g_allocator = *allocator;
    }
}

void glfwInitVulkanLoader(GLFWvkproc loader) {
#ifdef GLFW_INCLUDE_VULKAN
    g_vkGetInstanceProcAddr = loader;
#endif
}

int glfwGetPlatform(void) {
    return 0; // GLFW_PLATFORM_NULL or custom
}

JNIEXPORT void JNICALL
Java_com_verse_explorer_launcher_bridge_CallbackBridge_nativeSendData(
    JNIEnv *env, jclass clazz, jboolean isAndroid, jint type, jint i1, jint i2, jint i3, jint i4) {
    verseDispatchInput(type, i1, i2, i3, i4);
}

JNIEXPORT void JNICALL
Java_com_verse_explorer_launcher_bridge_CallbackBridge_nativeSetupBridgeWindow(
    JNIEnv *env, jclass clazz, jobject surface, jstring windowFilePath) {
    if (!surface) {
        LOGW("nativeSetupBridgeWindow: null surface");
        return;
    }
    ANativeWindow* newWindow = ANativeWindow_fromSurface(env, surface);
    if (!newWindow) {
        LOGE("nativeSetupBridgeWindow: ANativeWindow_fromSurface failed");
        return;
    }
    ANativeWindow* oldWindow = g_ownedNativeWindow;
    g_ownedNativeWindow = newWindow;
    g_ownedNativeWindowReady = 1;
    if (oldWindow) {
        ANativeWindow_release(oldWindow);
    }
    // 记录指针文件路径并写入指针，供 JVM 命名空间内的另一份 libpojavexec.so 恢复窗口。
    if (windowFilePath) {
        const char* path = env->GetStringUTFChars(windowFilePath, nullptr);
        if (path) {
            setenv(kWndFileEnv, path, 1);
            writePointerFile(newWindow, path);
            env->ReleaseStringUTFChars(windowFilePath, path);
        }
    }
    LOGI("nativeSetupBridgeWindow: owned window set to %p (%dx%d)",
         g_ownedNativeWindow,
         ANativeWindow_getWidth(newWindow),
         ANativeWindow_getHeight(newWindow));
}

JNIEXPORT void JNICALL
Java_com_verse_explorer_launcher_bridge_CallbackBridge_nativeSetWindowAttribNative(
    JNIEnv *env, jclass clazz, jint attrib, jint value) {
    GLFWwindow* window = g_focusedWindow;
    if (!window) return;
    if (attrib == 0x00020005) {
        window->decorated = (value == GLFW_TRUE);
    } else if (attrib == 0x00020007) {
        window->floating = (value == GLFW_TRUE);
    }
}

JNIEXPORT jint JNICALL
Java_com_verse_explorer_launcher_bridge_CallbackBridge_nativeGetFps(
    JNIEnv *env, jclass clazz) {
    return 0;
}

static bool g_useInputStackQueue = false;

JNIEXPORT void JNICALL
Java_com_verse_explorer_launcher_bridge_CallbackBridge_nativeSetUseInputStackQueue(
    JNIEnv *env, jclass clazz, jboolean useInputStackQueue) {
    g_useInputStackQueue = (useInputStackQueue == JNI_TRUE);
    verseSetStackQueue(g_useInputStackQueue);
    LOGI("Set useInputStackQueue (Verse): %d", g_useInputStackQueue);
}

JNIEXPORT void JNICALL
Java_org_lwjgl_glfw_CallbackBridge_nativeSetUseInputStackQueue(
    JNIEnv *env, jclass clazz, jboolean useInputStackQueue) {
    g_useInputStackQueue = (useInputStackQueue == JNI_TRUE);
    verseSetStackQueue(g_useInputStackQueue);
    LOGI("Set useInputStackQueue (LWJGL): %d", g_useInputStackQueue);
}

JNIEXPORT void JNICALL JavaCritical_org_lwjgl_glfw_CallbackBridge_nativeSetUseInputStackQueue(
    jboolean useInputStackQueue) {
    g_useInputStackQueue = (useInputStackQueue == JNI_TRUE);
    verseSetStackQueue(g_useInputStackQueue);
    LOGI("Set useInputStackQueue (critical): %d", g_useInputStackQueue);
}

} // extern "C"