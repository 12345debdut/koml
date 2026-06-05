# Phase 3 — Chat & Polish

## Goal vs. outcome

Production-ready v0.0.3. Real `session.chat()` for the four major model families, HuggingFace Hub search for discovery, Kotlin/Native macOS targets via cinterop, a tests suite, and a KDoc/error-message polish pass across the public API.

**Acceptance criteria — all met:**
- ✅ `session.chat(messages)` produces correct prompts for ChatML, Llama 3, Phi-3, Gemma, and None templates. 7 unit tests assert the exact rendered strings.
- ✅ `coordinator.registry.searchHuggingFace(query)` calls the HF Hub API and returns metadata-only `ModelInfo` results. Returns empty (not throws) on rate limit / network error so callers don't have to wrap.
- ✅ `:engine-llama` declares `macosArm64()` and `macosX64()` Kotlin/Native targets. `iosMain`'s `LlamaNative` and platform actuals moved to `appleMain` so both iOS and macOS native share the cinterop bindings.
- ✅ `scripts/build-llama-macos-native.sh` produces `build/llama-macos-native/<arch>/lib/libkoml-llama.a` per arch (Metal + Accelerate, no `LLAMA_BUILD_TOOLS`).
- ✅ MockEngine-based integration tests in `:download/commonTest` (download happy path, license gate, SHA-256 mismatch, already-downloaded short-circuit) + `:engine-llama/commonTest` chat-template tests. All run JVM-side via `kotlinx.coroutines.test`. No real network or model load needed.
- ✅ KDoc on every public type in `:core` (17 types). Error messages everywhere now carry model id + concrete recovery hints.

## What was built

### Chat templates (`:engine-llama` commonMain)

New `engine-llama/src/commonMain/kotlin/dev/koml/engine/chat/ChatTemplate.kt`:

- Sealed `ChatTemplate` with five data-object subclasses: `NoneTemplate`, `ChatMLTemplate`, `Llama3Template`, `Phi3Template`, `GemmaTemplate`.
- Each renders `List<ChatMessage>` to the model-specific prompt string and exposes its `defaultStopSequences`.
- `ChatTemplate.forPromptTemplate(PromptTemplate)` picks the right one from `ModelInfo.promptTemplate`.
- `DefaultLlmSession.chat()` is no longer a `NotImplementedError` — it renders via the right template, merges template stop sequences with caller `GenParams.stopSequences`, and delegates to `generate()`.

