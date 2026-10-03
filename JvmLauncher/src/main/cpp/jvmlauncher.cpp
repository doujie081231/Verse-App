#include <jni.h>
#include <string>
#include <vector>
#include <cstdarg>
#include <android/log.h>
#include <dlfcn.h>
#include <unistd.h>
#include <sys/stat.h>
#include <sys/types.h>
#include <dirent.h>
#include <signal.h>
#include <cstdlib>
#include <cstring>
#include <pthread.h>
#include <setjmp.h>
#include <fcntl.h>
#include <time.h>
#include <android/native_window.h>
#include <android/native_window_jni.h>

#define LOG_TAG "VerseJvm"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN, LOG_TAG, __VA_ARGS__)

static FILE *g_debugFile = nullptr;
static pthread_mutex_t g_debugMutex = PTHREAD_MUTEX_INITIALIZER;
static char g_debugLogPath[512] = {'\0'};

static void debugLog(const char *level, const char *fmt, ...) {
    va_list args1, args2;
    va_start(args1, fmt);
    va_copy(args2, args1);

    __android_log_vprint(
        strcmp(level, "E") == 0 ? ANDROID_LOG_ERROR :
        strcmp(level, "W") == 0 ? ANDROID_LOG_WARN : ANDROID_LOG_INFO,
        LOG_TAG, fmt, args1);
    va_end(args1);

    pthread_mutex_lock(&g_debugMutex);
    if (!g_debugFile) {
        if (g_debugLogPath[0] == '\0') {
            snprintf(g_debugLogPath, sizeof(g_debugLogPath), "/sdcard/verse_debug.log");
        }
        g_debugFile = fopen(g_debugLogPath, "a");
        if (g_debugFile) setvbuf(g_debugFile, nullptr, _IONBF, 0);
    }
    if (g_debugFile) {
        time_t now = time(nullptr);
        struct tm tm_buf;
        localtime_r(&now, &tm_buf);
        char timeStr[32];
        strftime(timeStr, sizeof(timeStr), "%m-%d %H:%M:%S", &tm_buf);
        fprintf(g_debugFile, "[%s] [%s] ", timeStr, level);
        vfprintf(g_debugFile, fmt, args2);
        fprintf(g_debugFile, "\n");
    }
    pthread_mutex_unlock(&g_debugMutex);
    va_end(args2);
}

#define FLOGI(...) debugLog("I", __VA_ARGS__)
#define FLOGE(...) debugLog("E", __VA_ARGS__)
#define FLOGW(...) debugLog("W", __VA_ARGS__)

typedef int (*JLI_Launch_t)(int, char **, int, const char **, int, const char **,
                            const char *, const char *, const char *, const char *,
                            jboolean, jboolean, jboolean, jint);

static volatile bool g_jvmRunning = false;
static volatile bool g_launchInProgress = false;
static volatile jint g_exitCode = 0;
static pthread_t g_jvmThread = 0;
static void *g_libjli = nullptr;
static JLI_Launch_t g_jliLaunchFunc = nullptr;

static pthread_cond_t g_jvmStartCond = PTHREAD_COND_INITIALIZER;
static pthread_mutex_t g_jvmStartMutex = PTHREAD_MUTEX_INITIALIZER;
static volatile bool g_jvmStartConfirmed = false;

static jobject g_exitCallback = nullptr;
static jmethodID g_onExitMethod = nullptr;
static JavaVM *g_androidVM = nullptr;

static ANativeWindow *g_nativeWindow = nullptr;
static volatile int g_nativeWindowReady = 0;
static int g_windowWidth = 0;
static int g_windowHeight = 0;
static int g_logFd = -1;

static sigjmp_buf g_jmpBuf;
static _Atomic bool g_jmpBufValid = false;

struct JVMLaunchArgs {
    char *jrePath;
    char **argv;
    int argc;
};

static _Atomic bool g_exitTripped = false;

static void (*g_realExitFn)(int) = nullptr;

static void hooked_exit(int status) {
    if (g_exitTripped) {
        if (g_realExitFn) g_realExitFn(status);
        _exit(status);
    }
    g_exitTripped = true;
    g_exitCode = status;
    g_jvmRunning = false;
    if (g_jmpBufValid) {
        g_jmpBufValid = false;
        siglongjmp(g_jmpBuf, status);
    }
    pthread_exit(nullptr);
}

static void hooked__exit(int status) {
    if (g_exitTripped) {
        _exit(status);
    }
    g_exitTripped = true;
    g_exitCode = status;
    g_jvmRunning = false;
    if (g_jmpBufValid) {
        g_jmpBufValid = false;
        siglongjmp(g_jmpBuf, status);
    }
    pthread_exit(nullptr);
}

static void (*g_realAbort)(void) = nullptr;

static void hooked_abort() {
    if (g_exitTripped) {
        if (g_realAbort) g_realAbort();
        _exit(134);
    }
    g_exitTripped = true;
    g_exitCode = -6;
    g_jvmRunning = false;
    if (g_jmpBufValid) {
        g_jmpBufValid = false;
        siglongjmp(g_jmpBuf, -6);
    }
    pthread_exit(nullptr);
}

static void installExitHook() {
    FLOGI("Installing exit/_exit hooks via bytehook...");
    void *libbytehook = dlopen("libbytehook.so", RTLD_NOW);
    if (libbytehook) {
        typedef int (*bytehook_init_t)(int, bool);
        typedef void *(*bytehook_hook_all_t)(void *, const char *, void *, void *, void **);
        
        bytehook_init_t bytehook_init_fn = (bytehook_init_t)dlsym(libbytehook, "bytehook_init");
        bytehook_hook_all_t bytehook_hook_all_fn = (bytehook_hook_all_t)dlsym(libbytehook, "bytehook_hook_all");
        
        if (bytehook_init_fn && bytehook_hook_all_fn) {
            // BYTEHOOK_MODE_AUTOMATIC=2（与 FCL fcl_loader.c init(AUTOMATIC, false) 一致），
            // 传 0 是无效 mode，init 返回 INTERNAL_ERROR 导致 fallback，且占用 init 状态
            // INIT_ALREADY_CALLED(4)：GameLaunchOrchestrator 已先经 FCLBridge.initializeHooks()
            // 完成 bytehook_init（FCL 3.x 唯一 init 入口），此处复用已初始化的 bytehook 装 exit hooks。
            int initResult = bytehook_init_fn(2, false);
            if (initResult == 0 || initResult == 4) {
                bytehook_hook_all_fn(nullptr, "exit", (void *)hooked_exit, nullptr, nullptr);
                bytehook_hook_all_fn(nullptr, "_exit", (void *)hooked__exit, nullptr, nullptr);
                FLOGI("ByteHook exit() + _exit() hooks installed successfully (initResult=%d)", initResult);
            } else {
                FLOGW("ByteHook init failed: %d, using atexit fallback", initResult);
                atexit([]() {
                    if (!g_exitTripped) {
                        g_exitTripped = true;
                        g_jvmRunning = false;
                    }
                });
            }
        } else {
            FLOGW("ByteHook symbols not found, using atexit fallback");
            atexit([]() {
                if (!g_exitTripped) {
                    g_exitTripped = true;
                    g_jvmRunning = false;
                }
            });
        }
    } else {
        FLOGW("libbytehook.so not found, using atexit fallback");
        atexit([]() {
            if (!g_exitTripped) {
                g_exitTripped = true;
                g_jvmRunning = false;
            }
        });
    }
    
    void *libc = dlopen("libc.so", RTLD_NOW);
    if (libc) {
        void *exitSym = dlsym(libc, "exit");
        void *abortSym = dlsym(libc, "abort");
        if (exitSym) g_realExitFn = (void (*)(int))exitSym;
        if (abortSym) g_realAbort = (void (*)(void))abortSym;
    }
    FLOGI("Exit hooks ready: realExit=%p, realAbort=%p", (void*)g_realExitFn, (void*)g_realAbort);
}

