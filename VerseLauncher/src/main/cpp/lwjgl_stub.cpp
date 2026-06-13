#include <jni.h>
#include <android/log.h>
#include <cstdlib>
#include <cstring>
#include <dlfcn.h>

#define LOG_TAG "LWJGLStub"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

extern "C" {

// ==================== MemoryUtil JNI ====================

JNIEXPORT jlong JNICALL
Java_org_lwjgl_system_MemoryUtil_nmemAlloc(JNIEnv* env, jclass cls, jlong size) {
    return (jlong)(intptr_t)malloc((size_t)size);
}

JNIEXPORT jlong JNICALL
Java_org_lwjgl_system_MemoryUtil_nmemCalloc(JNIEnv* env, jclass cls, jlong num, jlong size) {
    return (jlong)(intptr_t)calloc((size_t)num, (size_t)size);
}

JNIEXPORT void JNICALL
Java_org_lwjgl_system_MemoryUtil_nmemFree(JNIEnv* env, jclass cls, jlong ptr) {
    free((void*)(intptr_t)ptr);
}

JNIEXPORT jlong JNICALL
Java_org_lwjgl_system_MemoryUtil_nmemRealloc(JNIEnv* env, jclass cls, jlong ptr, jlong size) {
    return (jlong)(intptr_t)realloc((void*)(intptr_t)ptr, (size_t)size);
}

JNIEXPORT jlong JNICALL
Java_org_lwjgl_system_MemoryUtil_nmemAlignedAlloc(JNIEnv* env, jclass cls, jlong alignment, jlong size) {
    void* ptr = nullptr;
    posix_memalign(&ptr, (size_t)alignment, (size_t)size);
    return (jlong)(intptr_t)ptr;
}

JNIEXPORT void JNICALL
Java_org_lwjgl_system_MemoryUtil_nmemAlignedFree(JNIEnv* env, jclass cls, jlong ptr) {
    free((void*)(intptr_t)ptr);
}

JNIEXPORT void JNICALL
Java_org_lwjgl_system_MemoryUtil_nmemSet(JNIEnv* env, jclass cls, jlong addr, jint value, jlong bytes) {
    memset((void*)(intptr_t)addr, value, (size_t)bytes);
}

JNIEXPORT void JNICALL
Java_org_lwjgl_system_MemoryUtil_nmemCopy(JNIEnv* env, jclass cls, jlong src, jlong dst, jlong bytes) {
    memmove((void*)(intptr_t)dst, (const void*)(intptr_t)src, (size_t)bytes);
}

JNIEXPORT jlong JNICALL
Java_org_lwjgl_system_MemoryUtil_nmemAllocChecked(JNIEnv* env, jclass cls, jlong size) {
    if (size == 0) return 0;
    jlong ptr = (jlong)(intptr_t)malloc((size_t)size);
    if (!ptr) {
        jclass oom = env->FindClass("java/lang/OutOfMemoryError");
        if (oom) env->ThrowNew(oom, "Failed to allocate native memory");
    }
    return ptr;
}

JNIEXPORT jlong JNICALL
Java_org_lwjgl_system_MemoryUtil_nmemCallocChecked(JNIEnv* env, jclass cls, jlong num, jlong size) {
    if (num == 0 || size == 0) return 0;
    jlong ptr = (jlong)(intptr_t)calloc((size_t)num, (size_t)size);
    if (!ptr) {
        jclass oom = env->FindClass("java/lang/OutOfMemoryError");
        if (oom) env->ThrowNew(oom, "Failed to allocate native memory");
    }
    return ptr;
}

// ==================== MemoryAccess JNI ====================
JNIEXPORT jlong JNICALL
Java_org_lwjgl_system_MemoryAccess_nmemGetAddress(JNIEnv* env, jclass cls, jlong ptr) {
    return *(jlong*)(intptr_t)ptr;
}

// ==================== MemoryText JNI ====================
JNIEXPORT jstring JNICALL
Java_org_lwjgl_system_MemoryText_nmemUTF8(JNIEnv* env, jclass cls, jlong address, jint length) {
    if (address == 0 || length <= 0) return nullptr;
    const char* str = (const char*)(intptr_t)address;
    return env->NewStringUTF(str);
}

JNIEXPORT jint JNICALL
Java_org_lwjgl_system_MemoryText_nmemASCII(JNIEnv* env, jclass cls, jlong address, jint length, jlong target) {
    if (address == 0 || target == 0) return 0;
    const char* src = (const char*)(intptr_t)address;
    char* dst = (char*)(intptr_t)target;
    jint i = 0;
    for (; i < length && src[i] != 0; i++) {
        dst[i] = (src[i] & 0x80) ? '?' : src[i];
    }
    return i;
}

// ==================== PointerBuffer JNI ====================
JNIEXPORT void JNICALL
Java_org_lwjgl_system_PointerBuffer_nput(JNIEnv* env, jclass cls, jlong ptr, jlong value) {
    *(jlong*)(intptr_t)ptr = value;
}

// ==================== StructBuffer JNI ====================
JNIEXPORT void JNICALL
Java_org_lwjgl_system_StructBuffer_nwrap(JNIEnv* env, jclass cls, jlong address, jlong container, jint flags) {
    (void)address; (void)container; (void)flags;
}

// ==================== CustomBuffer JNI ====================
JNIEXPORT jlong JNICALL
Java_org_lwjgl_system_CustomBuffer_ncreate(JNIEnv* env, jclass cls, jlong size, jint flags) {
    void* ptr = malloc((size_t)size);
    if (!ptr) {
        jclass oom = env->FindClass("java/lang/OutOfMemoryError");
        if (oom) env->ThrowNew(oom, "Failed to create custom buffer");
    }
    return (jlong)(intptr_t)ptr;
}

JNIEXPORT void JNICALL
Java_org_lwjgl_system_CustomBuffer_ndelete(JNIEnv* env, jclass cls, jlong ptr) {
    free((void*)(intptr_t)ptr);
}

// ==================== MultiReleaseTextDecoding JNI ====================
JNIEXPORT jlong JNICALL
Java_org_lwjgl_system_MultiReleaseTextDecoding_ndecodeUTF8(JNIEnv* env, jclass cls, jlong src, jint length) {
    return src;
}

// ==================== Library JNI - REAL dlopen ====================

JNIEXPORT jlong JNICALL
Java_org_lwjgl_system_Library_nloadLibrary(JNIEnv* env, jclass cls, jstring name) {
    const char* cname = env->GetStringUTFChars(name, nullptr);
    LOGI("Library.nloadLibrary: %s", cname);

    char libName[256];
    if (strncmp(cname, "lib", 3) == 0) {
        snprintf(libName, sizeof(libName), "%s.so", cname);
    } else {
        snprintf(libName, sizeof(libName), "lib%s.so", cname);
    }

    dlerror();
    void* handle = dlopen(libName, RTLD_LAZY | RTLD_GLOBAL);
    if (!handle) {
        char* err = dlerror();
        LOGE("nloadLibrary: dlopen(%s) failed: %s", libName, err ? err : "unknown");
    } else {
        LOGI("nloadLibrary: dlopen(%s) success, handle=%p", libName, handle);
    }

    env->ReleaseStringUTFChars(name, cname);
    return (jlong)(intptr_t)handle;
}

JNIEXPORT void JNICALL
Java_org_lwjgl_system_Library_nfreeLibrary(JNIEnv* env, jclass cls, jlong handle) {
    if (handle != 0) {
        dlclose((void*)(intptr_t)handle);
    }
}

JNIEXPORT jlong JNICALL
Java_org_lwjgl_system_Library_ngetFunctionAddress(JNIEnv* env, jclass cls, jlong handle, jstring name) {
    const char* cname = env->GetStringUTFChars(name, nullptr);

    void* sym = nullptr;
    if (handle != 0) {
        sym = dlsym((void*)(intptr_t)handle, cname);
    }
    if (!sym) {
        sym = dlsym(RTLD_DEFAULT, cname);
    }

    if (!sym) {
        LOGI("ngetFunctionAddress: %s not found", cname);
    }

    env->ReleaseStringUTFChars(name, cname);
    return (jlong)(intptr_t)sym;
}

JNIEXPORT jstring JNICALL
Java_org_lwjgl_system_Library_nloadLibraryPath(JNIEnv* env, jclass cls, jlong handle) {
    (void)handle;
    return nullptr;
}

// ==================== DynamicLinkLoader JNI ====================
// FCL-style: hook for LWJGL's low-level dlopen wrapper

JNIEXPORT jlong JNICALL
Java_org_lwjgl_system_linux_DynamicLinkLoader_ndlopen(JNIEnv* env, jclass cls, jlong filename, jint mode) {
    const char* fname = (const char*)(intptr_t)filename;
    if (!fname) return 0;

    LOGI("DynamicLinkLoader.ndlopen: %s mode=%d", fname, mode);

    if (strstr(fname, "libvulkan.so")) {
        LOGI("ndlopen: vulkan load requested, using default dlopen");
    }

    dlerror();
    void* handle = dlopen(fname, (int)mode);
    if (!handle) {
        char* err = dlerror();
        LOGE("ndlopen: dlopen(%s) failed: %s", fname, err ? err : "unknown");
    } else {
        LOGI("ndlopen: dlopen(%s) success, handle=%p", fname, handle);
    }

    return (jlong)(intptr_t)handle;
}

// ==================== JNI FFI Invoke stubs (for MioLibPatcher) ====================
// MioLibPatcher transforms LWJGL classes to use JNI.invoke*() with ORIGINAL
// Java parameter types (int/float/double/long), not just longs.
// V3.5: Added all int (I), float (F), double (D) overloads for invokeV/I/J/P/D.
// On arm64, JNI correctly places float/double in FP registers (s0-s7/d0-d7)
// and int/long in integer registers (x0-x7). Using correct C parameter types
// ensures proper register assignment when forwarding to GL function pointers.
//
// Macro helpers for code generation
#define INV_N(FN, RET, SUFFIX, PARAMS, CTYPES, CALL) \
JNIEXPORT RET JNICALL \
Java_org_lwjgl_system_JNI_##FN##SUFFIX(JNIEnv* e, jclass c, jlong a PARAMS) { \
    (void)e;(void)c;if(a)RETURN_##RET((CTYPES)((intptr_t)a))CALL;RETURN_DEF_##RET; \
}
#define RETURN_V
#define RETURN_I return
#define RETURN_J return
#define RETURN_P return
#define RETURN_D return
#define RETURN_DEF_V ;return
#define RETURN_DEF_I 0;return
#define RETURN_DEF_J 0;return
#define RETURN_DEF_P 0;return
#define RETURN_DEF_D 0.0;return
#define RETURN0_V return
#define RETURN0_I return 0
#define RETURN0_J return 0
#define RETURN0_P return 0
#define RETURN0_D return 0.0

