cmake_minimum_required(VERSION 3.22.1)

project("terracotta")

add_library(terracotta SHARED
    terracotta.cpp
)

find_library(log-lib log)
find_library(android-lib android)

target_link_libraries(terracotta
    ${log-lib}
    ${android-lib}
    dl
)

target_compile_options(terracotta PRIVATE
    -Wall
    -Wextra
    -O2
)
