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
