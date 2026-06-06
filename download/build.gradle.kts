plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.library)
}

kotlin {
    jvmToolchain(17)

    compilerOptions {
        freeCompilerArgs.add("-Xexpect-actual-classes")
    }

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
        // Darwin engine: needed on every Apple target. The intermediate
        // appleMain / macosMain source sets aren't named in this hierarchy,
        // so we attach to each declared leaf directly.
        iosMain.dependencies {
            implementation(libs.ktor.client.darwin)
        }
        named("macosArm64Main") {
            dependencies { implementation(libs.ktor.client.darwin) }
        }
        named("macosX64Main") {
            dependencies { implementation(libs.ktor.client.darwin) }
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