static void resetSignalHandlersForJVM() {
    // Before launching the JVM, reset all signal handlers to their defaults so
    // HotSpot can install its own handlers on a clean slate. SIGSEGV is set to
    // SIG_IGN temporarily because Android specifically checks whether apps keep
    // SIGSEGV non-default; the JVM's crash handler will take it over during init.
    struct sigaction clean_sa;
    memset(&clean_sa, 0, sizeof(clean_sa));
    for (int sigid = SIGHUP; sigid < NSIG; sigid++) {
        if (sigid == SIGSEGV) {
            clean_sa.sa_handler = SIG_IGN;
        } else {
            clean_sa.sa_handler = SIG_DFL;
        }
        sigaction(sigid, &clean_sa, nullptr);
    }
    FLOGI("Signal handlers reset to defaults for clean JVM startup");
}

static void realtimeSignalProbe(int sig, siginfo_t *info, void *context) {
    FLOGI("DIAG: realtime signal %d received: si_pid=%d si_uid=%d si_code=%d si_addr=%p",
          sig,
          info ? info->si_pid : -1,
          info ? info->si_uid : -1,
          info ? info->si_code : -1,
          info ? info->si_addr : nullptr);
}

static void installRealtimeSignalProbe() {
    struct sigaction sa;
    memset(&sa, 0, sizeof(sa));
    sa.sa_sigaction = realtimeSignalProbe;
    sa.sa_flags = SA_SIGINFO;
    sigaction(SIGRTMIN, &sa, nullptr);
    sigaction(SIGRTMIN + 1, &sa, nullptr);
    sigaction(SIGRTMIN + 2, &sa, nullptr);
    FLOGI("DIAG: realtime signal probe installed for signals %d/%d/%d", SIGRTMIN, SIGRTMIN + 1, SIGRTMIN + 2);
}

static void notifyExit(jint exitCode) {
    FLOGI("notifyExit called with code %d", exitCode);
    if (g_androidVM && g_exitCallback && g_onExitMethod) {
        JNIEnv *env = nullptr;
        bool attached = false;
        jint attachResult = g_androidVM->GetEnv((void **)&env, JNI_VERSION_1_6);
        if (attachResult == JNI_EDETACHED) {
            if (g_androidVM->AttachCurrentThread(&env, nullptr) == JNI_OK) {
                attached = true;
            }
        }
        if (env) {
            FLOGI("Calling exit callback with code %d", exitCode);
            env->CallVoidMethod(g_exitCallback, g_onExitMethod, exitCode);
            if (env->ExceptionCheck()) {
                env->ExceptionDescribe();
                env->ExceptionClear();
            }
        } else {
            FLOGE("Failed to get JNIEnv for exit callback");
        }
        if (attached) {
            g_androidVM->DetachCurrentThread();
        }
    } else {
        FLOGW("Exit callback not set: vm=%p, callback=%p, method=%p",
              g_androidVM, g_exitCallback, g_onExitMethod);
    }
}

static void cleanupLaunchArgs(JVMLaunchArgs *launchArgs) {
    if (!launchArgs) return;
    free(launchArgs->jrePath);
    for (int i = 0; i < launchArgs->argc; i++) {
        free(launchArgs->argv[i]);
    }
    free(launchArgs->argv);
    free(launchArgs);
}

static void *dlopen_verbose(const char *path) {
    void *handle = dlopen(path, RTLD_GLOBAL | RTLD_LAZY);
    if (handle) {
        LOGI("Loaded: %s", path);
        FLOGI("DLOPEN: loading %s", path);
    } else {
        LOGW("Failed to load %s: %s", path, dlerror());
        FLOGW("DLOPEN: loading %s (error = %s)", path, dlerror());
    }
    return handle;
}

static bool dirExists(const char *path) {
    struct stat st;
    return stat(path, &st) == 0 && S_ISDIR(st.st_mode);
}

static const char* detectArchSubdir(const char *jreHome) {
    const char *candidates[] = {"aarch64", "arm64-v8a", "amd64", "x86_64", "i386", "x86", "arm", nullptr};
    char libPath[512];
    for (int i = 0; candidates[i]; i++) {
        snprintf(libPath, sizeof(libPath), "%s/lib/%s", jreHome, candidates[i]);
        if (dirExists(libPath)) {
            LOGI("Detected arch subdir: lib/%s", candidates[i]);
            return candidates[i];
        }
    }
    return nullptr;
}

static void preloadJreLibsRecursive(const char *dirPath) {
    DIR *dir = opendir(dirPath);
    if (!dir) {
        FLOGW("DLOPEN: skipping directory (opendir failed): %s (%s)", dirPath, strerror(errno));
        return;
    }

    struct dirent *entry;
    char fullPath[1024];
    while ((entry = readdir(dir)) != nullptr) {
        if (strcmp(entry->d_name, ".") == 0 || strcmp(entry->d_name, "..") == 0) continue;

        snprintf(fullPath, sizeof(fullPath), "%s/%s", dirPath, entry->d_name);
        struct stat st;
        if (stat(fullPath, &st) != 0) continue;

        if (S_ISDIR(st.st_mode)) {
            preloadJreLibsRecursive(fullPath);
        } else if (S_ISREG(st.st_mode)) {
            size_t len = strlen(fullPath);
            if (len > 3 && strcmp(fullPath + len - 3, ".so") == 0) {
                FLOGI("DLOPEN: recursive loading %s", fullPath);
                void *handle = dlopen(fullPath, RTLD_GLOBAL | RTLD_LAZY);
                if (!handle) {
                    FLOGE("DLOPEN: recursive loading %s failed: %s", fullPath, dlerror());
                }
            }
        }
    }
    closedir(dir);
}

static void preloadJreLibs(const char *jreHome) {
    char path[512];
    const char *archSubdir = detectArchSubdir(jreHome);

    if (archSubdir) {
        snprintf(path, sizeof(path), "%s/lib/%s/server/libjvm.so", jreHome, archSubdir);
        if (access(path, F_OK) == 0) {
            dlopen_verbose(path);
        } else {
            snprintf(path, sizeof(path), "%s/lib/server/libjvm.so", jreHome);
            dlopen_verbose(path);
        }
    } else {
        snprintf(path, sizeof(path), "%s/lib/server/libjvm.so", jreHome);
        dlopen_verbose(path);
    }

    const char *runtimeLibs[] = {
        "libjava.so",
        "libverify.so",
        "libnet.so",
        "libnio.so",
        "libzip.so",
        "libjimage.so",
        "libawt.so",
        "libawt_headless.so",
        "libawt_xawt.so",
        "libfontmanager.so",
        "libfreetype.so",
        "libinstrument.so",
        "libmanagement.so",
        nullptr
    };

    for (int i = 0; runtimeLibs[i]; i++) {
        bool loaded = false;
        if (archSubdir) {
            snprintf(path, sizeof(path), "%s/lib/%s/%s", jreHome, archSubdir, runtimeLibs[i]);
            if (dlopen_verbose(path)) loaded = true;
        }
        if (!loaded) {
            snprintf(path, sizeof(path), "%s/lib/%s", jreHome, runtimeLibs[i]);
            dlopen_verbose(path);
        }
    }

    char libDir[512];
    snprintf(libDir, sizeof(libDir), "%s/lib", jreHome);
    FLOGI("DLOPEN: starting recursive preload under %s", libDir);
    preloadJreLibsRecursive(libDir);
}

static void ensureJvmCfg(const char *jrePath) {
    char cfgPath[512];
    snprintf(cfgPath, sizeof(cfgPath), "%s/lib/jvm.cfg", jrePath);
    if (access(cfgPath, F_OK) != 0) {
        LOGW("jvm.cfg not found at %s, creating it...", cfgPath);
        FILE *f = fopen(cfgPath, "w");
        if (f) {
            fprintf(f, "-server KNOWN\n-client IGNORE\n");
            fclose(f);
            LOGI("Created jvm.cfg at %s", cfgPath);
        } else {
            LOGE("Failed to create jvm.cfg at %s: %s", cfgPath, strerror(errno));
        }
    } else {
        LOGI("jvm.cfg found at %s", cfgPath);
    }
}

