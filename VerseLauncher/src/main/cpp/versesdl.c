/*
 * libversesdl.so — SDL3 侧 GL 函数指针与 LWJGL 侧（gl4es）对齐
 *
 * MC 26.3（RenderPearl）在初始化 OpenGL 后端时校验
 *   LWJGL:  GL.getFunctionProvider().getFunctionAddress("glGetError")
 *   SDL:    SDL_GL_GetProcAddress("glGetError")
 * 两指针一致，否则抛 "glGetError mismatch"。
 * LWJGL 侧解析自 libng_gl4es.so（-Dorg.lwjgl.opengl.libname），
 * 而 SDL3 Android 后端经 eglGetProcAddress 返回系统 Mali 驱动实现，二者必然不一致。
 * 这里接管 dlsym 出口（LWJGL 的 DynamicLinkLoader.ndlsym 内部调用 libc dlsym），
 * 将 SDL_GL_GetProcAddress 替换为代理：以 "gl" 前缀的函数一律从 libng_gl4es.so 解析，
 * 其余透传真实 SDL 实现。同时 bytehook SDL_GL_GetProcAddress 自身，
 * 覆盖 SDL 库内部经 GOT 的直接调用路径。
 */
#include <jni.h>
#include <dlfcn.h>
#include <stdio.h>
#include <stdarg.h>
#include <stdbool.h>
#include <string.h>
#include <stdlib.h>
#include <unistd.h>
#include <sys/stat.h>
#include <android/log.h>
#include <vulkan/vulkan.h>

#define LOG_TAG "VerseSDL"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

/* 文件日志：logcat 在启动早期可能被冲掉/不可靠，关键路径同步落盘（VERSE_SDL_LOG_PATH） */
static void file_log(const char *fmt, ...) {
    const char *path = getenv("VERSE_SDL_LOG_PATH");
    if (path == NULL || path[0] == '\0') return;
    FILE *f = fopen(path, "a");
    if (f == NULL) return;
    va_list ap;
    va_start(ap, fmt);
    vfprintf(f, fmt, ap);
    va_end(ap);
    fputc('\n', f);
    fclose(f);
}

/* ---- 最小 bytehook 声明（libbytehook.so 为官方 1.0.10，与 FCL 同版本，工程内无头文件） ----
 * 注意 1.0.10 的 API：bytehook_init(bytehook_mode_t mode, bool debug)，mode 用
 * BYTEHOOK_MODE_AUTOMATIC(2)；FCL fcl_loader.c 同款调用：init(AUTOMATIC, false)。
 *
 * 不用 System.loadLibrary("bytehook") 加载：官方 libbytehook.so 的 JNI_OnLoad 在部分
 * 系统（Android 14/15 ART）上初始化失败返回 JNI_ERR，System.loadLibrary 直接抛
 * UnsatisfiedLinkError。FCL 是静态链接进 libfcl.so 的，从不触发该路径；这里改为
 * JNI_OnLoad 内 dlopen + dlsym 运行时解析（dlopen 不调用 JNI_OnLoad，绕开失败）。 */
typedef struct bytehook_stub *bytehook_stub_t;
typedef int bytehook_mode_t;

#define BYTEHOOK_MODE_AUTOMATIC 2
#define BYTEHOOK_STATUS_CODE_OK 0
/* bytehook 只允许 init 一次：versejvm 的 installExitHook 已先 init 成功时，
 * 这里再次 init 会返回 INIT_ALREADY_CALLED(4)，此时 bytehook 可用，继续装 hook。 */
#define BYTEHOOK_STATUS_CODE_INIT_ALREADY_CALLED 4

typedef bytehook_stub_t (*bytehook_hook_all_fn_t)(const char *callee_path_name,
                                                  const char *sym_name,
                                                  void *new_func, void *old_func, void *data);
typedef bytehook_stub_t (*bytehook_hook_single_fn_t)(const char *caller_path_name,
                                                     const char *callee_path_name,
                                                     const char *sym_name,
                                                     void *new_func, void *old_func, void *data);
typedef void (*bytehook_add_dlopen_callback_fn_t)(void (*callback)(const char *filename,
                                                                   void *data), void *data);
typedef int (*bytehook_init_fn_t)(bytehook_mode_t mode, bool debug);

static bytehook_hook_all_fn_t fn_hook_all;
static bytehook_hook_single_fn_t fn_hook_single;
static bytehook_add_dlopen_callback_fn_t fn_add_dlopen_callback;
static bytehook_init_fn_t fn_init;

static void *sdl3_handle;
static bool sdl_hooks_installed = false;

/* ---- 真实 dlsym（在 hook 安装前缓存，代理内直接调用避免递归） ---- */
typedef void *(*dlsym_t)(void *handle, const char *symbol);
static dlsym_t real_dlsym_fn;

typedef void *(*sdlGLGetProcAddress_t)(const char *proc);
static sdlGLGetProcAddress_t real_sdl_gl_get_proc_address;

