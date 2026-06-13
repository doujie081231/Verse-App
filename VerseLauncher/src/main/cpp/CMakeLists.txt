cmake_minimum_required(VERSION 3.22.1)
set(CMAKE_CXX_STANDARD 17)

project("verselauncher")

add_library(pojavexec SHARED
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

add_library(lwjgl SHARED
    lwjgl_loader.cpp
)

find_library(log-lib log)
find_library(android-lib android)
find_library(egl-lib EGL)
find_library(gles-lib GLESv3)

target_link_libraries(pojavexec
    ${log-lib}
    ${android-lib}
    ${egl-lib}
    ${gles-lib}
    dl
)

target_compile_options(pojavexec PRIVATE
    -Wall
    -Wextra
    -O2
    -fvisibility=default
)

target_link_libraries(lwjgl
    ${log-lib}
)

target_compile_options(lwjgl PRIVATE
    -Wall
    -O2
    -fvisibility=default
)
