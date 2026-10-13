cmake_minimum_required(VERSION 3.22.1)
project(otg-whisper)

# whisper.cpp is a submodule at app/src/main/cpp/whisper.cpp
# Fetch at build time if missing (keeps repo small):
#   git submodule update --init --recursive
set(WHISPER_DIR ${CMAKE_CURRENT_SOURCE_DIR}/whisper.cpp)

if(EXISTS ${WHISPER_DIR}/CMakeLists.txt)
    set(GGML_NATIVE OFF)
    set(GGML_CPU_ALL_VARIANTS OFF)
    add_subdirectory(${WHISPER_DIR} whisper-build)
    add_library(otg-whisper SHARED otg-jni.cpp)
    target_link_libraries(otg-whisper whisper log android)
else()
    # Placeholder so Gradle sync works before submodule fetch.
    add_library(otg-whisper SHARED otg-jni-stub.cpp)
    target_link_libraries(otg-whisper log android)
endif()