/* 渲染器 GL 库句柄：跟随 FCLauncher 注入的 SDL_OPENGL_LIBRARY（渲染器 GL 库绝对路径），
 * 与 LWJGL 侧 org.lwjgl.opengl.libname 保持同一实现，SDL 与 LWJGL 的 gl* 函数
 * 同源（RenderPearl 的 glGetError mismatch 校验才能通过）。
 * 不同渲染器（nggl4es/mobileglues 等）各带独立 GL 库，不能写死单一库名。 */
static void *gl4es_handle;

static void resolve_from_gl4es_path(char *path, size_t cap) {
    const char *appLibDir = getenv("VERSE_APP_LIB_DIR");
    const char *glLib = getenv("SDL_OPENGL_LIBRARY");
    if (glLib != NULL && glLib[0] != '\0') {
        if (glLib[0] == '/') {
            snprintf(path, cap, "%s", glLib);
        } else if (appLibDir != NULL && appLibDir[0] != '\0') {
            snprintf(path, cap, "%s/%s", appLibDir, glLib);
        }
    }
    if (path[0] == '\0' && appLibDir != NULL && appLibDir[0] != '\0') {
        snprintf(path, cap, "%s/libng_gl4es.so", appLibDir);
    }
}

static void *resolve_from_gl4es(const char *proc) {
    static bool resolved_once = false;
    if (gl4es_handle == NULL) {
        /* LWJGL 的 org.lwjgl.opengl.libname=<gl> 在 org.lwjgl.librarypath
         * （lwjgl natives 目录）找不到后从 "system paths"（= nativeLibraryDir）
         * 加载，日志形如 "Loaded from system paths: /data/app/.../lib/arm64/lib*.so"。
         * bionic dlopen 按完全相同路径去重返回同一句柄，因此必须用
         * SDL_OPENGL_LIBRARY 的绝对路径（= nativeLibraryDir）构造，
         * 不能拿 files/natives 的拷贝路径（那是另一份 inode/副本）。 */
        char path[512] = {0};
        resolve_from_gl4es_path(path, sizeof(path));
        if (path[0] != '\0') {
            gl4es_handle = dlopen(path, RTLD_NOW);
            file_log("resolve_from_gl4es: dlopen(%s) -> %p", path, (void *)gl4es_handle);
            LOGI("VSDL-resolve dlopen(%s) -> %p err=%s", path, (void *)gl4es_handle, dlerror());
            if (gl4es_handle == NULL) {
                file_log("resolve_from_gl4es: %s", dlerror());
            }
        }
        /* 兜底：SONAME 全局解析 */
        if (gl4es_handle == NULL) {
            gl4es_handle = dlopen("libng_gl4es.so", RTLD_NOW);
            LOGI("VSDL-resolve fallback dlopen(libng_gl4es.so) -> %p err=%s", (void *)gl4es_handle, dlerror());
        }
        if (gl4es_handle == NULL) {
            file_log("resolve_from_gl4es: all dlopen failed: %s", dlerror());
            LOGI("VSDL-resolve all dlopen failed: %s", dlerror());
        }
    }
    if (!resolved_once) {
        resolved_once = true;
        LOGI("VSDL-resolve gl4es_handle=%p", (void *)gl4es_handle);
    }
    if (gl4es_handle == NULL) return NULL;
    void *fn = real_dlsym_fn(gl4es_handle, proc);
    if (fn == NULL) {
        file_log("resolve_from_gl4es: dlsym(%s) failed: %s", proc, dlerror());
        LOGI("VSDL-resolve dlsym(%s) failed: %s", proc, dlerror());
    }
    return fn;
}

/* SDL_GL_GetProcAddress 代理：gl* 一律从渲染器 GL 库解析，保持与 LWJGL provider 同源 */
static void *sdlGLGetProcAddressProxy(const char *proc) {
    LOGI("VSDL-GLProc entry: %s", proc ? proc : "(null)");
    if (proc != NULL && strncmp(proc, "gl", 2) == 0) {
        void *fn = resolve_from_gl4es(proc);
        if (fn != NULL) {
            file_log("sdlGLGetProcAddressProxy: %s -> %p (from renderer gl lib)", proc, fn);
            LOGI("VSDL-GLProc %s -> %p (renderer gl lib)", proc, fn);
            return fn;
        }
        file_log("sdlGLGetProcAddressProxy: %s not in renderer gl lib", proc);
        LOGI("VSDL-GLProc %s NOT in renderer gl lib", proc);
    }
    if (real_sdl_gl_get_proc_address == NULL) {
        real_sdl_gl_get_proc_address =
            (sdlGLGetProcAddress_t)real_dlsym_fn(RTLD_DEFAULT, "SDL_GL_GetProcAddress");
    }
    void *fn = real_sdl_gl_get_proc_address != NULL ? real_sdl_gl_get_proc_address(proc) : NULL;
    if (proc != NULL && strncmp(proc, "gl", 2) == 0) {
        file_log("sdlGLGetProcAddressProxy: %s -> %p (fallback)", proc, fn);
        LOGI("VSDL-GLProc %s -> %p (fallback real SDL)", proc, fn);
    }
    return fn;
}