static void logJreStructure(const char *jrePath) {
    LOGI("=== JRE Directory Structure ===");
    LOGI("JRE Root: %s", jrePath);

    const char *checkPaths[] = {
        "/lib/jvm.cfg", "/lib/modules", "/lib/rt.jar",
        "/lib/server/libjvm.so", "/lib/aarch64/server/libjvm.so",
        "/lib/libjli.so", "/lib/jli/libjli.so",
        "/lib/aarch64/libjli.so", "/lib/aarch64/jli/libjli.so",
        "/bin/java", "/bin/java.sh", "/release",
        "/lib/libjava.so", "/lib/aarch64/libjava.so",
        nullptr
    };
    for (int i = 0; checkPaths[i]; i++) {
        char fullPath[512];
        snprintf(fullPath, sizeof(fullPath), "%s%s", jrePath, checkPaths[i]);
        if (access(fullPath, F_OK) == 0) {
            LOGI("  EXISTS: %s", checkPaths[i]);
        } else {
            LOGW("  MISSING: %s", checkPaths[i]);
        }
    }
    LOGI("=== End JRE Structure ===");
}

static void getJavaVersion(const char *jrePath, char *versionBuf, size_t bufSize) {
    snprintf(versionBuf, bufSize, "17.0.1");

    char releasePath[512];
    snprintf(releasePath, sizeof(releasePath), "%s/release", jrePath);

    FILE *f = fopen(releasePath, "r");
    if (!f) {
        LOGW("No release file found at %s, using default version 17.0.1", releasePath);
        return;
    }

    char line[256];
    while (fgets(line, sizeof(line), f)) {
        if (strstr(line, "JAVA_VERSION") || strstr(line, "JAVA_VERSION_NUMBER")) {
            char *start = strchr(line, '"');
            if (start) {
                start++;
                char *end = strchr(start, '"');
                if (end) *end = '\0';
                strncpy(versionBuf, start, bufSize - 1);
                versionBuf[bufSize - 1] = '\0';

                int major = 0, minor = 0, patch = 0;

                if (sscanf(versionBuf, "%d.%d.%d", &major, &minor, &patch) >= 2) {
                    snprintf(versionBuf, bufSize, "%d.%d.%d", major, minor, patch);
                    LOGI("Detected Java version from release: %s", versionBuf);
                    fclose(f);
                    return;
                } else if (sscanf(versionBuf, "%d.%d", &major, &minor) >= 1) {
                    snprintf(versionBuf, bufSize, "%d.%d.1", major, minor);
                    LOGI("Detected Java version from release: %s", versionBuf);
                    fclose(f);
                    return;
                }
            }
        }
    }

    fclose(f);
    LOGW("Could not parse JAVA_VERSION from release, using default 17.0.1");
}

static int launchJVMWithCreate(const char *jrePath, int argc, char **argv);

static int launchJVMWithJLI(const char *jrePath, int argc, char **argv) {
    logJreStructure(jrePath);
    ensureJvmCfg(jrePath);
    
    char jliPath[512];
    const char *archSubdir = detectArchSubdir(jrePath);
    
    void *libjli = nullptr;
    
    if (archSubdir) {
        snprintf(jliPath, sizeof(jliPath), "%s/lib/%s/jli/libjli.so", jrePath, archSubdir);
        libjli = dlopen(jliPath, RTLD_GLOBAL | RTLD_LAZY);
        if (libjli) { LOGI("Loaded libjli.so from: %s", jliPath); FLOGI("dlopen libjli: %s", jliPath); }
        else FLOGW("dlopen libjli FAILED: %s (%s)", jliPath, dlerror());
    }
    if (!libjli) {
        snprintf(jliPath, sizeof(jliPath), "%s/lib/jli/libjli.so", jrePath);
        libjli = dlopen(jliPath, RTLD_GLOBAL | RTLD_LAZY);
        if (libjli) { LOGI("Loaded libjli.so from: %s", jliPath); FLOGI("dlopen libjli: %s", jliPath); }
        else FLOGW("dlopen libjli FAILED: %s (%s)", jliPath, dlerror());
    }
    if (!libjli) {
        snprintf(jliPath, sizeof(jliPath), "%s/lib/libjli.so", jrePath);
        libjli = dlopen(jliPath, RTLD_GLOBAL | RTLD_LAZY);
        if (libjli) { LOGI("Loaded libjli.so from: %s", jliPath); FLOGI("dlopen libjli: %s", jliPath); }
        else FLOGW("dlopen libjli FAILED: %s (%s)", jliPath, dlerror());
    }
    
    if (!libjli) {
        FLOGE("Failed to load libjli.so for JLI_Launch");
        LOGE("Failed to load libjli.so for JLI_Launch");
        return -10;
    }
    
    JLI_Launch_t JLI_Launch_ptr = (JLI_Launch_t)dlsym(libjli, "JLI_Launch");
    if (!JLI_Launch_ptr) {
        FLOGE("Failed to find JLI_Launch in libjli.so: %s", dlerror());
        LOGE("Failed to find JLI_Launch in libjli.so: %s", dlerror());
        dlclose(libjli);
        return -11;
    }
    
    int newArgc = argc + 1;
    char **newArgv = (char **)malloc(sizeof(char *) * (newArgc + 1));
    char javaExe[512];
    snprintf(javaExe, sizeof(javaExe), "%s/bin/java", jrePath);
    newArgv[0] = strdup(javaExe);
    for (int i = 0; i < argc; i++) {
        newArgv[i + 1] = strdup(argv[i]);
    }
    newArgv[newArgc] = nullptr;

    g_libjli = libjli;
    memcpy(&g_jliLaunchFunc, &JLI_Launch_ptr, sizeof(JLI_Launch_ptr));

    char javaVersion[32];
    char javaVersionShort[32];
    getJavaVersion(jrePath, javaVersion, sizeof(javaVersion));
    snprintf(javaVersionShort, sizeof(javaVersionShort), "%.4s", javaVersion);

    FLOGI("=== Launching via JLI_Launch (FCL mode) ===");
    FLOGI("Java version: %s (short: %s)", javaVersion, javaVersionShort);
    FLOGI("First arg (java exe): %s", newArgv[0]);
    FLOGI("Total args: %d", newArgc);
    LOGI("=== Launching via JLI_Launch (FCL mode) ===");
    LOGI("Java version: %s (short: %s)", javaVersion, javaVersionShort);
    LOGI("First arg (java exe): %s", newArgv[0]);
    LOGI("Total args: %d", newArgc);
    for (int i = 0; i < newArgc && i < 10; i++) {
        LOGI("  argv[%d] = %s", i, newArgv[i]);
        FLOGI("  argv[%d] = %s", i, newArgv[i]);
    }
    if (newArgc > 10) { LOGI("  ... and %d more args", newArgc - 10); FLOGI("  ... and %d more args", newArgc - 10); }

    int result = JLI_Launch_ptr(newArgc, newArgv,
        0, nullptr, 0, nullptr,
        javaVersion, javaVersionShort, "java", "java",
        JNI_FALSE, JNI_FALSE, JNI_FALSE, 0);
    
    FLOGI("JLI_Launch returned: %d", result);
    LOGI("JLI_Launch returned: %d", result);
    
    for (int i = 0; i < newArgc; i++) {
        free(newArgv[i]);
    }
    free(newArgv);
    
    if (result != 0) {
        FLOGE("JLI_Launch failed with code %d, trying JNI_CreateJavaVM fallback...", result);
        LOGE("JLI_Launch failed with code %d, trying JNI_CreateJavaVM fallback...", result);
        result = launchJVMWithCreate(jrePath, argc, argv);
        FLOGI("JNI_CreateJavaVM fallback returned: %d", result);
        LOGI("JNI_CreateJavaVM fallback returned: %d", result);
    }
    
    return result;
}

