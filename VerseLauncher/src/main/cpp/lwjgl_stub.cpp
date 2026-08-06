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

// ==================== MemoryAccessJNI ====================
// @CriticalNative 方法：JNI 实现不包含 JNIEnv* 和 jclass 参数
// 这些方法返回 C 标准库函数的地址（函数指针），不是调用它们
JNIEXPORT jint JNICALL
Java_org_lwjgl_system_MemoryAccessJNI_getPointerSize(void) {
    return (jint)sizeof(void*);
}

JNIEXPORT jlong JNICALL
Java_org_lwjgl_system_MemoryAccessJNI_malloc(void) {
    return (jlong)(intptr_t)&malloc;
}

JNIEXPORT jlong JNICALL
Java_org_lwjgl_system_MemoryAccessJNI_calloc(void) {
    return (jlong)(intptr_t)&calloc;
}

JNIEXPORT jlong JNICALL
Java_org_lwjgl_system_MemoryAccessJNI_realloc(void) {
    return (jlong)(intptr_t)&realloc;
}

JNIEXPORT jlong JNICALL
Java_org_lwjgl_system_MemoryAccessJNI_free(void) {
    return (jlong)(intptr_t)&free;
}

JNIEXPORT jlong JNICALL
Java_org_lwjgl_system_MemoryAccessJNI_aligned_1alloc(void) {
    void* fn = dlsym(RTLD_DEFAULT, "aligned_alloc");
    if (!fn) {
        // Android < API 28 没有 aligned_alloc，用 posix_memalign 封装
        // 但这里需要返回真实函数地址，如果没有就返回 0
        LOGE("aligned_alloc not found, using nullptr");
    }
    return (jlong)(intptr_t)fn;
}

JNIEXPORT jlong JNICALL
Java_org_lwjgl_system_MemoryAccessJNI_aligned_1free(void) {
    // aligned_free 通常就是 free
    return (jlong)(intptr_t)&free;
}

// @CriticalNative 内存读取方法
JNIEXPORT jbyte JNICALL
Java_org_lwjgl_system_MemoryAccessJNI_ngetByte(jlong address) {
    return *(jbyte*)(intptr_t)address;
}

JNIEXPORT jshort JNICALL
Java_org_lwjgl_system_MemoryAccessJNI_ngetShort(jlong address) {
    return *(jshort*)(intptr_t)address;
}

JNIEXPORT jint JNICALL
Java_org_lwjgl_system_MemoryAccessJNI_ngetInt(jlong address) {
    return *(jint*)(intptr_t)address;
}

JNIEXPORT jlong JNICALL
Java_org_lwjgl_system_MemoryAccessJNI_ngetLong(jlong address) {
    return *(jlong*)(intptr_t)address;
}

JNIEXPORT jfloat JNICALL
Java_org_lwjgl_system_MemoryAccessJNI_ngetFloat(jlong address) {
    return *(jfloat*)(intptr_t)address;
}

JNIEXPORT jdouble JNICALL
Java_org_lwjgl_system_MemoryAccessJNI_ngetDouble(jlong address) {
    return *(jdouble*)(intptr_t)address;
}

JNIEXPORT jlong JNICALL
Java_org_lwjgl_system_MemoryAccessJNI_ngetAddress(jlong address) {
    return *(jlong*)(intptr_t)address;
}

// @CriticalNative 内存写入方法
JNIEXPORT void JNICALL
Java_org_lwjgl_system_MemoryAccessJNI_nputByte(jlong address, jbyte value) {
    *(jbyte*)(intptr_t)address = value;
}

JNIEXPORT void JNICALL
Java_org_lwjgl_system_MemoryAccessJNI_nputShort(jlong address, jshort value) {
    *(jshort*)(intptr_t)address = value;
}

JNIEXPORT void JNICALL
Java_org_lwjgl_system_MemoryAccessJNI_nputInt(jlong address, jint value) {
    *(jint*)(intptr_t)address = value;
}

