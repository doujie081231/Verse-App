#include <jni.h>
#include <string>
#include <vector>
#include <android/log.h>
#include <dlfcn.h>
#include <unistd.h>
#include <sys/stat.h>
#include <sys/types.h>
#include <dirent.h>
#include <cerrno>
#include <cstdlib>
#include <cstring>
#include <pthread.h>
#include <fcntl.h>
#include <sys/prctl.h>

#ifndef PR_SET_TAGGED_ADDR_CTRL
#define PR_SET_TAGGED_ADDR_CTRL 55
#endif
#ifndef PR_TAGGED_ADDR_ENABLE
#define PR_TAGGED_ADDR_ENABLE (1UL << 0)
#endif

#define LOG_TAG "JVMLoader"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN, LOG_TAG, __VA_ARGS__)

// Android 10+ 的 fdsan（FD 消毒器）会把部分 JDK/JRE（含 FCL 打包的旧构建）初始化期
// 合法的 close 判定为 double close（"attempted to close file descriptor N, expected
// to be unowned, actually owned by FILE*"）并直接 SIGABRT，导致 JVM 无法启动。
// verseexec 由 :jvm 安装器进程与游戏进程在启动 JVM 前加载，这里在库加载时把
// fdsan 降级为 WARN（1），仅放行访问，不改变其他行为（社区对 Pojav/FCL 旧 JVM 的
// 标准规避手段）。用 dlsym 解析以兼容 Android 9 及更低版本（无该符号时忽略）。
static void __attribute__((constructor)) verseexec_disable_fdsan() {
    typedef int (*fdsan_set_level_fn)(int level);
    void *libc = dlopen("libc.so", RTLD_NOLOAD);
    if (!libc) return;
    fdsan_set_level_fn fn = (fdsan_set_level_fn)dlsym(libc, "android_fdsan_set_error_level");
    if (fn) fn(1); // ANDROID_FDSAN_ERROR_LEVEL_WARN
}

typedef int (*JLI_Launch_t)(int argc, char **argv, int jreargc, const char **jreargv,
                            int appclasspathc, const char **appclasspathv,
                            const char *const version, const char *const classname,
                            const char *const jarname, const char *const appname,
                            jboolean javaargs, jboolean cpwildcard, jboolean javaw,
                            jint ergo);

static volatile bool g_jvmRunning = false;
static volatile bool g_jvmDestroyRequested = false;
static volatile jint g_exitCode = 0;
static pthread_t g_jvmThread = 0;

static jobject g_callbackObj = nullptr;
static jmethodID g_onExitMethod = nullptr;
static JavaVM *g_androidVM = nullptr;

struct JVMLaunchArgs {
    char *jrePath;
    char **argv;
    int argc;
};

static void listDir(const char *path, int depth = 0) {
    if (depth > 3) return;
    DIR *dir = opendir(path);
    if (!dir) {
        LOGW("  Cannot open dir: %s", path);
        return;
    }
    struct dirent *entry;
    while ((entry = readdir(dir)) != nullptr) {
        if (strcmp(entry->d_name, ".") == 0 || strcmp(entry->d_name, "..") == 0) continue;
        char fullPath[512];
        snprintf(fullPath, sizeof(fullPath), "%s/%s", path, entry->d_name);
        struct stat st;
        if (stat(fullPath, &st) == 0) {
            if (S_ISDIR(st.st_mode)) {
                LOGI("  %*s[D] %s/", depth * 2, "", entry->d_name);
                listDir(fullPath, depth + 1);
            } else {
                LOGI("  %*s[F] %s (%ld bytes)", depth * 2, "", entry->d_name, (long)st.st_size);
            }
        }
    }
    closedir(dir);
}

static void *dlopen_verbose(const char *path) {
    void *handle = dlopen(path, RTLD_GLOBAL | RTLD_LAZY);
    if (handle) {
        LOGI("DLOPEN: loaded %s", path);
    } else {
        LOGW("DLOPEN: failed to load %s: %s", path, dlerror());
    }
    return handle;
}

