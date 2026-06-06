plugins {
    alias(libs.plugins.kotlin.multiplatform) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.skie) apply false
    alias(libs.plugins.nmcp.aggregation)
    alias(libs.plugins.dokka)
}

// Single source of truth for group + version. Library modules pick these up via
// subprojects { ... } below; sample modules ignore the publishing config entirely.
allprojects {
    // Maven group — tied to the verified Sonatype Central Portal namespace
    // (io.github.<your-github-username>). Note: this is independent of the
    // Kotlin package names (dev.koml.*) which can stay as-is — they only
    // affect imports inside the library, not the published artifact
    // coordinates.
    group = "io.github.12345debdut"
    version = "0.0.5"
}

// Publishable modules. Anything not listed here is a sample/app and won't ship
// to Maven Central.
val publishableModules = setOf("core", "storage", "download", "registry", "engine-llama")

// Shared service that serialises GPG signing across the whole build.
// Without it, Gradle launches ~35 parallel signing tasks (5 modules × 7
// publications) and gpg-agent's secure memory pool runs out — surfacing as
// "gpg: signing failed: Cannot allocate memory". This caps signing to one
// task at a time while leaving everything else free to parallelise.
abstract class SerialSigningService : BuildService<BuildServiceParameters.None>
val serialSigningService = gradle.sharedServices.registerIfAbsent(
    "serialSigning", SerialSigningService::class.java,
) {
    maxParallelUsages.set(1)
}

subprojects {
    if (name !in publishableModules) return@subprojects

    apply(plugin = "maven-publish")
    apply(plugin = "signing")

    // Maven Central requires a javadoc JAR on every published artifact. KMP
    // doesn't generate one for the JVM (or per-target) publications by
    // default. We create one empty placeholder *per publication* so each
    // gets its own task output directory — sharing a single Jar task across
    // publications confuses Gradle's module metadata validator
    // ("file differs: expected core-jvm-0.0.5.jar, actual core-0.0.5-javadoc.jar").
    // The real API docs ship as a separate Dokka HTML site (workflows/docs.yml).

    // Wire every signing task into the shared serialiser.
    tasks.withType<org.gradle.plugins.signing.Sign>().configureEach {
        usesService(serialSigningService)
    }

    // Dokka is applied to every publishable module except :engine-llama —
    // engine-llama's iOS/macOS native compilations require user-supplied
    // static archives (build/llama-*/<arch>/lib/libkoml-llama.a) that we
    // can't guarantee are present at docs-gen time. The bulk of the public
    // KDoc lives in :core anyway; LlmKit + ChatTemplate are small enough to
    // browse in source.
    if (name != "engine-llama") {
        apply(plugin = "org.jetbrains.dokka")

        // Skip native source sets whose compilation requires user-supplied
        // static archives. Dokka still documents commonMain + JVM/Android.
        tasks.withType<org.jetbrains.dokka.gradle.DokkaTaskPartial>().configureEach {
            dokkaSourceSets.configureEach {
                val n = name.lowercase()
                if (n.contains("ios") || n.contains("macos") || n.contains("apple")) {
                    suppress.set(true)
                }
            }
        }
    }

    // One empty javadoc jar, attached only to the JVM publication. Central
    // Portal requires javadoc on JVM artifacts but exempts KMP per-target
    // publications (iOS/macOS klibs etc.) — our v0.0.5 first-attempt upload
    // confirmed all 55 non-JVM components validated with no javadoc present.
    val emptyJavadocJar = tasks.register<Jar>("emptyJavadocJar") {
        archiveClassifier.set("javadoc")
        destinationDirectory.set(layout.buildDirectory.dir("empty-javadoc"))
    }

    extensions.configure<PublishingExtension>("publishing") {
        publications.withType<MavenPublication>().matching { it.name == "jvm" }.configureEach {
            artifact(emptyJavadocJar)
        }
        publications.withType<MavenPublication>().configureEach {
            pom {
                name.set("Koml :${this@subprojects.name}")
                description.set(
                    "Koml — on-device LLM inference for Kotlin Multiplatform. " +
                        "A thin, idiomatic Kotlin wrapper over llama.cpp with Flow-based " +
                        "streaming on Android, iOS, JVM desktop, and macOS native.",
                )
                url.set("https://github.com/debdutsaha/koml")
                licenses {
                    license {
                        name.set("Apache License 2.0")
                        url.set("https://www.apache.org/licenses/LICENSE-2.0")
                        distribution.set("repo")
                    }
                }
                developers {
                    developer {
                        id.set("debdutsaha")
                        name.set("Debdut Saha")
                        email.set("debdut.saha.1@gmail.com")
                    }
                }
                scm {
                    url.set("https://github.com/debdutsaha/koml")
                    connection.set("scm:git:https://github.com/debdutsaha/koml.git")
                    developerConnection.set("scm:git:git@github.com:debdutsaha/koml.git")
                }
                issueManagement {
                    system.set("GitHub")
                    url.set("https://github.com/debdutsaha/koml/issues")
                }
            }
        }
    }

    // Signing — only kicks in for release builds. Three credential sources,
    // tried in order:
    //
    //   1. signing.keyId + signing.password + signing.keyFile (legacy
    //      file-based format — what's in ~/.gradle/gradle.properties on
    //      the maintainer's machine). Read automatically by the signing
    //      plugin; we don't need to wire it explicitly.
    //   2. signingInMemoryKey + signingInMemoryKeyPassword (CI / env-var
    //      format, set as GitHub Actions secrets in publish.yml).
    //   3. gpg --use-agent fallback.
    extensions.configure<SigningExtension>("signing") {
        val legacyKeyId = providers.gradleProperty("signing.keyId").orNull
        val inMemoryKey = providers.gradleProperty("signingInMemoryKey")
            .orElse(provider { System.getenv("SIGNING_IN_MEMORY_KEY") })
            .orNull
        val inMemoryPassword = providers.gradleProperty("signingInMemoryKeyPassword")
            .orElse(provider { System.getenv("SIGNING_IN_MEMORY_KEY_PASSWORD") })
            .orNull

        // Require signatures for any non-SNAPSHOT version. The task graph
        // isn't ready at configuration time, so we don't gate on "is publishing
        // in the plan" — instead, contributors on a SNAPSHOT version build
        // without needing signing keys configured at all.
        isRequired = !version.toString().endsWith("SNAPSHOT")

        // GPG 2.1+ no longer maintains secring.gpg, so the signing plugin's
        // file-based path (signing.keyFile) is broken on modern installs.
        // Always go through the gpg binary instead — it finds keys in the
        // modern ~/.gnupg/private-keys-v1.d/ keyring. The maintainer's
        // signing.keyId / signing.password gradle properties are still
        // respected by useGpgCmd().
        if (inMemoryKey != null) {
            useInMemoryPgpKeys(inMemoryKey, inMemoryPassword)
        } else {
            useGpgCmd()
        }
        sign(extensions.getByType<PublishingExtension>().publications)
    }
}

