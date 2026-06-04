plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.library)
}

kotlin {
    jvmToolchain(17)

    androidTarget()
    jvm()

    listOf(
        iosArm64(),
        iosSimulatorArm64(),
        iosX64(),
    )

    sourceSets {
        commonMain.dependencies {
            implementation(libs.kotlinx.coroutines.core)
        }
    }
}

android {
    namespace = "dev.koml.core"
    compileSdk = 35

    defaultConfig {
        minSdk = 26
    }
}
