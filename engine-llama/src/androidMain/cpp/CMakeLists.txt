cmake_minimum_required(VERSION 3.22.1)
project(koml-engine)

# Path to llama.cpp source, passed from Gradle via -DLLAMA_CPP_DIR=...
if(NOT DEFINED LLAMA_CPP_DIR)
    message(FATAL_ERROR "LLAMA_CPP_DIR must be set to the llama.cpp source directory")
endif()

# Configure llama.cpp: build only the core library, nothing else
set(LLAMA_BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_TESTS    OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_SERVER   OFF CACHE BOOL "" FORCE)
set(LLAMA_CURL           OFF CACHE BOOL "" FORCE)
set(BUILD_SHARED_LIBS    OFF CACHE BOOL "" FORCE)

add_subdirectory(${LLAMA_CPP_DIR} ${CMAKE_CURRENT_BINARY_DIR}/llama.cpp)

# JNI bridge
add_library(koml-jni SHARED koml_jni.cpp)

target_include_directories(koml-jni PRIVATE
    ${LLAMA_CPP_DIR}/include
)

find_library(log-lib log)

target_link_libraries(koml-jni PRIVATE
    llama
    ${log-lib}
)

target_compile_features(koml-jni PRIVATE cxx_std_17)
