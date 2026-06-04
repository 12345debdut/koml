# Known issues

Non-blocking gotchas, workarounds in place, and the plan for each. Update this file whenever a fix lands or a new known issue surfaces.

## 1. Metal disabled on iOS

**Status:** workaround in place; tracking for Phase 3 or llama.cpp version bump.

**Symptom:** With `GGML_METAL=ON` + `GGML_METAL_EMBED_LIBRARY=ON` at llama.cpp **b5460**, the embedded Metal shader fails at runtime with:

```
ggml_metal_load_library: error: ... unknown type name 'block_q4_0' / 'block_q4_1' / ...
```

The Metal source string compiled into the binary is missing the type definitions from `ggml-common.h` — they aren't being prepended into the embed payload for the iOS cross-build.

**Workaround:** `scripts/build-llama-ios.sh` builds with `-DGGML_METAL=OFF -DGGML_ACCELERATE=ON`. `cinterop/llama.def` drops `-framework Metal -framework MetalKit` from `linkerOpts`. iOS runs on CPU with Apple's Accelerate-accelerated BLAS, which is fast enough for the small models we ship.

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

## 3. Cinterop commonization warning

**Status:** cosmetic warning; tracked for Phase 2.

**Symptom:** Every iOS link logs:
```
w: ⚠️ CInterop Commonization Disabled
The project is using Kotlin Multiplatform with hierarchical structure ...
```

**Fix:** Add to `gradle.properties`:
```
kotlin.mpp.enableCInteropCommonization=true
```
Deferred to Phase 2 because we don't yet share `iosMain` code that needs commonized native bindings; once the registry/downloader land we may need shared `appleMain` code.

## 4. expect/actual classes are in Beta

**Status:** cosmetic warning; suppress in Phase 2.

**Symptom:** Every Kotlin compile logs:
```
w: ... 'expect'/'actual' classes ... are in Beta.
Consider using the '-Xexpect-actual-classes' flag to suppress this warning.
```

**Fix:** Add `-Xexpect-actual-classes` to `compilerOptions.freeCompilerArgs` in each KMP module's `kotlin {}` block. The feature is stable in practice — Koml uses `expect class LlamaNative` extensively.

## 5. llama.cpp b5460 API renames

**Status:** documented, code matches.

llama.cpp at b5460 split `llama_vocab` out of `llama_model` and renamed several lifecycle functions. The full mapping is in `~/.claude/projects/.../memory/project_llamacpp_api_b5460.md`. Both the Android JNI bridge (`koml_jni.cpp`) and the iOS cinterop bindings (`LlamaNative.ios.kt`) use the post-split API. Reference when bumping the llama.cpp submodule.

## 6. llama.cpp standalone build flags

**Status:** documented, scripts match.

When llama.cpp is invoked via `add_subdirectory()` (Android path), `LLAMA_STANDALONE=OFF` and most extras (tools, examples, tests) are off by default. When invoked directly via `cmake -S llama.cpp` (iOS path, and the future JVM/native paths), `LLAMA_STANDALONE=ON` and you must pass:
```
-DLLAMA_BUILD_EXAMPLES=OFF
-DLLAMA_BUILD_TESTS=OFF
-DLLAMA_BUILD_SERVER=OFF
-DLLAMA_BUILD_TOOLS=OFF
-DLLAMA_BUILD_COMMON=OFF
-DLLAMA_CURL=OFF
```

`scripts/build-llama-ios.sh` already does this. Phase 2 build scripts for JVM/native must do the same. Tracked in `memory/project_llamacpp_standalone_build.md`.

## 7. Stub registry and downloader

**Status:** Phase 2 will replace these.

`StubRegistry.curated()` returns `emptyList()`. `StubDownloader.download(...)` emits `DownloadState.Failed` immediately. The public interfaces exist so the API is stable from Phase 1 onward; the implementations are placeholders.

## 8. No `chat()` yet

**Status:** Phase 3.

`DefaultLlmSession.chat(...)` throws `NotImplementedError`. Use `generate()` with a manually-formatted prompt that matches your model's template until chat templates land in Phase 3.

## 9. Configuration cache not enabled

**Status:** cosmetic suggestion from Gradle 9.1.

Gradle suggests `org.gradle.configuration-cache=true` to speed up incremental builds. Not enabled yet because we haven't audited all build scripts for config-cache compatibility (especially the cmake/cinterop tasks). Phase 4 cleanup.