// ===== invokeV (void return) =====
// 0 params
JNIEXPORT void JNICALL Java_org_lwjgl_system_JNI_invokeV__J(JNIEnv* e, jclass c, jlong a) {(void)e;(void)c;if(a)((void(*)())((intptr_t)a))();}
// 1 param
JNIEXPORT void JNICALL Java_org_lwjgl_system_JNI_invokeV__JI(JNIEnv* e, jclass c, jlong a, jint p1) {(void)e;(void)c;if(a)((void(*)(int))((intptr_t)a))(p1);}
JNIEXPORT void JNICALL Java_org_lwjgl_system_JNI_invokeV__JJ(JNIEnv* e, jclass c, jlong a, jlong p1) {(void)e;(void)c;if(a)((void(*)(long))((intptr_t)a))(p1);}
JNIEXPORT void JNICALL Java_org_lwjgl_system_JNI_invokeV__JF(JNIEnv* e, jclass c, jlong a, jfloat p1) {(void)e;(void)c;if(a)((void(*)(float))((intptr_t)a))(p1);}
JNIEXPORT void JNICALL Java_org_lwjgl_system_JNI_invokeV__JD(JNIEnv* e, jclass c, jlong a, jdouble p1) {(void)e;(void)c;if(a)((void(*)(double))((intptr_t)a))(p1);}
// 2 params
JNIEXPORT void JNICALL Java_org_lwjgl_system_JNI_invokeV__JII(JNIEnv* e, jclass c, jlong a, jint p1, jint p2) {(void)e;(void)c;if(a)((void(*)(int,int))((intptr_t)a))(p1,p2);}
JNIEXPORT void JNICALL Java_org_lwjgl_system_JNI_invokeV__JJJ(JNIEnv* e, jclass c, jlong a, jlong p1, jlong p2) {(void)e;(void)c;if(a)((void(*)(long,long))((intptr_t)a))(p1,p2);}
JNIEXPORT void JNICALL Java_org_lwjgl_system_JNI_invokeV__JFF(JNIEnv* e, jclass c, jlong a, jfloat p1, jfloat p2) {(void)e;(void)c;if(a)((void(*)(float,float))((intptr_t)a))(p1,p2);}
JNIEXPORT void JNICALL Java_org_lwjgl_system_JNI_invokeV__JDD(JNIEnv* e, jclass c, jlong a, jdouble p1, jdouble p2) {(void)e;(void)c;if(a)((void(*)(double,double))((intptr_t)a))(p1,p2);}
JNIEXPORT void JNICALL Java_org_lwjgl_system_JNI_invokeV__JIF(JNIEnv* e, jclass c, jlong a, jint p1, jfloat p2) {(void)e;(void)c;if(a)((void(*)(int,float))((intptr_t)a))(p1,p2);}
JNIEXPORT void JNICALL Java_org_lwjgl_system_JNI_invokeV__JFI(JNIEnv* e, jclass c, jlong a, jfloat p1, jint p2) {(void)e;(void)c;if(a)((void(*)(float,int))((intptr_t)a))(p1,p2);}
JNIEXPORT void JNICALL Java_org_lwjgl_system_JNI_invokeV__JJI(JNIEnv* e, jclass c, jlong a, jlong p1, jint p2) {(void)e;(void)c;if(a)((void(*)(long,int))((intptr_t)a))(p1,p2);}
JNIEXPORT void JNICALL Java_org_lwjgl_system_JNI_invokeV__JIJ(JNIEnv* e, jclass c, jlong a, jint p1, jlong p2) {(void)e;(void)c;if(a)((void(*)(int,long))((intptr_t)a))(p1,p2);}
JNIEXPORT void JNICALL Java_org_lwjgl_system_JNI_invokeV__JID(JNIEnv* e, jclass c, jlong a, jint p1, jdouble p2) {(void)e;(void)c;if(a)((void(*)(int,double))((intptr_t)a))(p1,p2);}
JNIEXPORT void JNICALL Java_org_lwjgl_system_JNI_invokeV__JFD(JNIEnv* e, jclass c, jlong a, jfloat p1, jdouble p2) {(void)e;(void)c;if(a)((void(*)(float,double))((intptr_t)a))(p1,p2);}
// 3 params
JNIEXPORT void JNICALL Java_org_lwjgl_system_JNI_invokeV__JIII(JNIEnv* e, jclass c, jlong a, jint p1, jint p2, jint p3) {(void)e;(void)c;if(a)((void(*)(int,int,int))((intptr_t)a))(p1,p2,p3);}
JNIEXPORT void JNICALL Java_org_lwjgl_system_JNI_invokeV__JJJJ(JNIEnv* e, jclass c, jlong a, jlong p1, jlong p2, jlong p3) {(void)e;(void)c;if(a)((void(*)(long,long,long))((intptr_t)a))(p1,p2,p3);}
JNIEXPORT void JNICALL Java_org_lwjgl_system_JNI_invokeV__JFFF(JNIEnv* e, jclass c, jlong a, jfloat p1, jfloat p2, jfloat p3) {(void)e;(void)c;if(a)((void(*)(float,float,float))((intptr_t)a))(p1,p2,p3);}
JNIEXPORT void JNICALL Java_org_lwjgl_system_JNI_invokeV__JDDD(JNIEnv* e, jclass c, jlong a, jdouble p1, jdouble p2, jdouble p3) {(void)e;(void)c;if(a)((void(*)(double,double,double))((intptr_t)a))(p1,p2,p3);}
JNIEXPORT void JNICALL Java_org_lwjgl_system_JNI_invokeV__JIIF(JNIEnv* e, jclass c, jlong a, jint p1, jint p2, jfloat p3) {(void)e;(void)c;if(a)((void(*)(int,int,float))((intptr_t)a))(p1,p2,p3);}
JNIEXPORT void JNICALL Java_org_lwjgl_system_JNI_invokeV__JIFF(JNIEnv* e, jclass c, jlong a, jint p1, jfloat p2, jfloat p3) {(void)e;(void)c;if(a)((void(*)(int,float,float))((intptr_t)a))(p1,p2,p3);}
JNIEXPORT void JNICALL Java_org_lwjgl_system_JNI_invokeV__JIFI(JNIEnv* e, jclass c, jlong a, jint p1, jfloat p2, jint p3) {(void)e;(void)c;if(a)((void(*)(int,float,int))((intptr_t)a))(p1,p2,p3);}
JNIEXPORT void JNICALL Java_org_lwjgl_system_JNI_invokeV__JFFI(JNIEnv* e, jclass c, jlong a, jfloat p1, jfloat p2, jint p3) {(void)e;(void)c;if(a)((void(*)(float,float,int))((intptr_t)a))(p1,p2,p3);}
// 4 params
JNIEXPORT void JNICALL Java_org_lwjgl_system_JNI_invokeV__JIIII(JNIEnv* e, jclass c, jlong a, jint p1, jint p2, jint p3, jint p4) {(void)e;(void)c;if(a)((void(*)(int,int,int,int))((intptr_t)a))(p1,p2,p3,p4);}
JNIEXPORT void JNICALL Java_org_lwjgl_system_JNI_invokeV__JJJJJ(JNIEnv* e, jclass c, jlong a, jlong p1, jlong p2, jlong p3, jlong p4) {(void)e;(void)c;if(a)((void(*)(long,long,long,long))((intptr_t)a))(p1,p2,p3,p4);}
JNIEXPORT void JNICALL Java_org_lwjgl_system_JNI_invokeV__JFFFF(JNIEnv* e, jclass c, jlong a, jfloat p1, jfloat p2, jfloat p3, jfloat p4) {(void)e;(void)c;if(a)((void(*)(float,float,float,float))((intptr_t)a))(p1,p2,p3,p4);}
JNIEXPORT void JNICALL Java_org_lwjgl_system_JNI_invokeV__JDDDD(JNIEnv* e, jclass c, jlong a, jdouble p1, jdouble p2, jdouble p3, jdouble p4) {(void)e;(void)c;if(a)((void(*)(double,double,double,double))((intptr_t)a))(p1,p2,p3,p4);}
JNIEXPORT void JNICALL Java_org_lwjgl_system_JNI_invokeV__JIFFF(JNIEnv* e, jclass c, jlong a, jint p1, jfloat p2, jfloat p3, jfloat p4) {(void)e;(void)c;if(a)((void(*)(int,float,float,float))((intptr_t)a))(p1,p2,p3,p4);}
JNIEXPORT void JNICALL Java_org_lwjgl_system_JNI_invokeV__JIIFF(JNIEnv* e, jclass c, jlong a, jint p1, jint p2, jfloat p3, jfloat p4) {(void)e;(void)c;if(a)((void(*)(int,int,float,float))((intptr_t)a))(p1,p2,p3,p4);}
JNIEXPORT void JNICALL Java_org_lwjgl_system_JNI_invokeV__JIIIF(JNIEnv* e, jclass c, jlong a, jint p1, jint p2, jint p3, jfloat p4) {(void)e;(void)c;if(a)((void(*)(int,int,int,float))((intptr_t)a))(p1,p2,p3,p4);}
// 5 params
JNIEXPORT void JNICALL Java_org_lwjgl_system_JNI_invokeV__JIIIII(JNIEnv* e, jclass c, jlong a, jint p1, jint p2, jint p3, jint p4, jint p5) {(void)e;(void)c;if(a)((void(*)(int,int,int,int,int))((intptr_t)a))(p1,p2,p3,p4,p5);}
JNIEXPORT void JNICALL Java_org_lwjgl_system_JNI_invokeV__JJJJJJ(JNIEnv* e, jclass c, jlong a, jlong p1, jlong p2, jlong p3, jlong p4, jlong p5) {(void)e;(void)c;if(a)((void(*)(long,long,long,long,long))((intptr_t)a))(p1,p2,p3,p4,p5);}
JNIEXPORT void JNICALL Java_org_lwjgl_system_JNI_invokeV__JFFFFF(JNIEnv* e, jclass c, jlong a, jfloat p1, jfloat p2, jfloat p3, jfloat p4, jfloat p5) {(void)e;(void)c;if(a)((void(*)(float,float,float,float,float))((intptr_t)a))(p1,p2,p3,p4,p5);}
JNIEXPORT void JNICALL Java_org_lwjgl_system_JNI_invokeV__JIFFFF(JNIEnv* e, jclass c, jlong a, jint p1, jfloat p2, jfloat p3, jfloat p4, jfloat p5) {(void)e;(void)c;if(a)((void(*)(int,float,float,float,float))((intptr_t)a))(p1,p2,p3,p4,p5);}
JNIEXPORT void JNICALL Java_org_lwjgl_system_JNI_invokeV__JIIFFF(JNIEnv* e, jclass c, jlong a, jint p1, jint p2, jfloat p3, jfloat p4, jfloat p5) {(void)e;(void)c;if(a)((void(*)(int,int,float,float,float))((intptr_t)a))(p1,p2,p3,p4,p5);}
// 6 params
JNIEXPORT void JNICALL Java_org_lwjgl_system_JNI_invokeV__JIIIIII(JNIEnv* e, jclass c, jlong a, jint p1, jint p2, jint p3, jint p4, jint p5, jint p6) {(void)e;(void)c;if(a)((void(*)(int,int,int,int,int,int))((intptr_t)a))(p1,p2,p3,p4,p5,p6);}

