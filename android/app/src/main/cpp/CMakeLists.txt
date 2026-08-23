cmake_minimum_required(VERSION 3.22.1)

project(dextop_input LANGUAGES CXX)

add_library(
    dextop_input
    SHARED
    native_input.cpp
)

target_compile_features(dextop_input PRIVATE cxx_std_20)

target_link_libraries(
    dextop_input
    android
    log
)
