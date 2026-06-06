plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.library)
}

kotlin {
    jvmToolchain(17)

    compilerOptions {
        freeCompilerArgs.add("-Xexpect-actual-classes")
    }

    // Make sure appleMain/iosMain/macosMain are materialised so the
    // appleMain HttpClientFactory.kt sees shared deps.
    applyDefaultHierarchyTemplate()

    androidTarget()
    jvm()

    listOf(
        iosArm64(),
        iosSimulatorArm64(),
        iosX64(),
        macosArm64(),
        macosX64(),
    )

    sourceSets {
        commonMain.dependencies {
            api(project(":core"))
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.okio)
            implementation(libs.ktor.client.core)
            implementation(libs.kotlincrypto.sha2)
        }
        androidMain.dependencies {
            implementation(libs.ktor.client.okhttp)
        }
        jvmMain.dependencies {
            implementation(libs.ktor.client.okhttp)
        }
        // Darwin engine on appleMain — the HttpClientFactory.kt actual lives
        // there (shared between ios + macos). `by getting` is lazy so it
        // resolves at task-evaluation time after the default hierarchy has
        // materialised the source set.
        val appleMain by getting {
            dependencies {
                implementation(libs.ktor.client.darwin)
            }
        }
        named("commonTest") {
            dependencies {
                implementation(kotlin("test"))
                implementation(libs.kotlinx.coroutines.test)
                implementation(libs.ktor.client.mock)
                implementation(libs.okio.fakefilesystem)
            }
        }
    }
}

android {
    namespace = "dev.koml.download"
    compileSdk = 35

    defaultConfig {
        minSdk = 26
    }
}