// ===== invokeI (int return) =====
JNIEXPORT jint JNICALL Java_org_lwjgl_system_JNI_invokeI__J(JNIEnv* e, jclass c, jlong a) {(void)e;(void)c;return a?((int(*)())((intptr_t)a))():0;}
JNIEXPORT jint JNICALL Java_org_lwjgl_system_JNI_invokeI__JI(JNIEnv* e, jclass c, jlong a, jint p1) {(void)e;(void)c;return a?((int(*)(int))((intptr_t)a))(p1):0;}
JNIEXPORT jint JNICALL Java_org_lwjgl_system_JNI_invokeI__JJ(JNIEnv* e, jclass c, jlong a, jlong p1) {(void)e;(void)c;return a?((int(*)(long))((intptr_t)a))(p1):0;}
JNIEXPORT jint JNICALL Java_org_lwjgl_system_JNI_invokeI__JF(JNIEnv* e, jclass c, jlong a, jfloat p1) {(void)e;(void)c;return a?((int(*)(float))((intptr_t)a))(p1):0;}
JNIEXPORT jint JNICALL Java_org_lwjgl_system_JNI_invokeI__JD(JNIEnv* e, jclass c, jlong a, jdouble p1) {(void)e;(void)c;return a?((int(*)(double))((intptr_t)a))(p1):0;}
JNIEXPORT jint JNICALL Java_org_lwjgl_system_JNI_invokeI__JII(JNIEnv* e, jclass c, jlong a, jint p1, jint p2) {(void)e;(void)c;return a?((int(*)(int,int))((intptr_t)a))(p1,p2):0;}
JNIEXPORT jint JNICALL Java_org_lwjgl_system_JNI_invokeI__JJJ(JNIEnv* e, jclass c, jlong a, jlong p1, jlong p2) {(void)e;(void)c;return a?((int(*)(long,long))((intptr_t)a))(p1,p2):0;}
JNIEXPORT jint JNICALL Java_org_lwjgl_system_JNI_invokeI__JIF(JNIEnv* e, jclass c, jlong a, jint p1, jfloat p2) {(void)e;(void)c;return a?((int(*)(int,float))((intptr_t)a))(p1,p2):0;}
JNIEXPORT jint JNICALL Java_org_lwjgl_system_JNI_invokeI__JIJ(JNIEnv* e, jclass c, jlong a, jint p1, jlong p2) {(void)e;(void)c;return a?((int(*)(int,long))((intptr_t)a))(p1,p2):0;}
JNIEXPORT jint JNICALL Java_org_lwjgl_system_JNI_invokeI__JIII(JNIEnv* e, jclass c, jlong a, jint p1, jint p2, jint p3) {(void)e;(void)c;return a?((int(*)(int,int,int))((intptr_t)a))(p1,p2,p3):0;}
JNIEXPORT jint JNICALL Java_org_lwjgl_system_JNI_invokeI__JJJJ(JNIEnv* e, jclass c, jlong a, jlong p1, jlong p2, jlong p3) {(void)e;(void)c;return a?((int(*)(long,long,long))((intptr_t)a))(p1,p2,p3):0;}
JNIEXPORT jint JNICALL Java_org_lwjgl_system_JNI_invokeI__JIIF(JNIEnv* e, jclass c, jlong a, jint p1, jint p2, jfloat p3) {(void)e;(void)c;return a?((int(*)(int,int,float))((intptr_t)a))(p1,p2,p3):0;}
JNIEXPORT jint JNICALL Java_org_lwjgl_system_JNI_invokeI__JIIII(JNIEnv* e, jclass c, jlong a, jint p1, jint p2, jint p3, jint p4) {(void)e;(void)c;return a?((int(*)(int,int,int,int))((intptr_t)a))(p1,p2,p3,p4):0;}
JNIEXPORT jint JNICALL Java_org_lwjgl_system_JNI_invokeI__JJJJJ(JNIEnv* e, jclass c, jlong a, jlong p1, jlong p2, jlong p3, jlong p4) {(void)e;(void)c;return a?((int(*)(long,long,long,long))((intptr_t)a))(p1,p2,p3,p4):0;}

