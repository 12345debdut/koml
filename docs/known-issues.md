# Known issues

Non-blocking gotchas, workarounds in place, and the plan for each. Update this file whenever a fix lands or a new known issue surfaces.

## 1. Metal disabled on iOS

**Status:** deferred until the next llama.cpp version bump (which is a minor-version event per [VERSIONING.md](VERSIONING.md)). v0.0.x ships with CPU-only iOS — that's the supported configuration.

**Symptom:** With `GGML_METAL=ON` + `GGML_METAL_EMBED_LIBRARY=ON` at llama.cpp **b5460**, the embedded Metal shader fails at runtime with:

```
ggml_metal_load_library: error: ... unknown type name 'block_q4_0' / 'block_q4_1' / ...
```

The Metal source string compiled into the binary is missing the type definitions from `ggml-common.h` — they aren't being prepended into the embed payload for the iOS cross-build.

**Workaround:** `scripts/build-llama-ios.sh` builds with `-DGGML_METAL=OFF -DGGML_ACCELERATE=ON`. `cinterop/llama.def` drops `-framework Metal -framework MetalKit` from `linkerOpts`. iOS runs on CPU with Apple's Accelerate-accelerated BLAS, which is fast enough for the small models we ship.

**JVM macOS** keeps Metal on — the embed bug is iOS-specific in b5460. If the macOS JVM build ever hits the same error, fall back to `-DGGML_METAL=OFF` in `scripts/build-llama-jvm.sh`.

**Plan:**
- Phase 3 polish: re-investigate when we bump llama.cpp to a newer tag (the embed bug is upstream-known and may already be fixed).
- Alternative: ship the `.metallib` as a bundle resource and load at runtime (`GGML_METAL_EMBED_LIBRARY=OFF`).

## 2. AGP 9 + KMP compatibility bypass

**Status:** stable workaround; revisit when `com.android.kotlin.multiplatform.library` DSL stabilizes for NDK.

**Symptom:** AGP 9.0 marks `com.android.library` as incompatible with `org.jetbrains.kotlin.multiplatform`. Recommended migration is to the new `com.android.kotlin.multiplatform.library` plugin, but its DSL for `externalNativeBuild` (NDK + CMake) is still maturing.

**Workaround:** `gradle.properties` sets:
```
android.builtInKotlin=false
android.newDsl=false
```
Plus `:samples-android` applies `kotlin-android` explicitly. AGP reverts to its AGP 8.x-style behavior.

**Plan:** Migrate to `com.android.kotlin.multiplatform.library` post-v1, once the NDK + CMake DSL has documented examples for static-lib subprojects.

## 3. Cinterop commonization (RESOLVED in v0.0.2)

`kotlin.mpp.enableCInteropCommonization=true` is now in `gradle.properties`. Iosmain code can share native bindings across iOS targets without per-arch duplication; no more warning at link time.

## 4. expect/actual classes are in Beta (RESOLVED in v0.0.2)

Every KMP module's `kotlin {}` block now sets:
```kotlin
compilerOptions {
    freeCompilerArgs.add("-Xexpect-actual-classes")
}
```
The ~30 Beta warnings are gone. The feature is stable in practice and Koml uses `expect class LlamaNative` extensively.

## 5. llama.cpp b5460 API renames

**Status:** documented, code matches.

llama.cpp at b5460 split `llama_vocab` out of `llama_model` and renamed several lifecycle functions. The full mapping is in `~/.claude/projects/.../memory/project_llamacpp_api_b5460.md`. The Android JNI bridge (`engine-llama/cpp/koml_jni.cpp`, shared with the JVM JNI build) and the iOS cinterop bindings (`LlamaNative.ios.kt`) all use the post-split API. Reference when bumping the llama.cpp submodule.

## 6. llama.cpp standalone build flags

**Status:** documented, scripts match.