JNIEXPORT void JNICALL
Java_org_lwjgl_system_MemoryAccessJNI_nputLong(jlong address, jlong value) {
    *(jlong*)(intptr_t)address = value;
}

JNIEXPORT void JNICALL
Java_org_lwjgl_system_MemoryAccessJNI_nputFloat(jlong address, jfloat value) {
    *(jfloat*)(intptr_t)address = value;
}

JNIEXPORT void JNICALL
Java_org_lwjgl_system_MemoryAccessJNI_nputDouble(jlong address, jdouble value) {
    *(jdouble*)(intptr_t)address = value;
}

JNIEXPORT void JNICALL
Java_org_lwjgl_system_MemoryAccessJNI_nputAddress(jlong address, jlong value) {
    *(jlong*)(intptr_t)address = value;
}

// ==================== LibFFI JNI ====================
// @CriticalNative 方法：不带 JNIEnv*/jclass 参数
// 这些常量值在所有平台上相同（libffi 枚举值）
JNIEXPORT jshort JNICALL
Java_org_lwjgl_system_libffi_LibFFI_FFI_1TYPE_1DOUBLE(void) {
    return 3;
}

// FFI ABI 常量 - aarch64 Linux 值
JNIEXPORT jint JNICALL
Java_org_lwjgl_system_libffi_LibFFI_FFI_1WIN64(void) { return 0; }
JNIEXPORT jint JNICALL
Java_org_lwjgl_system_libffi_LibFFI_FFI_1GNUW64(void) { return 1; }
JNIEXPORT jint JNICALL
Java_org_lwjgl_system_libffi_LibFFI_FFI_1UNIX64(void) { return 2; }
JNIEXPORT jint JNICALL
Java_org_lwjgl_system_libffi_LibFFI_FFI_1EFI64(void) { return 3; }
JNIEXPORT jint JNICALL
Java_org_lwjgl_system_libffi_LibFFI_FFI_1SYSV(void) { return 1; }
JNIEXPORT jint JNICALL
Java_org_lwjgl_system_libffi_LibFFI_FFI_1STDCALL(void) { return 1; }
JNIEXPORT jint JNICALL
Java_org_lwjgl_system_libffi_LibFFI_FFI_1THISCALL(void) { return 2; }
JNIEXPORT jint JNICALL
Java_org_lwjgl_system_libffi_LibFFI_FFI_1FASTCALL(void) { return 3; }
JNIEXPORT jint JNICALL
Java_org_lwjgl_system_libffi_LibFFI_FFI_1MS_1CDECL(void) { return 4; }
JNIEXPORT jint JNICALL
Java_org_lwjgl_system_libffi_LibFFI_FFI_1PASCAL(void) { return 5; }
JNIEXPORT jint JNICALL
Java_org_lwjgl_system_libffi_LibFFI_FFI_1REGISTER(void) { return 6; }
JNIEXPORT jint JNICALL
Java_org_lwjgl_system_libffi_LibFFI_FFI_1VFP(void) { return 7; }
JNIEXPORT jint JNICALL
Java_org_lwjgl_system_libffi_LibFFI_FFI_1FIRST_1ABI(void) { return 0; }
JNIEXPORT jint JNICALL
Java_org_lwjgl_system_libffi_LibFFI_FFI_1LAST_1ABI(void) { return 2; }
JNIEXPORT jint JNICALL
Java_org_lwjgl_system_libffi_LibFFI_FFI_1DEFAULT_1ABI(void) { return 1; }

// nffi_type_* - Android 没有 libffi.so，需要自己定义 ffi_type 结构体
// ffi_type 结构: { size_t size, unsigned short alignment, unsigned short type, void* elements }
typedef struct {
    size_t size;
    unsigned short alignment;
    unsigned short type;
    void* elements;
} ffi_type_real;