static int launchJVMWithCreate(const char *jrePath, int argc, char **argv) {
    char libjvmPath[512];
    const char *archSubdir = detectArchSubdir(jrePath);

    void *libjvm = nullptr;
    
    if (archSubdir) {
        snprintf(libjvmPath, sizeof(libjvmPath), "%s/lib/%s/server/libjvm.so", jrePath, archSubdir);
        libjvm = dlopen(libjvmPath, RTLD_GLOBAL | RTLD_LAZY);
        if (libjvm) { LOGI("Loaded libjvm.so from: %s", libjvmPath); FLOGI("Loaded libjvm.so from: %s", libjvmPath); }
    }
    
    if (!libjvm) {
        snprintf(libjvmPath, sizeof(libjvmPath), "%s/lib/server/libjvm.so", jrePath);
        libjvm = dlopen(libjvmPath, RTLD_GLOBAL | RTLD_LAZY);
        if (libjvm) { LOGI("Loaded libjvm.so from: %s", libjvmPath); FLOGI("Loaded libjvm.so from: %s", libjvmPath); }
    }
    
    if (!libjvm) {
        snprintf(libjvmPath, sizeof(libjvmPath), "%s/lib/aarch64/server/libjvm.so", jrePath);
        libjvm = dlopen(libjvmPath, RTLD_GLOBAL | RTLD_LAZY);
        if (libjvm) { LOGI("Loaded libjvm.so from: %s", libjvmPath); FLOGI("Loaded libjvm.so from: %s", libjvmPath); }
    }
    
    if (!libjvm) {
        snprintf(libjvmPath, sizeof(libjvmPath), "%s/lib/amd64/server/libjvm.so", jrePath);
        libjvm = dlopen(libjvmPath, RTLD_GLOBAL | RTLD_LAZY);
        if (libjvm) { LOGI("Loaded libjvm.so from: %s", libjvmPath); FLOGI("Loaded libjvm.so from: %s", libjvmPath); }
    }
    
    if (!libjvm) {
        LOGE("Failed to load libjvm.so for JNI_CreateJavaVM");
        FLOGE("Failed to load libjvm.so for JNI_CreateJavaVM");
        return -5;
    }
    
    typedef jint (*JNI_CreateJavaVM_t)(JavaVM **, void **, void *);
    JNI_CreateJavaVM_t JNI_CreateJavaVM_ptr = (JNI_CreateJavaVM_t)dlsym(libjvm, "JNI_CreateJavaVM");
    if (!JNI_CreateJavaVM_ptr) {
        LOGE("Failed to find JNI_CreateJavaVM in libjvm.so: %s", dlerror());
        FLOGE("Failed to find JNI_CreateJavaVM in libjvm.so: %s", dlerror());
        return -6;
    }
    
    LOGI("=== Launching via JNI_CreateJavaVM ===");
    FLOGI("=== Launching via JNI_CreateJavaVM ===");
    logJreStructure(jrePath);
    ensureJvmCfg(jrePath);
    LOGI("Parsing %d args...", argc);
    FLOGI("Parsing %d args...", argc);
    
    for (int i = 0; i < argc; i++) {
        LOGI("  arg[%d]: %s", i, argv[i]);
        FLOGI("  arg[%d]: %s", i, argv[i]);
    }
    
    std::vector<JavaVMOption> jvmOptions;
    const char *classpath = nullptr;
    const char *mainClass = nullptr;
    int gameArgStart = -1;
    bool expectCp = false;
    bool expectModuleArg = false;
    const char *lastModuleOpt = nullptr;
    bool hasJavaHome = false;
    
    for (int i = 0; i < argc; i++) {
        if (expectCp) {
            classpath = argv[i];
            expectCp = false;
            LOGI("Classpath: %s", classpath);
            FLOGI("Classpath: %s", classpath);
            continue;
        }
        if (gameArgStart >= 0) {
            continue;
        }
        if (expectModuleArg) {
            // Merge two-arg module options: --add-exports <value> -> --add-exports=<value>
            char combined[1024];
            snprintf(combined, sizeof(combined), "%s=%s", lastModuleOpt, argv[i]);
            JavaVMOption opt;
            opt.optionString = strdup(combined);
            opt.extraInfo = nullptr;
            jvmOptions.push_back(opt);
            LOGI("Combined module option: %s", combined);
            FLOGI("Combined module option: %s", combined);
            expectModuleArg = false;
            continue;
        }
        if (strcmp(argv[i], "-cp") == 0 || strcmp(argv[i], "-classpath") == 0) {
            expectCp = true;
            continue;
        }
        if (strcmp(argv[i], "mio.Wrapper") == 0) {
            // FCL uses mio.Wrapper as the real main class; everything after is game args
            mainClass = argv[i];
            gameArgStart = i + 1;
            LOGI("Main class: %s (game args start at %d)", mainClass, gameArgStart);
            FLOGI("Main class: %s (game args start at %d)", mainClass, gameArgStart);
            continue;
        }
        if (argv[i][0] == '-') {
            // -javaagent / -agentlib / -agentpath 直接作为原生 JVM 选项传入，
            // 由 JVM 在 VM 初始化阶段原生加载 agent（含正确的 InstrumentationImpl），
            // 避免手动创建 InstrumentationImpl 因构造函数签名差异而失败。
            if (strncmp(argv[i], "-javaagent:", 11) == 0 ||
                strncmp(argv[i], "-agentlib:", 10) == 0 ||
                strncmp(argv[i], "-agentpath:", 11) == 0) {
                LOGI("Passing agent arg to JVM natively: %s", argv[i]);
                FLOGI("Passing agent arg to JVM natively: %s", argv[i]);
                JavaVMOption opt;
                opt.optionString = argv[i];
                opt.extraInfo = nullptr;
                jvmOptions.push_back(opt);
                continue;
            }
            // Module options that take a separate value argument (e.g. --add-exports <val>)
            if (strcmp(argv[i], "--add-exports") == 0 ||
                strcmp(argv[i], "--add-opens") == 0 ||
                strcmp(argv[i], "--add-modules") == 0 ||
                strcmp(argv[i], "--patch-module") == 0 ||
                strcmp(argv[i], "--enable-native-access") == 0 ||
                strcmp(argv[i], "--limit-modules") == 0 ||
                strcmp(argv[i], "--upgrade-module-path") == 0 ||
                strcmp(argv[i], "--module-path") == 0 ||
                strcmp(argv[i], "-p") == 0 ||
                strcmp(argv[i], "--module") == 0 ||
                strcmp(argv[i], "-m") == 0) {
                lastModuleOpt = argv[i];
                expectModuleArg = true;
                continue;
            }
            JavaVMOption opt;
            opt.optionString = argv[i];
            opt.extraInfo = nullptr;
            jvmOptions.push_back(opt);
            if (strncmp(argv[i], "-Djava.home=", 12) == 0) hasJavaHome = true;
        } else {
            mainClass = argv[i];
            gameArgStart = i + 1;
            LOGI("Main class: %s (game args start at %d)", mainClass, gameArgStart);
            FLOGI("Main class: %s (game args start at %d)", mainClass, gameArgStart);
        }
    }
    
    if (!hasJavaHome) {
        char homeBuf[512];
        snprintf(homeBuf, sizeof(homeBuf), "-Djava.home=%s", jrePath);
        JavaVMOption opt;
        opt.optionString = strdup(homeBuf);
        opt.extraInfo = nullptr;
        jvmOptions.push_back(opt);
        LOGI("Added -Djava.home=%s", jrePath);
    }
    
    char cpBuf[65536];
    if (classpath && strlen(classpath) > 0) {
        JavaVMOption opt;
        snprintf(cpBuf, sizeof(cpBuf), "-Djava.class.path=%s", classpath);
        opt.optionString = cpBuf;
        opt.extraInfo = nullptr;
        jvmOptions.push_back(opt);
        LOGI("Added classpath option");
        FLOGI("Added classpath option");
    }
    
    LOGI("JVM options count: %zu", jvmOptions.size());
    FLOGI("JVM options count: %zu", jvmOptions.size());
    
    JavaVMInitArgs vmArgs;
    vmArgs.version = JNI_VERSION_1_6;
    vmArgs.nOptions = (jint)jvmOptions.size();
    vmArgs.options = jvmOptions.data();
    vmArgs.ignoreUnrecognized = JNI_TRUE;
    
    JavaVM *jvm = nullptr;
    JNIEnv *env = nullptr;
    
    FLOGI("Calling JNI_CreateJavaVM...");
    LOGI("Calling JNI_CreateJavaVM...");
    jint createResult = JNI_CreateJavaVM_ptr(&jvm, (void **)&env, &vmArgs);
    if (createResult != JNI_OK) {
        LOGE("JNI_CreateJavaVM failed with code: %d", createResult);
        FLOGE("JNI_CreateJavaVM failed with code: %d", createResult);
        return createResult;
    }
    
    LOGI("JVM created successfully via JNI_CreateJavaVM");
    FLOGI("JVM created successfully via JNI_CreateJavaVM");

    installRealtimeSignalProbe();

    {
        jclass systemCls = env->FindClass("java/lang/System");
        if (systemCls) {
            jmethodID getProperty = env->GetStaticMethodID(systemCls, "getProperty", "(Ljava/lang/String;)Ljava/lang/String;");
            if (getProperty) {
                const char *props[] = {"jna.tmpdir", "java.io.tmpdir", "jna.nounpack", "jna.boot.library.path", "java.library.path", "user.home", nullptr};
                for (int i = 0; props[i]; i++) {
                    jstring key = env->NewStringUTF(props[i]);
                    jstring val = (jstring)env->CallStaticObjectMethod(systemCls, getProperty, key);
                    if (val) {
                        const char *valStr = env->GetStringUTFChars(val, nullptr);
                        LOGI("  System.getProperty(%s) = %s", props[i], valStr);
                        FLOGI("  System.getProperty(%s) = %s", props[i], valStr);
                        env->ReleaseStringUTFChars(val, valStr);
                        env->DeleteLocalRef(val);
                    } else {
                        LOGI("  System.getProperty(%s) = null", props[i]);
                        FLOGI("  System.getProperty(%s) = null", props[i]);
                        env->ExceptionClear();
                    }
                    env->DeleteLocalRef(key);
                }
            }
        }
    }
    
    if (mainClass && env && gameArgStart >= 0) {
        {
            jclass systemCls = env->FindClass("java/lang/System");
            if (systemCls) {
                jmethodID getProperty = env->GetStaticMethodID(systemCls, "getProperty", "(Ljava/lang/String;)Ljava/lang/String;");
                if (getProperty) {
                    const char *searchProps[] = {"java.library.path", "jna.tmpdir", "java.io.tmpdir", nullptr};
                    for (int i = 0; searchProps[i]; i++) {
                        jstring key = env->NewStringUTF(searchProps[i]);
                        jstring val = (jstring)env->CallStaticObjectMethod(systemCls, getProperty, key);
                        env->DeleteLocalRef(key);
                        if (env->ExceptionCheck()) { env->ExceptionClear(); continue; }
                        if (!val) continue;
                        const char *valStr = env->GetStringUTFChars(val, nullptr);
                        LOGI("  Search path from %s: %s", searchProps[i], valStr);
                        FLOGI("  Search path from %s: %s", searchProps[i], valStr);
                        
                        char *saveptr = nullptr;
                        char *pathCopy = strdup(valStr);
                        char *token = strtok_r(pathCopy, ":", &saveptr);
                        while (token) {
                            char soPath[1024];
                            snprintf(soPath, sizeof(soPath), "%s/libjnidispatch.so", token);
                            if (access(soPath, R_OK) == 0) {
                                LOGI("Pre-loading via dlopen: %s", soPath);
                                FLOGI("Pre-loading via dlopen: %s", soPath);
                                void *handle = dlopen(soPath, RTLD_GLOBAL | RTLD_LAZY);
                                if (handle) {
                                    LOGI("Successfully pre-loaded libjnidispatch.so via dlopen");
                                    FLOGI("Successfully pre-loaded libjnidispatch.so via dlopen");
                                } else {
                                    LOGE("dlopen failed: %s", dlerror());
                                    FLOGE("dlopen failed: %s", dlerror());
                                }
                                break;
                            }
                            token = strtok_r(nullptr, ":", &saveptr);
                        }
                        free(pathCopy);
                        env->ReleaseStringUTFChars(val, valStr);
                        env->DeleteLocalRef(val);
                        
                        break;
                    }
                }
            }
        }

        char clsName[512];
        snprintf(clsName, sizeof(clsName), "%s", mainClass);
        for (char *p = clsName; *p; p++) {
            if (*p == '.') *p = '/';
        }
        
        FLOGI("Finding main class: %s", clsName);
        LOGI("Finding main class: %s", clsName);
        jclass mainCls = env->FindClass(clsName);
        if (env->ExceptionCheck()) {
            env->ExceptionDescribe();
            env->ExceptionClear();
            LOGE("Failed to find main class: %s", clsName);
            FLOGE("Failed to find main class: %s", clsName);
            jvm->DestroyJavaVM();
            return -7;
        }
        
        if (!mainCls) {
            LOGE("FindClass returned null for: %s", clsName);
            FLOGE("FindClass returned null for: %s", clsName);
            jvm->DestroyJavaVM();
            return -8;
        }
        
        jmethodID mainMethod = env->GetStaticMethodID(mainCls, "main", "([Ljava/lang/String;)V");
        if (!mainMethod || env->ExceptionCheck()) {
            env->ExceptionDescribe();
            env->ExceptionClear();
            LOGE("Failed to find main() method");
            FLOGE("Failed to find main() method");
            jvm->DestroyJavaVM();
            return -9;
        }
        
        int gameArgCount = argc - gameArgStart;
        LOGI("Invoking main() with %d game args", gameArgCount);
        FLOGI("Invoking %s.main() with %d game args", mainClass, gameArgCount);
        
        jclass stringCls = env->FindClass("java/lang/String");
        jobjectArray argsObj = env->NewObjectArray(gameArgCount, stringCls, nullptr);
        for (int i = 0; i < gameArgCount; i++) {
            jstring arg = env->NewStringUTF(argv[gameArgStart + i]);
            env->SetObjectArrayElement(argsObj, i, arg);
        }
        
        env->CallStaticVoidMethod(mainCls, mainMethod, argsObj);
        
        if (env->ExceptionCheck()) {
            env->ExceptionDescribe();
            env->ExceptionClear();
            FLOGE("main() method threw an exception");
        }
        
        LOGI("Main method returned");
        FLOGI("Main method returned");
    }
    
    FLOGI("Destroying JVM...");
    jvm->DestroyJavaVM();
    LOGI("JVM destroyed");
    FLOGI("JVM destroyed");
    
    return 0;
}

