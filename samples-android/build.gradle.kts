plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

kotlin {
    jvmToolchain(17)
}

android {
    namespace = "dev.koml.samples.android"
    compileSdk = 35

    defaultConfig {
        applicationId = "dev.koml.samples.android"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"
    }
}

dependencies {
    implementation(project(":engine-llama"))
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.androidx.lifecycle.runtime)
    implementation(libs.androidx.appcompat)
}
