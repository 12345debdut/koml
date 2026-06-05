# Contributing to Koml

Thanks for considering a contribution. Koml is small enough that you can get up to speed in an afternoon — this doc walks you through the setup, the conventions, and what makes a good PR.

## TL;DR

1. Read [`docs/phase-3-summary.md`](docs/phase-3-summary.md) for the current state of the architecture.
2. Open an issue *before* large changes so we can agree on the approach.
3. Match the conventions of the existing code (boring and correct > clever).
4. `./gradlew :download:jvmTest :engine-llama:jvmTest` must pass before you push.
5. PR title in conventional-commits form: `feat(scope): ...`, `fix(scope): ...`, `docs(scope): ...`.

## Dev environment

| Tool | Version |
|---|---|
| JDK | 17 (Adoptium/Temurin recommended) |
| Android Studio | Otter 3 (2025.2.3) or newer |
| Android NDK | 27.x via SDK Manager |
| Android CMake | 3.22.1 via SDK Manager |
| Xcode | 16.x (only if working on iOS) |
| Homebrew | for `cmake` (host CMake distinct from Android's), `xcodegen` |

Bootstrap from a fresh clone:

```bash
git clone --recurse-submodules https://github.com/<you>/koml.git
cd koml
brew install cmake xcodegen        # if you don't already have them

# JVM/Android-only work? You can skip these. iOS or native work needs them:
./scripts/build-llama-ios.sh
./scripts/build-llama-jvm.sh
./scripts/build-llama-macos-native.sh
```

`local.properties` must point at your Android SDK:
```
sdk.dir=/Users/<you>/Library/Android/sdk
```

## Build + test

```bash
./gradlew assemble                                            # everything
./gradlew :download:jvmTest :engine-llama:jvmTest             # the tests
./gradlew :samples-android:assembleDebug                      # Android sample
./gradlew :samples-desktop:run                                # Compose Desktop sample
./gradlew :engine-llama:assembleKomlEngineDebugXCFramework    # iOS framework
```

CI runs JVM tests on Ubuntu + macOS for every PR. Don't push if `:download:jvmTest` or `:engine-llama:jvmTest` is failing on your machine — it will fail on CI too.

## Branching + commit conventions

- Branch off `main`. Use short, lowercase, dash-separated names: `fix-resume-after-truncation`, `add-llama4-template`.
- Commit messages follow [conventional commits](https://www.conventionalcommits.org/):
  - `feat(engine-llama): add Llama 4 chat template`
  - `fix(download): correct Range header off-by-one on resume`
  - `docs(readme): clarify JVM platform coverage`
  - `test(download): cover empty-content-length response`
- Squash-merge into `main` is the default — the PR title becomes the commit subject, so make it descriptive.

## What makes a good PR

- **One topic per PR.** Two features → two PRs. A drive-by rename should be its own commit (or its own PR if it touches more than 5 files).
- **Tests for new behaviour.** If you fix a downloader bug, add a MockEngine test that fails without your fix.
- **Update the docs.** New public API → KDoc + a paragraph in the current phase summary. New known issue → entry in `docs/known-issues.md`.
- **Don't break the `:core` public surface** without a deprecation cycle. See [VERSIONING.md](docs/VERSIONING.md) for the rules.
- **Keep the diff focused.** Reviewers will appreciate it.

## What we typically reject

- Changes that introduce new heavyweight dependencies. We deliberately stay close to the JetBrains/Touchlab/Square trio (kotlinx, ktor, okio, kotlincrypto). Anything else needs justification.
- New abstractions without a concrete second use case.
- Style-only churn unconnected to a bug fix or feature.
- Code that bypasses safety checks (`--no-verify`, `noinline`-everything, `@Suppress` blocks). If something is fighting you, that's a signal to talk first.

## Areas we'd love help with

- **More chat templates** as new model families ship (Llama 4, Qwen 3, etc.). See `engine-llama/src/commonMain/kotlin/dev/koml/engine/chat/ChatTemplate.kt`.
- **Linux x64 cross-build via Docker** (currently deferred — see `docs/known-issues.md#7`).
- **Per-repo HF file-list fetch** that would make `searchHuggingFace` results directly downloadable.
- **Per-platform integration tests** (only JVM is wired today via `:download/commonTest`).

## Reporting bugs / suggesting models

- Bug? Use the **Bug report** template — include OS, JDK, model id (if relevant), and steps to reproduce.
- Want a model added to the curated list? Use the **Model request** template. Permissive licenses (Apache-2.0, MIT, BSD) and direct HF download URLs only.
- Feature idea? Open a **Feature request** issue first; PRs implementing unsolicited features may be closed without review.

## Code of conduct

See [CODE_OF_CONDUCT.md](CODE_OF_CONDUCT.md).
