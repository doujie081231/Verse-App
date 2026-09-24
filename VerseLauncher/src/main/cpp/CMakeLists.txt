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

# ===== poemav exec（jniLibs 预编译 Pojav 官方版，与全 ABI 一致）=====
# 迁移结论（2026-09-21）：x86_64/armeabi-v7a 一直使用 jniLibs 预编译官方版 poemav exec，
# 模拟器整套可正常启动；仅 arm64-v8a 曾改用 fcl_orig 源码自编译版（本 CMake target），
# 实测游戏 JVM 内 System.loadLibrary("pojavexec") 时其自研 runtime 分支 FindClass(GLFW)
# 返回 NULL → JNI_OnLoad+0x250 空指针 SIGSEGV（hs_err 实证），且与官方 GLFW jar 的
# internalWindowSizeChanged/(J)V 签名不匹配。
# 已按用户要求「整套迁移 FCL/Pojav，不自研」：arm64-v8a 恢复 jniLibs 官方预编译版
# （64456B，含 updateMonitorSize/pojavInit，无自研符号），ART 侧预加载链
# （FCLauncher.preloadCallbackBridge → System.loadLibrary("pojavexec")）已铺好。
# 此 block 不再构建 poemav（避免覆盖 jniLibs 官方版），源码 fcl_orig/ 仅作对照参考。
# ===== 原 fcl_orig 自编译块已禁用（保留源码作对照）=====