// 静态 ffi_type 实例（值对应 libffi 标准定义）
static ffi_type_real s_ffi_type_void    = { 0, 0, 0, 0 };
static ffi_type_real s_ffi_type_uint8    = { 1, 1, 5, 0 };
static ffi_type_real s_ffi_type_sint8    = { 1, 1, 6, 0 };
static ffi_type_real s_ffi_type_uint16   = { 2, 2, 7, 0 };
static ffi_type_real s_ffi_type_sint16   = { 2, 2, 8, 0 };
static ffi_type_real s_ffi_type_uint32   = { 4, 4, 9, 0 };
static ffi_type_real s_ffi_type_sint32   = { 4, 4, 10, 0 };
static ffi_type_real s_ffi_type_uint64   = { 8, 8, 11, 0 };
static ffi_type_real s_ffi_type_sint64   = { 8, 8, 12, 0 };
static ffi_type_real s_ffi_type_uchar    = { 1, 1, 5, 0 };
static ffi_type_real s_ffi_type_schar    = { 1, 1, 6, 0 };
static ffi_type_real s_ffi_type_ushort   = { 2, 2, 7, 0 };
static ffi_type_real s_ffi_type_sshort   = { 2, 2, 8, 0 };
static ffi_type_real s_ffi_type_uint     = { 4, 4, 9, 0 };
static ffi_type_real s_ffi_type_sint     = { 4, 4, 10, 0 };
static ffi_type_real s_ffi_type_ulong    = { 8, 8, 11, 0 };
static ffi_type_real s_ffi_type_slong    = { 8, 8, 12, 0 };
static ffi_type_real s_ffi_type_float    = { 4, 4, 2, 0 };
static ffi_type_real s_ffi_type_double   = { 8, 8, 3, 0 };
static ffi_type_real s_ffi_type_longdouble = { 16, 16, 4, 0 };
static ffi_type_real s_ffi_type_pointer  = { 8, 8, 1, 0 };

