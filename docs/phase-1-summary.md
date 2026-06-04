# Phase 1 — Engine MVP

## Goal vs. outcome

Full Kotlin Multiplatform restructure with a stable public API in `:core` and a Flow-based streaming engine in `:engine-llama` running on **Android and iOS**.

**Acceptance criteria — both met:**
- ✅ Android sample streams tokens from a real prompt; Cancel button aborts cleanly mid-stream.
- ✅ iOS SwiftUI sample streams tokens from a real prompt; Cancel button aborts cleanly mid-stream.

Verified with **SmolLM2-135M Q8_0** on:
- Android arm64-v8a (physical device)
- iOS arm64 Simulator + Apple Silicon Mac

## What was built

### New modules

- **:core** — KMP module (Android, JVM, iosArm64, iosSimulatorArm64, iosX64). Public API only: interfaces, data classes, sealed `KomlException` hierarchy. Zero engine code.
- **:engine-llama** — converted from Android library to full KMP module:
  - `commonMain`: `LlmKit`, `DefaultLlmCoordinator`, `DefaultLlmSession`, `expect class LlamaNative`, stub registry/downloader
  - `androidMain`: Phase 0 JNI bridge moved here, wrapped as `actual class LlamaNative`
  - `iosMain`: cinterop bindings to `libkoml-llama.a`, `actual class LlamaNative` mirroring Android semantics

### Public API surface in :core (locked from now on, additive-only)

| Type | Package | Notes |
|---|---|---|
| `LlmCoordinator` | `dev.koml.core` | interface |
| `LlmSession` | `dev.koml.core` | interface; `generate()` returns `Flow<TokenEvent>`, cancellable |
| `ModelRegistry`, `ModelDownloader` | `dev.koml.core.{registry,download}` | interfaces — stub impls in Phase 1, real ones in Phase 2 |
| `ModelInfo`, `ModelHandle`, `ModelLicense`, `PromptTemplate` | `dev.koml.core.model` | data classes + enum |
| `GenParams`, `GenStats`, `TokenEvent`, `FinishReason`, `ChatMessage`, `ChatRole` | `dev.koml.core.session` | data + sealed + enum |
| `DownloadState` | `dev.koml.core.download` | sealed class |
| `LlmKitConfig`, `RuntimeConfig` | `dev.koml.core.config` | data classes |
| `KomlException` | `dev.koml.core.error` | sealed hierarchy, 6 subclasses |

### Engine implementation (:engine-llama commonMain)

- `LlmKit.initialize(LlmKitConfig)` → `LlmCoordinator` (suspend)
- `DefaultLlmSession.generate()`:
  - `flow { ... }.flowOn(sessionDispatcher)` where `sessionDispatcher = Dispatchers.Default.limitedParallelism(1)` per session
  - tokenize prompt → decode → loop: sample → check EoG / stop sequence / `ensureActive()` for cancellation → emit `TokenEvent.Token` → decode the new token
  - terminal `TokenEvent.Done(reason, stats)` carries tokens, ms, tok/s
- `chat()` throws `NotImplementedError` — chat templates are explicitly Phase 3.

### iOS bindings

- `engine-llama/src/nativeInterop/cinterop/llama.def` — points at `llama.h`, package `dev.koml.engine.native`, links `libkoml-llama.a`
- `scripts/build-llama-ios.sh` — three-arch llama.cpp build (ios-arm64, ios-simulator-arm64, ios-simulator-x64). Uses Unix Makefiles + explicit `CMAKE_SYSTEM_PROCESSOR`. Combines per-arch `lib*.a` into a single `libkoml-llama.a` via `libtool -static`.
- `engine-llama/build.gradle.kts` — configures cinterop per target with matching `libraryPath`, exports `KomlEngine.xcframework` via the standard `XCFramework` helper, re-exports `:core` types into the framework.
- `LlamaNative.ios.kt` mirrors the Android JNI semantics (same Long handles, same vocab/model split as b5460).