static void chmodRecursive(const char *dirPath, mode_t fileMode, mode_t dirMode) {
    DIR *dir = opendir(dirPath);
    if (!dir) {
        FLOGW("chmodRecursive: opendir failed for %s (%s)", dirPath, strerror(errno));
        return;
    }
    
    struct dirent *entry;
    char fullPath[1024];
    
    while ((entry = readdir(dir)) != nullptr) {
        if (strcmp(entry->d_name, ".") == 0 || strcmp(entry->d_name, "..") == 0) continue;
        
        snprintf(fullPath, sizeof(fullPath), "%s/%s", dirPath, entry->d_name);
        
        struct stat st;
        if (lstat(fullPath, &st) != 0) continue;
        
        if (S_ISDIR(st.st_mode)) {
            if (chmod(fullPath, dirMode) != 0) {
                FLOGW("chmod dir %s: %s", fullPath, strerror(errno));
            }
            chmodRecursive(fullPath, fileMode, dirMode);
        } else if (S_ISREG(st.st_mode)) {
            if (chmod(fullPath, fileMode) != 0) {
                FLOGW("chmod file %s: %s", fullPath, strerror(errno));
            }
        } else if (S_ISLNK(st.st_mode)) {
            chmod(fullPath, 0755);
        }
    }
    closedir(dir);
}

