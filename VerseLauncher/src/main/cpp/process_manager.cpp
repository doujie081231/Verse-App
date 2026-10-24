#include <jni.h>
#include <android/log.h>
#include <unistd.h>
#include <signal.h>
#include <sys/types.h>
#include <sys/wait.h>
#include <cstdio>
#include <cerrno>
#include <cstring>
#include <ctime>

#define LOG_TAG "ProcessManager"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN, LOG_TAG, __VA_ARGS__)

extern "C" {

JNIEXPORT jint JNICALL
Java_com_verse_explorer_launcher_ProcessManager_nativeGetPid(
    JNIEnv *env, jobject thiz) {
    return (jint)getpid();
}

JNIEXPORT jboolean JNICALL
Java_com_verse_explorer_launcher_ProcessManager_nativeKillProcess(
    JNIEnv *env, jobject thiz, jint pid) {
    if (pid <= 0) return JNI_FALSE;
    if (kill((pid_t)pid, SIGTERM) == 0) {
        LOGI("Sent SIGTERM to process %d", pid);
        return JNI_TRUE;
    }
    if (kill((pid_t)pid, SIGKILL) == 0) {
        LOGI("Sent SIGKILL to process %d", pid);
        return JNI_TRUE;
    }
    LOGW("Failed to kill process %d: %s", pid, strerror(errno));
    return JNI_FALSE;
}

JNIEXPORT jlong JNICALL
Java_com_verse_explorer_launcher_ProcessManager_nativeGetProcessMemory(
    JNIEnv *env, jobject thiz) {
    FILE *fp = fopen("/proc/self/status", "r");
    if (!fp) return 0;

    long vmRss = 0;
    char line[256];
    while (fgets(line, sizeof(line), fp)) {
        if (strncmp(line, "VmRSS:", 6) == 0) {
            sscanf(line + 6, "%ld", &vmRss);
            break;
        }
    }
    fclose(fp);
    return (jlong)(vmRss * 1024);
}

JNIEXPORT jstring JNICALL
Java_com_verse_explorer_launcher_ProcessManager_nativeGetProcessStatus(
    JNIEnv *env, jobject thiz) {
    FILE *fp = fopen("/proc/self/status", "r");
    if (!fp) return env->NewStringUTF("unknown");

    char line[256];
    while (fgets(line, sizeof(line), fp)) {
        if (strncmp(line, "State:", 6) == 0) {
            fclose(fp);
            char *start = line + 6;
            while (*start == ' ' || *start == '\t') start++;
            char *end = start + strlen(start) - 1;
            while (end > start && (*end == '\n' || *end == '\r' || *end == ' ')) *end-- = '\0';
            return env->NewStringUTF(start);
        }
    }
    fclose(fp);
    return env->NewStringUTF("unknown");
}

}