### Tooling added during Phase 1 (not in original plan)

- **SKIE 0.10.12** — Touchlab's Swift Kotlin Interface Enhancer. Bridges Kotlin `Flow<T>` as Swift `AsyncSequence`, generates exhaustive `onEnum(of:)` for sealed classes, exposes Kotlin `suspend` as Swift `async`. Without SKIE, `for await event in flow { ... }` won't compile in Swift.
- **XcodeGen** — declarative `samples-ios/project.yml` generates the Xcode project. Standard pattern in modern KMP repos; avoids committing 200+ lines of UUID-laden `project.pbxproj`.
- **AGP 9 + KMP bypass** — `android.builtInKotlin=false` + `android.newDsl=false` in `gradle.properties`. Reverts AGP 9 to AGP 8.x-style behavior so `com.android.library` + `kotlin-multiplatform` can coexist.
- **`jvmToolchain(17)`** in every module — aligns Java + Kotlin JVM targets.

### Samples

- **`:samples-android` MainActivity.kt** — refactored to `LlmKit.initialize() → coordinator.loadModel() → session.generate(...).collect { ... }`. Cancel button cancels the in-flight `Job`. Stats panel at the end (`reason`, `tokens`, `tok/s`).
- **`samples-ios/KomlSample/`** — SwiftUI app:
  - `KomlSampleApp.swift` + `ContentView.swift` using `for await event in flow` (SKIE-bridged), `switch onEnum(of: event)` for sealed-class match
  - `project.yml` for XcodeGen
  - `Info.plist`, `Resources/` for the bundled GGUF
  - README walks through `brew install xcodegen` + the rest of setup

## File inventory

### Created (Phase 1)

```
core/build.gradle.kts
core/src/commonMain/kotlin/dev/koml/core/
  LlmCoordinator.kt
  LlmSession.kt
  config/LlmKitConfig.kt
  config/RuntimeConfig.kt
  error/KomlException.kt
  model/ModelInfo.kt
  model/ModelHandle.kt
  model/ModelLicense.kt
  model/PromptTemplate.kt
  registry/ModelRegistry.kt
  download/ModelDownloader.kt
  download/DownloadState.kt
  session/GenParams.kt
  session/GenStats.kt
  session/TokenEvent.kt
  session/FinishReason.kt
  session/ChatMessage.kt
  session/ChatRole.kt

engine-llama/src/commonMain/kotlin/dev/koml/engine/
  LlmKit.kt
  LlamaNative.kt                       (expect class)
  internal/DefaultLlmCoordinator.kt
  internal/DefaultLlmSession.kt
  internal/StubRegistry.kt
  internal/StubDownloader.kt

engine-llama/src/androidMain/kotlin/dev/koml/engine/LlamaNative.android.kt
engine-llama/src/iosMain/kotlin/dev/koml/engine/LlamaNative.ios.kt
engine-llama/src/nativeInterop/cinterop/llama.def

scripts/build-llama-ios.sh

samples-ios/project.yml
samples-ios/README.md
samples-ios/KomlSample/Info.plist
samples-ios/KomlSample/KomlSampleApp.swift
samples-ios/KomlSample/ContentView.swift
samples-ios/KomlSample/Resources/.gitkeep

docs/phase-1-summary.md
docs/known-issues.md
README.md
```

### Modified

```
settings.gradle.kts                                       (add :core)
build.gradle.kts                                          (root: kotlin-multiplatform, kotlin-android, skie aliases)
gradle.properties                                         (AGP 9 bypass)
gradle/libs.versions.toml                                 (kotlinx-coroutines-core, kotlin-android, skie)
engine-llama/build.gradle.kts                             (KMP, iOS targets, XCFramework, cinterop, SKIE, export(:core))
samples-android/build.gradle.kts                          (kotlin-android plugin, jvmToolchain(17))
samples-android/src/main/res/layout/activity_main.xml     (cancel button)
samples-android/src/main/res/values/strings.xml           (cancel string)
samples-android/src/main/kotlin/.../MainActivity.kt       (streaming Flow API + cancel)
samples-android/src/main/res/values/themes.xml            (NoActionBar)
.gitignore                                                (samples-ios xcodeproj, derived data, gguf binaries)
```