// ===== invokeJ (long return) =====
JNIEXPORT jlong JNICALL Java_org_lwjgl_system_JNI_invokeJ__J(JNIEnv* e, jclass c, jlong a) {(void)e;(void)c;return a?((jlong(*)())((intptr_t)a))():0;}
JNIEXPORT jlong JNICALL Java_org_lwjgl_system_JNI_invokeJ__JI(JNIEnv* e, jclass c, jlong a, jint p1) {(void)e;(void)c;return a?((jlong(*)(int))((intptr_t)a))(p1):0;}
JNIEXPORT jlong JNICALL Java_org_lwjgl_system_JNI_invokeJ__JJ(JNIEnv* e, jclass c, jlong a, jlong p1) {(void)e;(void)c;return a?((jlong(*)(long))((intptr_t)a))(p1):0;}
JNIEXPORT jlong JNICALL Java_org_lwjgl_system_JNI_invokeJ__JF(JNIEnv* e, jclass c, jlong a, jfloat p1) {(void)e;(void)c;return a?((jlong(*)(float))((intptr_t)a))(p1):0;}
JNIEXPORT jlong JNICALL Java_org_lwjgl_system_JNI_invokeJ__JD(JNIEnv* e, jclass c, jlong a, jdouble p1) {(void)e;(void)c;return a?((jlong(*)(double))((intptr_t)a))(p1):0;}
JNIEXPORT jlong JNICALL Java_org_lwjgl_system_JNI_invokeJ__JII(JNIEnv* e, jclass c, jlong a, jint p1, jint p2) {(void)e;(void)c;return a?((jlong(*)(int,int))((intptr_t)a))(p1,p2):0;}
JNIEXPORT jlong JNICALL Java_org_lwjgl_system_JNI_invokeJ__JJJ(JNIEnv* e, jclass c, jlong a, jlong p1, jlong p2) {(void)e;(void)c;return a?((jlong(*)(long,long))((intptr_t)a))(p1,p2):0;}
JNIEXPORT jlong JNICALL Java_org_lwjgl_system_JNI_invokeJ__JIII(JNIEnv* e, jclass c, jlong a, jint p1, jint p2, jint p3) {(void)e;(void)c;return a?((jlong(*)(int,int,int))((intptr_t)a))(p1,p2,p3):0;}
JNIEXPORT jlong JNICALL Java_org_lwjgl_system_JNI_invokeJ__JJJJ(JNIEnv* e, jclass c, jlong a, jlong p1, jlong p2, jlong p3) {(void)e;(void)c;return a?((jlong(*)(long,long,long))((intptr_t)a))(p1,p2,p3):0;}