JNIEXPORT jlong JNICALL
Java_org_lwjgl_system_libffi_LibFFI_nffi_1type_1void(JNIEnv* e, jclass c) {
    return (jlong)(intptr_t)&s_ffi_type_void;
}
JNIEXPORT jlong JNICALL
Java_org_lwjgl_system_libffi_LibFFI_nffi_1type_1uint8(JNIEnv* e, jclass c) {
    return (jlong)(intptr_t)&s_ffi_type_uint8;
}
JNIEXPORT jlong JNICALL
Java_org_lwjgl_system_libffi_LibFFI_nffi_1type_1sint8(JNIEnv* e, jclass c) {
    return (jlong)(intptr_t)&s_ffi_type_sint8;
}
JNIEXPORT jlong JNICALL
Java_org_lwjgl_system_libffi_LibFFI_nffi_1type_1uint16(JNIEnv* e, jclass c) {
    return (jlong)(intptr_t)&s_ffi_type_uint16;
}
JNIEXPORT jlong JNICALL
Java_org_lwjgl_system_libffi_LibFFI_nffi_1type_1sint16(JNIEnv* e, jclass c) {
    return (jlong)(intptr_t)&s_ffi_type_sint16;
}
JNIEXPORT jlong JNICALL
Java_org_lwjgl_system_libffi_LibFFI_nffi_1type_1uint32(JNIEnv* e, jclass c) {
    return (jlong)(intptr_t)&s_ffi_type_uint32;
}
JNIEXPORT jlong JNICALL
Java_org_lwjgl_system_libffi_LibFFI_nffi_1type_1sint32(JNIEnv* e, jclass c) {
    return (jlong)(intptr_t)&s_ffi_type_sint32;
}
JNIEXPORT jlong JNICALL
Java_org_lwjgl_system_libffi_LibFFI_nffi_1type_1uint64(JNIEnv* e, jclass c) {
    return (jlong)(intptr_t)&s_ffi_type_uint64;
}
JNIEXPORT jlong JNICALL
Java_org_lwjgl_system_libffi_LibFFI_nffi_1type_1sint64(JNIEnv* e, jclass c) {
    return (jlong)(intptr_t)&s_ffi_type_sint64;
}
JNIEXPORT jlong JNICALL
Java_org_lwjgl_system_libffi_LibFFI_nffi_1type_1uchar(JNIEnv* e, jclass c) {
    return (jlong)(intptr_t)&s_ffi_type_uchar;
}
JNIEXPORT jlong JNICALL
Java_org_lwjgl_system_libffi_LibFFI_nffi_1type_1schar(JNIEnv* e, jclass c) {
    return (jlong)(intptr_t)&s_ffi_type_schar;
}
JNIEXPORT jlong JNICALL
Java_org_lwjgl_system_libffi_LibFFI_nffi_1type_1ushort(JNIEnv* e, jclass c) {
    return (jlong)(intptr_t)&s_ffi_type_ushort;
}
JNIEXPORT jlong JNICALL
Java_org_lwjgl_system_libffi_LibFFI_nffi_1type_1sshort(JNIEnv* e, jclass c) {
    return (jlong)(intptr_t)&s_ffi_type_sshort;
}
JNIEXPORT jlong JNICALL
Java_org_lwjgl_system_libffi_LibFFI_nffi_1type_1uint(JNIEnv* e, jclass c) {
    return (jlong)(intptr_t)&s_ffi_type_uint;
}
JNIEXPORT jlong JNICALL
Java_org_lwjgl_system_libffi_LibFFI_nffi_1type_1sint(JNIEnv* e, jclass c) {
    return (jlong)(intptr_t)&s_ffi_type_sint;
}
JNIEXPORT jlong JNICALL
Java_org_lwjgl_system_libffi_LibFFI_nffi_1type_1ulong(JNIEnv* e, jclass c) {
    return (jlong)(intptr_t)&s_ffi_type_ulong;
}
JNIEXPORT jlong JNICALL
Java_org_lwjgl_system_libffi_LibFFI_nffi_1type_1slong(JNIEnv* e, jclass c) {
    return (jlong)(intptr_t)&s_ffi_type_slong;
}
JNIEXPORT jlong JNICALL
Java_org_lwjgl_system_libffi_LibFFI_nffi_1type_1float(JNIEnv* e, jclass c) {
    return (jlong)(intptr_t)&s_ffi_type_float;
}
JNIEXPORT jlong JNICALL
Java_org_lwjgl_system_libffi_LibFFI_nffi_1type_1double(JNIEnv* e, jclass c) {
    return (jlong)(intptr_t)&s_ffi_type_double;
}
JNIEXPORT jlong JNICALL
Java_org_lwjgl_system_libffi_LibFFI_nffi_1type_1longdouble(JNIEnv* e, jclass c) {
    return (jlong)(intptr_t)&s_ffi_type_longdouble;
}
JNIEXPORT jlong JNICALL
Java_org_lwjgl_system_libffi_LibFFI_nffi_1type_1pointer(JNIEnv* e, jclass c) {
    return (jlong)(intptr_t)&s_ffi_type_pointer;
}

