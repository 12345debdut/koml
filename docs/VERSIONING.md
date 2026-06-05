# Versioning

Koml follows [Semantic Versioning 2.0](https://semver.org/) with one specific carve-out for the `0.x` line.

## Public API surface

For versioning purposes, the **public API** is everything visible from `:core`'s `commonMain`. Specifically:

- All interfaces under `dev.koml.core.*` (`LlmCoordinator`, `LlmSession`, `ModelRegistry`, `ModelDownloader`, `ModelStorage`)
- All data classes, sealed classes, and enums under `dev.koml.core.*`
- `dev.koml.engine.LlmKit` (the entry point object)
- `dev.koml.engine.chat.ChatTemplate` (sealed class) — third-party templates may need to know about it

Everything else — anything `internal`, anything in `engine-llama/internal/`, anything under the `dev.koml.engine.native.*` cinterop packages — is implementation detail and may change without notice.

## Pre-1.0 rules

While we're on `0.x.x`:

- **Major-version bumps are not used.** A backwards-incompatible change to the public API surface ships in the **minor** version (`0.1.0`, `0.2.0`, …).
- **Each new minor adds at least one feature** *and* may include breaking changes to the public API.
- **Patch versions** (`0.0.4`, `0.0.5`, …) within a minor are **strictly backwards-compatible**: bug fixes, doc improvements, internal refactors. We *will* add new methods to interfaces with default implementations during a patch series, since that's source- and binary-compatible. We *won't* rename or remove existing types/methods during a patch series.

In other words, while we're pre-1.0:

| Bump | Means |
|---|---|
| `0.0.x` → `0.0.(x+1)` | Bug fixes, docs, additive default-impl methods, internal cleanups. Safe to upgrade. |
| `0.0.x` → `0.1.0` | First minor — likely small breaking changes as we settle the API for 1.0. Read the release notes. |
| `0.x.0` → `0.(x+1).0` | New features + possibly breaking changes. Read the release notes. |

## 1.0 and beyond

When we cut `1.0.0`:

- **The public API surface is frozen for the lifetime of 1.x.** Adding new methods to interfaces requires a default implementation. Removing or renaming anything requires a 2.0.
- **Patch and minor versions follow standard SemVer**: patches are bug-only, minors are additive.
- **Breaking changes ship in major bumps** with at least one minor of deprecation warning beforehand whenever possible.

We're not at 1.0 yet because:
- We want at least one full release cycle of community use before locking ergonomics.
- Linux Kotlin/Native and Linux JVM JNI aren't shipped yet.
- The HF search API surface is intentionally restrictive (metadata-only) and may need a more capable variant.

## How to check the surface

Before a release we run:

```bash
./gradlew :core:apiCheck    # not yet wired — Phase 4 stretch
```

In the meantime, `git diff <last-tag>..HEAD -- 'core/src/commonMain/**/*.kt'` reveals any public-surface drift.

## Deprecation policy

When we deprecate something pre-1.0:
- Annotate with `@Deprecated(message = "...", replaceWith = ReplaceWith("..."))`.
- Keep it for at least one minor release before removing.
- Note the removal in the release notes of the removing version.

## llama.cpp version bumps

The `external/llama.cpp` submodule is **pinned to a specific tag** (currently `b5460`). Bumping it counts as a **minor** version bump because:

1. llama.cpp's C API has historically had breaking renames within the same year (e.g. the `llama_vocab` split at b5460).
2. Model compatibility can shift — GGUF files quantised with one llama.cpp era sometimes fail to load with another.
3. We need to re-run the manifest SHA-256 helper after a bump.

We don't auto-bump llama.cpp on patch releases.
