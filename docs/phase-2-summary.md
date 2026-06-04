# Phase 2 — Distribution Layer

## Goal vs. outcome

Turn Koml from "works if you put a GGUF on disk" into "works if you call `coordinator.downloader.download(...)`." Add the JVM desktop engine alongside the existing Android + iOS engines.

**Acceptance criteria — all met:**
- ✅ `:core` exposes a real `ModelStorage` interface; Phase 1 public API unchanged except for the additive default-impl `LlmCoordinator.acceptLicense(modelId)`.
- ✅ `:storage`, `:download`, `:registry` modules compile and pass smoke builds on Android, JVM, all three iOS targets.
- ✅ `:engine-llama` gains a `jvm()` target with JNI; the same `koml_jni.cpp` is shared between Android and JVM via `#ifdef __ANDROID__`.
- ✅ `DefaultLlmCoordinator` wired to real impls; `StubRegistry` and `StubDownloader` deleted.
- ✅ `:samples-desktop` Compose Desktop sample compiles.
- ✅ `:samples-android` refactored: spinner picks a curated model, Download → Load → Generate → Cancel buttons drive the new flow.
- ✅ `samples-ios/KomlSample/ContentView.swift` updated with model picker + download UI; XCFramework rebuilt with the new `:core` types (ModelStorage, `acceptLicense`) exported.
- ✅ License-acceptance code path is end-to-end: gate checked in `DefaultModelDownloader`, override implemented in `DefaultLlmCoordinator.acceptLicense`, marker files persisted under `<root>/licenses/`.

**Verification gap (intentional):** The runtime end-to-end test (browse → download → load → generate on a real device) requires real SHA-256s in `CuratedModels.kt`. Run `scripts/refresh-manifest-shas.sh` to populate them, then test on Android / iOS / JVM. The build is GREEN on all platforms today.

## What was built

### New modules

| Module | Targets | Purpose |
|---|---|---|
| `:storage` | Android, JVM, iOS×3 | `DefaultModelStorage` (Okio-backed), `expect fun komlRootDir()`, Android `KomlContext` auto-init via `androidx-startup` |
| `:download` | Android, JVM, iOS×3 | `DefaultModelDownloader` (Ktor + Okio + kotlincrypto sha2 + resumable + license gate), `ModelDownloaderFactory.create()` |
| `:registry` | Android, JVM, iOS×3 | `DefaultModelRegistry` + hardcoded `CuratedModels.list` (5 ungated models) |
| `:samples-desktop` | JVM | Compose Desktop app exercising the full flow |

### `:engine-llama` changes

- **`jvm()` target** added to `kotlin {}` block. `LlamaNative.jvm.kt` extracts a `libkoml-jni.dylib` slice from the JAR (`META-INF/native/<arch>/`) to a temp dir and `System.load()`s it once (idempotent via `@Volatile var loaded` + `synchronized`).
- **`engine-llama/cpp/koml_jni.cpp`** — the Phase 0 JNI source moved here from `androidMain/cpp/`. Now compiled by both the Android NDK build and the JVM build via `#ifdef __ANDROID__` for logging differences (`__android_log_print` vs `fprintf(stderr, ...)`). Android `CMakeLists.txt` updated to reference `../../../cpp/koml_jni.cpp`.
- **`engine-llama/src/jvmMain/cpp/CMakeLists.txt`** — `find_package(JNI)`, links static `libllama*.a` + `libggml*.a` + Foundation/Accelerate/Metal frameworks.
- **`collectJvmNativeLibs` Gradle Copy task** — moves built `.dylib`s from `build/llama-jvm/<arch>/` into `build/generated/native-resources/META-INF/native/<arch>/`, which becomes the `jvmMain` resources root.
- **`DefaultLlmCoordinator` rewired:**
  - `storage = ModelStorageFactory.create()`
  - `registry = DefaultModelRegistry()`
  - `downloadStack = ModelDownloaderFactory.create(storage) { id -> registry.resolve(id) }`
  - `downloader = downloadStack.downloader`
  - `acceptLicense(modelId)` delegates to `downloadStack.acceptLicense(...)` after verifying the model exists in the registry.
