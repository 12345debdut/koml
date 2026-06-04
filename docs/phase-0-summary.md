# Phase 0 — JNI Proof of Concept

## What was built

A minimal Android project proving the llama.cpp JNI boundary works end-to-end.

### Modules

- **:engine-llama** — Android library containing the JNI bridge to llama.cpp. Exposes `LlamaNative`, a Kotlin class with `external` functions for model loading, tokenization, decoding, sampling, and token-to-string conversion.
- **:samples-android** — Bare-bones Android app that loads a hardcoded GGUF from `/data/local/tmp/model.gguf`, tokenizes a prompt, and generates 10 tokens. Output appears on screen and in Logcat.

### Key decisions

- **AGP 9.0.1** with built-in Kotlin support (no separate `kotlin-android` plugin).
- **Gradle 9.1**, **Kotlin 2.2.10** (bundled with AGP 9.0).
- **arm64-v8a only** — single ABI for fast iteration.
- **llama.cpp built as static library** via CMake `add_subdirectory`, linked into our `koml-jni` shared library.
- **No position tracking in batch API** — using `llama_batch_get_one` for simplicity. If output is garbled, switch to explicit batch construction with position tracking.
- **Sampler chain created per sample call** — wasteful but correct for a PoC.

### JNI surface (LlamaNative.kt)

| Method | Purpose |
|--------|---------|
| `initBackend()` | Initialize llama.cpp backend |
| `loadModel(path): Long` | Load GGUF, return model handle |
| `freeModel(handle)` | Release model |
| `createContext(model, nCtx): Long` | Create inference context |
| `freeContext(ctx)` | Release context |
| `tokenize(model, text, addBos): IntArray` | Text to token IDs |
| `decode(ctx, tokens): Boolean` | Evaluate token batch |
| `sampleToken(ctx, temp, topP, topK): Int` | Sample next token |
| `tokenToPiece(model, token): String` | Token ID to text |
| `isEogToken(model, token): Boolean` | Check end-of-generation |

## Verified results

Tested on Android arm64-v8a with SmolLM2-135M Q8_0:

```
Prompt:  The capital of France is
Output:  Paris, a city with a rich history and culture
```

Clean lifecycle: backend init → model load → context create → tokenize → decode prompt → sample/decode 10 tokens → free context → free model.

## API notes (llama.cpp b5460)

The b5460 release split `llama_vocab` out of `llama_model`. The JNI code uses the post-split API:

| Function | Notes |
|----------|-------|
| `llama_model_load_from_file` | (not `llama_load_model_from_file`) |
| `llama_model_free` | (not `llama_free_model`) |
| `llama_init_from_model` | (not `llama_new_context_with_model`) |
| `llama_model_get_vocab(model)` | called inside tokenize/piece/eog paths |
| `llama_tokenize(vocab, ...)` | first arg is vocab |
| `llama_token_to_piece(vocab, ...)` | first arg is vocab |
| `llama_vocab_is_eog(vocab, token)` | (not the deprecated `llama_token_is_eog`) |

`llama_batch_get_one` with null positions auto-tracks across decode calls — verified working.

## Manual steps before building

1. Initialize git and add llama.cpp as a submodule:
   ```bash
   cd /path/to/Koml
   git init
   git submodule add https://github.com/ggml-org/llama.cpp.git external/llama.cpp
   cd external/llama.cpp
   git checkout b5460
   cd ../..
   ```

2. Generate the Gradle wrapper:
   ```bash
   gradle wrapper --gradle-version 9.1
   ```
   Or copy `gradlew` / `gradlew.bat` / `gradle-wrapper.jar` from another project.

3. Create `local.properties` pointing to your Android SDK:
   ```
   sdk.dir=/path/to/Android/sdk
   ```

4. Download a small GGUF for testing and push to device:
   ```bash
   # Option A: SmolLM2-135M (~150 MB, fastest iteration)
   # Download from https://huggingface.co/HuggingFaceTB/SmolLM2-135M-Instruct-GGUF
   adb push smollm2-135m-instruct-q8_0.gguf /data/local/tmp/model.gguf

   # Option B: TinyLlama-1.1B-Chat (~670 MB, better output quality)
   # Download from https://huggingface.co/TheBloke/TinyLlama-1.1B-Chat-v1.0-GGUF
   adb push tinyllama-1.1b-chat-v1.0.Q4_K_M.gguf /data/local/tmp/model.gguf
   ```

## Verification

```bash
./gradlew :samples-android:assembleDebug
adb install samples-android/build/outputs/apk/debug/samples-android-debug.apk
adb logcat -s KomlSample KomlEngine
# Press "Generate" in the app — watch for 10 token lines in Logcat
```

## Commit message

```
feat(engine-llama): add Android JNI proof of concept for llama.cpp

Gradle skeleton with AGP 9.0.1 / Gradle 9.1. Minimal JNI bridge
(LlamaNative) exposing load, tokenize, decode, sample, and
token-to-piece. Sample app generates 10 tokens from a hardcoded
prompt and GGUF path.
```

## What comes next (Phase 1)

- Add iOS target via cinterop
- Implement the full :core public API (interfaces + data classes)
- Build LlmSession with Flow-based streaming and stop-sequence detection
- Per-session single-threaded dispatcher
- KomlException hierarchy