/* ---- SDL GL context profile 强制（移动渲染器均为 OpenGL ES 实现） ----
 * 游戏按桌面 GL 惯例请求 profile（SDL_GL_CONTEXT_PROFILE_MASK=20 传非 ES 值），
 * 而 Android 宿主与渲染器（nggl4es/mobileglues 等）只接受 ES profile；
 * 非 ES 请求会让 GL context 创建失败，随后 glGetString 在无有效 context 时
 * 返回无效字符串指针，LWJGL 对其 strlen → SIGSEGV。
 * 拦截 SDL_GL_SetAttribute 把所有 profile 请求强制为 ES（SDL_GL_CONTEXT_PROFILE_ES=4），
 * 并在 SDL3 加载后预先设置一次，覆盖 SDL_CreateWindowWithProperties 直接走
 * 全局 GL attribute 的路径。 */
static int (*real_sdl_gl_set_attribute)(int, int);

static int custom_SDL_GL_SetAttribute_Func(int attr, int value) {
    if (attr == 20 /* SDL_GL_CONTEXT_PROFILE_MASK */ && value != 4 /* SDL_GL_CONTEXT_PROFILE_ES */) {
        file_log("SDL_GL_SetAttribute(PROFILE_MASK): force ES (was %d)", value);
        value = 4;
    }
    if (real_sdl_gl_set_attribute == NULL) return -1;
    return real_sdl_gl_set_attribute(attr, value);
}

static void preset_egl_profile_es(void) {
    if (real_sdl_gl_set_attribute == NULL && sdl3_handle != NULL) {
        real_sdl_gl_set_attribute =
            (int (*)(int, int)) real_dlsym_fn(sdl3_handle, "SDL_GL_SetAttribute");
    }
    if (real_sdl_gl_set_attribute != NULL) {
        real_sdl_gl_set_attribute(20, 4);
        file_log("preset_egl_profile_es: SDL_GL_SetAttribute(PROFILE_MASK, ES) ok");
    } else {
        file_log("preset_egl_profile_es: SDL_GL_SetAttribute unavailable");
    }
}

/* ---- eglGetProcAddress 代理（最底层统一出口） ----
 * SDL3 Android 后端与 LWJGL 的 GL 函数都经 eglGetProcAddress 获取。
 * 把 egl* 开头的函数交给真实 EGL（SDL/LWJGL 各自 EGL 句柄），而 gl* 一律
 * 从 libng_gl4es.so 解析——无论 SDL 还是 LWJGL 拿到的 glGetError 都是
 * ng_gl4es 的实现，RenderPearl 的 "glGetError mismatch" 校验即可通过。 */
typedef void *(*eglGetProcAddress_t)(const char *proc);
static eglGetProcAddress_t real_egl_get_proc_address;

static void *eglGetProcAddressProxy(const char *proc) {
    LOGI("VSDL-EGLProc entry: %s", proc ? proc : "(null)");
    if (proc != NULL && strncmp(proc, "gl", 2) == 0) {
        void *fn = resolve_from_gl4es(proc);
        if (fn != NULL) {
            file_log("eglGetProcAddressProxy: %s -> %p (from gl4es)", proc, fn);
            LOGI("VSDL-EGLProc %s -> %p (renderer gl lib)", proc, fn);
            return fn;
        }
        LOGI("VSDL-EGLProc %s NOT in renderer gl lib", proc);
    }
    if (real_egl_get_proc_address == NULL) {
        real_egl_get_proc_address =
            (eglGetProcAddress_t)real_dlsym_fn(RTLD_DEFAULT, "eglGetProcAddress");
    }
    void *fn = real_egl_get_proc_address != NULL ? real_egl_get_proc_address(proc) : NULL;
    if (proc != NULL && strncmp(proc, "gl", 2) == 0) {
        file_log("eglGetProcAddressProxy: %s -> %p (fallback)", proc, fn);
    }
    return fn;
}

static void *custom_eglGetProcAddress_Func(const char *proc) {
    return eglGetProcAddressProxy(proc);
}

/* ---- SDL 的 EGL 函数解析接管（移动渲染器宿主 EGL 兼容归一化） ----
 * 部分宿主 libEGL（MobileGlues 等自带 EGL）不接受 RENDERABLE_TYPE 携带
 * ES3_BIT/OPENGL_BIT 的 config 请求，也不识别 EGL_CONTEXT_MAJOR/MINOR_VERSION_KHR
 * 上下文属性；MC 26.3 RenderPearl 按桌面/ES3 惯例请求这些属性会被宿主拒绝 →
 * GL context 创建失败 → glGetString 在无有效 context 时返回无效字符串指针 →
 * LWJGL strlen → SIGSEGV。
 * SDL 经 SDL_LoadFunction 取得 EGL 函数指针后直接调用（不走 PLT），
 * 故在其解析 eglChooseConfig/eglCreateContext 时注入归一化代理。
 * 归一化：RENDERABLE_TYPE 统一并入 ES2_BIT（实际上下文版本由
 * EGL_CONTEXT_CLIENT_VERSION 决定，不受影响）；剔除 MAJOR/MINOR_VERSION，
 * 仅保留 CLIENT_VERSION；ES3 上下文创建失败时降级为 ES2 重试。 */