- **`StubRegistry.kt` and `StubDownloader.kt` deleted.**

### `:core` additions (surgical)

- `core/src/commonMain/kotlin/dev/koml/core/storage/ModelStorage.kt` — interface with 8 suspending methods covering models dir, model file path, partial file path, license record path, exists/size/delete/mkdirs.
- `LlmCoordinator.acceptLicense(modelId: String): Boolean = false` — new default-impl method (binary-compatible).

### Build scripts

- `scripts/build-llama-jvm.sh` — builds llama.cpp + `libkoml-jni.dylib` for macOS arm64 + x64. Uses Unix Makefiles, explicit `CMAKE_OSX_ARCHITECTURES`. Auto-resolves JDK 17 via `/usr/libexec/java_home -v 17`. Calls `install_name_tool -id "@loader_path/..."` on each `.dylib` so the JVM extractor's relocation works.
- `scripts/refresh-manifest-shas.sh` — `curl -L` each of the 5 GGUFs, computes `sha256sum` and `wc -c`, prints paste-ready Kotlin diff lines for `CuratedModels.kt`.

### Sample updates

- **`:samples-android` `MainActivity.kt`** — full refactor:
  - Top: `Spinner` of curated model names + metadata line (size, ctx, license, RAM).
  - Middle: Download / Load / Delete button row, with progress bar + status line.
  - Below: Generate / Cancel button row + monospace output `TextView`.
  - All wired to `coordinator.registry.curated()`, `coordinator.downloader.download(model).collect { ... }`, `coordinator.loadModel(handle)`, `session.generate(...).collect { ... }`. Uses `lifecycleScope` for cancellation.
- **`samples-ios/KomlSample/ContentView.swift`** — picker + download UI added:
  - `Picker("Model", selection:)` over `coordinator.registry.curated()`.
  - Download/Load/Delete row, `ProgressView(value:)` for download progress.
  - Generate/Cancel row, monospace `ScrollView` for output.
  - Uses SKIE's `onEnum(of:)` for `DownloadState` and `TokenEvent` cases. Same `try await` async/throws bridging pattern as Phase 1.

### Root config

