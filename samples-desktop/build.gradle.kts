plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.compose.compiler)
    alias(libs.plugins.compose.multiplatform)
}

kotlin {
    jvmToolchain(17)

    jvm {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        }
    }

    sourceSets {
        named("jvmMain") {
            dependencies {
                implementation(project(":engine-llama"))
                implementation(compose.desktop.currentOs)
                implementation(compose.material3)
                implementation(compose.materialIconsExtended)
                implementation(libs.kotlinx.coroutines.swing)
            }
        }
    }
}

compose.desktop {
    application {
        mainClass = "dev.koml.samples.desktop.MainKt"
        nativeDistributions {
            targetFormats(org.jetbrains.compose.desktop.application.dsl.TargetFormat.Dmg)
            packageName = "KomlSampleDesktop"
            // Compose Desktop DMG metadata requires MAJOR > 0 (so 0.0.x rejected).
            // The library itself is still on 0.0.x — this version is only for the
            // installer bundle.
            packageVersion = "1.0.0"
            macOS {
                bundleID = "dev.koml.samples.desktop"
            }
        }
    }
}