### Moved (Phase 0 files, content unchanged)

```
engine-llama/src/main/cpp/CMakeLists.txt → engine-llama/src/androidMain/cpp/CMakeLists.txt
engine-llama/src/main/cpp/koml_jni.cpp   → engine-llama/src/androidMain/cpp/koml_jni.cpp
engine-llama/src/main/kotlin/.../LlamaNative.kt → engine-llama/src/androidMain/kotlin/.../LlamaNative.android.kt
```

## Issues hit during Phase 1 and how they were resolved

| Issue | Fix |
|---|---|
| `LlamaNative` JNI compile errors against b5460 | API rename: `llama_load_model_from_file` → `llama_model_load_from_file`, `llama_free_model` → `llama_model_free`, `llama_new_context_with_model` → `llama_init_from_model`. Tokenize/piece/EoG now take `llama_vocab*` from `llama_model_get_vocab(model)`. See `memory/project_llamacpp_api_b5460.md`. |
| iOS CMake: `LLAMA_BUILD_TOOLS` defaults to ON for standalone builds | Added `-DLLAMA_BUILD_TOOLS=OFF` (and `LLAMA_BUILD_COMMON=OFF`). Android `add_subdirectory()` path didn't have this problem because `LLAMA_STANDALONE=OFF` there. |
| Xcode generator + iOS cross-compile: empty `CMAKE_SYSTEM_PROCESSOR`, `target_compile_features` failures | Switched to `-G "Unix Makefiles"` + explicit `-DCMAKE_SYSTEM_PROCESSOR="$ARCHS"`. |
| AGP 9 + `kotlin-multiplatform` incompatibility | Set `android.builtInKotlin=false` and `android.newDsl=false`. Apply `kotlin-android` explicitly in `:samples-android`. |
| Inconsistent JVM targets (Java 11, Kotlin 21) | `kotlin { jvmToolchain(17) }` in every module. |
| `Dispatchers.IO` not accessible from KMP commonMain on iOS native | Use `Dispatchers.Default.limitedParallelism(1)`. |
| `StringBuilder.delete(...)` not in KMP | Use `StringBuilder.deleteRange(...)`. |
| Cinterop unresolved `useContents`/`readValue`/`n_gpu_layers`/`n_ctx` | Missing imports — added `kotlinx.cinterop.useContents` and `kotlinx.cinterop.readValue`. |
| `:core` types missing from XCFramework Swift headers | `export(project(":core"))` on each framework binary. |
| Swift `for await event in flow` doesn't compile (vanilla KMP) | Added SKIE 0.10.12 to bridge `Flow` as `AsyncSequence`. |
| Swift `as? TokenEventToken` doesn't work with SKIE | SKIE bridges sealed-class subclasses as nested types. Use `switch onEnum(of: event) { case .token(let t): ... }`. |
| Swift `'init()' is unavailable` on `LlmKitConfig()` / `'init' takes no arguments` | Pass parameters explicitly: `LlmKitConfig(maxConcurrentSessions: 1)`. |
| iOS Metal shader runtime error: `unknown type name 'block_q4_0'` | Disabled Metal on iOS (`GGML_METAL=OFF`). Falls back to Accelerate-accelerated CPU. Known b5460 embed bug. Tracked in `docs/known-issues.md`. |

## Manual setup (for next session / new contributor)

1. **Install prerequisites** (one-time):
   ```bash
   brew install xcodegen
   ```
   (Plus JDK 17, Xcode 16, Android Studio with NDK 27 + CMake 3.22 — same as Phase 0.)

