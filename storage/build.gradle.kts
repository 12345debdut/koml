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
    )

    sourceSets {
        commonMain.dependencies {
            api(project(":core"))
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.okio)
        }
        androidMain.dependencies {
            implementation(libs.androidx.startup)
        }
    }
}

android {
    namespace = "dev.koml.storage"
    compileSdk = 35

    defaultConfig {
        minSdk = 26
    }
}