#define EGL_NONE 0x3038
#define EGL_RENDERABLE_TYPE 0x22
#define EGL_OPENGL_ES2_BIT 0x4
#define EGL_OPENGL_ES3_BIT 0x40
#define EGL_OPENGL_BIT 0x2
#define EGL_CONTEXT_CLIENT_VERSION 0x3098
#define EGL_CONTEXT_MAJOR_VERSION_KHR 0x3098
#define EGL_CONTEXT_MINOR_VERSION_KHR 0x30A1

typedef void *EGLDisplay;
typedef void *EGLConfig;
typedef void *EGLSurface;
typedef int EGLBoolean;
typedef int EGLint;
typedef EGLBoolean (*eglChooseConfig_t)(EGLDisplay, const EGLint *, EGLConfig *, EGLint, EGLint *);
typedef void *(*eglCreateContext_t)(EGLDisplay, EGLConfig, void *, const EGLint *);
typedef EGLBoolean (*eglSwapBuffers_t)(EGLDisplay, EGLSurface);

static int normalizeEglChooseConfigList(const EGLint *attrib_list, EGLint *fixed, int cap) {
    if (attrib_list == NULL) return 0;
    int n = 0;
    for (int i = 0; n < cap - 2; i += 2) {
        EGLint attr = attrib_list[i];
        EGLint val = attrib_list[i + 1];
        if (attr == EGL_NONE) {
            fixed[n] = EGL_NONE;
            fixed[n + 1] = 0;
            n += 2;
            break;
        }
        if (attr == EGL_RENDERABLE_TYPE) {
            if ((val & (EGL_OPENGL_ES3_BIT | EGL_OPENGL_BIT)) != 0 &&
                (val & EGL_OPENGL_ES2_BIT) == 0) {
                val = (val & ~(EGL_OPENGL_ES3_BIT | EGL_OPENGL_BIT)) | EGL_OPENGL_ES2_BIT;
            }
        }
        fixed[n] = attr;
        fixed[n + 1] = val;
        n += 2;
    }
    return n > 0;
}

static int normalizeEglContextAttribs(const EGLint *attrib_list, EGLint *fixed, int cap) {
    int version = 0;
    if (attrib_list == NULL) return 0;
    int n = 0;
    for (int i = 0; n < cap - 2; i += 2) {
        EGLint attr = attrib_list[i];
        EGLint val = attrib_list[i + 1];
        if (attr == EGL_NONE) {
            fixed[n] = EGL_NONE;
            fixed[n + 1] = 0;
            n += 2;
            break;
        }
        if (attr == EGL_CONTEXT_MAJOR_VERSION_KHR) {
            if (version == 0) version = val;
            continue;
        }
        if (attr == EGL_CONTEXT_MINOR_VERSION_KHR) continue;
        if (attr == EGL_CONTEXT_CLIENT_VERSION && version == 0) version = val;
        fixed[n] = attr;
        fixed[n + 1] = val;
        n += 2;
    }
    return version;
}

static eglChooseConfig_t sOrigEglChooseConfig = NULL;
static eglCreateContext_t sOrigEglCreateContext = NULL;

static void *proxyEglCreateContext(EGLDisplay dpy, EGLConfig config, void *share,
                                   const EGLint *attrib_list) {
    /* hook_all 直接 GOT 拦截时（不经 dlsym/SDL_LoadFunction），origin 可能未初始化 */
    if (sOrigEglCreateContext == NULL) {
        sOrigEglCreateContext = (eglCreateContext_t) real_dlsym_fn(RTLD_DEFAULT, "eglCreateContext");
        LOGI("VSDL-proxy eglCreateContext resolve orig=%p", (void *)sOrigEglCreateContext);
    }
    if (sOrigEglCreateContext == NULL) return NULL;
    EGLint fixed[64];
    const EGLint *use_list = attrib_list;
    int version = normalizeEglContextAttribs(attrib_list, fixed, 64);
    if (version > 0) use_list = fixed;

    void *ctx = sOrigEglCreateContext(dpy, config, share, use_list);
    if (ctx == NULL && version > 2) {
        file_log("eglCreateContext failed with version=%d, retrying ES2", version);
        LOGI("VSDL-proxy eglCreateContext version=%d failed, retry ES2", version);
        EGLint es2[3] = {EGL_CONTEXT_CLIENT_VERSION, 2, EGL_NONE};
        ctx = sOrigEglCreateContext(dpy, config, share, es2);
    }
    return ctx;
}