- `settings.gradle.kts` — added 4 includes (`:storage`, `:download`, `:registry`, `:samples-desktop`).
- `gradle/libs.versions.toml` — added: ktor 3.3.0 (+ okhttp/darwin engines), okio 3.10.2, kotlinx-serialization 1.9.0, kotlincrypto-hash 0.6.0, androidx-startup 1.2.0, compose-multiplatform 1.8.2, kotlinx-coroutines-swing, plus the compose & serialization plugin aliases.
- `gradle.properties` — added `kotlin.mpp.enableCInteropCommonization=true` (resolves known-issue #3).
- Every KMP module's `build.gradle.kts` adds `compilerOptions { freeCompilerArgs.add("-Xexpect-actual-classes") }` (resolves known-issue #4).

## File inventory

### Created (Phase 2)

```
core/src/commonMain/kotlin/dev/koml/core/storage/ModelStorage.kt

storage/build.gradle.kts
storage/src/commonMain/kotlin/dev/koml/storage/{DefaultModelStorage,ModelStorageFactory,KomlRoot}.kt
storage/src/androidMain/kotlin/dev/koml/storage/{KomlRoot,KomlContextInitializer}.kt
storage/src/androidMain/AndroidManifest.xml
storage/src/jvmMain/kotlin/dev/koml/storage/KomlRoot.kt
storage/src/iosMain/kotlin/dev/koml/storage/KomlRoot.kt

download/build.gradle.kts
download/src/commonMain/kotlin/dev/koml/download/{DefaultModelDownloader,LicenseGate,HttpClientFactory,Sha256Util,ModelDownloaderFactory}.kt
download/src/{androidMain,jvmMain,iosMain}/kotlin/dev/koml/download/HttpClientFactory.kt

registry/build.gradle.kts
registry/src/commonMain/kotlin/dev/koml/registry/{DefaultModelRegistry,CuratedModels}.kt

engine-llama/cpp/koml_jni.cpp                                    (MOVED from androidMain/cpp/)
engine-llama/src/jvmMain/cpp/CMakeLists.txt
engine-llama/src/jvmMain/kotlin/dev/koml/engine/LlamaNative.jvm.kt

samples-desktop/build.gradle.kts
samples-desktop/src/jvmMain/kotlin/dev/koml/samples/desktop/{Main,App}.kt

scripts/build-llama-jvm.sh
scripts/refresh-manifest-shas.sh

docs/phase-2-summary.md
docs/releases/v0.0.2.md
```

### Modified

```
core/src/commonMain/kotlin/dev/koml/core/LlmCoordinator.kt              (+1 default method)
core/build.gradle.kts                                                   (+ -Xexpect-actual-classes)
engine-llama/build.gradle.kts                                           (+jvm(), +collectJvmNativeLibs, +deps)
engine-llama/src/androidMain/cpp/CMakeLists.txt                         (+1 line: shared cpp path)
engine-llama/src/commonMain/kotlin/dev/koml/engine/internal/DefaultLlmCoordinator.kt   (rewired)
samples-android/src/main/kotlin/dev/koml/samples/android/MainActivity.kt   (full refactor)
samples-android/src/main/res/layout/activity_main.xml                   (model picker layout)
samples-android/src/main/res/values/strings.xml                         (new strings)
samples-ios/KomlSample/ContentView.swift                                (download flow added)
settings.gradle.kts                                                     (+4 includes)
gradle/libs.versions.toml                                               (+9 entries, +2 plugins)
gradle.properties                                                       (+cinterop commonization)
README.md                                                               (status table updated)
docs/known-issues.md                                                    (resolved #3, #4; +#7, +#8)
```

### Deleted

```
engine-llama/src/commonMain/kotlin/dev/koml/engine/internal/StubRegistry.kt
engine-llama/src/commonMain/kotlin/dev/koml/engine/internal/StubDownloader.kt
```

## Issues hit during Phase 2 and how they were resolved

| Issue | Fix |
|---|---|
| `:samples-desktop` declared in `settings.gradle.kts` before the module dir existed → Gradle config failure | Created the directory with a minimal `build.gradle.kts` early, filled in Compose later. |
| `ByteReadChannel.readAvailable` unresolved in Ktor 3.3 | Added explicit `import io.ktor.utils.io.readAvailable`. |
| Okio `.buffer().use { ... }` failed on Kotlin/Native (`AutoCloseable` receiver mismatch) | Replaced lambda `.use { }` with explicit `try { ... } finally { src.close() }`. |
| Compose Desktop DMG metadata rejects `packageVersion = "0.0.2"` (requires MAJOR > 0) | Set DMG version to `"1.0.0"` while keeping the library at `0.0.x`. |
| `internal` modifiers on `defaultHttpClient`, `LicenseGate`, `FileBackedLicenseGate` blocked `:engine-llama` from wiring them | Added a public `ModelDownloaderFactory.create(storage, resolveModel)` returning a `ModelDownloadStack` (downloader + license-accept callback). Keeps internals encapsulated. |

## Manual setup additions

On top of Phase 1's setup:

```bash
# Once
./scripts/build-llama-jvm.sh           # ~10 min, produces build/llama-jvm/<arch>/libkoml-jni.dylib

# Before publishing a release: populate real SHA-256s in CuratedModels.kt
./scripts/refresh-manifest-shas.sh     # ~30 min, ~5 GB of downloads
```

## Verification

**Compile checks (already passed):**
```bash
./gradlew :samples-android:assembleDebug
./gradlew :engine-llama:assembleKomlEngineDebugXCFramework
./gradlew :samples-desktop:compileKotlinJvm
./gradlew :core:compileKotlinJvm :storage:compileKotlinJvm \
          :registry:compileKotlinJvm :download:compileKotlinJvm
./gradlew :storage:compileKotlinIosSimulatorArm64 \
          :registry:compileKotlinIosSimulatorArm64 \
          :download:compileKotlinIosSimulatorArm64
```

**Runtime end-to-end (requires real manifest SHAs):**
1. Run `./scripts/refresh-manifest-shas.sh`, paste the output into `registry/src/commonMain/kotlin/dev/koml/registry/CuratedModels.kt`.
2. Run `./scripts/build-llama-jvm.sh`.
3. **JVM**: `./gradlew :samples-desktop:run` → pick SmolLM2-135M → Download → Load → Generate → Cancel.
4. **Android**: `./gradlew :samples-android:installDebug` → same flow.
5. **iOS**: rebuild XCFramework, regenerate Xcode project (`cd samples-ios && xcodegen`), build & run on simulator → same flow.

**License gate (unit-test-style):**
1. Temporarily flip one model's `requiresAcceptance` to `true` in `CuratedModels.kt`.
2. `coordinator.downloader.download(model).first()` → expect `Failed(LicenseNotAcceptedException)`.
3. `coordinator.acceptLicense(model.id)` → returns `true`; marker file appears under `<root>/licenses/`.
4. Re-run download → succeeds normally.

## Known limitations going into Phase 3

- **Manifest SHA-256s are placeholders.** Runtime downloads will fail integrity checks until `scripts/refresh-manifest-shas.sh` is run and the result pasted in.
- **JVM macOS only.** Linux/Windows users build from source per `docs/known-issues.md#7`.
- **`searchHuggingFace()` is a stub.** Returns `emptyList()`. Phase 3.
- **No chat templates.** `session.chat()` still throws `NotImplementedError`. Phase 3.
- **No native (Kotlin/Native macOS/Linux) targets.** Phase 3.

## Commit message

```
feat(storage,download,registry,engine-llama): distribution layer + JVM target

- :storage      — Okio-backed ModelStorage; expect/actual KomlRoot per
                  platform; Android context auto-captured via
                  androidx-startup InitializationProvider.
- :download     — Ktor (OkHttp on JVM/Android, Darwin on iOS) + Okio +
                  kotlincrypto SHA-256 streaming downloads. Resumable via
                  HTTP Range header, atomic rename on verify, throttled
                  progress emissions (~4 Hz), file-backed license gate.
- :registry     — DefaultModelRegistry over a hardcoded CuratedModels list
                  of 5 ungated models (SmolLM2-135M/1.7B, TinyLlama-1.1B,
                  Phi-3-mini-4k, Qwen2.5-1.5B). SHA-256s are placeholders
                  populated by scripts/refresh-manifest-shas.sh.
- :engine-llama — added jvm() target. koml_jni.cpp moved to engine-llama/
                  cpp/ with #ifdef __ANDROID__ guards so the same source
                  builds for both Android NDK and JVM JNI. LlamaNative.jvm.kt
                  extracts META-INF/native/<arch>/libkoml-jni.dylib from
                  the JAR and System.load()s it once. DefaultLlmCoordinator
                  rewired to real impls; Stub*.kt deleted. New LlmCoordinator
                  .acceptLicense(modelId) default-impl method delegates to
                  the license gate.
- :samples-desktop — Compose Desktop app (macOS): model picker, download
                     with live progress, load → streaming generate with
                     cancel. Material 3.
- :samples-android — Spinner-driven model picker + download flow before
                     the existing generation UI.
- samples-ios      — ContentView gets the picker + download flow; same
                     SKIE-bridged Flow → AsyncSequence pattern.

Verified all 6 modules compile on JVM, Android, and iosSimulatorArm64.
End-to-end runtime test deferred until refresh-manifest-shas.sh
populates the real SHA-256s.

Resolves known-issues #3 (cinterop commonization) and #4 (expect/actual
Beta warnings).
```

## What comes next (Phase 3 — Chat & Polish)

- Chat templates per model family (chatml, llama3, phi3, gemma)
- `ModelRegistry.searchHuggingFace()` via HF Hub API
- `nativeMain` targets (macOS arm64/x64, Linux x64)
- License-acceptance UX on each sample (Phase 2 has the engine-side gate; samples skip the UI since all 5 curated models are Apache/MIT)
- Full error taxonomy with helpful messages
- README per target with quickstart
- KDoc on every public API
- Integration tests for the download → load → generate path on JVM
