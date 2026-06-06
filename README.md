# Koml

[![CI](https://github.com/debdutsaha/koml/actions/workflows/ci.yml/badge.svg?branch=main)](https://github.com/debdutsaha/koml/actions/workflows/ci.yml)
[![License](https://img.shields.io/badge/license-Apache%202.0-blue.svg)](LICENSE)
[![Kotlin](https://img.shields.io/badge/kotlin-2.2.10-blue.svg?logo=kotlin)](https://kotlinlang.org)

On-device LLM inference for Kotlin Multiplatform — a thin, idiomatic Kotlin wrapper over [llama.cpp](https://github.com/ggml-org/llama.cpp). Coroutines and Flow on the surface, GGUF and `llama.cpp` underneath. Works on Android, iOS, JVM desktop, and macOS native today.

> ⚠️ **Pre-1.0 — under active development.** APIs in `:core` are frozen. See [`docs/phase-4-summary.md`](docs/phase-4-summary.md) for the current state.

## Status

| Target | Engine | Streaming | Sample |
|---|---|---|---|
| Android (arm64-v8a) | ✅ JNI | ✅ Flow | ✅ `:samples-android` |
| iOS (arm64 device + simulator) | ✅ cinterop, CPU only ([#1](docs/known-issues.md)) | ✅ Flow → Swift `AsyncSequence` (SKIE) | ✅ `samples-ios/` |
| JVM desktop (macOS arm64 + x64) | ✅ JNI | ✅ Flow | ✅ `:samples-desktop` (Compose) |
| JVM desktop (Linux, Windows) | ⏳ build from source ([#7](docs/known-issues.md)) | — | — |
| Kotlin/Native (macOS arm64 + x64) | ✅ cinterop, Metal-accelerated | ✅ Flow | — (consume as a klib) |
| Kotlin/Native (Linux) | ⏳ v0.0.4 | — | — |

| Feature | Status |
|---|---|
| Flow-based streaming generation | ✅ |
| Per-session single-threaded dispatcher | ✅ |
| Cancellation via Flow / `Task.cancel()` | ✅ |
| Stop-sequence detection | ✅ |
| Model download with resume + SHA-256 | ✅ |
| Curated model registry (5 ungated models) | ✅ |
| License acceptance gate | ✅ |
| Platform-aware storage (`:storage`) | ✅ |
| Chat templates (chatml, llama3, phi3, gemma) | ✅ (v0.0.3) |
| Hugging Face Hub search (metadata-only) | ✅ (v0.0.3) |
| Hugging Face Hub search with file details | ✅ (v0.0.5) |
| Integration tests (MockEngine + FakeFileSystem) | ✅ (v0.0.3) |
| Real-GGUF end-to-end JVM test (opt-in) | ✅ (v0.0.5) |
| Dokka HTML docs site | ✅ (v0.0.5) |
| CI-driven publishing on `v*` tag push | ✅ (v0.0.5) |

## Quickstart per target

### Android

```kotlin
val coordinator = LlmKit.initialize()
val model = coordinator.registry.curated().first { it.id == "smollm2-135m-instruct-q8" }

// Stream download progress
coordinator.downloader.download(model).collect { state ->
    when (state) {
        is DownloadState.Progress  -> showProgress(state.bytesDownloaded, state.totalBytes)
        is DownloadState.Completed -> openChat(state.handle)
        is DownloadState.Failed    -> showError(state.error.message)
        DownloadState.Paused       -> Unit
    }
}

// Chat
val session = coordinator.loadModel(handle, RuntimeConfig(contextSize = 2048))
session.chat(
    messages = listOf(
        ChatMessage(ChatRole.System, "You are concise."),
        ChatMessage(ChatRole.User, "Why is the sky blue?"),
    ),
).collect { event ->
    if (event is TokenEvent.Token) print(event.text)
}
session.unload()
```

### iOS (SwiftUI)

```swift
let coordinator = try await LlmKit.shared.initialize(
    config: LlmKitConfig(maxConcurrentSessions: 1)
)
let models = try await coordinator.registry.curated() as? [ModelInfo] ?? []
let model = models.first(where: { $0.id == "smollm2-135m-instruct-q8" })!

for await state in coordinator.downloader.download(model: model) {
    switch onEnum(of: state) {
    case .progress(let p): print("\(p.bytesDownloaded)/\(p.totalBytes)")
    case .completed: break
    case .failed(let f): print(f.error.message ?? "")
    case .paused: break
    }
}
```

SwiftUI sample lives in [`samples-ios/`](samples-ios/) — generated via XcodeGen from `project.yml`.

### JVM desktop (Compose)

```bash
./gradlew :samples-desktop:run
```

Material 3 sample with model picker, live download progress, and a streaming chat screen with cancel. macOS arm64 + x64 only in v0.0.3.

### Kotlin/Native (macOS)

`engine-llama` exposes `macosArm64` and `macosX64` targets producing klibs you can consume from a Kotlin/Native CLI:

```kotlin
// in your Native CLI's commonMain
implementation("io.github.12345debdut:engine-llama:0.0.3")

fun main() = runBlocking {
    val coordinator = LlmKit.initialize()
    val handle = coordinator.downloader.localModels().first()
    val session = coordinator.loadModel(handle, RuntimeConfig(contextSize = 2048))
    session.generate("Explain Kotlin coroutines in one paragraph.")
        .collect { event ->
            if (event is TokenEvent.Token) print(event.text)
        }
}
```

The macOS native target uses Metal acceleration (no b5460 embed bug here, only iOS hits that). Run `scripts/build-llama-macos-native.sh` once to produce `build/llama-macos-native/<arch>/lib/libkoml-llama.a` before linking.

## Repository layout

```
:core               Public API (interfaces, data classes, sealed classes, KomlException, ModelStorage)
:engine-llama       KMP engine: LlmKit, DefaultLlmSession, expect/actual LlamaNative, chat templates
:storage            Platform-aware filesystem paths (Android filesDir, iOS Documents, JVM ~/.koml/)
:download           Ktor + Okio resumable downloads, SHA-256 verify, license gate
:registry           Bundled JSON manifest of 5 curated models + HuggingFace Hub search
:samples-android    Android sample (Views + lifecycleScope)
:samples-desktop    Compose Desktop sample (macOS)
samples-ios/        SwiftUI sample (XcodeGen project)
scripts/            Build helpers (build-llama-ios.sh, build-llama-jvm.sh, build-llama-macos-native.sh, refresh-manifest-shas.sh)
external/llama.cpp  Vendored as a git submodule, pinned to b5460
docs/               Phase summaries, known issues, release notes
```

## Setup

See [`docs/phase-3-summary.md`](docs/phase-3-summary.md) for the full bootstrap. TL;DR:

```bash
# One-time
git submodule update --init --recursive
brew install xcodegen cmake

# Static libs (each ~10 min)
./scripts/build-llama-ios.sh             # iOS arm64 + simulator
./scripts/build-llama-jvm.sh             # macOS arm64 + x64 JNI dylibs
./scripts/build-llama-macos-native.sh    # macOS arm64 + x64 Kotlin/Native cinterop

# Real SHAs for the curated manifest (~30 min, ~5 GB)
./scripts/refresh-manifest-shas.sh

# Build samples
./gradlew :samples-android:assembleDebug
./gradlew :engine-llama:assembleKomlEngineDebugXCFramework
./gradlew :samples-desktop:run

# Tests
./gradlew :download:jvmTest :engine-llama:jvmTest

# iOS Xcode project
cd samples-ios && xcodegen && open KomlSample.xcodeproj
```

## Roadmap

| Phase | Scope | Status |
|---|---|---|
| 0 | JNI proof of concept (Android) | ✅ Done |
| 1 | Full KMP engine, iOS bindings, streaming API | ✅ Done |
| 2 | JVM desktop, registry, downloader, storage, license gate | ✅ Done |
| 3 | Chat templates, HF search, native targets, error+KDoc polish, tests | ✅ Done |
| 4 | Maven Central publishing, CI, contributing docs | ✅ Done |
| 4.5 | Pre-1.0 polish: HF withFileDetails, Dokka, stop-seq tests, integration test, CI publish | ✅ Done (v0.0.5) |
| — | First Central publish + community-driven iteration | ⏳ Next |

## License

[Apache License 2.0](LICENSE). llama.cpp is MIT-licensed and vendored as a submodule.

## Contributing

See [CONTRIBUTING.md](CONTRIBUTING.md) for dev setup, conventions, and what makes a good PR. By participating in any project space you agree to the [Code of Conduct](CODE_OF_CONDUCT.md).

Maintainer-only: see [docs/PUBLISHING.md](docs/PUBLISHING.md) for the Maven Central release flow.