static EGLBoolean proxyEglChooseConfig(EGLDisplay dpy, const EGLint *attrib_list,
                                       EGLConfig *configs, EGLint config_size,
                                       EGLint *num_config) {
    if (sOrigEglChooseConfig == NULL) {
        sOrigEglChooseConfig = (eglChooseConfig_t) real_dlsym_fn(RTLD_DEFAULT, "eglChooseConfig");
        LOGI("VSDL-proxy eglChooseConfig resolve orig=%p", (void *)sOrigEglChooseConfig);
    }
    if (sOrigEglChooseConfig == NULL) return 0;
    EGLint fixed[64];
    const EGLint *use_list = attrib_list;
    if (normalizeEglChooseConfigList(attrib_list, fixed, 64)) {
        use_list = fixed;
    }
    return sOrigEglChooseConfig(dpy, use_list, configs, config_size, num_config);
}

/* SDL_LoadFunction hook：SDL 经它解析 EGL 函数指针后直接调用（不走 PLT），
 * 在此注入 eglChooseConfig/eglCreateContext 归一化代理 */
static void *custom_SDL_LoadFunction_Func(void *handle, const char *name) {
    if (name != NULL) {
        LOGI("VSDL-LoadFunc %s (hooked)", name);
    }
    void *r = real_dlsym_fn(handle, name);
    if (name != NULL) {
        if (strcmp(name, "eglChooseConfig") == 0) {
            if (sOrigEglChooseConfig == NULL && r != NULL) {
                sOrigEglChooseConfig = (eglChooseConfig_t) r;
            }
            r = (void *) proxyEglChooseConfig;
        } else if (strcmp(name, "eglCreateContext") == 0) {
            if (sOrigEglCreateContext == NULL && r != NULL) {
                sOrigEglCreateContext = (eglCreateContext_t) r;
            }
            r = (void *) proxyEglCreateContext;
        }
    }
    return r;
}

/* dlsym 出口拦截：LWJGL 的 DynamicLinkLoader.ndlsym（无论 liblwjgl.so 还是
 * pojavexec 注册的 customDlsym 实现）内部均调用 libc dlsym，经 GOT 补丁拦截。
 * SDL_GL_GetProcAddress → 代理（gl* 从渲染器 GL 库解析）；
 * SDL_GL_SetAttribute → 代理（profile 强制 ES）。 */
static void *hooked_dlsym(void *handle, const char *symbol) {
    void *real = real_dlsym_fn(handle, symbol);
    if (symbol != NULL) {
        if (strcmp(symbol, "SDL_GL_GetProcAddress") == 0) {
            return (void *)sdlGLGetProcAddressProxy;
        }
        if (strcmp(symbol, "SDL_GL_SetAttribute") == 0) {
            if (real_sdl_gl_set_attribute == NULL && real != NULL) {
                real_sdl_gl_set_attribute = (int (*)(int, int)) real;
            }
            return (void *)custom_SDL_GL_SetAttribute_Func;
        }
    }
    return real;
}

/* SDL 库内部 GOT 路径的直接调用兜底（代理不依赖 bytehook 调用上下文） */
static void *custom_SDL_GL_GetProcAddress_Func(const char *proc) {
    return sdlGLGetProcAddressProxy(proc);
}

/* dlopen libbytehook.so 并解析所需符号；返回 0 表示可用 */
static int load_bytehook_runtime(void) {
    const char *appLibDir = getenv("VERSE_APP_LIB_DIR");
    if (appLibDir != NULL && appLibDir[0] != '\0') {
        char path[512];
        snprintf(path, sizeof(path), "%s/libbytehook.so", appLibDir);
        void *bh = dlopen(path, RTLD_NOW | RTLD_GLOBAL);
        if (bh != NULL) {
            fn_init = (bytehook_init_fn_t)dlsym(bh, "bytehook_init");
            fn_hook_all = (bytehook_hook_all_fn_t)dlsym(bh, "bytehook_hook_all");
            fn_hook_single = (bytehook_hook_single_fn_t)dlsym(bh, "bytehook_hook_single");
            fn_add_dlopen_callback = (bytehook_add_dlopen_callback_fn_t)dlsym(bh, "bytehook_add_dlopen_callback");
            if (fn_init != NULL && fn_hook_all != NULL) {
                LOGI("VerseSDL bytehook resolved from %s (init=%p hook_all=%p)",
                     path, (void *)fn_init, (void *)fn_hook_all);
                file_log("bytehook resolved from %s init=%p hook_all=%p hook_single=%p",
                         path, (void *)fn_init, (void *)fn_hook_all, (void *)fn_hook_single);
                return 0;
            }
            file_log("bytehook dlsym failed from %s: %s", path, dlerror());
        } else {
            file_log("dlopen(%s) failed: %s", path, dlerror());
            LOGE("dlopen(%s) failed: %s", path, dlerror());
        }
    } else {
        file_log("VERSE_APP_LIB_DIR not set");
    }
    /* 兜底：按 soname 匹配已加载副本（pojavexec NEEDED 已连带加载） */
    void *bh = dlopen("libbytehook.so", RTLD_NOW | RTLD_GLOBAL);
    if (bh == NULL) {
        file_log("dlopen(libbytehook.so) failed: %s", dlerror());
        LOGE("dlopen(libbytehook.so) failed: %s", dlerror());
        return -1;
    }
    fn_init = (bytehook_init_fn_t)dlsym(bh, "bytehook_init");
    fn_hook_all = (bytehook_hook_all_fn_t)dlsym(bh, "bytehook_hook_all");
    fn_hook_single = (bytehook_hook_single_fn_t)dlsym(bh, "bytehook_hook_single");
    fn_add_dlopen_callback = (bytehook_add_dlopen_callback_fn_t)dlsym(bh, "bytehook_add_dlopen_callback");
    if (fn_init == NULL || fn_hook_all == NULL) {
        file_log("dlsym bytehook_init/bytehook_hook_all failed: %s", dlerror());
        LOGE("dlsym bytehook_init/bytehook_hook_all failed: %s", dlerror());
        return -1;
    }
    LOGI("VerseSDL bytehook resolved by soname (init=%p hook_all=%p)",
         (void *)fn_init, (void *)fn_hook_all);
    file_log("bytehook resolved by soname init=%p hook_all=%p hook_single=%p",
             (void *)fn_init, (void *)fn_hook_all, (void *)fn_hook_single);
    return 0;
}