// ===== invokeP (pointer/long return) =====
JNIEXPORT jlong JNICALL Java_org_lwjgl_system_JNI_invokeP__J(JNIEnv* e, jclass c, jlong a) {(void)e;(void)c;return a?((jlong(*)())((intptr_t)a))():0;}
JNIEXPORT jlong JNICALL Java_org_lwjgl_system_JNI_invokeP__JI(JNIEnv* e, jclass c, jlong a, jint p1) {(void)e;(void)c;return a?((jlong(*)(int))((intptr_t)a))(p1):0;}
JNIEXPORT jlong JNICALL Java_org_lwjgl_system_JNI_invokeP__JJ(JNIEnv* e, jclass c, jlong a, jlong p1) {(void)e;(void)c;return a?((jlong(*)(long))((intptr_t)a))(p1):0;}
JNIEXPORT jlong JNICALL Java_org_lwjgl_system_JNI_invokeP__JF(JNIEnv* e, jclass c, jlong a, jfloat p1) {(void)e;(void)c;return a?((jlong(*)(float))((intptr_t)a))(p1):0;}
JNIEXPORT jlong JNICALL Java_org_lwjgl_system_JNI_invokeP__JII(JNIEnv* e, jclass c, jlong a, jint p1, jint p2) {(void)e;(void)c;return a?((jlong(*)(int,int))((intptr_t)a))(p1,p2):0;}
JNIEXPORT jlong JNICALL Java_org_lwjgl_system_JNI_invokeP__JJJ(JNIEnv* e, jclass c, jlong a, jlong p1, jlong p2) {(void)e;(void)c;return a?((jlong(*)(long,long))((intptr_t)a))(p1,p2):0;}
JNIEXPORT jlong JNICALL Java_org_lwjgl_system_JNI_invokeP__JIII(JNIEnv* e, jclass c, jlong a, jint p1, jint p2, jint p3) {(void)e;(void)c;return a?((jlong(*)(int,int,int))((intptr_t)a))(p1,p2,p3):0;}
JNIEXPORT jlong JNICALL Java_org_lwjgl_system_JNI_invokeP__JJJJ(JNIEnv* e, jclass c, jlong a, jlong p1, jlong p2, jlong p3) {(void)e;(void)c;return a?((jlong(*)(long,long,long))((intptr_t)a))(p1,p2,p3):0;}