When llama.cpp is invoked via `add_subdirectory()` (Android path), `LLAMA_STANDALONE=OFF` and most extras are off by default. When invoked directly via `cmake -S llama.cpp` (iOS and JVM paths), `LLAMA_STANDALONE=ON` and you must pass:
```
-DLLAMA_BUILD_EXAMPLES=OFF
-DLLAMA_BUILD_TESTS=OFF
-DLLAMA_BUILD_SERVER=OFF
-DLLAMA_BUILD_TOOLS=OFF
-DLLAMA_BUILD_COMMON=OFF
-DLLAMA_CURL=OFF
```
Both `scripts/build-llama-ios.sh` and `scripts/build-llama-jvm.sh` do this. Tracked in `memory/project_llamacpp_standalone_build.md`.

## 7. JVM platform coverage (macOS only)

**Status:** **Linux and Windows are deferred past 1.0.** Both require additional CI infrastructure (Docker cross-build for Linux from macOS hosts; a Windows runner for the JNI dylib). Neither is on the v1.0 critical path — the existing `scripts/build-llama-jvm.sh` produces Linux/Windows libs when run on the target host. v1.x is the natural moment to add them once there's demand.

`scripts/build-llama-jvm.sh` builds for macOS arm64 + x64 only. `LlamaNative.jvm.kt`'s `detectArch()` throws on Linux/Windows with a clear message pointing the user at the build script. To add Linux x64:
1. Run `scripts/build-llama-jvm.sh` on a Linux host (or via Docker `linux/amd64`).
2. Place the resulting `libkoml-jni.so` at `build/llama-jvm/linux-x64/libkoml-jni.so`.
3. Add a `from(sourceRoot.dir("linux-x64"))` block to the `collectJvmNativeLibs` task in `engine-llama/build.gradle.kts`.
4. Add a `"linux-x64"` arm to `LlamaNative.jvm.kt`'s `detectArch()`.

Phase 4 will set up GitHub Actions to do this automatically per-OS.

## 8. Curated manifest SHA-256s are placeholders

**Status:** v0.0.2 ships with `sha256 = "TODO_..."` placeholders.

The `:registry` module's `CuratedModels.list` has placeholder SHA-256 strings for all five models — downloads will fail their integrity check until the real hashes are filled in. Run:
```bash
./scripts/refresh-manifest-shas.sh
```
to download each GGUF, compute the real SHA-256 and byte size, and print a paste-ready diff for `CuratedModels.kt`. Must be re-run whenever a model file on HuggingFace gets re-quantized.

## 9. ~~No `chat()` yet~~ (RESOLVED in v0.0.3)

`session.chat(messages)` now picks the right template from `ModelInfo.promptTemplate` and renders the prompt. Five `ChatTemplate` data objects in `engine-llama/src/commonMain/kotlin/dev/koml/engine/chat/`: None, ChatML, Llama3, Phi3, Gemma. Stop sequences are merged automatically.

## 9a. HF search results aren't directly downloadable

**Status:** intentional v0.0.3 design; revisit when adding an opt-in `withFileDetails` flag.

`coordinator.registry.searchHuggingFace(query)` returns `ModelInfo` objects with blank `downloadUrl` and `sha256`. The HF Hub API doesn't include per-file metadata in the list-models response — each result would need an additional `GET /api/models/<id>` call to fetch the file list. We deliberately skip that to avoid making one search into ~20 API hits.

**Workaround:** if you want to download a search hit, supplement the `ModelInfo` yourself before passing to `coordinator.downloader.download(...)`. The KDoc on `ModelRegistry.searchHuggingFace` describes the contract.

**Plan:** v0.0.4+ may add `searchHuggingFace(query, withFileDetails = true)` that fans out per result. Off by default to keep the cheap path cheap.

## 10. ~~Configuration cache not enabled~~ (RESOLVED in v0.0.4)

`org.gradle.configuration-cache=true` is now in `gradle.properties`. Phase 4 audited the JVM tests, Android assemble, and iOS XCFramework + SKIE paths — all three are config-cache-compatible at the current plugin versions. Roughly halves warm-run config time.
