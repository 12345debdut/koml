# Publishing to Maven Central

Maintainer-only doc — describes how a tagged Koml release lands on `central.sonatype.com`. Consumers can ignore this entirely.

## Once per maintainer

### 1. Create a Sonatype Central Portal account

Sign in at <https://central.sonatype.com> with the GitHub identity that owns the `dev.koml` namespace.

In **Account → Generate User Token**, generate a token. The two values you copy out are your `mavenCentralUsername` and `mavenCentralPassword` — these go into `~/.gradle/gradle.properties` (see step 3).

### 2. Generate a GPG signing key

```bash
gpg --quick-generate-key "Debdut Saha <debdut.saha.1@gmail.com>" rsa4096 sign 2y
gpg --armor --export-secret-keys <KEY_ID>     # paste into gradle.properties
```

Publish the public half to a keyserver so Maven Central can verify uploads:

```bash
gpg --keyserver keys.openpgp.org --send-keys <KEY_ID>
gpg --keyserver keyserver.ubuntu.com --send-keys <KEY_ID>
```

### 3. Local credentials

`~/.gradle/gradle.properties`:

```properties
mavenCentralUsername=<the token username from Central Portal>
mavenCentralPassword=<the token password from Central Portal>

# Multi-line PGP private key — make sure literal \n is preserved (no actual newlines).
signingInMemoryKey=-----BEGIN PGP PRIVATE KEY BLOCK-----\n...\n-----END PGP PRIVATE KEY BLOCK-----
signingInMemoryKeyPassword=<passphrase>
```

Don't commit this file. It already lives outside the repo.

## Each release

### 1. Bump the version

Single source of truth: root `build.gradle.kts` →

```kotlin
allprojects {
    group = "dev.koml"
    version = "0.0.4"        // ← bump here
}
```

Patch (0.0.x → 0.0.(x+1)) for additive/bug-fix releases. Minor (0.x.0 → 0.(x+1).0) for breaking changes. See [VERSIONING.md](VERSIONING.md).

### 2. Update the release notes

Add `docs/releases/v0.0.4.md` (copy structure from the previous release). Link it from the README's roadmap table.

### 3. Refresh manifest SHAs if needed

```bash
./scripts/refresh-manifest-shas.sh
```

Paste the output into `registry/src/commonMain/kotlin/dev/koml/registry/CuratedModels.kt`. Skip this step if no model files on HuggingFace have changed since the last release.

### 4. Rebuild native libraries

```bash
./scripts/build-llama-ios.sh
./scripts/build-llama-jvm.sh
./scripts/build-llama-macos-native.sh
```

Each takes ~10 min. Required so the JAR includes the freshest `libkoml-jni.dylib` slices.

### 5. Run the full build + tests

```bash
./gradlew :download:jvmTest :engine-llama:jvmTest
./gradlew assemble
./gradlew :engine-llama:assembleKomlEngineDebugXCFramework
```

All three must be green. If iOS XCFramework fails, the published artifact won't be usable on iOS.

### 6. Stage to Central Portal

```bash
./gradlew publishAggregationToCentralPortal
```

This:
- Builds every publishable module (`:core`, `:storage`, `:download`, `:registry`, `:engine-llama`).
- Signs every artifact with your GPG key.
- Uploads a single bundle to <https://central.sonatype.com>.
- Leaves it in **validated** state — *not* automatically released.

### 7. Verify and release

Log in to <https://central.sonatype.com> → **Deployments**. Your bundle should show "Validated" within a few minutes.

Click each module to verify:
- Group: `dev.koml`
- Version: matches what you set in step 1.
- The `*-all.jar` / `*-sources.jar` / `*-javadoc.jar` / signature `.asc` files are present.
- POM metadata has correct license, developer, SCM URLs.

If everything looks right, click **Publish**. Central does its own validation pass and propagates to mirrors within ~30 min.

### 8. Tag and push

```bash
git tag -s v0.0.4 -m "v0.0.4"
git push origin v0.0.4
```

Then on GitHub, create a release using the tag and paste the `docs/releases/v0.0.4.md` content into the release body.

## Sanity-checking a publication

```bash
# Download a freshly published artifact and inspect
curl -L "https://repo1.maven.org/maven2/dev/koml/core/0.0.4/core-0.0.4.jar" -o /tmp/core.jar
jar tf /tmp/core.jar | head -20

# Verify the signature
curl -L "https://repo1.maven.org/maven2/dev/koml/core/0.0.4/core-0.0.4.jar.asc" -o /tmp/core.jar.asc
gpg --verify /tmp/core.jar.asc /tmp/core.jar
```

## CI publishing (Phase 4 stretch)

When GitHub Actions gets release credentials, the same flow runs from a workflow triggered by pushing a `v*` tag. The relevant env vars match the gradle properties:

```yaml
env:
  MAVEN_CENTRAL_USERNAME: ${{ secrets.MAVEN_CENTRAL_USERNAME }}
  MAVEN_CENTRAL_PASSWORD: ${{ secrets.MAVEN_CENTRAL_PASSWORD }}
  SIGNING_IN_MEMORY_KEY: ${{ secrets.SIGNING_IN_MEMORY_KEY }}
  SIGNING_IN_MEMORY_KEY_PASSWORD: ${{ secrets.SIGNING_IN_MEMORY_KEY_PASSWORD }}
```

Not enabled yet — current Phase 4 CI is build + test only. Set up after the first manual release goes smoothly.
