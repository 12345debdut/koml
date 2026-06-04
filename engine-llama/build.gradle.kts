import org.jetbrains.kotlin.gradle.plugin.mpp.apple.XCFramework

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.library)
    alias(libs.plugins.skie)
}

kotlin {
    jvmToolchain(17)

    androidTarget()

    val xcf = XCFramework("KomlEngine")
    val iosTargets = listOf(
        iosArm64(),
        iosSimulatorArm64(),
        iosX64(),
    )

    iosTargets.forEach { target ->
        target.binaries.framework {
            baseName = "KomlEngine"
            isStatic = false
            binaryOption("bundleId", "dev.koml.engine")
            // Re-export public types from :core so Swift can see ModelInfo,
            // GenParams, RuntimeConfig, the KomlException hierarchy, etc.
            // Without this, only types defined directly in :engine-llama
            // (LlmKit) appear in the generated KomlEngine-Swift.h header.
            export(project(":core"))
            xcf.add(this)
        }

        target.compilations.getByName("main") {
            cinterops.create("llama") {
                defFile(project.file("src/nativeInterop/cinterop/llama.def"))
                packageName("dev.koml.engine.native")
                includeDirs(rootProject.file("external/llama.cpp/include"))
                includeDirs(rootProject.file("external/llama.cpp/ggml/include"))

                val archDir = when (target.name) {
                    "iosArm64" -> "ios-arm64"
                    "iosSimulatorArm64" -> "ios-simulator-arm64"
                    "iosX64" -> "ios-simulator-x64"
                    else -> error("Unsupported iOS target: ${target.name}")
                }
                val libDir = rootProject.file("build/llama-ios/$archDir/lib")
                extraOpts("-libraryPath", libDir.absolutePath)
            }
        }
    }

    sourceSets {
        commonMain.dependencies {
            api(project(":core"))
            implementation(libs.kotlinx.coroutines.core)
        }
    }
}

skie {
    analytics {
        disableUpload.set(true)
    }
}

android {
    namespace = "dev.koml.engine"
    compileSdk = 35

    defaultConfig {
        minSdk = 26

        ndk {
            abiFilters += "arm64-v8a"
        }

        externalNativeBuild {
            cmake {
                arguments("-DLLAMA_CPP_DIR=${rootProject.projectDir}/external/llama.cpp")
                cppFlags("-std=c++17")
            }
        }
    }

    externalNativeBuild {
        cmake {
            path = file("src/androidMain/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }
}
