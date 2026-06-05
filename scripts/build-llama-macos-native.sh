#!/usr/bin/env bash
# Build llama.cpp as static libraries for macOS Kotlin/Native targets, combined
# into a single libkoml-llama.a per arch — same pattern as build-llama-ios.sh,
# different host. Output paths line up with the cinterop config in
# engine-llama/build.gradle.kts (macosArm64 + macosX64).
#
# Usage: ./scripts/build-llama-macos-native.sh
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
LLAMA_DIR="$ROOT/external/llama.cpp"
BUILD_DIR="$ROOT/build/llama-macos-native"

if [ ! -d "$LLAMA_DIR" ]; then
    echo "ERROR: llama.cpp source not found at $LLAMA_DIR"
    echo "Run: git submodule update --init --recursive"
    exit 1
fi

build_arch() {
    local OUT_NAME=$1   # macos-arm64 | macos-x64
    local ARCH=$2       # arm64 | x86_64

    local OUT="$BUILD_DIR/$OUT_NAME"
    echo "==> Building $OUT_NAME (arch=$ARCH)"
    rm -rf "$OUT" && mkdir -p "$OUT/build"

    cmake -S "$LLAMA_DIR" -B "$OUT/build" \
        -G "Unix Makefiles" \
        -DCMAKE_SYSTEM_PROCESSOR="$ARCH" \
        -DCMAKE_OSX_ARCHITECTURES="$ARCH" \
        -DCMAKE_OSX_DEPLOYMENT_TARGET=12.0 \
        -DCMAKE_BUILD_TYPE=Release \
        -DBUILD_SHARED_LIBS=OFF \
        -DLLAMA_BUILD_EXAMPLES=OFF \
        -DLLAMA_BUILD_TESTS=OFF \
        -DLLAMA_BUILD_SERVER=OFF \
        -DLLAMA_BUILD_TOOLS=OFF \
        -DLLAMA_BUILD_COMMON=OFF \
        -DLLAMA_CURL=OFF \
        -DGGML_METAL=ON \
        -DGGML_METAL_EMBED_LIBRARY=ON \
        -DGGML_ACCELERATE=ON \
        -DGGML_BLAS=OFF
    cmake --build "$OUT/build" -j

    mkdir -p "$OUT/lib"
    find "$OUT/build" -name "*.a" -type f -exec cp {} "$OUT/lib/" \;

    # Combine into a single static archive for simpler cinterop linking.
    cd "$OUT/lib"
    rm -f libkoml-llama.a
    libtool -static -o libkoml-llama.a $(ls libllama*.a libggml*.a 2>/dev/null)
    cd "$ROOT"
    echo "==> Produced $OUT/lib/libkoml-llama.a"
}

build_arch macos-arm64 arm64
build_arch macos-x64   x86_64

echo
echo "==> Done."
echo "    macOS arm64: $BUILD_DIR/macos-arm64/lib/libkoml-llama.a"
echo "    macOS x64:   $BUILD_DIR/macos-x64/lib/libkoml-llama.a"