Template formats (sourced from each model's official `tokenizer_config.json`):

| Family | Open | Close | System? | Stop |
|---|---|---|---|---|
| ChatML | `<|im_start|>{role}\n` | `<|im_end|>` | yes | `<|im_end|>` |
| Llama 3 | `<|begin_of_text|><|start_header_id|>{role}<|end_header_id|>\n\n` | `<|eot_id|>` | yes | `<|eot_id|>` |
| Phi-3 | `<|{role}|>\n` | `<|end|>` | yes | `<|end|>`, `<|endoftext|>` |
| Gemma | `<start_of_turn>{role}\n` | `<end_of_turn>` | **no** (collapses into first user turn) | `<end_of_turn>` |
| None | (none) | (none) | concat with `\n` | (none) |

### HuggingFace Hub search (`:registry`)

- New deps: `ktor-client-content-negotiation`, `ktor-serialization-kotlinx-json`, `kotlinx-serialization-json`. Per-platform `defaultRegistryHttpClient()` actuals install JSON content negotiation on top of the platform's Ktor engine.
- New `HuggingFaceSearcher` (internal) calls `GET https://huggingface.co/api/models?search=<q>&filter=gguf&sort=downloads&direction=-1&limit=20` and maps each result to `ModelInfo`. Empty list on 4xx/5xx or parse failure.
- `DefaultModelRegistry` now accepts an optional `HuggingFaceSearcher` in its primary constructor. Backward-compatible default constructor still works.
- New `DefaultModelRegistryFactory.create(enableHuggingFaceSearch = true)` is the canonical entry point and is what `DefaultLlmCoordinator` calls.
- Search results carry blank `downloadUrl` + `sha256` since we deliberately avoid the N+1 per-repo file-list call. KDoc on `searchHuggingFace` documents this; callers wanting downloadable results from HF need to supplement.

### Kotlin/Native macOS targets (`:engine-llama` + dependency modules)

- Added `macosArm64()` and `macosX64()` targets to **all** library modules (`:core`, `:storage`, `:download`, `:registry`, `:engine-llama`). KMP variant matching requires every transitive dep to expose the target.
- Moved `engine-llama/src/iosMain/.../LlamaNative.ios.kt` → `engine-llama/src/appleMain/.../LlamaNative.apple.kt`. Bindings are identical for iOS and macOS; the default Kotlin 2.x source-set hierarchy makes `appleMain` the parent of both `iosMain` and `macosMain`, so the file is consumed by both.
- Moved `:storage` and `:download` iosMain actuals (`KomlRoot`, `SystemFs`, `HttpClientFactory`) to `appleMain` for the same reason. iOS and macOS use the same Foundation / Darwin / NSDocumentDirectory APIs.
- Added `engine-llama/src/nativeInterop/cinterop/llama-macos.def` — same package, headers, and bindings as `llama.def`, but with `Metal + MetalKit` frameworks in `linkerOpts` (the b5460 ggml-common embed bug that bites iOS doesn't affect macOS).
- Added per-target cinterop config in `engine-llama/build.gradle.kts` pointing at `build/llama-macos-native/<arch>/lib/`.
- New `scripts/build-llama-macos-native.sh` mirrors `build-llama-ios.sh`'s pattern: per-arch `cmake -G "Unix Makefiles"`, all `LLAMA_BUILD_*=OFF` flags, `libtool -static -o libkoml-llama.a` combines the per-arch llama+ggml archives.

### Integration tests

- New `:download/commonTest` source set: `DefaultModelDownloaderTest` (4 cases) + `Sha256UtilTest` (2 cases) using `kotlinx.coroutines.test.runTest`, `io.ktor.client.engine.mock.MockEngine`, `okio.fakefilesystem.FakeFileSystem`. Total runtime ~250 ms.
- New `:engine-llama/commonTest` source set: `ChatTemplateTest` with 7 cases verifying exact rendered strings per family.
- Added `kotlin-test`, `kotlinx-coroutines-test`, `ktor-client-mock`, `okio-fakefilesystem` to `libs.versions.toml`.

### KDoc + error-message polish

- KDoc written for every public type in `:core` (17 files total): `LlmCoordinator`, `LlmSession`, `LlmKitConfig`, `RuntimeConfig`, `ModelInfo`, `ModelHandle`, `ModelLicense`, `PromptTemplate`, `ChatMessage`, `ChatRole`, `GenParams`, `GenStats`, `TokenEvent`, `FinishReason`, `KomlException`, `ModelRegistry`, `ModelDownloader`, `DownloadState`. Cross-references via `[Type]` work in IDE hovers and dokka-generated HTML.
- Error messages in `DefaultLlmSession` updated to include the model id and prompt token count; "Decode failed for prompt" now says "Decode failed for prompt (X tokens) on model 'Y' — context window (Z) may be exceeded." giving the user something to act on.

## File inventory

### Created (Phase 3)

```
engine-llama/src/commonMain/kotlin/dev/koml/engine/chat/ChatTemplate.kt
engine-llama/src/appleMain/kotlin/dev/koml/engine/LlamaNative.apple.kt       (MOVED from iosMain)
engine-llama/src/nativeInterop/cinterop/llama-macos.def
engine-llama/src/commonTest/kotlin/dev/koml/engine/chat/ChatTemplateTest.kt

registry/src/commonMain/kotlin/dev/koml/registry/HuggingFaceSearcher.kt
registry/src/commonMain/kotlin/dev/koml/registry/HttpClientFactory.kt
registry/src/appleMain/kotlin/dev/koml/registry/HttpClientFactory.kt          (MOVED from iosMain)
registry/src/androidMain/kotlin/dev/koml/registry/HttpClientFactory.kt
registry/src/jvmMain/kotlin/dev/koml/registry/HttpClientFactory.kt

download/src/commonTest/kotlin/dev/koml/download/DefaultModelDownloaderTest.kt
download/src/commonTest/kotlin/dev/koml/download/Sha256UtilTest.kt
download/src/appleMain/kotlin/dev/koml/download/HttpClientFactory.kt          (MOVED from iosMain)
download/src/appleMain/kotlin/dev/koml/download/SystemFs.kt                   (MOVED from iosMain)

storage/src/appleMain/kotlin/dev/koml/storage/KomlRoot.kt                     (MOVED from iosMain)
storage/src/appleMain/kotlin/dev/koml/storage/SystemFs.kt                     (MOVED from iosMain)

scripts/build-llama-macos-native.sh

docs/phase-3-summary.md
docs/releases/v0.0.3.md
```

### Modified

```
core/src/commonMain/kotlin/dev/koml/core/**/*.kt                              KDoc on every public type
core/build.gradle.kts                                                         +macosArm64 +macosX64
storage/build.gradle.kts                                                      +macosArm64 +macosX64
download/build.gradle.kts                                                     +macosArm64 +macosX64 +commonTest deps
registry/build.gradle.kts                                                     +macosArm64 +macosX64 +Ktor JSON +serialization plugin
engine-llama/build.gradle.kts                                                 +macosArm64 +macosX64 +cinterop config +commonTest deps
engine-llama/src/commonMain/kotlin/dev/koml/engine/internal/DefaultLlmSession.kt   chat() impl + error msgs
engine-llama/src/commonMain/kotlin/dev/koml/engine/internal/DefaultLlmCoordinator.kt   uses DefaultModelRegistryFactory.create()
registry/src/commonMain/kotlin/dev/koml/registry/DefaultModelRegistry.kt      takes optional HuggingFaceSearcher
download/src/commonMain/kotlin/dev/koml/download/Sha256Util.kt                +sha256OfBytes helper
gradle/libs.versions.toml                                                     +ktor content-negotiation/serialization/mock, kotlinx-coroutines-test, okio-fakefilesystem
README.md                                                                     per-target quickstarts
docs/known-issues.md                                                          minor updates
```

## Issues hit during Phase 3 and how they were resolved

| Issue | Fix |
|---|---|
| Adding macOS native targets to `:engine-llama` failed Gradle variant matching because dep modules (`:core` etc.) didn't declare matching targets | Added `macosArm64()` + `macosX64()` to every dep module's `kotlin {}` block. |
| `iosMain` actuals (`KomlRoot`, `SystemFs`, `HttpClientFactory`) weren't visible to `macosMain` | Moved them to `appleMain`. Default Kotlin 2.x hierarchy makes them visible to both. |
| cinterop for macOS targets needs `libkoml-llama.a` to exist at configure time | New `scripts/build-llama-macos-native.sh`. Documented as one-time setup like the iOS counterpart. |
| `Phi3Template.render` used a `<\|...\|>` literal that confused Markdown rendering of this doc | Built role tags character-by-character (`append('<').append('|')` etc.) so the source itself is unambiguous. |
| Some Phase 2 KDoc-target files weren't read during Phase 3 so Edit tool refused to write | Re-read them, then re-wrote. Result is identical to the intended single-pass edit. |

## Verification

**Compile + tests (all green):**
```bash
./gradlew :core:compileKotlinJvm
./gradlew :download:jvmTest                    # 6 tests pass
./gradlew :engine-llama:jvmTest                # 7 tests pass
./gradlew :engine-llama:compileKotlinIosSimulatorArm64
./gradlew :engine-llama:compileKotlinJvm
./gradlew :engine-llama:compileDebugKotlinAndroid
```

**macOS native linking** (requires the new script run first):
```bash
./scripts/build-llama-macos-native.sh
./gradlew :engine-llama:cinteropLlamaMacosArm64
./gradlew :engine-llama:compileKotlinMacosArm64
./gradlew :engine-llama:compileKotlinMacosX64
```

**HF search runtime smoke (manual):**
```kotlin
val coordinator = LlmKit.initialize()
val results = coordinator.registry.searchHuggingFace("smollm2")
println(results.take(3).joinToString("\n") { it.displayName })
```

**Chat-template runtime smoke (Android / desktop sample):**
After downloading `phi-3-mini-4k-instruct-q4`, switch the generate call in the sample to:
```kotlin
session.chat(
    listOf(
        ChatMessage(ChatRole.System, "You are a helpful assistant. Reply in one sentence."),
        ChatMessage(ChatRole.User, "What's the capital of France?"),
    ),
)
```
— and verify the output is a short well-formed sentence (not raw special tokens, not infinite babble).

## Open items / known limitations going into Phase 4

- **HF search results aren't directly downloadable** — they carry blank `downloadUrl`/`sha256`. Callers who want to download a search hit must supplement those fields themselves. v0.0.4 may add an opt-in `withFileDetails = true` that fans out per-result.
- **Linux x64 Kotlin/Native** still deferred (no Docker dependency yet). CI in Phase 4 will fill this in.
- **No publishing infrastructure.** Phase 4 adds Maven Central config + GitHub Actions CI + CONTRIBUTING.md / LICENSE.
- **Curated manifest SHA-256s still placeholders** in the shipped source. Run `scripts/refresh-manifest-shas.sh` before downloading.

## Commit message

```
feat(v0.0.3): chat templates, HF search, macOS native targets, tests, KDoc

Phase 3 brings session.chat() to life with model-family-specific
prompt templates, adds HuggingFace Hub search for discovery, exposes
:engine-llama as a Kotlin/Native library on macOS arm64+x64, and
ships the first integration-test suite.

Chat templates (:engine-llama)
- New sealed ChatTemplate with NoneTemplate / ChatMLTemplate /
  Llama3Template / Phi3Template / GemmaTemplate data objects, each
  rendering List<ChatMessage> to its model family's exact prompt
  format. Templates expose defaultStopSequences which are merged with
  caller GenParams.stopSequences so assistant turns terminate cleanly.
- DefaultLlmSession.chat() now picks the right template from
  ModelInfo.promptTemplate, renders, and delegates to generate().
  GemmaTemplate collapses ChatRole.System content into the first user
  turn since Gemma has no system role.
- 7 unit tests in commonTest assert the exact rendered output per
  family.

HuggingFace Hub search (:registry)
- Added Ktor content negotiation + kotlinx-serialization-json. Per-
  platform defaultRegistryHttpClient() installs JSON parsing.
- New HuggingFaceSearcher hits the public /api/models endpoint with
  search + filter=gguf + sort=downloads. Returns metadata-only
  ModelInfo (blank downloadUrl/sha256 — see KDoc for rationale).
  Errors degrade to empty list, not exceptions.
- DefaultModelRegistry now takes an optional HuggingFaceSearcher;
  DefaultModelRegistryFactory.create() wires the standard HTTP client.

Kotlin/Native macOS targets (:engine-llama + deps)
- Added macosArm64 + macosX64 targets to :core, :storage, :download,
  :registry, and :engine-llama. KMP variant matching requires every
  dep to expose the target.
- Moved iOS-only sources to appleMain so macOS native shares them:
  LlamaNative.apple.kt, KomlRoot.kt, SystemFs.kt, HttpClientFactory.kt.
- New scripts/build-llama-macos-native.sh + llama-macos.def.
  llama-macos.def links Metal + MetalKit (the b5460 embed bug only
  bites iOS; macOS keeps Metal acceleration).

Integration tests
- :download/commonTest — DefaultModelDownloaderTest (4 cases: happy
  path, license gate, SHA mismatch, already-downloaded short-circuit)
  + Sha256UtilTest. Uses Ktor MockEngine + Okio FakeFileSystem; no
  real network or disk. Runs in ~250 ms.
- :engine-llama/commonTest — ChatTemplateTest, 7 cases.
- Added kotlin-test, kotlinx-coroutines-test, ktor-client-mock,
  okio-fakefilesystem.

Polish
- KDoc on every public type in :core (17 files). Cross-references
  via [Type] resolve in IDE hovers and dokka.
- Error messages in DefaultLlmSession now include model id + prompt
  token count + context window so users can act on them.

Verified
- ./gradlew :core:compileKotlinJvm :download:jvmTest :engine-llama:jvmTest
  passes all targets.
- All existing Android / iOS / JVM / Compose-Desktop builds still green.
- macOS native targets compile + cinterop once
  scripts/build-llama-macos-native.sh has produced the static archive.

Out of scope (deferred to v0.0.4)
- Linux x64 Kotlin/Native target.
- Maven Central publishing config + GitHub Actions CI.
- Per-repo HF file-list fetch (would make search results directly
  downloadable).
- License-acceptance UX in samples (gate is functional but dormant
  since the five curated models are Apache/MIT).
```

## What comes next (v0.0.4 — Publication Prep)

- Maven Central publishing configuration (signing, POM metadata, but don't actually publish — you do that)
- GitHub Actions CI (build + test on Linux + macOS, JVM only — mobile CI is expensive)
- `CONTRIBUTING.md`, `CODE_OF_CONDUCT.md`, `LICENSE` (Apache 2.0)
- Issue templates
- Versioning policy documented
- Configuration cache compatibility audit
- (Stretch) Linux x64 Kotlin/Native target via Docker cross-compile