// ffi_* 函数 - Android 没有 libffi.so，返回 stub 值
// 这些函数只在 ffi_call 时真正需要，MC 的 freetype 加载只需要 ffi_type 地址
JNIEXPORT jlong JNICALL
Java_org_lwjgl_system_libffi_LibFFI_nffi_1get_1version(JNIEnv* e, jclass c) {
    return 0;
}
JNIEXPORT jlong JNICALL
Java_org_lwjgl_system_libffi_LibFFI_ffi_1get_1version_1number(JNIEnv* e, jclass c) {
    return 0x33F00; // 3.3.6
}
JNIEXPORT jint JNICALL
Java_org_lwjgl_system_libffi_LibFFI_ffi_1get_1default_1abi(JNIEnv* e, jclass c) {
    return 1; // FFI_SYSV on aarch64
}
JNIEXPORT jint JNICALL
Java_org_lwjgl_system_libffi_LibFFI_nffi_1prep_1cif(JNIEnv* e, jclass c, jlong cif, jint abi, jint nargs, jlong rtype, jlong atypes) {
    return 0; // FFI_OK
}
JNIEXPORT jint JNICALL
Java_org_lwjgl_system_libffi_LibFFI_nffi_1prep_1cif_1var(JNIEnv* e, jclass c, jlong cif, jint abi, jint fixedargs, jint ntotalargs, jlong rtype, jlong atypes) {
    return 0; // FFI_OK
}
JNIEXPORT void JNICALL
Java_org_lwjgl_system_libffi_LibFFI_nffi_1call(JNIEnv* e, jclass c, jlong cif, jlong fnptr, jlong rvalue, jlong avalues) {
    // stub - MC 不应该走到这里
}
JNIEXPORT jint JNICALL
Java_org_lwjgl_system_libffi_LibFFI_nffi_1get_1struct_1offsets(JNIEnv* e, jclass c, jint abi, jlong type, jlong offsets) {
    return 0; // FFI_OK
}
JNIEXPORT jlong JNICALL
Java_org_lwjgl_system_libffi_LibFFI_ffi_1get_1closure_1size(JNIEnv* e, jclass c) {
    return 40; // ffi_closure 大小 (aarch64)
}
JNIEXPORT jlong JNICALL
Java_org_lwjgl_system_libffi_LibFFI_nffi_1closure_1alloc(JNIEnv* e, jclass c, jlong size, jlong code) {
    void* mem = malloc((size_t)size);
    if (code) *(void**)(intptr_t)code = mem;
    return (jlong)(intptr_t)mem;
}
JNIEXPORT void JNICALL
Java_org_lwjgl_system_libffi_LibFFI_nffi_1closure_1free(JNIEnv* e, jclass c, jlong closure) {
    free((void*)(intptr_t)closure);
}
JNIEXPORT jint JNICALL
Java_org_lwjgl_system_libffi_LibFFI_nffi_1prep_1closure_1loc(JNIEnv* e, jclass c, jlong closure, jlong cif, jlong fun, jlong userdata, jlong codeloc) {
    return 0; // FFI_OK
}

// ==================== ThreadLocalUtil JNI ====================
// @CriticalNative 方法：不带 JNIEnv*/jclass 参数
JNIEXPORT jlong JNICALL
Java_org_lwjgl_system_ThreadLocalUtil_getThreadJNIEnv(void) {
    return 0; // 不需要真实 JNIEnv 指针，LWJGL 仅用于内部检查
}

JNIEXPORT jlong JNICALL
Java_org_lwjgl_system_ThreadLocalUtil_getFunctionMissingAbort(void) {
    return 0;
}

