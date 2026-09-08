cmake_minimum_required(VERSION 3.22.1)
project("scam-classifier")

set(CMAKE_CXX_STANDARD 11)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# ---- llama.cpp / ggml: CPU-only, no extras, deterministic sub-build ----
# These are set BEFORE add_subdirectory so llama.cpp's own option() calls
# respect them (cache FORCE overrides the defaults).
set(BUILD_SHARED_LIBS OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_COMMON OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_TESTS OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_TOOLS OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_SERVER OFF CACHE BOOL "" FORCE)
set(LLAMA_CURL OFF CACHE BOOL "" FORCE)
set(GGML_NATIVE OFF CACHE BOOL "" FORCE)
set(GGML_OPENMP OFF CACHE BOOL "" FORCE)
set(GGML_LLAMAFILE OFF CACHE BOOL "" FORCE)
set(GGML_BLAS OFF CACHE BOOL "" FORCE)
set(GGML_CUDA OFF CACHE BOOL "" FORCE)
set(GGML_METAL OFF CACHE BOOL "" FORCE)
set(GGML_VULKAN OFF CACHE BOOL "" FORCE)
set(GGML_OPENCL OFF CACHE BOOL "" FORCE)
set(GGML_SYCL OFF CACHE BOOL "" FORCE)
set(GGML_CANN OFF CACHE BOOL "" FORCE)
set(GGML_MUSA OFF CACHE BOOL "" FORCE)
set(GGML_RPC OFF CACHE BOOL "" FORCE)
set(GGML_WEBGPU OFF CACHE BOOL "" FORCE)
set(GGML_CPU_ALL_VARIANTS OFF CACHE BOOL "" FORCE)
set(GGML_BACKEND_DL OFF CACHE BOOL "" FORCE)
set(GGML_CPU_KLEIDIAI OFF CACHE BOOL "" FORCE)

# NEON and optimization flags per ABI (mirrors :stt-engine)
if(ANDROID_ABI STREQUAL "arm64-v8a")
    add_definitions(-DGGML_USE_NEON -DGGML_USE_ARM_ARCH_EXTENSIONS)
    set(CMAKE_C_FLAGS "${CMAKE_C_FLAGS} -march=armv8.2-a+dotprod")
    set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -march=armv8.2-a+dotprod")
elseif(ANDROID_ABI STREQUAL "armeabi-v7a")
    add_definitions(-DGGML_USE_NEON)
    set(CMAKE_C_FLAGS "${CMAKE_C_FLAGS} -mfpu=neon -mfpu=neon-fp16")
    set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -mfpu=neon -mfpu=neon-fp16")
endif()

set(CMAKE_C_FLAGS "${CMAKE_C_FLAGS} -O3 -DNDEBUG")
set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -O3 -DNDEBUG")

# Vendored llama.cpp (pinned tag b6103). Exposes the `llama` static library
# with its GGML backends and PUBLIC include dirs (llama.h + ggml.h).
add_subdirectory(llama)

add_library(scam-classifier SHARED
    llama-jni.cpp
)

target_link_libraries(scam-classifier
    llama
    android
    log
    m
    dl
)