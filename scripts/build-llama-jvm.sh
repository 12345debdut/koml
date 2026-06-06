#!/usr/bin/env bash
# Build llama.cpp + the koml-jni shared library for desktop JVM on macOS,
# both arm64 (Apple Silicon) and x86_64 (Intel). Output:
#
#   build/llama-jvm/macos-arm64/libkoml-jni.dylib
#   build/llama-jvm/macos-x64/libkoml-jni.dylib
#
# These are picked up by engine-llama's collectJvmNativeLibs Gradle task and
# packaged under META-INF/native/<arch>/ in the JVM JAR.
#
# Linux/Windows aren't built by this script. Build them from source on the
# target host with the same llama.cpp flags.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
LLAMA_DIR="$ROOT/external/llama.cpp"
JNI_CMAKE_DIR="$ROOT/engine-llama/src/jvmMain/cpp"
OUT_ROOT="$ROOT/build/llama-jvm"

if [ ! -d "$LLAMA_DIR" ]; then
    echo "ERROR: llama.cpp not found at $LLAMA_DIR"
    echo "Run: git submodule update --init --recursive"
    exit 1
fi

# Resolve JAVA_HOME for JDK 17 (find_package(JNI) needs jni.h from a JDK).
if [ -z "${JAVA_HOME:-}" ]; then
    if /usr/libexec/java_home -v 17 >/dev/null 2>&1; then
        JAVA_HOME="$(/usr/libexec/java_home -v 17)"
    else
        echo "ERROR: JAVA_HOME not set and JDK 17 not found via /usr/libexec/java_home -v 17"
        echo "Install one: brew install --cask temurin@17"
        exit 1
    fi
fi
export JAVA_HOME
echo "==> Using JAVA_HOME=$JAVA_HOME"

build_arch() {
    local ARCH="$1"          # arm64 | x86_64
    local OUT_NAME="$2"      # macos-arm64 | macos-x64
    local OUT="$OUT_ROOT/$OUT_NAME"

    echo "==> Building llama.cpp for $OUT_NAME (arch=$ARCH)"
    rm -rf "$OUT"
    mkdir -p "$OUT/llama-build"

    cmake -S "$LLAMA_DIR" -B "$OUT/llama-build" \
        -G "Unix Makefiles" \
        -DCMAKE_BUILD_TYPE=Release \
        -DCMAKE_OSX_ARCHITECTURES="$ARCH" \
        -DCMAKE_OSX_DEPLOYMENT_TARGET=12.0 \
        -DBUILD_SHARED_LIBS=OFF \
        -DLLAMA_BUILD_EXAMPLES=OFF \
        -DLLAMA_BUILD_TESTS=OFF \
        -DLLAMA_BUILD_SERVER=OFF \
        -DLLAMA_BUILD_TOOLS=OFF \
        -DLLAMA_BUILD_COMMON=OFF \
        -DLLAMA_CURL=OFF \
        -DGGML_NATIVE=OFF \
        -DGGML_METAL=OFF \
        -DGGML_ACCELERATE=ON \
        -DGGML_BLAS=OFF
    cmake --build "$OUT/llama-build" -j

    echo "==> Building koml-jni for $OUT_NAME"
    mkdir -p "$OUT/jni-build"
    cmake -S "$JNI_CMAKE_DIR" -B "$OUT/jni-build" \
        -G "Unix Makefiles" \
        -DCMAKE_BUILD_TYPE=Release \
        -DCMAKE_OSX_ARCHITECTURES="$ARCH" \
        -DCMAKE_OSX_DEPLOYMENT_TARGET=12.0 \
        -DLLAMA_CPP_DIR="$LLAMA_DIR" \
        -DLLAMA_LIB_DIR="$OUT/llama-build" \
        -DJAVA_HOME="$JAVA_HOME"
    cmake --build "$OUT/jni-build" -j

    cp "$OUT/jni-build/libkoml-jni.dylib" "$OUT/libkoml-jni.dylib"
    # Make the dylib relocatable so JVM extraction to a temp dir still resolves @loader_path symbols.
    install_name_tool -id "@loader_path/libkoml-jni.dylib" "$OUT/libkoml-jni.dylib" || true
    echo "==> Produced $OUT/libkoml-jni.dylib"
}

build_arch arm64  macos-arm64
build_arch x86_64 macos-x64

echo
echo "==> Done."
echo "    macOS arm64: $OUT_ROOT/macos-arm64/libkoml-jni.dylib"
echo "    macOS x64:   $OUT_ROOT/macos-x64/libkoml-jni.dylib"
echo
echo "Now run: ./gradlew :engine-llama:jvmJar"
