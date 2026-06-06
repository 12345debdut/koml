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
    group = "dev.koml"
    version = "0.0.5"
}

// Publishable modules. Anything not listed here is a sample/app and won't ship
// to Maven Central.
val publishableModules = setOf("core", "storage", "download", "registry", "engine-llama")

subprojects {
    if (name !in publishableModules) return@subprojects

    apply(plugin = "maven-publish")
    apply(plugin = "signing")

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

    extensions.configure<PublishingExtension>("publishing") {
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

    // Signing — only kicks in for release builds. Reads credentials from
    // ~/.gradle/gradle.properties or env vars (set in CI), so local dev
    // builds never need to sign.
    extensions.configure<SigningExtension>("signing") {
        val signingKey = providers.gradleProperty("signingInMemoryKey")
            .orElse(provider { System.getenv("SIGNING_IN_MEMORY_KEY") })
            .orNull
        val signingPassword = providers.gradleProperty("signingInMemoryKeyPassword")
            .orElse(provider { System.getenv("SIGNING_IN_MEMORY_KEY_PASSWORD") })
            .orNull

        isRequired = !version.toString().endsWith("SNAPSHOT") &&
            gradle.taskGraph.allTasks.any { it.name.contains("publish", ignoreCase = true) }

        if (signingKey != null) {
            useInMemoryPgpKeys(signingKey, signingPassword)
        } else {
            // Fall back to `gpg --use-agent` for local maintainer publishes.
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
        username = providers.gradleProperty("mavenCentralUsername")
            .orElse(provider { System.getenv("MAVEN_CENTRAL_USERNAME") })
        password = providers.gradleProperty("mavenCentralPassword")
            .orElse(provider { System.getenv("MAVEN_CENTRAL_PASSWORD") })
        // After upload, leave the bundle in the Portal in "validated" state so a
        // human can click "Publish" at central.sonatype.com. Switch to
        // "AUTOMATIC" once we've shipped a few releases and trust the pipeline.
        publishingType = "USER_MANAGED"
    }
    publishAllProjectsProbablyBreakingProjectIsolation()
}