// Aggregated Dokka multi-module HTML site for github.io. Use:
//   ./gradlew dokkaHtmlMultiModule
// Output: build/dokka/htmlMultiModule/ (open index.html, or push to gh-pages branch).
tasks.named<org.jetbrains.dokka.gradle.DokkaMultiModuleTask>("dokkaHtmlMultiModule") {
    moduleName.set("Koml")
    outputDirectory.set(rootProject.layout.buildDirectory.dir("dokka/htmlMultiModule"))
}

// Wire every publishable module's publications into the aggregation plugin so a
// single ./gradlew publishToCentralPortal stages all five module bundles.
nmcpAggregation {
    centralPortal {
        // Credentials, tried in order:
        //   1. mavenCentralUsername / mavenCentralPassword  — vanniktech-style
        //   2. SONATYPE_USERNAME    / SONATYPE_PASSWORD     — what's in
        //                                                     ~/.gradle/gradle.properties
        //                                                     on the maintainer's machine
        //   3. env vars MAVEN_CENTRAL_USERNAME / _PASSWORD  — CI path
        username = providers.gradleProperty("mavenCentralUsername")
            .orElse(providers.gradleProperty("SONATYPE_USERNAME"))
            .orElse(provider { System.getenv("MAVEN_CENTRAL_USERNAME") })
        password = providers.gradleProperty("mavenCentralPassword")
            .orElse(providers.gradleProperty("SONATYPE_PASSWORD"))
            .orElse(provider { System.getenv("MAVEN_CENTRAL_PASSWORD") })
        // After upload, leave the bundle in the Portal in "validated" state so a
        // human can click "Publish" at central.sonatype.com. Switch to
        // "AUTOMATIC" once we've shipped a few releases and trust the pipeline.
        publishingType = "USER_MANAGED"
    }
    publishAllProjectsProbablyBreakingProjectIsolation()
}
