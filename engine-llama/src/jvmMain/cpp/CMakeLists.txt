cmake_minimum_required(VERSION 3.22)
project(koml-jni-jvm CXX)

# Inputs from scripts/build-llama-jvm.sh:
#   LLAMA_CPP_DIR  — path to vendored llama.cpp source (for include dir)
#   LLAMA_LIB_DIR  — path to the per-arch llama.cpp build output (for static libs)
#   JAVA_HOME      — JDK root (for find_package(JNI))

if(NOT DEFINED LLAMA_CPP_DIR)
    message(FATAL_ERROR "LLAMA_CPP_DIR must be set")
endif()
if(NOT DEFINED LLAMA_LIB_DIR)
    message(FATAL_ERROR "LLAMA_LIB_DIR must be set")
endif()

find_package(JNI REQUIRED)
include_directories(${JNI_INCLUDE_DIRS} ${LLAMA_CPP_DIR}/include)

add_library(koml-jni SHARED
    ${CMAKE_CURRENT_SOURCE_DIR}/../../../cpp/koml_jni.cpp
)

# Collect all libllama* and libggml* static archives produced by the
# llama.cpp build for this arch.
file(GLOB LLAMA_STATIC_LIBS
    "${LLAMA_LIB_DIR}/src/libllama*.a"
    "${LLAMA_LIB_DIR}/ggml/src/libggml*.a"
    "${LLAMA_LIB_DIR}/ggml/src/ggml-cpu/libggml-cpu*.a"
    "${LLAMA_LIB_DIR}/ggml/src/ggml-metal/libggml-metal*.a"
    "${LLAMA_LIB_DIR}/ggml/src/ggml-blas/libggml-blas*.a"
)

target_link_libraries(koml-jni PRIVATE
    ${LLAMA_STATIC_LIBS}
    "-framework Foundation"
    "-framework Accelerate"
    "-framework Metal"
    "-framework MetalKit"
)

target_compile_features(koml-jni PRIVATE cxx_std_17)
set_target_properties(koml-jni PROPERTIES PREFIX "lib")
