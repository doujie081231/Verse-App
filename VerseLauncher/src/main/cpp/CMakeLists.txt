cmake_minimum_required(VERSION 3.22.1)
set(CMAKE_CXX_STANDARD 17)

project("verselauncher")

add_library(verseexec SHARED
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
)

add_library(lwjgl_loader SHARED
    lwjgl_loader.cpp
)

# libawt_xawt.so 桩库 — 替代标准 JDK 的 AWT XAWT 库
# Android 上不需要真实 X11 实现，用空 JNI 桩函数替代
add_library(awt_xawt SHARED
    awt_xawt/xawt_fake.c
)

# glibc 兼容 stub 库 — 用于满足 glibc 编译的库的 DT_NEEDED 依赖
# Android bionic libc 没有版本化的库名（如 libdl.so.2、libpthread.so.0 等）
# 这个 stub 库提供一个有效的 ELF 文件，让动态链接器能找到这些库
# 实际的符号（dlopen、pthread_create 等）会从 Android 的 libc.so 中解析
# 部署时会复制为 libdl.so.2、libpthread.so.0、libgcc_s.so.1、libstdc++.so.6、libm.so.6 等名称
add_library(glibc_stub SHARED
    glibc_stub.c
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

target_link_libraries(lwjgl_loader
    ${log-lib}
)

target_compile_options(lwjgl_loader PRIVATE
    -Wall
    -O2
    -fvisibility=default
)

target_link_libraries(glibc_stub
    ${log-lib}
)

target_compile_options(glibc_stub PRIVATE
    -Wall
    -O2
    -fvisibility=default
)
