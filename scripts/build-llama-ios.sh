#!/usr/bin/env bash
# Build llama.cpp as static libraries for iOS targets, combined into a single
# libkoml-llama.a per arch. Output paths line up with the cinterop config in
# engine-llama/build.gradle.kts.
#
# Usage: ./scripts/build-llama-ios.sh
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
LLAMA_DIR="$ROOT/external/llama.cpp"
BUILD_DIR="$ROOT/build/llama-ios"

if [ ! -d "$LLAMA_DIR" ]; then
    echo "ERROR: llama.cpp source not found at $LLAMA_DIR"
    echo "Run: git submodule update --init --recursive"
    exit 1
fi

build_arch() {
    local OUT_NAME=$1
    local SYSROOT=$2
    local ARCHS=$3
    local DEPLOYMENT=$4

    local OUT="$BUILD_DIR/$OUT_NAME"
    echo "==> Building $OUT_NAME (sysroot=$SYSROOT, archs=$ARCHS)"

    cmake -S "$LLAMA_DIR" -B "$OUT/build" \
        -G "Unix Makefiles" \
        -DCMAKE_SYSTEM_NAME=iOS \
        -DCMAKE_SYSTEM_PROCESSOR="$ARCHS" \
        -DCMAKE_OSX_SYSROOT="$SYSROOT" \
        -DCMAKE_OSX_ARCHITECTURES="$ARCHS" \
        -DCMAKE_OSX_DEPLOYMENT_TARGET="$DEPLOYMENT" \
        -DCMAKE_BUILD_TYPE=Release \
        -DBUILD_SHARED_LIBS=OFF \
        -DLLAMA_BUILD_EXAMPLES=OFF \
        -DLLAMA_BUILD_TESTS=OFF \
        -DLLAMA_BUILD_SERVER=OFF \
        -DLLAMA_BUILD_TOOLS=OFF \
        -DLLAMA_BUILD_COMMON=OFF \
        -DLLAMA_CURL=OFF \
        -DGGML_METAL=OFF \
        -DGGML_ACCELERATE=ON \
        -DGGML_BLAS=OFF

    cmake --build "$OUT/build" -j

    mkdir -p "$OUT/lib"
    find "$OUT/build" -name "*.a" -type f -exec cp {} "$OUT/lib/" \;

    # Combine into a single static lib for simpler cinterop linking
    cd "$OUT/lib"
    rm -f libkoml-llama.a
    libtool -static -o libkoml-llama.a $(ls libllama*.a libggml*.a 2>/dev/null)
    cd "$ROOT"
}

build_arch "ios-arm64"           iphoneos        "arm64"  "15.0"
build_arch "ios-simulator-arm64" iphonesimulator "arm64"  "15.0"
build_arch "ios-simulator-x64"   iphonesimulator "x86_64" "15.0"

echo "==> Done."
echo "    Per-arch libs under $BUILD_DIR/<arch>/lib/libkoml-llama.a"