static void fixJrePermissions(const char *jrePath) {
    LOGI("Fixing JRE file permissions for %s (using POSIX chmod)", jrePath);
    FLOGI("fixJrePermissions: %s", jrePath);
    
    char path[1024];
    
    // V3.7: Fix bin/java FIRST before recursive chmod, with explicit error checking
    snprintf(path, sizeof(path), "%s/bin/java", jrePath);
    if (access(path, F_OK) == 0) {
        struct stat st;
        if (stat(path, &st) == 0) {
            FLOGI("bin/java: mode=%o, uid=%d, gid=%d", st.st_mode & 0777, st.st_uid, st.st_gid);
        }
        if (access(path, X_OK) == 0) {
            LOGI("  bin/java is already executable");
            FLOGI("  bin/java is already executable");
        } else {
            if (chmod(path, 0755) == 0) {
                LOGI("  bin/java chmod 755 OK");
                FLOGI("  bin/java chmod 755 OK");
            } else {
                LOGE("  bin/java chmod FAILED: %s (errno=%d)", strerror(errno), errno);
                FLOGE("  bin/java chmod FAILED: %s (errno=%d)", strerror(errno), errno);
            }
            if (access(path, X_OK) == 0) {
                LOGI("  bin/java is now executable");
                FLOGI("  bin/java is now executable");
            } else {
                LOGE("  bin/java still NOT executable after chmod!");
                FLOGE("  bin/java still NOT executable after chmod!");
            }
        }
    } else {
        LOGE("  bin/java NOT FOUND at %s!", path);
        FLOGE("  bin/java NOT FOUND at %s!", path);
    }
    
    snprintf(path, sizeof(path), "%s/bin", jrePath);
    if (access(path, F_OK) == 0) {
        chmodRecursive(path, 0755, 0755);
        FLOGI("  Set 755 on %s recursively", path);
    } else {
        FLOGW("  bin dir not found: %s", path);
    }
    
    snprintf(path, sizeof(path), "%s/lib", jrePath);
    if (access(path, F_OK) == 0) {
        chmodRecursive(path, 0755, 0755);
        
        snprintf(path, sizeof(path), "%s/lib/modules", jrePath);
        if (access(path, F_OK) == 0) chmod(path, 0644);
        
        snprintf(path, sizeof(path), "%s/lib/jvm.cfg", jrePath);
        if (access(path, F_OK) == 0) chmod(path, 0644);
        
        FLOGI("  Set 755 on lib recursively, 644 on modules/jvm.cfg");
        LOGI("  Set 755 on lib (recursively), 644 on modules/jvm.cfg/rt.jar");
    }
    
    snprintf(path, sizeof(path), "%s/release", jrePath);
    if (access(path, F_OK) == 0) chmod(path, 0644);
    
    FLOGI("JRE permissions fix complete for %s", jrePath);
    LOGI("JRE permissions fixed");
}

static void *jvmThreadFunc(void *arg) {
    JVMLaunchArgs *launchArgs = (JVMLaunchArgs *)arg;
    
    FLOGI("========================================");
    FLOGI("=== TERRACOTTA V3.13 - JLI_Launch primary + JNI_CreateJavaVM fallback ===");
    FLOGI("JRE: %s", launchArgs->jrePath);
    FLOGI("Args: %d", launchArgs->argc);
    LOGI("=== JVM Thread Started ===");
    LOGI("=== TERRACOTTA V3.13 - JLI_Launch primary + JNI_CreateJavaVM fallback ===");
    LOGI("JRE: %s", launchArgs->jrePath);
    LOGI("Args: %d", launchArgs->argc);
    
    fixJrePermissions(launchArgs->jrePath);
    logJreStructure(launchArgs->jrePath);
    ensureJvmCfg(launchArgs->jrePath);

    resetSignalHandlersForJVM();
    
    char jreLibPath[512];
    snprintf(jreLibPath, sizeof(jreLibPath), "%s/lib", launchArgs->jrePath);
    struct stat st;
    if (stat(jreLibPath, &st) != 0 || !S_ISDIR(st.st_mode)) {
        LOGE("JRE lib directory does not exist: %s", jreLibPath);
        g_jvmRunning = false;
        g_exitCode = -1;
        notifyExit(-1);
        cleanupLaunchArgs(launchArgs);
        return nullptr;
    }
    
    char ldPath[4096];
    const char *existingLdPath = getenv("LD_LIBRARY_PATH");

    const char *archSubdir = detectArchSubdir(launchArgs->jrePath);

    char nativesPath[1024] = {0};
    char jnaPath[1024] = {0};
    char modRuntimePath[1024] = {0};
    for (int i = 0; i < launchArgs->argc; i++) {
        if (launchArgs->argv[i] && strncmp(launchArgs->argv[i], "-Djava.library.path=", 20) == 0) {
            const char *natives = launchArgs->argv[i] + 20;
            strncpy(nativesPath, natives, sizeof(nativesPath) - 1);
            LOGI("Found java.library.path from args: %s", nativesPath);
        }
        if (launchArgs->argv[i] && strncmp(launchArgs->argv[i], "-Djna.boot.library.path=", 24) == 0) {
            const char *jna = launchArgs->argv[i] + 24;
            strncpy(jnaPath, jna, sizeof(jnaPath) - 1);
            LOGI("Found jna.boot.library.path from args: %s", jna);
        }
    }
    const char *modRuntimeEnv = getenv("MOD_ANDROID_RUNTIME");
    if (modRuntimeEnv && strlen(modRuntimeEnv) > 0) {
        strncpy(modRuntimePath, modRuntimeEnv, sizeof(modRuntimePath) - 1);
    }

    int written = snprintf(ldPath, sizeof(ldPath), "%s/lib", launchArgs->jrePath);
    if (written >= (int)sizeof(ldPath)) written = (int)sizeof(ldPath) - 1;
    written += snprintf(ldPath + written, sizeof(ldPath) - written, ":%s/lib/jli", launchArgs->jrePath);
    if (written >= (int)sizeof(ldPath)) written = (int)sizeof(ldPath) - 1;
    written += snprintf(ldPath + written, sizeof(ldPath) - written, ":%s/lib/server", launchArgs->jrePath);
    if (written >= (int)sizeof(ldPath)) written = (int)sizeof(ldPath) - 1;
    if (archSubdir) {
        written += snprintf(ldPath + written, sizeof(ldPath) - written, ":%s/lib/%s", launchArgs->jrePath, archSubdir);
        if (written >= (int)sizeof(ldPath)) written = (int)sizeof(ldPath) - 1;
        written += snprintf(ldPath + written, sizeof(ldPath) - written, ":%s/lib/%s/jli", launchArgs->jrePath, archSubdir);
        if (written >= (int)sizeof(ldPath)) written = (int)sizeof(ldPath) - 1;
        written += snprintf(ldPath + written, sizeof(ldPath) - written, ":%s/lib/%s/server", launchArgs->jrePath, archSubdir);
        if (written >= (int)sizeof(ldPath)) written = (int)sizeof(ldPath) - 1;
    }
    if (nativesPath[0] != '\0') {
        written += snprintf(ldPath + written, sizeof(ldPath) - written, ":%s", nativesPath);
        if (written >= (int)sizeof(ldPath)) written = (int)sizeof(ldPath) - 1;
    }
    written += snprintf(ldPath + written, sizeof(ldPath) - written, ":/system/lib64:/vendor/lib64:/vendor/lib64/hw:/system_ext/lib64:/system/lib:/vendor/lib");
    if (written >= (int)sizeof(ldPath)) written = (int)sizeof(ldPath) - 1;
    if (jnaPath[0] != '\0') {
        written += snprintf(ldPath + written, sizeof(ldPath) - written, ":%s", jnaPath);
        if (written >= (int)sizeof(ldPath)) written = (int)sizeof(ldPath) - 1;
    }
    if (modRuntimePath[0] != '\0') {
        written += snprintf(ldPath + written, sizeof(ldPath) - written, ":%s", modRuntimePath);
        if (written >= (int)sizeof(ldPath)) written = (int)sizeof(ldPath) - 1;
    }
    if (existingLdPath && strlen(existingLdPath) > 0) {
        snprintf(ldPath + written, sizeof(ldPath) - written, ":%s", existingLdPath);
    }

    // FCL Pattern: Use android_update_LD_LIBRARY_PATH (proper Android linker API)
    // setenv() does NOT update the dynamic linker's search path on Android
    void *libdl_handle = dlopen("libdl.so", RTLD_LAZY);
    if (libdl_handle) {
        typedef void (*update_ld_path_t)(const char*);
        update_ld_path_t android_update = (update_ld_path_t)dlsym(libdl_handle, "android_update_LD_LIBRARY_PATH");
        if (!android_update) {
            android_update = (update_ld_path_t)dlsym(libdl_handle, "__loader_android_update_LD_LIBRARY_PATH");
        }
        if (android_update) {
            android_update(ldPath);
            LOGI("Updated LD_LIBRARY_PATH via android_update_LD_LIBRARY_PATH: %s", ldPath);
        } else {
            setenv("LD_LIBRARY_PATH", ldPath, 1);
            LOGI("Fallback setenv LD_LIBRARY_PATH=%s", ldPath);
        }
        dlclose(libdl_handle);
    } else {
        setenv("LD_LIBRARY_PATH", ldPath, 1);
        LOGI("Fallback setenv LD_LIBRARY_PATH=%s", ldPath);
    }
    LOGI("Arch subdir detected: %s", archSubdir ? archSubdir : "none");
    setenv("JAVA_HOME", launchArgs->jrePath, 1);

    FLOGI("=== Environment Variables ===");
    const char *dumpKeys[] = {
        "JAVA_HOME", "FCL_NATIVEDIR", "POJAV_NATIVEDIR", "HOME", "TMPDIR",
        "POJAV_RENDERER", "LIBGL_NAME", "LIBEGL_NAME", "LIBGL_ES",
        "FORCE_VSYNC", "DRIVER_PATH", "LD_LIBRARY_PATH", "PATH",
        "MOD_ANDROID_RUNTIME", "POJAVEXEC_EGL", "DLOPEN", nullptr
    };
    for (int i = 0; dumpKeys[i]; i++) {
        const char *val = getenv(dumpKeys[i]);
        FLOGI("  %s=%s", dumpKeys[i], val ? val : "(unset)");
    }
    FLOGI("  LD_LIBRARY_PATH (built): %s", ldPath);

    installExitHook();

    preloadJreLibs(launchArgs->jrePath);
    
    g_jmpBufValid = true;
    int jmpResult = sigsetjmp(g_jmpBuf, 1);
    
    if (jmpResult == 0) {
        pthread_mutex_lock(&g_jvmStartMutex);
        g_jvmStartConfirmed = true;
        pthread_cond_signal(&g_jvmStartCond);
        pthread_mutex_unlock(&g_jvmStartMutex);

        FLOGI("Using JNI_CreateJavaVM directly (Android noexec workaround)...");
        LOGI("Using JNI_CreateJavaVM directly (Android noexec workaround)...");
        int result = launchJVMWithCreate(launchArgs->jrePath, launchArgs->argc, launchArgs->argv);
        FLOGI("JNI_CreateJavaVM returned: %d", result);
        LOGI("JNI_CreateJavaVM returned: %d", result);

        if (result == -5 || result == -6) {
            FLOGI("JNI_CreateJavaVM unavailable (libjvm.so missing), trying JLI_Launch...");
            LOGI("JNI_CreateJavaVM unavailable (libjvm.so missing), trying JLI_Launch...");
            result = launchJVMWithJLI(launchArgs->jrePath, launchArgs->argc, launchArgs->argv);
            FLOGI("JLI_Launch returned: %d", result);
            LOGI("JLI_Launch returned: %d", result);
        }
        
        g_exitCode = result;
    } else {
        FLOGW("JVM launch interrupted with code %d", jmpResult);
        LOGW("JVM launch interrupted with code %d", jmpResult);
        g_exitCode = jmpResult;
    }
    
    g_jmpBufValid = false;
    g_jvmRunning = false;
    g_jliLaunchFunc = nullptr;
    g_libjli = nullptr;
    
    FLOGI("JVM thread exiting with code %d", g_exitCode);
    notifyExit(g_exitCode);
    cleanupLaunchArgs(launchArgs);
    
    return nullptr;
}