/* 安装 SDL_GL_GetProcAddress + dlsym hooks（bytehook 已 init 后调用） */
static void install_verse_sdl_hooks(void) {
    if (sdl_hooks_installed) return;
    int initResult = fn_init(BYTEHOOK_MODE_AUTOMATIC, false);
    if (initResult != BYTEHOOK_STATUS_CODE_OK &&
        initResult != BYTEHOOK_STATUS_CODE_INIT_ALREADY_CALLED) {
        LOGE("bytehook_init failed result=%d", initResult);
        file_log("bytehook_init failed result=%d", initResult);
        return;
    }
    file_log("bytehook init ok (result=%d), installing hooks", initResult);
    /* SDL_GL_GetProcAddress：GOT/PLT 补丁（覆盖 SDL3 库内直接调用） */
    fn_hook_all(NULL, "SDL_GL_GetProcAddress",
                (void *)custom_SDL_GL_GetProcAddress_Func, NULL, NULL);
    /* SDL_LoadFunction：SDL 经它解析 EGL 函数指针（eglChooseConfig/eglCreateContext
     * 归一化代理，移动渲染器宿主 EGL 兼容；二分验证：停用后 glGetString SIGSEGV 复现，
     * 该归一化是 MobileGlues context 创建的前提） */
    fn_hook_all(NULL, "SDL_LoadFunction",
                (void *)custom_SDL_LoadFunction_Func, NULL, NULL);
    /* SDL_GL_SetAttribute 不做 GOT/PLT 补丁（bytehook 补丁该符号在 DefaultDispatch
     * 线程触发 SEGV_ACCERR 写保护页崩溃——其导出段特殊）；profile 强制改由
     * dlsym 出口代理（hooked_dlsym 返回 custom_SDL_GL_SetAttribute_Func）+ 下方
     * SDL3 加载后的 preset 预置双保险覆盖。 */
    /* 兜底：SDL3 加载后立即预置 ES profile（覆盖 props 创建窗口直接走全局 attribute） */
    preset_egl_profile_es();
    /* 若 SDL3 已加载，精准补丁其导出符号（RenderPearl/LWJGL 经 dlsym 取地址后直接调用） */
    if (fn_hook_single != NULL && sdl3_handle != NULL) {
        fn_hook_single(NULL, "libSDL3.so", "SDL_GL_GetProcAddress",
                       (void *)custom_SDL_GL_GetProcAddress_Func, NULL, NULL);
        file_log("bytehook_hook_single libSDL3.so SDL_GL_GetProcAddress installed");
    }
    /* eglGetProcAddress：SDL/LWJGL 拿 gl* 的统一出口。libEGL.so 由系统或
     * renderer 依赖链加载，hook_single 覆盖已加载实例，hook_all 兜底。 */
    fn_hook_all(NULL, "eglGetProcAddress",
                (void *)custom_eglGetProcAddress_Func, NULL, NULL);
    if (fn_hook_single != NULL) {
        fn_hook_single(NULL, "libEGL.so", "eglGetProcAddress",
                       (void *)custom_eglGetProcAddress_Func, NULL, NULL);
        file_log("bytehook_hook_single libEGL.so eglGetProcAddress installed");
    }
    /* 注意：不在此对 eglCreateContext/eglChooseConfig 做 hook_all 全局补丁——
     * 该层级会拦截所有库（含 nggl4es 等其它 GL 渲染器的 SDL/poemexec 调用），
     * 且 sOrig 经 RTLD_DEFAULT 可能解析到 mobileglues 的错误实现，破坏 26.2
     * 等版本的 GL context 创建。EGL 归一化仅保留 dlsym 出口（hooked_dlsym）与
     * SDL_LoadFunction 出口两条窄路径。 */
    /* dlsym 出口：LWJGL DynamicLinkLoader 经 libc dlsym 取 SDL_GL_GetProcAddress 地址 */
    fn_hook_all(NULL, "dlsym", (void *)hooked_dlsym, NULL, NULL);
    LOGI("VerseSDL hooks installed: dlsym=%p SDL_GL_GetProcAddress=%p",
         (void *)hooked_dlsym, (void *)custom_SDL_GL_GetProcAddress_Func);
    file_log("hooks installed dlsym=%p sdl=%p",
             (void *)hooked_dlsym, (void *)custom_SDL_GL_GetProcAddress_Func);
    sdl_hooks_installed = true;
}

