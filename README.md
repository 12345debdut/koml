# Koml

On-device LLM inference for Kotlin Multiplatform — a thin, idiomatic Kotlin wrapper over [llama.cpp](https://github.com/ggml-org/llama.cpp). Coroutines and Flow on the surface, GGUF and `llama.cpp` underneath. Works on Android, iOS, JVM desktop, and native (macOS / Linux) targets.

> ⚠️ **Pre-1.0 — under active development.** APIs are stable in the public surface but implementations are still landing phase by phase. See [`docs/phase-1-summary.md`](docs/phase-1-summary.md) for the current state.

## Status

| Target | Engine | Streaming | Sample |
|---|---|---|---|
| Android | ✅ (JNI) | ✅ Flow | ✅ `:samples-android` |
| iOS | ✅ (cinterop, CPU only — see [#1](docs/known-issues.md#1-metal-disabled-on-ios)) | ✅ Flow → Swift `AsyncSequence` via SKIE | ✅ `samples-ios/` |
| JVM desktop | ⏳ Phase 2 | — | ⏳ |
| Native (macOS, Linux) | ⏳ Phase 3 | — | — |

| Feature | Status |
|---|---|
| Flow-based streaming generation | ✅ |
| Per-session single-threaded dispatcher | ✅ |
| Cancellation via Flow / `Task.cancel()` | ✅ |
| Stop-sequence detection | ✅ |
| Model download with resume + SHA-256 | ⏳ Phase 2 |
| Curated model registry | ⏳ Phase 2 |
| Hugging Face Hub search | ⏳ Phase 3 |
| Chat templates (chatml, llama3, phi3, gemma) | ⏳ Phase 3 |
| License acceptance flow | ⏳ Phase 3 |

## Quickstart — Android

```kotlin
val coordinator = LlmKit.initialize()
val session = coordinator.loadModel(
    ModelHandle(info = …, localPath = "/path/to/model.gguf"),
    RuntimeConfig(contextSize = 2048),
)

session.generate(
    prompt = "The capital of France is",
    params = GenParams(maxTokens = 64),
).collect { event ->
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
let session = try await coordinator.loadModel(
    handle: ModelHandle(info: …, localPath: modelPath),
    runtime: RuntimeConfig(contextSize: 2048, threads: 4, gpuLayers: 0)
)
let flow = session.generate(prompt: "The capital of France is",
                            params: GenParams(maxTokens: 64, …))

for await event in flow {
    switch onEnum(of: event) {
    case .token(let token):  output += token.text
    case .done(let done):    print("\n[\(done.reason), \(done.stats.tokensPerSecond) tok/s]")
    case .error(let err):    print("\nError: \(err.cause.message ?? "")")
    }
}
try? await session.unload()
```

SwiftUI sample lives in [`samples-ios/`](samples-ios/) — generated via XcodeGen from `project.yml`.

## Repository layout

```
:core               public API (interfaces, data classes, sealed classes, KomlException)
:engine-llama       KMP engine: LlmKit, DefaultLlmSession, expect/actual LlamaNative
:samples-android    Android sample (Views + lifecycleScope)
samples-ios/        SwiftUI sample (XcodeGen project)
scripts/            Build helpers (e.g. build-llama-ios.sh)
external/llama.cpp  Vendored as a git submodule, pinned to b5460
docs/               Phase summaries, known issues
```

## Setup

See [`docs/phase-1-summary.md`](docs/phase-1-summary.md#manual-setup-for-next-session--new-contributor) for the full bootstrap. TL;DR:

```bash
# Submodule + tooling (one-time)
git submodule update --init --recursive
brew install xcodegen cmake

# iOS-only: build llama.cpp static libs (~10 min)
./scripts/build-llama-ios.sh

# Build
./gradlew :samples-android:assembleDebug
./gradlew :engine-llama:assembleKomlEngineDebugXCFramework

# iOS app
cd samples-ios && xcodegen && open KomlSample.xcodeproj
```

## Roadmap

| Phase | Scope | Status |
|---|---|---|
| 0 | JNI proof of concept (Android) | ✅ Done |
| 1 | Full KMP engine, iOS bindings, streaming API | ✅ Done |
| 2 | JVM desktop, registry, downloader, storage | ⏳ Next |
| 3 | Chat templates, HF search, native targets, polish | ⏳ |
| 4 | Maven Central publishing, CI, contributing docs | ⏳ |

## License

Apache 2.0 (to be added in Phase 4). llama.cpp is MIT-licensed.
