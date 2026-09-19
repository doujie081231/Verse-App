cmake_minimum_required(VERSION 3.22.1)
set(CMAKE_CXX_STANDARD 17)

project("verselauncher")

set(VERSELAUNCHER_SOURCES
    jni_launcher.cpp
    jvm_loader.cpp
    verse_bridge.cpp
    process_manager.cpp
    glfw_bridge.cpp
    lwjgl_bridge.cpp
    input_bridge.cpp
    egl_bridge.cpp
    lwjgl_stub.cpp
    glfw_jni_bridge.cpp
    glfw_hook.cpp
    pojav_compat.cpp
    generated_jni_invoke.cpp
)

# verseexec — JVMLoader 所需的 JNI 库（nativeLaunchJVM / JLI 绑定等）。
# 注：pojavexec / awt_xawt / OSMesa 等已有 jniLibs 预编译版本（来自 Pojav/FCL 迁移），
# 不再在此处重复构建，避免与预编译库同名冲突。
add_library(verseexec SHARED
    ${VERSELAUNCHER_SOURCES}
)

find_library(log-lib log)
find_library(android-lib android)
find_library(egl-lib EGL)
find_library(gles-lib GLESv3)

target_link_libraries(verseexec
    ${log-lib}
    ${android-lib}
    ${egl-lib}
    ${gles-lib}
    dl
)

target_compile_options(verseexec PRIVATE
    -Wall
    -Wextra
    -O2
    -fvisibility=default
)

# ===== poemav exec（fcl_orig 源码构建，替换 jniLibs 预编译 Pojav 版，仅 arm64-v8a）=====
# 背景：预编译（Pojav 官方）版 poemav exec 的 poemavInit 依赖 JNI_OnLoad runtime 分支先初始化
# vmGlfwClass/method_*，否则 glfwInit → pojavInit → updateMonitorSize 空指针 SIGSEGV；而 bionic 按
# SONAME 复用已加载库，游戏 JVM 内再 System.loadLibrary("pojavexec") 不会重新触发 JNI_OnLoad，
# 该依赖在 ARM 真机无法稳定满足（模拟器因 linker namespace 行为差异巧合正常）。
# fcl_orig 源码版 poemavInit 不调用 updateMonitorSize（只做 ANativeWindow 处理 + pojavInitOpenGL），
# 从根本上消除该空指针路径；JNI natives（GLFW 回调/cursor/nativeSetInputReady 等）与 FclCore 配对。
# 编译为独立 .so（不链接 libfcl.so）：链接所需 fcl 符号由 fcl_stub.c 提供。
# x86_64 模拟器保留 jniLibs 预编译版，此处仅构建 arm64-v8a。
if(${ANDROID_ABI} STREQUAL "arm64-v8a")
    add_library(pojavexec SHARED
        fcl_orig/bigcoreaffinity.c
        fcl_orig/egl_bridge.c
        fcl_orig/ctxbridges/loader_dlopen.c
        fcl_orig/ctxbridges/gl_bridge.c
        fcl_orig/ctxbridges/osm_bridge.c
        fcl_orig/ctxbridges/egl_loader.c
        fcl_orig/ctxbridges/osmesa_loader.c
        fcl_orig/ctxbridges/swap_interval_no_egl.c
        fcl_orig/environ/environ.c
        fcl_orig/input_bridge_v3.c
        fcl_orig/virgl/virgl.c
        fcl_orig/jre_launcher.c
        fcl_orig/lwjgl_dlopen_hook.c
        fcl_orig/driver_helper/driver_helper.c
        fcl_orig/driver_helper/nsbypass.c
        fcl_stub.c
    )

    target_include_directories(pojavexec PRIVATE
        ${CMAKE_CURRENT_SOURCE_DIR}/fcl_orig
        ${CMAKE_CURRENT_SOURCE_DIR}/fcl_orig/ctxbridges
        ${CMAKE_CURRENT_SOURCE_DIR}/fcl_orig/environ
        ${CMAKE_CURRENT_SOURCE_DIR}/fcl_orig/virgl
        ${CMAKE_CURRENT_SOURCE_DIR}/fcl_orig/driver_helper
    )

    target_link_libraries(pojavexec PRIVATE
        ${log-lib}
        ${android-lib}
        dl
        EGL
        GLESv2
    )

    # 导出所有符号到全局动态符号表：游戏 JVM 内 dlsym(RTLD_DEFAULT) 查找
    # Java_org_lwjgl_glfw_GLFW_* 时（System.loadLibrary → classloader namespace），
    # 确保本库符号对 JVM 可见（避免 26.2 官方 GLFW 类 UnsatisfiedLinkError）。
    target_link_options(pojavexec PRIVATE
        -Wl,--export-dynamic
    )

    # 默认可见性：游戏 JVM 需通过 dlsym(RTLD_DEFAULT, "Java_org_lwjgl_glfw_*"/"pojavInit") 动态绑定
    target_compile_options(pojavexec PRIVATE
        -O2
        -DADRENO_POSSIBLE
    )
endif()
