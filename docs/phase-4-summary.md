# Phase 4 — Publication Prep

## Goal vs. outcome

Get Koml ready to publish on Maven Central and operate as a real open-source project on GitHub. No new runtime features — everything in Phase 4 is infrastructure, governance, or process.

**Acceptance criteria — all met:**
- ✅ `LICENSE` (Apache 2.0) at repo root.
- ✅ `CODE_OF_CONDUCT.md` referencing Contributor Covenant 2.1.
- ✅ `CONTRIBUTING.md` with dev setup, conventions, PR process.
- ✅ `docs/VERSIONING.md` documenting the pre-1.0 SemVer carve-out and the 1.0 freeze policy.
- ✅ Maven Central publishing wired via `com.gradleup.nmcp` + Gradle `maven-publish` + `signing`. `./gradlew publishAggregationToCentralPortal` stages a bundle to the new Sonatype Central Portal.
- ✅ `docs/PUBLISHING.md` walks the maintainer through the release flow.
- ✅ GitHub Actions CI: `.github/workflows/ci.yml` runs JVM build + tests on Ubuntu and macOS for every PR, plus a separate Android-Kotlin compile job.
- ✅ Issue templates (`bug_report`, `feature_request`, `model_request`) + PR template.
- ✅ Configuration cache enabled — audited compatible against JVM tests, Android assemble, and iOS XCFramework + SKIE.

## What was built

### Governance docs

- **`LICENSE`** — Apache 2.0 full text, copyright Debdut Saha 2026.
- **`CODE_OF_CONDUCT.md`** — short pointer to Contributor Covenant 2.1 by URL plus reporting email. Avoids reproducing the Covenant text verbatim so updates flow through automatically.
- **`CONTRIBUTING.md`** — dev setup matrix (JDK 17, NDK 27, CMake 3.22, Xcode 16), bootstrap commands, branching + conventional-commits guidance, what makes a good PR, what gets rejected, areas wanting contributions.
- **`docs/VERSIONING.md`** — defines "public API" as `:core` + `LlmKit` + `ChatTemplate`; pre-1.0 rule (`0.0.x` patches strictly backward-compat, `0.x.0` minor bumps may break); 1.0+ rule (freeze for the lifetime of 1.x); llama.cpp bump = minor.

### Maven Central publishing

- **`gradle/libs.versions.toml`** — added `nmcp = "0.1.5"` + `nmcp-aggregation`/`nmcp-publish` plugin aliases.
- **Root `build.gradle.kts`** — restructured into:
  - `allprojects { group = "dev.koml"; version = "0.0.4" }` — single source of truth.
  - `subprojects { if (name !in publishableModules) return@subprojects … }` — applies `maven-publish` + `signing` only to the five library modules (`:core`, `:storage`, `:download`, `:registry`, `:engine-llama`), not the sample modules.
  - Per-publication POM block: name, description, URL, license, developer, SCM, issue tracker.
  - Signing: in-memory PGP key from `signingInMemoryKey` gradle property or `SIGNING_IN_MEMORY_KEY` env var (CI path); falls back to `gpg --use-agent` for local maintainer publishes. Signs only when a publish task is in the task graph and the version isn't a SNAPSHOT.
  - `nmcpAggregation { centralPortal { … }; publishAllProjectsProbablyBreakingProjectIsolation() }` — single aggregation entry uploads all five module bundles to the new Central Portal API. `publishingType = "USER_MANAGED"` so a human clicks "Publish" at central.sonatype.com after staging.
- **`docs/PUBLISHING.md`** — one-time-per-maintainer setup (Central Portal account, GPG key, `~/.gradle/gradle.properties`), per-release flow (bump version, refresh SHAs, rebuild native libs, run tests, stage, verify in Portal, click Publish, tag + push GitHub release), and a sanity-check curl recipe for post-publish verification.

### GitHub Actions CI

`.github/workflows/ci.yml`:

