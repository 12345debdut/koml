# Koml

[![CI](https://github.com/12345debdut/koml/actions/workflows/ci.yml/badge.svg?branch=main)](https://github.com/12345debdut/koml/actions/workflows/ci.yml)
[![License](https://img.shields.io/badge/license-Apache%202.0-blue.svg)](LICENSE)
[![Kotlin](https://img.shields.io/badge/kotlin-2.2.10-blue.svg?logo=kotlin)](https://kotlinlang.org)
[![Maven Central](https://img.shields.io/maven-central/v/io.github.12345debdut/engine-llama?label=Maven%20Central)](https://central.sonatype.com/artifact/io.github.12345debdut/engine-llama)

> **On-device LLM inference for Kotlin Multiplatform.** A thin, idiomatic wrapper over [llama.cpp](https://github.com/ggml-org/llama.cpp). Coroutines and Flow on the surface, GGUF and `llama.cpp` underneath. Same code runs on Android, iOS, JVM desktop, and macOS native.

```kotlin
val coordinator = LlmKit.initialize()
val handle = coordinator.downloader
    .download(coordinator.registry.curated().first())
    .filterIsInstance<DownloadState.Completed>()
    .first()
    .handle

coordinator.loadModel(handle).chat(
    listOf(
        ChatMessage(ChatRole.System, "You are concise."),
        ChatMessage(ChatRole.User, "Why is the sky blue?"),
    )
).collect { event ->
    if (event is TokenEvent.Token) print(event.text)
}
```

That's the whole API. Same shape on Android, iOS (via SKIE), JVM, and Kotlin/Native.

---

- [Why Koml](#why-koml)
- [Install](#install)
- [Quickstart](#quickstart)
- [Platforms](#platform-support)
- [Features](#features)
- [Per-target setup](#per-target-setup)
- [API documentation](#api-documentation)
- [Local development](#local-development)
- [Roadmap](#roadmap)
- [Contributing](#contributing)
- [License](#license)

## Why Koml

There's an existing C++ runtime — [llama.cpp](https://github.com/ggml-org/llama.cpp) — that already solves the hard part: loading quantised transformers from GGUF, doing the math fast on every platform, managing KV cache. What's been missing is a **Kotlin-shaped seam** over it: one that feels native to the idioms Kotlin developers already use, doesn't leak C-level abstractions, and works the same way on every target without per-platform forking.

That's what Koml is.

- **Streaming as a `Flow<TokenEvent>`.** Cancel the collecting coroutine and generation stops at the next sampling iteration. No threads to manage.
- **One API, four targets.** Identical entry point on Android, iOS, JVM desktop, and macOS Kotlin/Native — the only thing that changes per platform is how you obtain a Swift / Java consumer instance.
- **Curated model registry.** Five vetted ungated GGUF models bundled in (SmolLM2, TinyLlama, Phi-3-mini, Qwen 2.5). HF Hub search for everything else.
- **Resumable, verified downloads.** SHA-256 verify-as-you-write, atomic rename on success, HTTP Range-based resume across process restarts.
- **Per-model chat templates.** ChatML, Llama 3, Phi-3, Gemma — each rendered from the spec on each model's HuggingFace card.
- **Boring and correct.** Sealed error types, default-impl interface methods for backward compat, every public type has KDoc.

## Install

```kotlin
// build.gradle.kts
dependencies {
    implementation("io.github.12345debdut:engine-llama:0.0.5")
}
```

The `engine-llama` artifact transitively brings in `:core`, `:storage`, `:download`, and `:registry` — you only need this one line.

**Multiplatform projects:** put the dependency on the right source set. The KMP `engine-llama` publication auto-resolves to the per-target artifact (`engine-llama-jvm`, `engine-llama-iosarm64`, etc.) when consumed from a Kotlin Multiplatform consumer.

```kotlin
kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation("io.github.12345debdut:engine-llama:0.0.5")
        }
    }
}
```

**iOS via XCFramework:** the iOS publishing path uses an XCFramework rather than CocoaPods or SPM in v0.0.5. See [Per-target setup → iOS](#ios-swiftui) below.

## Quickstart

The end-to-end happy path on any platform — Android, JVM, macOS Native — looks identical:

```kotlin
import dev.koml.engine.LlmKit
import dev.koml.core.config.RuntimeConfig
import dev.koml.core.download.DownloadState
import dev.koml.core.session.*
import kotlinx.coroutines.flow.*

suspend fun runIt() {
    // 1. Wire up the coordinator (storage, registry, downloader, license gate).
    val coordinator = LlmKit.initialize()

    // 2. Pick a model from the curated registry.
    val model = coordinator.registry.curated()
        .first { it.id == "smollm2-135m-instruct-q8" }

    // 3. Download (resumable, SHA-256 verified, ~145 MB for SmolLM2-135M).
    val handle = coordinator.downloader.download(model)
        .onEach { state ->
            if (state is DownloadState.Progress) {
                println("${state.bytesDownloaded} / ${state.totalBytes}")
            }
        }
        .filterIsInstance<DownloadState.Completed>()
        .first()
        .handle

    // 4. Load (one session per loaded model; serialised internally).
    val session = coordinator.loadModel(handle, RuntimeConfig(contextSize = 2048))

    // 5. Stream tokens. session.chat() picks the right template from the model
    //    (here: ChatML for SmolLM2). session.generate() takes a raw prompt.
    session.chat(
        listOf(
            ChatMessage(ChatRole.System, "You are concise. One sentence answers."),
            ChatMessage(ChatRole.User, "Why is the sky blue?"),
        ),
        GenParams(maxTokens = 128),
    ).collect { event ->
        when (event) {
            is TokenEvent.Token -> print(event.text)
            is TokenEvent.Done  -> println(
                "\n[${event.reason}, ${event.stats.generatedTokens} tokens, " +
                "%.1f tok/s]".format(event.stats.tokensPerSecond)
            )
            is TokenEvent.Error -> println("\nError: ${event.cause.message}")
        }
    }

    // 6. Release the native model + context.
    session.unload()
}
```

That's it. Cancellation is automatic via Flow — drop the collecting coroutine and generation stops at the next iteration.

## Platform support

| Target | Status | Acceleration | Sample |
|---|---|---|---|
| **Android** arm64-v8a | ✅ Production-ready | CPU | [`samples-android`](samples-android/) |
| **iOS** arm64 (device + simulator) | ✅ Production-ready | CPU + Accelerate BLAS | [`samples-ios`](samples-ios/) |
| **iOS** x86_64 simulator | ✅ Production-ready | CPU | (same sample) |
| **JVM desktop** (macOS arm64) | ✅ Production-ready | CPU + Accelerate BLAS | [`samples-desktop`](samples-desktop/) (Compose) |
| **JVM desktop** (macOS x64) | ✅ Production-ready | CPU + Accelerate BLAS | (same sample) |
| **Kotlin/Native** (macOS arm64) | ✅ Production-ready | CPU + Accelerate BLAS | — |
| **Kotlin/Native** (macOS x64) | ✅ Production-ready | CPU + Accelerate BLAS | — |
| JVM desktop (Linux, Windows) | ⏳ Build from source | — | — |
| Kotlin/Native (Linux) | ⏳ Post-1.0 | — | — |
| Android (other ABIs) | ⏳ Post-1.0 | — | — |

> Metal acceleration is currently disabled on all macOS Apple targets due to an upstream b5460 embed bug — see [known issue #1](docs/known-issues.md). CPU + Accelerate is still very fast for the small models we ship; expect ~10–40 tok/s for SmolLM2-135M on Apple Silicon.

## Features

| Feature | Since |
|---|---|
| Flow-based streaming generation | v0.0.1 |
| Per-session single-threaded dispatcher | v0.0.1 |
| Cancellation via Flow / `Task.cancel()` | v0.0.1 |
| Stop-sequence detection | v0.0.1 (rewritten with edge-case tests in v0.0.5) |
| Model download with resume + SHA-256 verify | v0.0.2 |
| Curated model registry (5 ungated models) | v0.0.2 |
| License acceptance gate | v0.0.2 |
| Platform-aware storage (`:storage`) | v0.0.2 |
| Chat templates (ChatML, Llama 3, Phi-3, Gemma) | v0.0.3 |
| Hugging Face Hub search (metadata-only) | v0.0.3 |
| Hugging Face Hub search with file details | v0.0.5 |
| Integration tests (MockEngine + FakeFileSystem) | v0.0.3 |
| Real-GGUF end-to-end JVM test (opt-in) | v0.0.5 |
| Dokka HTML documentation site | v0.0.5 |
| Maven Central publishing | v0.0.5 |
| CI-driven publishing on `v*` tag push | v0.0.5 |

## Curated models

```kotlin
coordinator.registry.curated()
```

returns five vetted, ungated, permissively-licensed models:

| ID | Size | License | Context | RAM | Template |
|---|---|---|---|---|---|
| `smollm2-135m-instruct-q8` | ~145 MB | Apache-2.0 | 2048 | 256 MB | ChatML |
| `smollm2-1.7b-instruct-q4km` | ~1.0 GB | Apache-2.0 | 2048 | 1.5 GB | ChatML |
| `tinyllama-1.1b-chat-q4km` | ~668 MB | Apache-2.0 | 2048 | 1.0 GB | ChatML |
| `phi-3-mini-4k-instruct-q4` | ~2.4 GB | MIT | 4096 | 3.0 GB | Phi-3 |
| `qwen2.5-1.5b-instruct-q4km` | ~986 MB | Apache-2.0 | 32768 | 1.5 GB | ChatML |

For anything else, search HuggingFace:

```kotlin
// Cheap: metadata only (~1 API call)
val results = coordinator.registry.searchHuggingFace("smollm2")

// Pricier but each result is directly downloadable (~N+1 API calls — one
// per result to fetch its file list and SHA-256)
val downloadable = coordinator.registry.searchHuggingFaceWithDetails("smollm2")
```

## Per-target setup

### Android

```kotlin
// build.gradle.kts
dependencies {
    implementation("io.github.12345debdut:engine-llama:0.0.5")
}
```

The library auto-registers an [androidx-startup](https://developer.android.com/topic/libraries/app-startup) `Initializer` that captures the application context — **you don't need to thread `Context` through `LlmKit`**. Models land in `<context.filesDir>/koml/models/`.

Full Views-based sample: [`samples-android`](samples-android/).

### iOS (SwiftUI)

Koml's KMP iOS targets ship as an XCFramework consumed via SKIE for idiomatic Swift bridging. Kotlin `Flow` becomes Swift `AsyncSequence`; sealed classes get exhaustive `switch` via `onEnum(of:)`.

```swift
import KomlEngine

let coordinator = try await LlmKit.shared.initialize(
    config: LlmKitConfig(maxConcurrentSessions: 1)
)
let models = try await coordinator.registry.curated() as? [ModelInfo] ?? []
let model = models.first { $0.id == "smollm2-135m-instruct-q8" }!

let flow = coordinator.downloader.download(model: model)
for await state in flow {
    switch onEnum(of: state) {
    case .progress(let p): print("\(p.bytesDownloaded)/\(p.totalBytes)")
    case .completed(let c): /* … load & generate … */ break
    case .failed(let f):   print(f.error.message ?? "")
    case .paused: break
    }
}
```

Full SwiftUI sample: [`samples-ios`](samples-ios/) — Xcode project generated declaratively from `project.yml` via XcodeGen.

### JVM desktop (Compose)

```bash
./gradlew :samples-desktop:run
```

Material 3 sample with model picker, live download progress, and streaming chat with cancel. macOS arm64 + x64. Both Apple Silicon and Intel Macs.

To consume from your own desktop app:

```kotlin
dependencies {
    implementation("io.github.12345debdut:engine-llama:0.0.5")
}
```

The native JNI lib for both macOS archs is packaged inside the JAR under `META-INF/native/macos-{arm64,x64}/`; `LlamaNative` extracts the right slice to a temp dir and `System.load`s it once per JVM process.

### Kotlin/Native (macOS)

Both `macosArm64` and `macosX64` klibs are published. Use from a Kotlin/Native CLI:

```kotlin
// build.gradle.kts (Kotlin/Native consumer)
kotlin {
    macosArm64()
    sourceSets {
        macosMain.dependencies {
            implementation("io.github.12345debdut:engine-llama:0.0.5")
        }
    }
}
```

One-time setup if you also want to build Koml itself from source: `./scripts/build-llama-macos-native.sh` produces `build/llama-macos-native/<arch>/lib/libkoml-llama.a`.

## API documentation

- **API reference (Dokka HTML site):** [https://12345debdut.github.io/koml/](https://12345debdut.github.io/koml/) (auto-published from `main` via `.github/workflows/docs.yml`)
- **Versioning policy:** [`docs/VERSIONING.md`](docs/VERSIONING.md)
- **Known issues:** [`docs/known-issues.md`](docs/known-issues.md)
- **Per-phase journey:** [`docs/phase-1-summary.md`](docs/phase-1-summary.md) through [`docs/phase-4-summary.md`](docs/phase-4-summary.md)
- **Release notes:** [`docs/releases/`](docs/releases/)
- **Publishing flow (maintainers):** [`docs/PUBLISHING.md`](docs/PUBLISHING.md)

## Local development

Only needed if you're building Koml itself (not consuming it via Maven).

### Prerequisites

| Tool | Version | Why |
|---|---|---|
| JDK | 17 (Temurin recommended) | Gradle + Kotlin toolchain |
| Android Studio | Otter 3 (2025.2.3)+ | NDK + AGP 9 |
| Android NDK | 27.x | C++ build for Android |
| Android CMake | 3.22.1 | NDK external native build |
| Xcode | 16.x | iOS framework / XCFramework |
| Homebrew | — | for `cmake` (host) + `xcodegen` |

### Bootstrap

```bash
# 1. Clone with the llama.cpp submodule
git clone --recurse-submodules https://github.com/12345debdut/koml.git
cd koml

# 2. Host tooling
brew install cmake xcodegen

# 3. Local SDK path
echo "sdk.dir=$HOME/Library/Android/sdk" > local.properties

# 4. Static libs — needed only if working on the engine itself.
#    Each ~10 min; skip the ones for targets you're not building.
./scripts/build-llama-ios.sh             # iOS arm64 device + simulators
./scripts/build-llama-jvm.sh             # macOS arm64 + x64 JNI dylibs
./scripts/build-llama-macos-native.sh    # macOS arm64 + x64 Kotlin/Native

# 5. Build + test
./gradlew assemble
./gradlew :download:jvmTest :engine-llama:jvmTest      # ~3 sec

# 6. Run any sample
./gradlew :samples-android:assembleDebug
./gradlew :samples-desktop:run
cd samples-ios && xcodegen && open KomlSample.xcodeproj
```

### Real-GGUF integration test (opt-in)

Catches "the mocks all pass but llama.cpp doesn't actually decode" regressions. Real download from HF, real model load, real generation. Skipped by default:

```bash
KOML_INTEGRATION_TESTS=1 ./gradlew :engine-llama:jvmTest \
    --tests "dev.koml.engine.integration.*"
```

~2 min, ~145 MB of network. Requires `./scripts/build-llama-jvm.sh` to have been run.

## Repository layout

```
:core               Public API — interfaces, data classes, sealed types
:engine-llama       KMP engine: LlmKit, DefaultLlmSession, LlamaNative, chat templates
:storage            Platform-aware filesystem paths
:download           Ktor + Okio resumable downloads + SHA-256 + license gate
:registry           Bundled curated manifest + HF Hub search
:samples-android    Android sample (Views + lifecycleScope)
:samples-desktop    Compose Desktop sample (macOS)
samples-ios/        SwiftUI sample (XcodeGen-generated Xcode project)
scripts/            Native lib build helpers
external/llama.cpp  Vendored submodule, pinned to b5460
docs/               Phase summaries, known issues, release notes
```

## Roadmap

| Version | Scope | Status |
|---|---|---|
| v0.0.1 | KMP engine + iOS bindings + streaming API | ✅ Shipped |
| v0.0.2 | JVM desktop + registry + downloader + storage + license gate | ✅ Shipped |
| v0.0.3 | Chat templates + HF search + Kotlin/Native macOS + KDoc + tests | ✅ Shipped |
| v0.0.4 | (Skipped — became the publishing-infra dry-run) | — |
| **v0.0.5** | **First Maven Central release. HF withFileDetails, Dokka site, CI publish.** | ✅ Shipped |
| v0.1.0 | First feedback-shaped release (ergonomics fixes from community use) | ⏳ |
| v1.0.0 | API frozen for the lifetime of 1.x | ⏳ |

Beyond v1.0, Linux JVM/Native and Windows JVM coverage are tracked but not on the critical path. See [`docs/VERSIONING.md`](docs/VERSIONING.md) for the SemVer policy.

## Contributing

See [CONTRIBUTING.md](CONTRIBUTING.md) for the dev setup matrix, branching, conventional commits, and what makes a good PR. By participating you agree to the [Code of Conduct](CODE_OF_CONDUCT.md).

If you've used Koml and something feels awkward — **now is the time to file it**. Once we cut v1.0 the public API is frozen.

## License

[Apache License 2.0](LICENSE). llama.cpp is MIT-licensed and vendored as a submodule.

---

Built by [@12345debdut](https://github.com/12345debdut). Maven Central: `io.github.12345debdut`.