2. **Build llama.cpp for iOS** (one-time, ~10–15 min):
   ```bash
   ./scripts/build-llama-ios.sh
   ```
   Output: `build/llama-ios/<arch>/lib/libkoml-llama.a` (three archs).

3. **Build the iOS XCFramework**:
   ```bash
   ./gradlew :engine-llama:assembleKomlEngineDebugXCFramework
   ```
   Output: `engine-llama/build/XCFrameworks/debug/KomlEngine.xcframework`.

4. **Build the Android sample APK**:
   ```bash
   ./gradlew :samples-android:assembleDebug
   ```

5. **Set up the iOS sample** (see [`samples-ios/README.md`](../samples-ios/README.md)):
   ```bash
   cd samples-ios
   curl -L -o "KomlSample/Resources/model.gguf" \
     "https://huggingface.co/bartowski/SmolLM2-135M-Instruct-GGUF/resolve/main/SmolLM2-135M-Instruct-Q8_0.gguf"
   xcodegen
   open KomlSample.xcodeproj
   ```

## Verification

**Android:**
```bash
adb push samples-android/build/outputs/apk/debug/samples-android-debug.apk /data/local/tmp/
adb install -r samples-android/build/outputs/apk/debug/samples-android-debug.apk
adb logcat -s KomlSample KomlEngine
# Tap Generate — tokens stream in. Tap Cancel mid-stream — see [cancelled].
```

**iOS:**
After XcodeGen setup, in Xcode: ⇧⌘K → ⌘R. Tap Generate → tokens stream → Cancel → `[cancelled]`.

## Open items / known limitations

See [`docs/known-issues.md`](known-issues.md) for the full list. Highlights:

- **No Metal on iOS** — CPU-only via Accelerate. Tracked for Phase 3 / llama.cpp version bump.
- **Stub registry/downloader** — Phase 2 replaces these.
- **No `chat()`** — Phase 3 adds chat templates.
- **AGP 9 bypass** — `android.builtInKotlin=false` / `android.newDsl=false`. Migrate to `com.android.kotlin.multiplatform.library` when its NDK support matures.
- **Cinterop commonization warning** — enable `kotlin.mpp.enableCInteropCommonization=true` in Phase 2.

## Commit message

```
feat(core,engine-llama): KMP restructure with Flow streaming + iOS bindings

- :core module with public API: LlmCoordinator, LlmSession, sealed
  TokenEvent / DownloadState / KomlException, GenParams, ModelInfo, etc.
- :engine-llama converted to KMP. commonMain holds LlmKit,
  DefaultLlmCoordinator, DefaultLlmSession (Flow-based generate with
  per-session limitedParallelism(1) dispatcher, stop-sequence detection,
  cancellation via ensureActive).
- androidMain wraps the Phase 0 JNI bridge as actual LlamaNative.
- iosMain uses cinterop bindings to llama.cpp static libs.
- SKIE 0.10.12 bridges Kotlin Flow to Swift AsyncSequence and sealed
  classes to onEnum(of:) for exhaustive switch.
- scripts/build-llama-ios.sh builds llama.cpp for three iOS arch slices
  with Accelerate (CPU); Metal disabled pending the b5460 ggml embed fix.
- samples-android refactored to use the new streaming API + cancel.
- samples-ios: SwiftUI app + XcodeGen project.yml.

Verified streaming + cancellation on Android arm64-v8a and iOS arm64
Simulator with SmolLM2-135M Q8_0.
```

## What comes next (Phase 2 — Distribution Layer)

- `:engine-llama` JVM target (JNI for macOS arm64/x64, Linux x64)
- `:storage` module with expect/actual paths
- `:download` module with Ktor + Okio resumable + SHA-256 verification
- `:registry` module loading the curated JSON manifest (5 models)
- Compose Desktop sample (`:samples-desktop`)
- `LlmKit.initialize()` wires real registry + downloader
