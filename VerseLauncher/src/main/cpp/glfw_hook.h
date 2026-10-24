#pragma once

#include <stdint.h>
#include <stddef.h>

struct GLFWwindow;
struct GLFWmonitor;
struct GLFWcursor;

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

#ifdef __cplusplus
extern "C" {
#endif

int glfwInit(void);
void glfwTerminate(void);
void glfwDefaultWindowHints(void);
void glfwWindowHint(int hint, int value);
void glfwWindowHintString(int hint, const char* value);
GLFWwindow* glfwCreateWindow(int width, int height, const char* title, GLFWmonitor* monitor, GLFWwindow* share);
void glfwDestroyWindow(GLFWwindow* window);
int glfwWindowShouldClose(GLFWwindow* window);
void glfwSetWindowShouldClose(GLFWwindow* window, int value);
void glfwSetWindowTitle(GLFWwindow* window, const char* title);
void glfwSetWindowIcon(GLFWwindow* window, int count, const GLFWimage* images);
void glfwGetWindowPos(GLFWwindow* window, int* xpos, int* ypos);
void glfwSetWindowPos(GLFWwindow* window, int xpos, int ypos);
void glfwGetWindowSize(GLFWwindow* window, int* width, int* height);
void glfwSetWindowSize(GLFWwindow* window, int width, int height);
void glfwSetWindowSizeLimits(GLFWwindow* window, int minWidth, int minHeight, int maxWidth, int maxHeight);
void glfwSetWindowAspectRatio(GLFWwindow* window, int numer, int denom);
void glfwGetFramebufferSize(GLFWwindow* window, int* width, int* height);
void glfwGetWindowFrameSize(GLFWwindow* window, int* left, int* top, int* right, int* bottom);
void glfwGetWindowContentScale(GLFWwindow* window, float* xscale, float* yscale);
float glfwGetWindowOpacity(GLFWwindow* window);
void glfwSetWindowOpacity(GLFWwindow* window, float opacity);
void glfwIconifyWindow(GLFWwindow* window);
void glfwRestoreWindow(GLFWwindow* window);
void glfwMaximizeWindow(GLFWwindow* window);
void glfwShowWindow(GLFWwindow* window);
void glfwHideWindow(GLFWwindow* window);
void glfwFocusWindow(GLFWwindow* window);
void glfwRequestWindowAttention(GLFWwindow* window);
int glfwGetWindowAttrib(GLFWwindow* window, int attrib);
void glfwSetWindowAttrib(GLFWwindow* window, int attrib, int value);
void glfwSetWindowUserPointer(GLFWwindow* window, void* pointer);
void* glfwGetWindowUserPointer(GLFWwindow* window);
void glfwSetWindowPosCallback(GLFWwindow* window, GLFWwindowposfun callback);
void glfwSetWindowSizeCallback(GLFWwindow* window, GLFWwindowsizefun callback);
void glfwSetWindowCloseCallback(GLFWwindow* window, GLFWwindowclosefun callback);
void glfwSetWindowRefreshCallback(GLFWwindow* window, GLFWwindowrefreshfun callback);
void glfwSetWindowFocusCallback(GLFWwindow* window, GLFWwindowfocusfun callback);
void glfwSetWindowIconifyCallback(GLFWwindow* window, GLFWwindowiconifyfun callback);
void glfwSetWindowMaximizeCallback(GLFWwindow* window, GLFWwindowmaximizefun callback);
void glfwSetFramebufferSizeCallback(GLFWwindow* window, GLFWframebuffersizefun callback);
void glfwSetWindowContentScaleCallback(GLFWwindow* window, GLFWwindowcontentscalefun callback);
void glfwPollEvents(void);
void glfwWaitEvents(void);
void glfwWaitEventsTimeout(double timeout);
void glfwPostEmptyEvent(void);
int glfwGetInputMode(GLFWwindow* window, int mode);
void glfwSetInputMode(GLFWwindow* window, int mode, int value);
int glfwRawMouseMotionSupported(void);
const char* glfwGetKeyName(int key, int scancode);
int glfwGetKeyScancode(int key);
int glfwGetKey(GLFWwindow* window, int key);
int glfwGetMouseButton(GLFWwindow* window, int button);
void glfwGetCursorPos(GLFWwindow* window, double* xpos, double* ypos);
void glfwSetCursorPos(GLFWwindow* window, double xpos, double ypos);
void glfwSetClipboardString(GLFWwindow* window, const char* string);
const char* glfwGetClipboardString(GLFWwindow* window);
GLFWcursor* glfwCreateCursor(const GLFWimage* image, int xhot, int yhot);
GLFWcursor* glfwCreateStandardCursor(int shape);
void glfwDestroyCursor(GLFWcursor* cursor);
void glfwSetCursor(GLFWwindow* window, GLFWcursor* cursor);
void glfwSetKeyCallback(GLFWwindow* window, GLFWkeyfun callback);
void glfwSetCharCallback(GLFWwindow* window, GLFWcharfun callback);
void glfwSetCharModsCallback(GLFWwindow* window, GLFWcharmodsfun callback);
void glfwSetMouseButtonCallback(GLFWwindow* window, GLFWmousebuttonfun callback);
void glfwSetCursorPosCallback(GLFWwindow* window, GLFWcursorposfun callback);
void glfwSetCursorEnterCallback(GLFWwindow* window, GLFWcursorenterfun callback);
void glfwSetScrollCallback(GLFWwindow* window, GLFWscrollfun callback);
void glfwSetDropCallback(GLFWwindow* window, GLFWdropfun callback);
int glfwJoystickPresent(int jid);
const float* glfwGetJoystickAxes(int jid, int* count);
const unsigned char* glfwGetJoystickButtons(int jid, int* count);
const unsigned char* glfwGetJoystickHats(int jid, int* count);
const char* glfwGetJoystickName(int jid);
const char* glfwGetJoystickGUID(int jid);
void glfwSetJoystickUserPointer(int jid, void* pointer);
void* glfwGetJoystickUserPointer(int jid);
int glfwJoystickIsGamepad(int jid);
int glfwUpdateGamepadMappings(const char* string);
const char* glfwGetGamepadName(int jid);
int glfwGetGamepadState(int jid, GLFWgamepadstate* state);
double glfwGetTime(void);
void glfwSetTime(double time);
uint64_t glfwGetTimerValue(void);
uint64_t glfwGetTimerFrequency(void);
void glfwMakeContextCurrent(GLFWwindow* window);
GLFWwindow* glfwGetCurrentContext(void);
void glfwSwapBuffers(GLFWwindow* window);
void glfwSwapInterval(int interval);
int glfwExtensionSupported(const char* extension);
GLFWglproc glfwGetProcAddress(const char* procname);
int glfwVulkanSupported(void);
const char** glfwGetRequiredInstanceExtensions(uint32_t* count);
GLFWmonitor** glfwGetMonitors(int* count);
GLFWmonitor* glfwGetPrimaryMonitor(void);
void glfwGetMonitorPos(GLFWmonitor* monitor, int* xpos, int* ypos);
void glfwGetMonitorWorkarea(GLFWmonitor* monitor, int* xpos, int* ypos, int* width, int* height);
void glfwGetMonitorPhysicalSize(GLFWmonitor* monitor, int* widthMM, int* heightMM);
void glfwGetMonitorContentScale(GLFWmonitor* monitor, float* xscale, float* yscale);
const char* glfwGetMonitorName(GLFWmonitor* monitor);
void glfwSetMonitorUserPointer(GLFWmonitor* monitor, void* pointer);
void* glfwGetMonitorUserPointer(GLFWmonitor* monitor);
GLFWmonitorfun glfwSetMonitorCallback(GLFWmonitorfun callback);
const GLFWvidmode* glfwGetVideoModes(GLFWmonitor* monitor, int* count);
const GLFWvidmode* glfwGetVideoMode(GLFWmonitor* monitor);
void glfwSetGamma(GLFWmonitor* monitor, float gamma);
const GLFWgammaramp* glfwGetGammaRamp(GLFWmonitor* monitor);
void glfwSetGammaRamp(GLFWmonitor* monitor, const GLFWgammaramp* ramp);
GLFWmonitor* glfwSetWindowMonitor(GLFWwindow* window, GLFWmonitor* monitor, int xpos, int ypos, int width, int height, int refreshRate);
void glfwSetErrorCallback(GLFWerrorfun callback);
int glfwGetError(const char** description);
void glfwSetJoystickCallback(GLFWjoystickfun callback);
const char* glfwGetVersionString(void);
void glfwGetVersion(int* major, int* minor, int* rev);
int glfwPlatformSupported(int platform);
void* glfwGetNativeWindowHandle(GLFWwindow* window);
void glfwInitHint(int hint, int value);
void glfwInitAllocator(const GLFWallocator* allocator);
void glfwInitVulkanLoader(GLFWvkproc loader);
int glfwGetPlatform(void);

#ifdef __cplusplus
}
#endif