// ===== invokeD (double return) =====
JNIEXPORT jdouble JNICALL Java_org_lwjgl_system_JNI_invokeD__J(JNIEnv* e, jclass c, jlong a) {(void)e;(void)c;return a?((jdouble(*)())((intptr_t)a))():0.0;}
JNIEXPORT jdouble JNICALL Java_org_lwjgl_system_JNI_invokeD__JI(JNIEnv* e, jclass c, jlong a, jint p1) {(void)e;(void)c;return a?((jdouble(*)(int))((intptr_t)a))(p1):0.0;}
JNIEXPORT jdouble JNICALL Java_org_lwjgl_system_JNI_invokeD__JJ(JNIEnv* e, jclass c, jlong a, jlong p1) {(void)e;(void)c;return a?((jdouble(*)(long))((intptr_t)a))(p1):0.0;}
JNIEXPORT jdouble JNICALL Java_org_lwjgl_system_JNI_invokeD__JF(JNIEnv* e, jclass c, jlong a, jfloat p1) {(void)e;(void)c;return a?((jdouble(*)(float))((intptr_t)a))(p1):0.0;}
JNIEXPORT jdouble JNICALL Java_org_lwjgl_system_JNI_invokeD__JD(JNIEnv* e, jclass c, jlong a, jdouble p1) {(void)e;(void)c;return a?((jdouble(*)(double))((intptr_t)a))(p1):0.0;}
JNIEXPORT jdouble JNICALL Java_org_lwjgl_system_JNI_invokeD__JII(JNIEnv* e, jclass c, jlong a, jint p1, jint p2) {(void)e;(void)c;return a?((jdouble(*)(int,int))((intptr_t)a))(p1,p2):0.0;}
JNIEXPORT jdouble JNICALL Java_org_lwjgl_system_JNI_invokeD__JJJ(JNIEnv* e, jclass c, jlong a, jlong p1, jlong p2) {(void)e;(void)c;return a?((jdouble(*)(long,long))((intptr_t)a))(p1,p2):0.0;}
JNIEXPORT jdouble JNICALL Java_org_lwjgl_system_JNI_invokeD__JFF(JNIEnv* e, jclass c, jlong a, jfloat p1, jfloat p2) {(void)e;(void)c;return a?((jdouble(*)(float,float))((intptr_t)a))(p1,p2):0.0;}
JNIEXPORT jdouble JNICALL Java_org_lwjgl_system_JNI_invokeD__JDD(JNIEnv* e, jclass c, jlong a, jdouble p1, jdouble p2) {(void)e;(void)c;return a?((jdouble(*)(double,double))((intptr_t)a))(p1,p2):0.0;}
JNIEXPORT jdouble JNICALL Java_org_lwjgl_system_JNI_invokeD__JJJJ(JNIEnv* e, jclass c, jlong a, jlong p1, jlong p2, jlong p3) {(void)e;(void)c;return a?((jdouble(*)(long,long,long))((intptr_t)a))(p1,p2,p3):0.0;}

#undef INV_N
#undef RETURN_V
#undef RETURN_I
#undef RETURN_J
#undef RETURN_P
#undef RETURN_D
#undef RETURN_DEF_V
#undef RETURN_DEF_I
#undef RETURN_DEF_J
#undef RETURN_DEF_P
#undef RETURN_DEF_D
#undef RETURN0_V
#undef RETURN0_I
#undef RETURN0_J
#undef RETURN0_P
#undef RETURN0_D

} // extern "C"