/* SDL3 加载回调：库 dlopen 时立刻补丁其 SDL_GL_GetProcAddress（若尚未安装） */
static void on_sdl3_dlopen(const char *filename, void *data) {
    (void)data;
    if (filename == NULL) return;
    if (strstr(filename, "libSDL3.so") != NULL || strstr(filename, "libSDL3") != NULL) {
        file_log("on_sdl3_dlopen: %s", filename);
        if (sdl3_handle == NULL) {
            sdl3_handle = dlopen("libSDL3.so", RTLD_NOW);
            file_log("on_sdl3_dlopen: sdl3_handle=%p err=%s", (void *)sdl3_handle, dlerror());
        }
        install_verse_sdl_hooks();
    }
}

JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM *vm, void *reserved) {
    (void)vm;
    (void)reserved;

    file_log("JNI_OnLoad enter");

    /* 先缓存真实 dlsym，再安装 hook，避免递归 */
    real_dlsym_fn = (dlsym_t)dlsym(RTLD_DEFAULT, "dlsym");
    if (real_dlsym_fn == NULL) {
        LOGE("cannot resolve real dlsym");
        file_log("cannot resolve real dlsym");
        return JNI_VERSION_1_6;
    }
    file_log("real dlsym=%p", (void *)real_dlsym_fn);

    if (load_bytehook_runtime() == 0) {
        /* 注册 SDL3 dlopen 回调：LWJGL 由游戏 JVM dlopen libSDL3.so 时触发 */
        if (fn_add_dlopen_callback != NULL) {
            fn_add_dlopen_callback(on_sdl3_dlopen, NULL);
            file_log("bytehook_add_dlopen_callback registered");
        }
        /* 若 SDL3 已加载（如 launcher 提前 dlopen），立即补丁 */
        sdl3_handle = dlopen("libSDL3.so", RTLD_NOW);
        if (sdl3_handle != NULL) {
            file_log("SDL3 already loaded, handle=%p", (void *)sdl3_handle);
            install_verse_sdl_hooks();
        } else {
            file_log("SDL3 not loaded yet: %s", dlerror());
        }
    }
    file_log("JNI_OnLoad exit");
    return JNI_VERSION_1_6;
}

/* 由 CallbackBridge.notifyLauncher 在 System.loadLibrary("SDL3") 之后调用：
 * 此时 SDL3 已在 ART 进程加载，对已加载实例补齐 hook_single 补丁。 */
JNIEXPORT void JNICALL
Java_org_libsdl_app_SDL_nativeInstallSdlHooks(JNIEnv *env, jclass clazz) {
    (void)env;
    (void)clazz;
    file_log("nativeInstallSdlHooks enter");
    if (load_bytehook_runtime() != 0) {
        file_log("nativeInstallSdlHooks: bytehook unavailable");
        return;
    }
    sdl3_handle = dlopen("libSDL3.so", RTLD_NOW);
    file_log("nativeInstallSdlHooks: sdl3_handle=%p err=%s", (void *)sdl3_handle, dlerror());
    install_verse_sdl_hooks();
    file_log("nativeInstallSdlHooks exit");
}

/* ---- Vulkan 后端：预加载 vkshim 并把句柄写入 VULKAN_PTR ----
 * RenderPearl（26.2+/26.3）Vulkan 后端要求 SDL 与 LWJGL 使用同一 Vulkan 加载器实例
 * （校验 vkGetInstanceProcAddr 指针一致）。FCL 的做法：egl_bridge 在 VKSHIM_ENABLE=1
 * 时 dlopen libvkshim.so 并把句柄写入 VULKAN_PTR，pojavexec 的 LWJGL ndlopen hook 与
 * SDL LoadObject hook 都会返回该句柄。这里在 JVM 启动前（app 进程）预 dlopen
 * libvkshim.so 并预置 VULKAN_PTR，效果等价且不依赖重编 pojavexec。 */

