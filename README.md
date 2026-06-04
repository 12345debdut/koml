# Koml

On-device LLM inference for Kotlin Multiplatform — a thin, idiomatic Kotlin wrapper over [llama.cpp](https://github.com/ggml-org/llama.cpp). Coroutines and Flow on the surface, GGUF and `llama.cpp` underneath. Works on Android, iOS, and JVM desktop today; macOS / Linux native binaries land later.

> ⚠️ **Pre-1.0 — under active development.** APIs in `:core` are stable. See [`docs/phase-2-summary.md`](docs/phase-2-summary.md) for the current state.

## Status

| Target | Engine | Streaming | Sample |
|---|---|---|---|
| Android | ✅ (JNI) | ✅ Flow | ✅ `:samples-android` |
| iOS | ✅ (cinterop, CPU only — see [#1](docs/known-issues.md#1-metal-disabled-on-ios)) | ✅ Flow → Swift `AsyncSequence` via SKIE | ✅ `samples-ios/` |
| JVM desktop (macOS arm64 + x64) | ✅ (JNI) | ✅ Flow | ✅ `:samples-desktop` (Compose) |
| JVM desktop (Linux, Windows) | ⏳ build from source — see [#7](docs/known-issues.md#7-jvm-platform-coverage-macos-only-in-v002) | — | — |
| Native (macOS, Linux) | ⏳ Phase 3 | — | — |

| Feature | Status |
|---|---|
| Flow-based streaming generation | ✅ |
| Per-session single-threaded dispatcher | ✅ |
| Cancellation via Flow / `Task.cancel()` | ✅ |
| Stop-sequence detection | ✅ |
| Model download with resume + SHA-256 | ✅ (v0.0.2) |
| Curated model registry (5 ungated models) | ✅ (v0.0.2) |
| License acceptance gate | ✅ (v0.0.2) |
| Platform-aware storage (`:storage`) | ✅ (v0.0.2) |
| Hugging Face Hub search | ⏳ Phase 3 |
| Chat templates (chatml, llama3, phi3, gemma) | ⏳ Phase 3 |

## Quickstart — Android / JVM (Kotlin)

```kotlin
val coordinator = LlmKit.initialize()

// Browse and pick a model from the bundled curated manifest.
val model = coordinator.registry.curated().first { it.id == "smollm2-135m-instruct-q8" }

// Download it (Flow of progress events; safe to cancel).
val handle: ModelHandle = coordinator.downloader.download(model)
    .filterIsInstance<DownloadState.Completed>()
    .first()
    .handle

// Load and generate.
val session = coordinator.loadModel(handle, RuntimeConfig(contextSize = 2048))
session.generate("The capital of France is", GenParams(maxTokens = 64)).collect { event ->
    when (event) {
        is TokenEvent.Token -> print(event.text)
        is TokenEvent.Done  -> println("\n[${event.reason}, ${event.stats.tokensPerSecond} tok/s]")
        is TokenEvent.Error -> println("\nError: ${event.cause.message}")
    }
}
session.unload()
```

## Quickstart — iOS (SwiftUI)

```swift
let coordinator = try await LlmKit.shared.initialize(
    config: LlmKitConfig(maxConcurrentSessions: 1)
)
let models = try await coordinator.registry.curated() as? [ModelInfo] ?? []
let model = models.first { $0.id == "smollm2-135m-instruct-q8" }!

let flow = coordinator.downloader.download(model: model)
for await state in flow {
    switch onEnum(of: state) {
    case .progress(let p): print("\(p.bytesDownloaded)/\(p.totalBytes)")
    case .completed(let c): /* load and generate */ break
    case .failed(let f):   print(f.error.message ?? "")
    case .paused: break
    }
}
```

SwiftUI sample lives in [`samples-ios/`](samples-ios/) — generated via XcodeGen from `project.yml`.

## Repository layout

```
:core               public API (interfaces, data classes, sealed classes, KomlException)
:engine-llama       KMP engine: LlmKit, DefaultLlmSession, expect/actual LlamaNative
:storage            platform-aware filesystem paths (Android filesDir, iOS Documents, JVM ~/.koml/)
:download           Ktor + Okio resumable downloads, SHA-256 verify, license gate
:registry           bundled JSON manifest of 5 curated models
:samples-android    Android sample (Views + lifecycleScope)
:samples-desktop    Compose Desktop sample (macOS)
samples-ios/        SwiftUI sample (XcodeGen project)
scripts/            Build helpers (build-llama-ios.sh, build-llama-jvm.sh, refresh-manifest-shas.sh)
external/llama.cpp  Vendored as a git submodule, pinned to b5460
docs/               Phase summaries, known issues, release notes
```

## Setup

See [`docs/phase-2-summary.md`](docs/phase-2-summary.md) for the full bootstrap. TL;DR:

```bash
# One-time
git submodule update --init --recursive
brew install xcodegen cmake

# iOS static libs (~10 min)
./scripts/build-llama-ios.sh

# JVM macOS static libs (~10 min)
./scripts/build-llama-jvm.sh

# Refresh curated-manifest SHA-256s (downloads ~5 GB)
./scripts/refresh-manifest-shas.sh

# Build samples
./gradlew :samples-android:assembleDebug
./gradlew :engine-llama:assembleKomlEngineDebugXCFramework
./gradlew :samples-desktop:run                                  # Compose Desktop

# iOS Xcode project
cd samples-ios && xcodegen && open KomlSample.xcodeproj
```

## Roadmap

| Phase | Scope | Status |
|---|---|---|
| 0 | JNI proof of concept (Android) | ✅ Done |
| 1 | Full KMP engine, iOS bindings, streaming API | ✅ Done |
| 2 | JVM desktop, registry, downloader, storage, license gate | ✅ Done |
| 3 | Chat templates, HF search, native targets, polish | ⏳ Next |
| 4 | Maven Central publishing, CI, contributing docs | ⏳ |

## License

Apache 2.0 (to be added in Phase 4). llama.cpp is MIT-licensed.