JNIEXPORT jlong JNICALL
Java_org_lwjgl_system_ThreadLocalUtil_setupEnvData(jint capsOffset) {
    return 0;
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

JNIEXPORT jlong JNICALL
Java_org_lwjgl_system_linux_DynamicLinkLoader_ndlsym(JNIEnv* env, jclass cls, jlong handle, jlong name) {
    const char* cname = (const char*)(intptr_t)name;
    if (!cname) return 0;

    void* sym = nullptr;
    if (handle != 0) {
        sym = dlsym((void*)(intptr_t)handle, cname);
    }
    if (!sym) {
        sym = dlsym(RTLD_DEFAULT, cname);
    }

    if (!sym) {
        LOGI("ndlsym: %s not found", cname);
    }

    return (jlong)(intptr_t)sym;
}

JNIEXPORT jlong JNICALL
Java_org_lwjgl_system_linux_DynamicLinkLoader_ndlerror(JNIEnv* env, jclass cls) {
    dlerror(); // 清除旧错误
    return 0;
}

JNIEXPORT jint JNICALL
Java_org_lwjgl_system_linux_DynamicLinkLoader_ndlclose(JNIEnv* env, jclass cls, jlong handle) {
    if (handle != 0) {
        return dlclose((void*)(intptr_t)handle);
    }
    return 0;
}

// ==================== JNINativeInterface stubs ====================

JNIEXPORT jobject JNICALL
Java_org_lwjgl_system_jni_JNINativeInterface_nNewDirectByteBuffer(JNIEnv* env, jclass cls, jlong address, jlong capacity) {
    return env->NewDirectByteBuffer((void*)(intptr_t)address, capacity);
}

JNIEXPORT jlong JNICALL
Java_org_lwjgl_system_jni_JNINativeInterface_nGetDirectBufferAddress(JNIEnv* env, jclass cls, jobject buffer) {
    return (jlong)(intptr_t)env->GetDirectBufferAddress(buffer);
}

JNIEXPORT jlong JNICALL
Java_org_lwjgl_system_jni_JNINativeInterface_nGetDirectBufferCapacity(JNIEnv* env, jclass cls, jobject buffer) {
    return env->GetDirectBufferCapacity(buffer);
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

// ==================== LibCStdlib stubs ====================
// LWJGL 3.3+ 的 org.lwjgl.system.libc.LibCStdlib 类需要这些原生方法。
// MC 26.2 使用 LWJGL 3.3.6-snapshot，FreeType 加载时调用 nmalloc。
JNIEXPORT jlong JNICALL
Java_org_lwjgl_system_libc_LibCStdlib_nmalloc(JNIEnv* env, jclass cls, jlong size) {
    (void)env; (void)cls;
    void* ptr = malloc((size_t)size);
    return (jlong)(intptr_t)ptr;
}

JNIEXPORT jlong JNICALL
Java_org_lwjgl_system_libc_LibCStdlib_ncalloc(JNIEnv* env, jclass cls, jlong num, jlong size) {
    (void)env; (void)cls;
    void* ptr = calloc((size_t)num, (size_t)size);
    return (jlong)(intptr_t)ptr;
}

JNIEXPORT jlong JNICALL
Java_org_lwjgl_system_libc_LibCStdlib_nrealloc(JNIEnv* env, jclass cls, jlong ptr, jlong size) {
    (void)env; (void)cls;
    void* new_ptr = realloc((void*)(intptr_t)ptr, (size_t)size);
    return (jlong)(intptr_t)new_ptr;
}

JNIEXPORT void JNICALL
Java_org_lwjgl_system_libc_LibCStdlib_nfree(JNIEnv* env, jclass cls, jlong ptr) {
    (void)env; (void)cls;
    free((void*)(intptr_t)ptr);
}

// ==================== LibCStdlib aligned_alloc (LWJGL 3.3+) ====================
JNIEXPORT jlong JNICALL
Java_org_lwjgl_system_libc_LibCStdlib_naligned_1alloc(JNIEnv* env, jclass cls, jlong alignment, jlong size) {
    (void)env; (void)cls;
    void* ptr = nullptr;
#if defined(__ANDROID__) && __ANDROID_API__ >= 28
    ptr = aligned_alloc((size_t)alignment, (size_t)size);
#else
    if (posix_memalign(&ptr, (size_t)alignment, (size_t)size) != 0) ptr = nullptr;
#endif
    return (jlong)(intptr_t)ptr;
}

// ==================== LibCStdlib getPointerSize ====================
JNIEXPORT jint JNICALL
Java_org_lwjgl_system_libc_LibCStdlib_ngetPointerSize(JNIEnv* env, jclass cls) {
    (void)env; (void)cls;
    return (jint)sizeof(void*);
}

} // extern "C"