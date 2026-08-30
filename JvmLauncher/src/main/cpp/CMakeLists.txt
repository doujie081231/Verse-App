cmake_minimum_required(VERSION 3.22.1)

project("versejvm")

add_library(versejvm SHARED
    jvmlauncher.cpp
)

find_library(log-lib log)
find_library(android-lib android)

target_link_libraries(versejvm
    ${log-lib}
    ${android-lib}
    dl
)

target_compile_options(versejvm PRIVATE
    -Wall
    -Wextra
    -O2
)
