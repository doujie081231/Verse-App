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

// ==================== ffi_cif / ffi_closure 结构布局 (x86_64 SysV) ====================
// ffi_cif:    abi(0) nargs(4) arg_types(8) rtype(16) bytes(24) flags(28)  -> size 32, align 8
// ffi_closure: tramp[24] cif(24) fun(32) user_data(40)                    -> size 48, align 8
typedef struct {
    jint abi;
    jint nargs;
    void** arg_types;
    void* rtype;
    jint bytes;
    jint flags;
} verse_ffi_cif;

typedef struct {
    char tramp[24];
    verse_ffi_cif* cif;
    void (*fun)(verse_ffi_cif*, void*, void**, void*);
    void* user_data;
} verse_ffi_closure;

// FFICIF.offsets(long __size) -> 返回 SIZEOF，并把各字段偏移写入 __size 缓冲区
JNIEXPORT jint JNICALL
Java_org_lwjgl_system_libffi_FFICIF_offsets(JNIEnv* env, jclass cls, jlong __size) {
    (void)env; (void)cls;
    jint* offsets = __size ? (jint*)(intptr_t)__size : nullptr;
    if (offsets) {
        offsets[0] = 0;  // ABI
        offsets[1] = 4;  // NARGS
        offsets[2] = 8;  // ARG_TYPES
        offsets[3] = 16; // RTYPE
        offsets[4] = 24; // BYTES
        offsets[5] = 28; // FLAGS
    }
    return (jint)sizeof(verse_ffi_cif); // 32
}

// FFIClosure.offsets(long __size) -> 返回 SIZEOF，并把各字段偏移写入 __size 缓冲区
JNIEXPORT jint JNICALL
Java_org_lwjgl_system_libffi_FFIClosure_offsets(JNIEnv* env, jclass cls, jlong __size) {
    (void)env; (void)cls;
    jint* offsets = __size ? (jint*)(intptr_t)__size : nullptr;
    if (offsets) {
        offsets[0] = 24; // CIF (tramp 之后)
        offsets[1] = 32; // FUN
        offsets[2] = 40; // USER_DATA
    }
    return (jint)sizeof(verse_ffi_closure); // 48
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
    (void)e; (void)c;
    verse_ffi_cif* fc = cif ? (verse_ffi_cif*)(intptr_t)cif : nullptr;
    if (!fc) return 1; // FFI_BAD_TYPEDEF
    fc->abi = abi;
    fc->nargs = nargs;
    fc->rtype = (void*)(intptr_t)rtype;
    fc->arg_types = atypes ? (void**)(intptr_t)atypes : nullptr;
    fc->bytes = 0;
    fc->flags = 0;
    return 0; // FFI_OK
}
JNIEXPORT jint JNICALL
Java_org_lwjgl_system_libffi_LibFFI_nffi_1prep_1cif_1var(JNIEnv* e, jclass c, jlong cif, jint abi, jint fixedargs, jint ntotalargs, jlong rtype, jlong atypes) {
    (void)e; (void)c; (void)fixedargs;
    verse_ffi_cif* fc = cif ? (verse_ffi_cif*)(intptr_t)cif : nullptr;
    if (!fc) return 1; // FFI_BAD_TYPEDEF
    fc->abi = abi;
    fc->nargs = ntotalargs;
    fc->rtype = (void*)(intptr_t)rtype;
    fc->arg_types = atypes ? (void**)(intptr_t)atypes : nullptr;
    fc->bytes = 0;
    fc->flags = 0;
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
Java_org_lwjgl_system_ThreadLocalUtil_nsetupEnvData(jint capsOffset) {
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

// ==================== JNI FFI Invoke methods ====================
// The real org.lwjgl.system.JNI class declares 1398 native invoke* methods
// (function address is the LAST parameter; array params are forwarded via
// Get<Type>ArrayElements). These are generated by gen_jni_invoke.py from the
// javap output of the FCL LWJGL JNI class and #included below.

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

// ==================== Generated JNI.invoke methods ====================
#include "generated_jni_invoke.cpp"

} // extern "C"