extern "C" {

JNIEXPORT jint JNI_OnLoad(JavaVM* vm, void* reserved) {
    LOGI("Terracotta native library loaded");
    FLOGI("Terracotta JNI_OnLoad - native library loaded");
    g_androidVM = vm;
    
    void* handle = dlopen("libglfw.so", RTLD_NOW | RTLD_GLOBAL);
    if (handle) {
        LOGI("Pre-loaded custom libglfw.so successfully");
        FLOGI("Pre-loaded custom libglfw.so successfully");
    } else {
        LOGW("Failed to pre-load libglfw.so: %s", dlerror());
        FLOGW("Failed to pre-load libglfw.so: %s", dlerror());
    }
    
    return JNI_VERSION_1_6;
}

JNIEXPORT void JNICALL
Java_com_verse_explorer_jvmlauncher_JvmLauncherAPI_nativeSetenv(
    JNIEnv *env, jclass clazz, jstring key, jstring value) {
    const char *keyStr = env->GetStringUTFChars(key, nullptr);
    const char *valueStr = env->GetStringUTFChars(value, nullptr);
    setenv(keyStr, valueStr, 1);
    LOGI("setenv: %s=%s", keyStr, valueStr);
    env->ReleaseStringUTFChars(key, keyStr);
    env->ReleaseStringUTFChars(value, valueStr);
}

JNIEXPORT void JNICALL
Java_com_verse_explorer_jvmlauncher_JvmLauncherAPI_nativeSetLogPath(
    JNIEnv *env, jclass clazz, jstring path) {
    const char *pathStr = env->GetStringUTFChars(path, nullptr);
    snprintf(g_debugLogPath, sizeof(g_debugLogPath), "%s", pathStr);
    LOGI("Debug log path set: %s", g_debugLogPath);
    env->ReleaseStringUTFChars(path, pathStr);
}

JNIEXPORT jint JNICALL
Java_com_verse_explorer_jvmlauncher_JvmLauncherAPI_nativeChdir(
    JNIEnv *env, jclass clazz, jstring path) {
    const char *pathStr = env->GetStringUTFChars(path, nullptr);
    int result = chdir(pathStr);
    LOGI("chdir: %s -> %d", pathStr, result);
    env->ReleaseStringUTFChars(path, pathStr);
    return result;
}

JNIEXPORT jlong JNICALL
Java_com_verse_explorer_jvmlauncher_JvmLauncherAPI_nativeDlopen(
    JNIEnv *env, jclass clazz, jstring path) {
    const char *pathStr = env->GetStringUTFChars(path, nullptr);
    void *handle = dlopen(pathStr, RTLD_GLOBAL | RTLD_LAZY);
    LOGI("dlopen: %s -> %p", pathStr, handle);
    if (handle) {
        FLOGI("nativeDlopen: %s -> %p", pathStr, handle);
    } else {
        FLOGW("nativeDlopen FAILED: %s (%s)", pathStr, dlerror());
    }
    env->ReleaseStringUTFChars(path, pathStr);
    return reinterpret_cast<jlong>(handle);
}

JNIEXPORT jint JNICALL
Java_com_verse_explorer_jvmlauncher_JvmLauncherAPI_nativeRedirectStdio(
    JNIEnv *env, jclass clazz, jstring path) {
    const char *pathStr = env->GetStringUTFChars(path, nullptr);
    LOGI("redirectStdio: %s", pathStr);
    FLOGI("redirectStdio: %s", pathStr);
    
    if (g_logFd >= 0) {
        close(g_logFd);
        g_logFd = -1;
    }
    
    if (pathStr && strlen(pathStr) > 0 && strcmp(pathStr, "/dev/null") != 0) {
        g_logFd = open(pathStr, O_WRONLY | O_CREAT | O_APPEND, 0644);
        if (g_logFd < 0) {
            LOGW("Failed to open log file: %s", pathStr);
        } else {
            dup2(g_logFd, STDOUT_FILENO);
            dup2(g_logFd, STDERR_FILENO);
            LOGI("Stdout/stderr redirected to: %s", pathStr);
        }
    }
    
    env->ReleaseStringUTFChars(path, pathStr);
    return 0;
}

JNIEXPORT void JNICALL
Java_com_verse_explorer_jvmlauncher_JvmLauncherAPI_nativeSetLdLibraryPath(
    JNIEnv *env, jclass clazz, jstring path) {
    const char *pathStr = env->GetStringUTFChars(path, nullptr);
    setenv("LD_LIBRARY_PATH", pathStr, 1);
    
    void *libdl_handle = dlopen("libdl.so", RTLD_LAZY);
    if (libdl_handle) {
        typedef void (*update_ld_path_t)(const char*);
        update_ld_path_t android_update = (update_ld_path_t)dlsym(libdl_handle, "android_update_LD_LIBRARY_PATH");
        if (!android_update) {
            android_update = (update_ld_path_t)dlsym(libdl_handle, "__loader_android_update_LD_LIBRARY_PATH");
        }
        if (android_update) {
            android_update(pathStr);
            FLOGI("Updated LD_LIBRARY_PATH via android_update: %s", pathStr);
            LOGI("Updated LD_LIBRARY_PATH via android_update_LD_LIBRARY_PATH: %s", pathStr);
        } else {
            FLOGI("Fallback setenv LD_LIBRARY_PATH=%s", pathStr);
            LOGI("Fallback setenv LD_LIBRARY_PATH=%s", pathStr);
        }
        dlclose(libdl_handle);
    } else {
        FLOGI("Fallback setenv LD_LIBRARY_PATH=%s", pathStr);
        LOGI("Fallback setenv LD_LIBRARY_PATH=%s", pathStr);
    }
    
    env->ReleaseStringUTFChars(path, pathStr);
}

JNIEXPORT jint JNICALL
Java_com_verse_explorer_jvmlauncher_JvmLauncherAPI_nativeLaunchJVM(
    JNIEnv *env, jclass clazz, jstring jrePath, jobjectArray argsArray) {
    
    LOGI("=== nativeLaunchJVM V2.7 ===");
    
    if (g_launchInProgress) {
        LOGW("Launch already in progress, rejecting duplicate call");
        return -3;
    }
    
    if (g_jvmRunning) {
        LOGW("JVM marked as running, forcing reset");
        g_jvmRunning = false;
    }
    
    if (!jrePath || !argsArray) {
        LOGE("Missing parameters");
        return -1;
    }
    
    pthread_mutex_lock(&g_jvmStartMutex);
    g_jvmStartConfirmed = false;
    pthread_mutex_unlock(&g_jvmStartMutex);
    
    g_launchInProgress = true;
    
    JVMLaunchArgs *launchArgs = (JVMLaunchArgs *)calloc(1, sizeof(JVMLaunchArgs));
    
    const char *jrePathStr = env->GetStringUTFChars(jrePath, nullptr);
    launchArgs->jrePath = strdup(jrePathStr);
    FLOGI("nativeLaunchJVM: JRE=%s, %d args", jrePathStr, launchArgs->argc);
    env->ReleaseStringUTFChars(jrePath, jrePathStr);
    
    launchArgs->argc = env->GetArrayLength(argsArray);
    launchArgs->argv = (char **)malloc(launchArgs->argc * sizeof(char *));
    for (int i = 0; i < launchArgs->argc; i++) {
        auto arg = (jstring)env->GetObjectArrayElement(argsArray, i);
        const char *argStr = env->GetStringUTFChars(arg, nullptr);
        launchArgs->argv[i] = strdup(argStr);
        env->ReleaseStringUTFChars(arg, argStr);
        env->DeleteLocalRef(arg);
    }
    
    struct stat st;
    if (stat(launchArgs->jrePath, &st) != 0) {
        LOGE("JRE path does not exist: %s", launchArgs->jrePath);
        cleanupLaunchArgs(launchArgs);
        g_launchInProgress = false;
        return -1;
    }
    
    g_jvmRunning = true;
    g_exitCode = 0;
    g_jvmStartConfirmed = false;
    
    int ret = pthread_create(&g_jvmThread, nullptr, jvmThreadFunc, launchArgs);
    if (ret != 0) {
        LOGE("Failed to create JVM thread: %s", strerror(ret));
        g_jvmRunning = false;
        g_launchInProgress = false;
        cleanupLaunchArgs(launchArgs);
        return -1;
    }
    
    struct timespec ts;
    clock_gettime(CLOCK_REALTIME, &ts);
    ts.tv_sec += 15;
    
    pthread_mutex_lock(&g_jvmStartMutex);
    int waitResult = 0;
    while (!g_jvmStartConfirmed && waitResult == 0) {
        waitResult = pthread_cond_timedwait(&g_jvmStartCond, &g_jvmStartMutex, &ts);
    }
    bool started = g_jvmStartConfirmed;
    pthread_mutex_unlock(&g_jvmStartMutex);
    
    if (!started) {
        LOGE("JVM thread failed to start within 15s timeout (JRE setup or native lib load failed)");
        g_jvmRunning = false;
        g_launchInProgress = false;
        return -2;
    }
    
    pthread_detach(g_jvmThread);
    LOGI("JVM thread confirmed startup, JVM launching...");
    g_launchInProgress = false;
    return 0;
}

JNIEXPORT jboolean JNICALL
Java_com_verse_explorer_jvmlauncher_JvmLauncherAPI_nativeIsJVMRunning(
    JNIEnv *env, jclass clazz) {
    return g_jvmRunning ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT void JNICALL
Java_com_verse_explorer_jvmlauncher_JvmLauncherAPI_nativeDestroyJVM(
    JNIEnv *env, jclass clazz) {
    if (g_jvmRunning) {
        LOGI("Destroying JVM...");
        g_jvmRunning = false;
        g_launchInProgress = false;
    }
}

JNIEXPORT jint JNICALL
Java_com_verse_explorer_jvmlauncher_JvmLauncherAPI_nativeGetExitCode(
    JNIEnv *env, jclass clazz) {
    return g_exitCode;
}

JNIEXPORT jint JNICALL
Java_com_verse_explorer_jvmlauncher_JvmLauncherAPI_nativeGetPid(
    JNIEnv *env, jclass clazz) {
    return (jint)getpid();
}

JNIEXPORT void JNICALL
Java_com_verse_explorer_jvmlauncher_JvmLauncherAPI_nativeSetupExitTrap(
    JNIEnv *env, jclass clazz, jobject callback) {
    if (g_exitCallback) {
        env->DeleteGlobalRef(g_exitCallback);
    }
    g_exitCallback = env->NewGlobalRef(callback);
    
    if (!g_androidVM) {
        env->GetJavaVM(&g_androidVM);
    }
    
    jclass callbackClass = env->GetObjectClass(callback);
    g_onExitMethod = env->GetMethodID(callbackClass, "onExit", "(I)V");
    if (env->ExceptionCheck()) {
        env->ExceptionClear();
        LOGE("Failed to find onExit method");
        g_onExitMethod = nullptr;
    }
    env->DeleteLocalRef(callbackClass);
    LOGI("Exit trap setup complete");
}

JNIEXPORT void JNICALL
Java_com_verse_explorer_jvmlauncher_JvmLauncherAPI_nativeReleaseExitCallback(
    JNIEnv *env, jclass clazz) {
    if (g_exitCallback) {
        env->DeleteGlobalRef(g_exitCallback);
        g_exitCallback = nullptr;
        g_onExitMethod = nullptr;
        LOGI("Exit callback released");
    }
}

JNIEXPORT void JNICALL
Java_com_verse_explorer_jvmlauncher_JvmLauncherAPI_nativeSetupBridgeWindow(
    JNIEnv *env, jclass clazz, jobject surface) {

    g_nativeWindowReady = 0;

    if (surface) {
        ANativeWindow* newWindow = ANativeWindow_fromSurface(env, surface);
        if (newWindow) {
            ANativeWindow* oldWindow = g_nativeWindow;
            g_nativeWindow = newWindow;
            g_windowWidth = ANativeWindow_getWidth(newWindow);
            g_windowHeight = ANativeWindow_getHeight(newWindow);
            g_nativeWindowReady = 1;
            LOGI("Bridge window set: %dx%d", g_windowWidth, g_windowHeight);
            FLOGI("Bridge window set: %dx%d, ptr=%p", g_windowWidth, g_windowHeight, newWindow);

            if (oldWindow) {
                ANativeWindow_release(oldWindow);
            }
        } else {
            LOGE("Failed to get ANativeWindow from surface!");
            FLOGE("Failed to get ANativeWindow from surface!");
        }
    } else {
        LOGW("nativeSetupBridgeWindow called with null surface");
        FLOGW("nativeSetupBridgeWindow called with null surface");
    }
}

extern "C" {
__attribute__((visibility("default")))
ANativeWindow* pojav_getNativeWindow(void) {
    if (!g_nativeWindowReady || !g_nativeWindow) return nullptr;
    ANativeWindow* w = g_nativeWindow;
    ANativeWindow_acquire(w);
    return w;
}

__attribute__((visibility("default")))
ANativeWindow* getFCLNativeWindow(void) {
    if (!g_nativeWindowReady || !g_nativeWindow) return nullptr;
    ANativeWindow* w = g_nativeWindow;
    ANativeWindow_acquire(w);
    return w;
}
}

JNIEXPORT void JNICALL
Java_com_verse_explorer_jvmlauncher_JvmLauncherAPI_nativeSendData(
    JNIEnv *env, jclass clazz, jboolean isAndroid, jint type, jint i1, jint i2, jint i3, jint i4) {
}

}
