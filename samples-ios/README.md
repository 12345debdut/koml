# Koml iOS Sample

A SwiftUI app that streams tokens from an on-device GGUF via the `:engine-llama` KMP framework.

## One-time setup

1. **Install XcodeGen** (used to generate `KomlSample.xcodeproj` from `project.yml`):
   ```bash
   brew install xcodegen
   ```
2. **Build the llama.cpp iOS static libs** (from repo root, ~10 min):
   ```bash
   ./scripts/build-llama-ios.sh
   ```
3. **Assemble the KomlEngine XCFramework** (from repo root):
   ```bash
   ./gradlew :engine-llama:assembleKomlEngineDebugXCFramework
   ```
4. **Add a GGUF** named `model.gguf` to `samples-ios/KomlSample/Resources/`. Any model that fits in the simulator's memory works. SmolLM2-135M Q8_0 (~145 MB) is a good pick:
   ```bash
   curl -L -o "samples-ios/KomlSample/Resources/model.gguf" \
     "https://huggingface.co/bartowski/SmolLM2-135M-Instruct-GGUF/resolve/main/SmolLM2-135M-Instruct-Q8_0.gguf"
   ```

## Generate the Xcode project

```bash
cd samples-ios
xcodegen
open KomlSample.xcodeproj
```

## Build and run

In Xcode: select an iOS Simulator (or your device with a valid signing team) and hit **⌘R**.

- Press **Generate** — tokens stream into the SwiftUI view.
- Mid-stream, press **Cancel** — generation aborts and `[cancelled]` is appended.

The Xcode "Build KMP XCFramework" pre-build phase runs `./gradlew :engine-llama:assembleKomlEngineDebugXCFramework` automatically, so changes in Kotlin code are picked up on the next Xcode build.

## Why XcodeGen and not a committed `.xcodeproj`?

`project.pbxproj` is huge, UUID-laden, and merge-hostile. `project.yml` is small, declarative, and reviewable. Standard pattern in modern KMP repos.

## Troubleshooting

- **"java: command not found" during the pre-build script** — the script looks for JDK 17 at common Homebrew and Zulu paths. If yours is elsewhere, edit the `preBuildScripts` block in `project.yml` and re-run `xcodegen`.
- **"Cannot find KomlEngine in scope"** — the XCFramework wasn't built. Run step 3 above manually once, then rebuild in Xcode.
- **Model loading hangs** — first run with a large GGUF on the simulator can take 20+ seconds. Watch the Xcode console for `KomlEngine` log lines.
