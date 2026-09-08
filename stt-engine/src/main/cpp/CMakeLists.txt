cmake_minimum_required(VERSION 3.22.1)
project("stt-engine")

set(CMAKE_CXX_STANDARD 11)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# whisper.cpp sources (vendored, CPU backend only — no CUDA/Metal/OpenCL/SYCL)
set(WHISPER_SOURCES
    whisper/ggml.c
    whisper/ggml-alloc.c
    whisper/ggml-backend.c
    whisper/ggml-quants.c
    whisper/whisper.cpp
)

# NEON and optimization flags per ABI
if(ANDROID_ABI STREQUAL "arm64-v8a")
    add_definitions(-DGGML_USE_NEON -DGGML_USE_ARM_ARCH_EXTENSIONS)
    set(CMAKE_C_FLAGS "${CMAKE_C_FLAGS} -march=armv8.2-a+dotprod")
    set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -march=armv8.2-a+dotprod")
elseif(ANDROID_ABI STREQUAL "armeabi-v7a")
    add_definitions(-DGGML_USE_NEON)
    set(CMAKE_C_FLAGS "${CMAKE_C_FLAGS} -mfpu=neon -mfpu=neon-fp16")
    set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -mfpu=neon -mfpu=neon-fp16")
endif()

set(CMAKE_C_FLAGS "${CMAKE_C_FLAGS} -std=c11 -O3 -DNDEBUG")
set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -O3 -DNDEBUG")

add_library(stt-engine SHARED
    whisper-jni.cpp
    ${WHISPER_SOURCES}
)

target_include_directories(stt-engine PRIVATE
    whisper
)

target_link_libraries(stt-engine
    android
    log
    m
)