static char *findFileInDir(const char *dirPath, const char *fileName, int maxDepth = 4) {
    if (maxDepth <= 0) return nullptr;
    DIR *dir = opendir(dirPath);
    if (!dir) return nullptr;

    struct dirent *entry;
    char *result = nullptr;
    while ((entry = readdir(dir)) != nullptr) {
        if (strcmp(entry->d_name, ".") == 0 || strcmp(entry->d_name, "..") == 0) continue;

        char fullPath[512];
        snprintf(fullPath, sizeof(fullPath), "%s/%s", dirPath, entry->d_name);

        if (entry->d_type == DT_REG) {
            if (strcmp(entry->d_name, fileName) == 0) {
                result = strdup(fullPath);
                break;
            }
        } else if (entry->d_type == DT_DIR) {
            result = findFileInDir(fullPath, fileName, maxDepth - 1);
            if (result) break;
        } else {
            struct stat st;
            if (stat(fullPath, &st) == 0) {
                if (S_ISREG(st.st_mode) && strcmp(entry->d_name, fileName) == 0) {
                    result = strdup(fullPath);
                    break;
                }
                if (S_ISDIR(st.st_mode)) {
                    result = findFileInDir(fullPath, fileName, maxDepth - 1);
                    if (result) break;
                }
            }
        }
    }
    closedir(dir);
    return result;
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

static void preloadJreLibs(const char *jreHome) {
    char path[512];

    const char *jvmPaths[] = {
        "/lib/server/libjvm.so",
        "/lib/aarch64/server/libjvm.so",
        "/lib/amd64/server/libjvm.so",
        "/lib/i386/server/libjvm.so",
        "/lib/arm/server/libjvm.so"
    };
    for (int i = 0; i < 5; i++) {
        snprintf(path, sizeof(path), "%s%s", jreHome, jvmPaths[i]);
        if (dlopen_verbose(path)) break;
    }

    const char *archSubdir = detectArchSubdir(jreHome);

    const char *runtimeLibs[] = {
        "/lib/libfreetype.so",
        "/lib/libfreetype.so.6",
        "/lib/libverify.so",
        "/lib/libjava.so",
        "/lib/libnet.so",
        "/lib/libnio.so",
        "/lib/libawt.so",
        "/lib/libawt_headless.so",
        "/lib/libfontmanager.so",
        "/lib/libzip.so",
        "/lib/libjimage.so",
        "/lib/libsyslookup.so",
        "/lib/libinstrument.so",
        "/lib/libmanagement.so",
        "/lib/libmanagement_ext.so",
        "/lib/libjdwp.so",
        "/lib/libdt_socket.so",
        "/lib/libattach.so",
        "/lib/libsctp.so",
        "/lib/libextnet.so",
        "/lib/libjaas.so",
        "/lib/libprefs.so",
        "/lib/librmi.so",
        "/lib/liblcms.so",
        "/lib/libjavajpeg.so",
        "/lib/libjawt.so",
        "/lib/libjsound.so",
        "/lib/libj2pcsc.so",
        "/lib/libsunec.so",
        "/lib/libj2gss.so",
        "/lib/libmanagement_agent.so",
        "/lib/libtinyiconv.so",
        nullptr
    };

    for (int i = 0; runtimeLibs[i]; i++) {
        bool loaded = false;
        if (archSubdir) {
            snprintf(path, sizeof(path), "%s/lib/%s/%s", jreHome, archSubdir, runtimeLibs[i] + 4);
            if (dlopen_verbose(path)) loaded = true;
        }
        if (!loaded) {
            snprintf(path, sizeof(path), "%s%s", jreHome, runtimeLibs[i]);
            dlopen_verbose(path);
        }
    }
}

static void notifyExit(jint exitCode) {
    if (g_androidVM && g_callbackObj && g_onExitMethod) {
        JNIEnv *env = nullptr;
        bool attached = false;
        jint attachResult = g_androidVM->GetEnv((void **)&env, JNI_VERSION_1_6);
        if (attachResult == JNI_EDETACHED) {
            if (g_androidVM->AttachCurrentThread(&env, nullptr) == JNI_OK) {
                attached = true;
            }
        }
        if (env) {
            env->CallVoidMethod(g_callbackObj, g_onExitMethod, exitCode);
        }
        if (attached) {
            g_androidVM->DetachCurrentThread();
        }
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

static void setupLdLibraryPath(const char *jrePath, int argc, char **argv) {
    char ldPath[4096];
    const char *archSubdir = detectArchSubdir(jrePath);

    char nativesPath[1024] = {0};
    for (int i = 0; i < argc; i++) {
        if (argv[i] && strncmp(argv[i], "-Djava.library.path=", 20) == 0) {
            const char *natives = argv[i] + 20;
            strncpy(nativesPath, natives, sizeof(nativesPath) - 1);
            LOGI("Found java.library.path from args: %s", nativesPath);
            break;
        }
    }

    int written = snprintf(ldPath, sizeof(ldPath), "%s/lib", jrePath);
    written += snprintf(ldPath + written, sizeof(ldPath) - written, ":%s/lib/jli", jrePath);
    written += snprintf(ldPath + written, sizeof(ldPath) - written, ":%s/lib/server", jrePath);
    if (archSubdir) {
        written += snprintf(ldPath + written, sizeof(ldPath) - written, ":%s/lib/%s", jrePath, archSubdir);
        written += snprintf(ldPath + written, sizeof(ldPath) - written, ":%s/lib/%s/jli", jrePath, archSubdir);
        written += snprintf(ldPath + written, sizeof(ldPath) - written, ":%s/lib/%s/server", jrePath, archSubdir);
    }

    if (nativesPath[0] != '\0') {
        written += snprintf(ldPath + written, sizeof(ldPath) - written, ":%s", nativesPath);
    }

#if defined(__aarch64__) || defined(__x86_64__)
    written += snprintf(ldPath + written, sizeof(ldPath) - written, ":/system/lib64:/vendor/lib64:/vendor/lib64/hw:/system_ext/lib64");
#else
    written += snprintf(ldPath + written, sizeof(ldPath) - written, ":/system/lib:/vendor/lib:/vendor/lib/hw:/system_ext/lib");
#endif

    const char *existingLdPath = getenv("LD_LIBRARY_PATH");
    if (existingLdPath && strlen(existingLdPath) > 0) {
        snprintf(ldPath + written, sizeof(ldPath) - written, ":%s", existingLdPath);
    }

    void *libdl_handle = dlopen("libdl.so", RTLD_LAZY);
    if (libdl_handle) {
        typedef void (*update_ld_path_t)(const char*);
        update_ld_path_t android_update = (update_ld_path_t)dlsym(libdl_handle, "android_update_LD_LIBRARY_PATH");
        if (!android_update) {
            android_update = (update_ld_path_t)dlsym(libdl_handle, "__loader_android_update_LD_LIBRARY_PATH");
        }
        if (android_update) {
            android_update(ldPath);
            LOGI("Updated LD_LIBRARY_PATH via android_update_LD_LIBRARY_PATH");
        } else {
            setenv("LD_LIBRARY_PATH", ldPath, 1);
            LOGI("Fallback setenv LD_LIBRARY_PATH");
        }
        dlclose(libdl_handle);
    } else {
        setenv("LD_LIBRARY_PATH", ldPath, 1);
    }
}

static int launchViaJLI(const char *jrePath, int argc, char **argv) {
    char libjliPath[512];
    const char *jliPaths[] = {
        "/lib/jli/libjli.so",
        "/lib/libjli.so",
        "/lib/aarch64/jli/libjli.so",
        "/lib/amd64/jli/libjli.so",
        "/lib/arm/jli/libjli.so",
        "/lib/i386/jli/libjli.so",
        nullptr
    };

    void *libjli = nullptr;
    for (int i = 0; jliPaths[i]; i++) {
        snprintf(libjliPath, sizeof(libjliPath), "%s%s", jrePath, jliPaths[i]);
        libjli = dlopen(libjliPath, RTLD_GLOBAL | RTLD_LAZY);
        if (libjli) {
            LOGI("Loaded libjli.so from: %s", libjliPath);
            break;
        }
    }

    if (!libjli) {
        LOGW("libjli.so not found, cannot use JLI_Launch");
        return -10;
    }

    auto jliLaunch = (JLI_Launch_t)dlsym(libjli, "JLI_Launch");
    if (!jliLaunch) {
        LOGW("JLI_Launch symbol not found in libjli.so: %s", dlerror());
        dlclose(libjli);
        return -11;
    }

    LOGI("=== Launching via JLI_Launch ===");
    LOGI("argc=%d", argc);
    for (int i = 0; i < argc && i < 15; i++) {
        LOGI("  argv[%d] = %s", i, argv[i]);
    }
    if (argc > 15) LOGI("  ... (%d more args)", argc - 15);

    int result = jliLaunch(
        argc, argv,
        0, nullptr,
        0, nullptr,
        nullptr, nullptr, nullptr, nullptr,
        JNI_TRUE, JNI_TRUE, JNI_FALSE, 0
    );

    LOGI("JLI_Launch returned: %d", result);
    return result;
}

static int launchViaJNI(const char *jrePath, int argc, char **argv) {
    char libjvmPath[512];
    const char *jvmPaths[] = {
        "/lib/server/libjvm.so",
        "/lib/aarch64/server/libjvm.so",
        "/lib/amd64/server/libjvm.so",
        "/lib/arm/server/libjvm.so"
    };

    void *libjvm = nullptr;
    for (int i = 0; i < 4; i++) {
        snprintf(libjvmPath, sizeof(libjvmPath), "%s%s", jrePath, jvmPaths[i]);
        libjvm = dlopen(libjvmPath, RTLD_GLOBAL | RTLD_LAZY);
        if (libjvm) {
            LOGI("Loaded libjvm.so from: %s", libjvmPath);
            break;
        }
    }

    if (!libjvm) {
        LOGE("FATAL: Failed to load libjvm.so");
        return -5;
    }

    typedef jint (*JNI_CreateJavaVM_t)(JavaVM **, void **, void *);
    auto JNI_CreateJavaVM_ptr = (JNI_CreateJavaVM_t)dlsym(libjvm, "JNI_CreateJavaVM");
    if (!JNI_CreateJavaVM_ptr) {
        LOGE("FATAL: Failed to find JNI_CreateJavaVM: %s", dlerror());
        return -6;
    }

    LOGI("=== Launching via JNI_CreateJavaVM (fallback) ===");

    std::vector<JavaVMOption> jvmOptions;
    const char *classpath = nullptr;
    const char *mainClass = nullptr;
    int gameArgStart = -1;
    bool expectCp = false;

    for (int i = 0; i < argc; i++) {
        if (expectCp) {
            classpath = argv[i];
            expectCp = false;
            continue;
        }
        if (gameArgStart >= 0) continue;
        if (strcmp(argv[i], "-cp") == 0 || strcmp(argv[i], "-classpath") == 0) {
            expectCp = true;
            continue;
        }
        if (argv[i][0] == '-') {
            JavaVMOption opt;
            opt.optionString = argv[i];
            opt.extraInfo = nullptr;
            jvmOptions.push_back(opt);
        } else {
            mainClass = argv[i];
            gameArgStart = i + 1;
            LOGI("Main class: %s (game args start at %d)", mainClass, gameArgStart);
        }
    }

    char cpBuf[65536];
    if (classpath && strlen(classpath) > 0) {
        snprintf(cpBuf, sizeof(cpBuf), "-Djava.class.path=%s", classpath);
        JavaVMOption opt;
        opt.optionString = cpBuf;
        opt.extraInfo = nullptr;
        jvmOptions.push_back(opt);
    }

    bool hasJavaHome = false;
    for (const auto &opt : jvmOptions) {
        if (strncmp(opt.optionString, "-Djava.home=", 12) == 0) {
            hasJavaHome = true;
            break;
        }
    }
    std::vector<char*> strdupPtrs;

    if (!hasJavaHome) {
        char homeBuf[512];
        snprintf(homeBuf, sizeof(homeBuf), "-Djava.home=%s", jrePath);
        char *dup = strdup(homeBuf);
        strdupPtrs.push_back(dup);
        JavaVMOption homeOpt;
        homeOpt.optionString = dup;
        homeOpt.extraInfo = nullptr;
        jvmOptions.push_back(homeOpt);
    }

    char modulesPath[512];
    snprintf(modulesPath, sizeof(modulesPath), "%s/lib/modules", jrePath);
    if (access(modulesPath, R_OK) == 0) {
        char bcpBuf[65536];
        snprintf(bcpBuf, sizeof(bcpBuf), "-Xbootclasspath/a:%s", modulesPath);
        char *dup = strdup(bcpBuf);
        strdupPtrs.push_back(dup);
        JavaVMOption bcpOpt;
        bcpOpt.optionString = dup;
        bcpOpt.extraInfo = nullptr;
        jvmOptions.push_back(bcpOpt);
    } else {
        char rtPath[512];
        snprintf(rtPath, sizeof(rtPath), "%s/lib/rt.jar", jrePath);
        if (access(rtPath, R_OK) == 0) {
            char bcpBuf[65536];
            snprintf(bcpBuf, sizeof(bcpBuf), "-Xbootclasspath/a:%s", rtPath);
            char *dup = strdup(bcpBuf);
            strdupPtrs.push_back(dup);
            JavaVMOption bcpOpt;
            bcpOpt.optionString = dup;
            bcpOpt.extraInfo = nullptr;
            jvmOptions.push_back(bcpOpt);
        }
    }

    JavaVMInitArgs vmArgs;
    vmArgs.version = JNI_VERSION_1_6;
    vmArgs.nOptions = (jint)jvmOptions.size();
    vmArgs.options = jvmOptions.data();
    vmArgs.ignoreUnrecognized = JNI_TRUE;

    // Disable Android tagged addressing before JVM creation
    // Standard JRE doesn't handle MTE/TBI tagged pointers
    int prctlResult = prctl(PR_SET_TAGGED_ADDR_CTRL, 0, 0, 0, 0);
    LOGI("prctl(PR_SET_TAGGED_ADDR_CTRL, 0) = %d (errno=%d)", prctlResult, errno);

    JavaVM *jvm = nullptr;
    JNIEnv *jniEnv = nullptr;

    jint createResult = JNI_CreateJavaVM_ptr(&jvm, (void **)&jniEnv, &vmArgs);
    for (auto ptr : strdupPtrs) free(ptr);
    if (createResult != JNI_OK) {
        LOGE("JNI_CreateJavaVM failed with code: %d", createResult);
        return createResult;
    }

    LOGI("JVM created successfully via JNI_CreateJavaVM");

    jclass systemCls = jniEnv->FindClass("java/lang/System");
    if (systemCls) {
        jmethodID getProperty = jniEnv->GetStaticMethodID(systemCls, "getProperty", "(Ljava/lang/String;)Ljava/lang/String;");
        if (getProperty) {
            const char *props[] = {"jna.tmpdir", "java.io.tmpdir", "jna.nounpack", "jna.boot.library.path", "java.library.path", "user.home", nullptr};
            for (int i = 0; props[i]; i++) {
                jstring key = jniEnv->NewStringUTF(props[i]);
                auto val = (jstring)jniEnv->CallStaticObjectMethod(systemCls, getProperty, key);
                if (val) {
                    const char *valStr = jniEnv->GetStringUTFChars(val, nullptr);
                    LOGI("  System.getProperty(%s) = %s", props[i], valStr);
                    jniEnv->ReleaseStringUTFChars(val, valStr);
                    jniEnv->DeleteLocalRef(val);
                } else {
                    jniEnv->ExceptionClear();
                }
                jniEnv->DeleteLocalRef(key);
            }
        }
    }

    if (mainClass && jniEnv && gameArgStart >= 0 && gameArgStart < argc) {
        char clsName[512];
        snprintf(clsName, sizeof(clsName), "%s", mainClass);
        for (char *p = clsName; *p; p++) {
            if (*p == '.') *p = '/';
        }

        jclass mainCls = jniEnv->FindClass(clsName);
        if (jniEnv->ExceptionCheck()) {
            jniEnv->ExceptionDescribe();
            jniEnv->ExceptionClear();
            LOGE("Failed to find main class: %s", clsName);
        } else if (mainCls) {
            jmethodID mainMethod = jniEnv->GetStaticMethodID(mainCls, "main", "([Ljava/lang/String;)V");
            if (!mainMethod || jniEnv->ExceptionCheck()) {
                jniEnv->ExceptionDescribe();
                jniEnv->ExceptionClear();
                LOGE("Failed to find main() method");
            } else {
                int gameArgCount = argc - gameArgStart;
                LOGI("Invoking main() with %d game args", gameArgCount);

                jclass stringCls = jniEnv->FindClass("java/lang/String");
                jobjectArray argsObj = jniEnv->NewObjectArray(gameArgCount, stringCls, nullptr);
                for (int i = 0; i < gameArgCount; i++) {
                    jstring arg = jniEnv->NewStringUTF(argv[gameArgStart + i]);
                    jniEnv->SetObjectArrayElement(argsObj, i, arg);
                }

                jniEnv->CallStaticVoidMethod(mainCls, mainMethod, argsObj);

                if (jniEnv->ExceptionCheck()) {
                    jniEnv->ExceptionDescribe();
                    jniEnv->ExceptionClear();
                }

                LOGI("Main method returned");
            }
        }
    }

    jvm->DestroyJavaVM();
    LOGI("JVM destroyed");
    return 0;
}

static void *jvmThreadFunc(void *arg) {
    JVMLaunchArgs *launchArgs = (JVMLaunchArgs *)arg;

    LOGI("=== JVM Thread Started (pojavexec V3.0) ===");
    LOGI("JRE: %s", launchArgs->jrePath);
    LOGI("Argc: %d", launchArgs->argc);
    for (int i = 0; i < launchArgs->argc && i < 10; i++) {
        LOGI("  argv[%d] = %s", i, launchArgs->argv[i]);
    }
    if (launchArgs->argc > 10) {
        LOGI("  ... (%d more args)", launchArgs->argc - 10);
    }

    {
        char cfgPath[512];
        snprintf(cfgPath, sizeof(cfgPath), "%s/lib/jvm.cfg", launchArgs->jrePath);
        if (access(cfgPath, F_OK) != 0) {
            LOGW("jvm.cfg not found at %s, creating it...", cfgPath);
            FILE *f = fopen(cfgPath, "w");
            if (f) {
                fprintf(f, "-server KNOWN\n-client IGNORE\n");
                fclose(f);
                LOGI("Created jvm.cfg");
            } else {
                LOGE("Failed to create jvm.cfg: %s", strerror(errno));
            }
        }
    }

    {
        char jreLibPath[512];
        snprintf(jreLibPath, sizeof(jreLibPath), "%s/lib", launchArgs->jrePath);
        struct stat st;
        if (stat(jreLibPath, &st) != 0 || !S_ISDIR(st.st_mode)) {
            LOGE("FATAL: JRE lib directory does not exist: %s", jreLibPath);
            LOGE("Listing JRE root directory:");
            listDir(launchArgs->jrePath, 0);
            g_jvmRunning = false;
            g_exitCode = -1;
            notifyExit(-1);
            cleanupLaunchArgs(launchArgs);
            return nullptr;
        }
        LOGI("JRE lib directory exists: %s", jreLibPath);
    }

    setupLdLibraryPath(launchArgs->jrePath, launchArgs->argc, launchArgs->argv);

    setenv("JAVA_HOME", launchArgs->jrePath, 1);
    LOGI("Set JAVA_HOME=%s", launchArgs->jrePath);

    const char *homeEnv = getenv("HOME");
    if (homeEnv && strlen(homeEnv) > 0) {
        chdir(homeEnv);
        LOGI("chdir to HOME=%s", homeEnv);
    }

    preloadJreLibs(launchArgs->jrePath);

    int result = launchViaJLI(launchArgs->jrePath, launchArgs->argc, launchArgs->argv);
    if (result == -10 || result == -11) {
        LOGW("JLI_Launch unavailable (code %d), falling back to JNI_CreateJavaVM", result);
        result = launchViaJNI(launchArgs->jrePath, launchArgs->argc, launchArgs->argv);
    }

    g_exitCode = result;
    g_jvmRunning = false;

    if (g_jvmDestroyRequested) {
        LOGI("JVM shutdown was requested by destroyJVM()");
        g_exitCode = 0;
    }

    notifyExit(g_exitCode);
    cleanupLaunchArgs(launchArgs);

    return nullptr;
}

extern "C" {

JNIEXPORT jint JNICALL
Java_com_verse_explorer_launcher_launch_JVMLoader_nativeLaunchJVM(
    JNIEnv *env, jobject thiz,
    jstring jrePath, jobjectArray argsArray) {

    LOGI("=== Native JVM Launch Request (V3.0) ===");

    if (g_jvmRunning) {
        LOGE("JVM is already running!");
        return -1;
    }

    if (!jrePath || !argsArray) {
        LOGE("Missing required parameters");
        return -1;
    }

    JVMLaunchArgs *launchArgs = (JVMLaunchArgs *)calloc(1, sizeof(JVMLaunchArgs));

    const char *jrePathStr = env->GetStringUTFChars(jrePath, nullptr);
    launchArgs->jrePath = strdup(jrePathStr);
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

    LOGI("JRE: %s", launchArgs->jrePath);
    LOGI("Total args: %d", launchArgs->argc);

    {
        struct stat st;
        if (stat(launchArgs->jrePath, &st) != 0) {
            LOGE("JRE path does not exist: %s", launchArgs->jrePath);
            cleanupLaunchArgs(launchArgs);
            return -1;
        }
    }

    g_jvmRunning = true;
    g_jvmDestroyRequested = false;
    g_exitCode = 0;

    env->GetJavaVM(&g_androidVM);

    int ret = pthread_create(&g_jvmThread, nullptr, jvmThreadFunc, launchArgs);
    if (ret != 0) {
        LOGE("Failed to create JVM thread: %s", strerror(ret));
        g_jvmRunning = false;
        cleanupLaunchArgs(launchArgs);
        return -1;
    }

    pthread_detach(g_jvmThread);

    LOGI("JVM thread created successfully");
    return 0;
}

JNIEXPORT void JNICALL
Java_com_verse_explorer_launcher_launch_JVMLoader_nativeDestroyJVM(
    JNIEnv *env, jobject thiz) {
    if (g_jvmRunning) {
        LOGI("Requesting JVM shutdown...");
        g_jvmDestroyRequested = true;
    }
}

JNIEXPORT jboolean JNICALL
Java_com_verse_explorer_launcher_launch_JVMLoader_nativeIsJVMRunning(
    JNIEnv *env, jobject thiz) {
    return g_jvmRunning ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT void JNICALL
Java_com_verse_explorer_launcher_launch_JVMLoader_nativeSetExitCallback(
    JNIEnv *env, jobject thiz, jobject callback) {
    if (g_callbackObj) {
        env->DeleteGlobalRef(g_callbackObj);
    }
    g_callbackObj = env->NewGlobalRef(callback);

    if (!g_androidVM) {
        env->GetJavaVM(&g_androidVM);
    }

    jclass callbackClass = env->GetObjectClass(callback);
    g_onExitMethod = env->GetMethodID(callbackClass, "onExit", "(I)V");

    LOGI("Exit callback set");
}

JNIEXPORT jint JNICALL
Java_com_verse_explorer_launcher_launch_JVMLoader_nativeGetExitCode(
    JNIEnv *env, jobject thiz) {
    return g_exitCode;
}

JNIEXPORT jint JNICALL
Java_com_verse_explorer_launcher_launch_JVMLoader_nativeGetPid(
    JNIEnv *env, jobject thiz) {
    return (jint)getpid();
}

JNIEXPORT jint JNICALL
Java_com_verse_explorer_launcher_launch_JVMLoader_nativeGetJniErrorCode(
    JNIEnv *env, jobject thiz) {
    return g_exitCode;
}

}