/* Vulkan 驱动器=Turnip（VULKAN_DRIVER_SYSTEM 未置 "1"）时对齐 FCL egl_bridge load_vulkan：
 * 先 RTLD_GLOBAL 载 liblinkerhook.so 拦截 android_dlopen_ext，再加载系统加载器——加载器
 * 枚举驱动（vulkan.*）的请求被 hook 转向 DRIVER_PATH/libvulkan_freedreno.so。
 * 加载成功不代表驱动可用（freedreno 仅支持 Adreno，非 Adreno 设备设备枚举为空），
 * 建实例探测物理设备数并校验 vendor（freedreno 报 Qualcomm 0x5143；非 Adreno 硬件上
 * freedreno 可能仍枚举出不可用的虚拟设备，仅凭数量会误判），失败时回落 vkshim。 */
static int vsdl_turnip_probe(void *loader) {
    PFN_vkCreateInstance create = (PFN_vkCreateInstance) dlsym(loader, "vkCreateInstance");
    PFN_vkEnumeratePhysicalDevices enumerate =
            (PFN_vkEnumeratePhysicalDevices) dlsym(loader, "vkEnumeratePhysicalDevices");
    PFN_vkGetPhysicalDeviceProperties props_fn =
            (PFN_vkGetPhysicalDeviceProperties) dlsym(loader, "vkGetPhysicalDeviceProperties");
    PFN_vkDestroyInstance destroy = (PFN_vkDestroyInstance) dlsym(loader, "vkDestroyInstance");
    if (create == NULL || enumerate == NULL) return 0;

    VkInstanceCreateInfo ci;
    memset(&ci, 0, sizeof(ci));
    ci.sType = VK_STRUCTURE_TYPE_INSTANCE_CREATE_INFO;
    VkInstance instance = VK_NULL_HANDLE;
    if (create(&ci, NULL, &instance) != VK_SUCCESS) return 0;

    uint32_t count = 0;
    VkResult result = enumerate(instance, &count, NULL);
    int adreno = 0;
    if (result == VK_SUCCESS && count > 0) {
        VkPhysicalDevice devs[8];
        uint32_t n = count > 8 ? 8 : count;
        if (enumerate(instance, &n, devs) == VK_SUCCESS) {
            for (uint32_t i = 0; i < n; ++i) {
                if (props_fn == NULL) { adreno = 1; break; }
                VkPhysicalDeviceProperties p;
                memset(&p, 0, sizeof(p));
                props_fn(devs[i], &p);
                file_log("vulkan driver: turnip device vendorID=0x%04x deviceName=%s",
                         p.vendorID, p.deviceName);
                /* freedreno/Turnip 仅实现 Qualcomm Adreno（vendorID 0x5143） */
                if (p.vendorID == 0x5143) { adreno = 1; break; }
            }
        }
    }
    if (destroy != NULL) destroy(instance, NULL);
    file_log("vulkan driver: turnip probe result=%d devices=%u adreno=%d",
             (int) result, count, adreno);
    return adreno;
}

JNIEXPORT void JNICALL
Java_org_libsdl_app_SDL_nativeEnableVulkanShim(JNIEnv *env, jclass clazz) {
    (void)env;
    (void)clazz;
    if (getenv("VKSHIM_ENABLE") == NULL) {
        file_log("vkshim: VKSHIM_ENABLE not set, skip");
        return;
    }
    if (getenv("VULKAN_PTR") != NULL) {
        file_log("vkshim: VULKAN_PTR already set, skip");
        return;
    }
    const char *driverMode = getenv("VULKAN_DRIVER_SYSTEM");
    if (driverMode == NULL || strcmp(driverMode, "1") != 0) {
        /* Turnip 模式：hook 先于加载器进入全局符号表，之后 loader 的 android_dlopen_ext
         * 调用（驱动加载）才会被劫持；探测失败时卸载并回落 vkshim（系统驱动） */
        void *hook = dlopen("liblinkerhook.so", RTLD_NOW | RTLD_GLOBAL);
        if (hook != NULL) {
            void *loader = dlopen("/system/lib64/libvulkan.so", RTLD_LAZY | RTLD_LOCAL);
            if (loader == NULL) {
                file_log("vulkan driver: turnip loader load failed: %s", dlerror());
                dlclose(hook);
            } else if (vsdl_turnip_probe(loader)) {
                char buf[64];
                snprintf(buf, sizeof(buf), "%p", loader);
                setenv("VULKAN_PTR", buf, 1);
                file_log("vulkan driver: turnip enabled, VULKAN_PTR=%s", buf);
                return;
            } else {
                file_log("vulkan driver: turnip unavailable, fallback to vkshim");
                dlclose(loader);
            }
        } else {
            file_log("vulkan driver: liblinkerhook.so load failed: %s", dlerror());
        }
    }
    void *h = dlopen("libvkshim.so", RTLD_NOW | RTLD_LOCAL);
    if (h == NULL) {
        file_log("vkshim: dlopen libvkshim.so failed: %s", dlerror());
        return;
    }
    char buf[64];
    snprintf(buf, sizeof(buf), "%p", h);
    setenv("VULKAN_PTR", buf, 1);
    file_log("vkshim: enabled, VULKAN_PTR=%s", buf);
}