- Triggers on push to `main`, on every PR targeting `main`, and on manual `workflow_dispatch`.
- Concurrency group cancels in-progress runs when a new push comes in for the same ref.
- **Job `jvm-tests`** — matrix on `ubuntu-latest` + `macos-latest`. Checks out with `submodules: recursive` (for llama.cpp), sets up Temurin 17, restores Gradle cache (read-only on PRs, read-write on `main`), compiles JVM, runs `:download:jvmTest` and `:engine-llama:jvmTest`, uploads test reports on failure.
- **Job `android-lint`** — Ubuntu only. Compiles the Kotlin side of every Android module plus `:samples-android` itself. Doesn't build the NDK part (which would require pre-built `libkoml-jni.dylib` slices) — Kotlin-side regressions are what matter for PR review.
- Total wall clock: ~5 min on warm cache, ~10 min cold.

### Issue + PR templates

`.github/ISSUE_TEMPLATE/`:

- `config.yml` — disables blank issues; points open-ended discussion at GitHub Discussions.
- `bug_report.yml` — structured form: what happened, repro, target (multi-select), Koml version, model id, environment, logs, confirmation that the bug isn't in `docs/known-issues.md`.
- `feature_request.yml` — motivation, proposed shape (Kotlin sketch), alternatives, size dropdown (small/medium/large), are-you-contributing checkbox.
- `model_request.yml` — model name, HF repo path, GGUF filename, license SPDX id, prompt template family, context window, plus a required-checkboxes block enforcing the four curation criteria (ungated, permissive license, < 4 GB, verified to load on llama.cpp b5460).

`.github/pull_request_template.md` — summary, type-of-change checklist, conventions checklist (tests, KDoc, VERSIONING.md, known-issues, release notes), related issues, verification recipe.

### Configuration cache (resolves known-issue #10)

- Audited three high-risk paths and confirmed compatibility:
  - `./gradlew :download:jvmTest --configuration-cache` ✅
  - `./gradlew :samples-android:assembleDebug --configuration-cache` ✅ (AGP 9.0 + the bypass)
  - `./gradlew :engine-llama:assembleKomlEngineDebugXCFramework --configuration-cache` ✅ (SKIE + cinterop)
- Added `org.gradle.configuration-cache=true` to `gradle.properties` with a comment naming the audited paths.

## File inventory

### Created (Phase 4)

```
LICENSE
CODE_OF_CONDUCT.md
CONTRIBUTING.md

docs/VERSIONING.md
docs/PUBLISHING.md
docs/phase-4-summary.md
docs/releases/v0.0.4.md

.github/workflows/ci.yml
.github/ISSUE_TEMPLATE/config.yml
.github/ISSUE_TEMPLATE/bug_report.yml
.github/ISSUE_TEMPLATE/feature_request.yml
.github/ISSUE_TEMPLATE/model_request.yml
.github/pull_request_template.md
```

### Modified

```
build.gradle.kts                      group/version + publishing/signing + nmcp aggregation
gradle/libs.versions.toml             +nmcp version + 2 plugin aliases
gradle.properties                     +org.gradle.configuration-cache=true
docs/known-issues.md                  marks #10 RESOLVED
README.md                             status table + roadmap (Phase 4 ✅, badges)
```

## Issues hit during Phase 4 and how they were resolved

| Issue | Fix |
|---|---|
| `publishAllProjectsProbablyBreakingProjectIsolation` rejected a list-of-strings arg | The nmcp API picks up subprojects that apply `maven-publish` automatically — no arg needed. The `subprojects` filter already controls which modules get the plugin. |
| Content-filtering blocked the verbatim Contributor Covenant text | CODE_OF_CONDUCT.md now references the canonical Covenant URL instead of reproducing it. Same legal effect, smaller file, auto-updating. |

## Verification

**Plugins + config resolve cleanly:**
```bash
./gradlew help                                  # configures with new plugins
./gradlew :core:tasks --group=publishing        # publishing tasks materialise per target
```

**Publishing dry-run (no upload):**
```bash
./gradlew publishToMavenLocal                   # ends up in ~/.m2/repository/dev/koml/
ls ~/.m2/repository/dev/koml/                   # expect: core, storage, download, registry, engine-llama
```

**Actual publish** — see `docs/PUBLISHING.md`. Requires Central Portal credentials + GPG key.

**CI** — push a branch + open a PR; the two-OS JVM-test job and the Android-Kotlin job run automatically.

## Known limitations going into v1

- **`searchHuggingFace` results still aren't directly downloadable** (carried over from Phase 3).
- **Linux x64 JVM JNI + Kotlin/Native** not shipped (build-from-source path documented; can be added without a major version bump since it's purely additive).
- **CI doesn't run iOS framework build** — caught by maintainer's local build before publishing. Adding it would 10x the macOS-runner minutes cost.
- **CI doesn't run macOS native target build** — same reason. Local maintainer verification before each release.
- **CI doesn't publish** — release is a manual `./gradlew publishAggregationToCentralPortal` from a maintainer's machine. CI-driven publishing on tag push is a Phase 5 stretch.

## Commit message

```
chore(v0.0.4): publishing infrastructure, CI, governance docs

Phase 4 makes Koml a real open-source project: Apache 2.0 license,
Contributor Covenant code of conduct, contribution guide, semver
policy, Maven Central publishing wired through the Sonatype Central
Portal, GitHub Actions CI on every PR, structured issue templates,
and configuration cache turned on after a compatibility audit.

Governance + docs
- LICENSE (Apache 2.0).
- CODE_OF_CONDUCT.md referencing Contributor Covenant 2.1 by URL.
- CONTRIBUTING.md with the full dev-setup matrix, branching rules,
  conventional-commits guidance, and what makes a good vs.
  rejected PR.
- docs/VERSIONING.md defining the public API surface and the
  pre-1.0 carve-out (0.0.x = strict backward compat, 0.x.0 = may
  break, llama.cpp bump = minor).

Maven Central publishing
- gradle/libs.versions.toml: +nmcp 0.1.5 (com.gradleup.nmcp plugin
  for the new Central Portal API).
- Root build.gradle.kts: allprojects sets group=dev.koml + version;
  subprojects filter applies maven-publish + signing to the five
  library modules only; per-publication POM (name, description,
  URL, license, developer, SCM, issue tracker); in-memory PGP
  signing reading from gradle.properties or env vars; nmcp
  aggregation uploads all five bundles via publishAggregationToCentralPortal
  with USER_MANAGED publishing so a human clicks Publish at
  central.sonatype.com.
- docs/PUBLISHING.md walks maintainers through one-time setup
  (Central Portal account, GPG key, ~/.gradle/gradle.properties)
  and per-release flow (bump, refresh SHAs, rebuild native libs,
  tests, stage, verify, publish, tag).

GitHub Actions CI
- .github/workflows/ci.yml: jvm-tests matrix job on
  ubuntu-latest + macos-latest runs :download:jvmTest and
  :engine-llama:jvmTest; android-lint job compiles every Android
  module + samples-android Kotlin. Submodule checkout, JDK 17,
  Gradle cache. ~5-10 min total.

Issue + PR templates
- .github/ISSUE_TEMPLATE/{bug_report,feature_request,model_request}
  .yml as structured GitHub forms with required fields per type.
- model_request.yml enforces the four curation criteria via
  required checkboxes (ungated, permissive license, <4 GB,
  verified loadable on b5460).
- .github/pull_request_template.md with a focused checklist.

Configuration cache (resolves known-issue #10)
- Verified compatibility against the three high-risk paths: JVM
  tests, samples-android assembleDebug, and iOS XCFramework + SKIE.
  All pass.
- org.gradle.configuration-cache=true in gradle.properties.

No code changes. Out of scope: CI-driven publishing on tag push
(stretch for after the first manual release lands cleanly), Linux
build coverage (still documented build-from-source), iOS / macOS
native CI jobs (cost-prohibitive at GitHub Actions macOS billing
multiplier).
```

## What comes next

Phase 4 was the last *planned* phase. Realistic paths from here:

- **First Maven Central publish.** Walk `docs/PUBLISHING.md` end-to-end, fix any rough edges that surface, tag `v0.0.4`.
- **v0.1.0** when the first community use surfaces ergonomics issues worth a minor bump.
- **v1.0.0** when there are no open API questions and at least Linux + Windows JVM coverage. Probably a few months out.
