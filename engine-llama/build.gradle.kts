import org.jetbrains.kotlin.gradle.plugin.mpp.apple.XCFramework

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.library)
    alias(libs.plugins.skie)
}

// Pre-built native libs land at build/llama-jvm/<arch>/libkoml-jni.dylib after
// scripts/build-llama-jvm.sh runs. This task stages them under
// META-INF/native/<arch>/ so they get packaged into the JVM JAR and can be
// extracted at runtime by LlamaNative.jvm.kt.
val collectJvmNativeLibs by tasks.registering(Copy::class) {
    val sourceRoot = rootProject.layout.projectDirectory.dir("build/llama-jvm")
    val destRoot = layout.buildDirectory.dir("generated/native-resources")

    from(sourceRoot.dir("macos-arm64")) {
        include("libkoml-jni.dylib")
        into("META-INF/native/macos-arm64")
    }
    from(sourceRoot.dir("macos-x64")) {
        include("libkoml-jni.dylib")
        into("META-INF/native/macos-x64")
    }
    into(destRoot)
}

kotlin {
    jvmToolchain(17)

    compilerOptions {
        freeCompilerArgs.add("-Xexpect-actual-classes")
    }

    androidTarget()
    jvm()

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

    // macOS native targets — same cinterop bindings as iOS (shared via appleMain),
    // but a separate .def with Metal linkage since macOS doesn't have the b5460
    // embed bug iOS does. Static libs come from scripts/build-llama-macos-native.sh.
    val macosTargets = listOf(
        macosArm64(),
        macosX64(),
    )

    macosTargets.forEach { target ->
        target.compilations.getByName("main") {
            cinterops.create("llama") {
                defFile(project.file("src/nativeInterop/cinterop/llama-macos.def"))
                packageName("dev.koml.engine.native")
                includeDirs(rootProject.file("external/llama.cpp/include"))
                includeDirs(rootProject.file("external/llama.cpp/ggml/include"))

                val archDir = when (target.name) {
                    "macosArm64" -> "macos-arm64"
                    "macosX64" -> "macos-x64"
                    else -> error("Unsupported macOS target: ${target.name}")
                }
                val libDir = rootProject.file("build/llama-macos-native/$archDir/lib")
                extraOpts("-libraryPath", libDir.absolutePath)
            }
        }
    }

    sourceSets {
        commonMain.dependencies {
            api(project(":core"))
            api(project(":storage"))
            api(project(":download"))
            api(project(":registry"))
            implementation(libs.kotlinx.coroutines.core)
        }
        named("jvmMain") {
            resources.srcDir(collectJvmNativeLibs.map { it.destinationDir })
        }
        named("commonTest") {
            dependencies {
                implementation(kotlin("test"))
                implementation(libs.kotlinx.coroutines.test)
            }
        }
        named("jvmTest") {
            dependencies {
                // JUnit 4 for org.junit.Assume.assumeTrue — gates the
                // network-bound JvmEndToEndTest behind KOML_INTEGRATION_TESTS=1.
                implementation("junit:junit:4.13.2")
            }
        }
    }
}

// Make the resources processing depend on the native lib copy so the JAR
// always contains the freshest .dylib slices.
tasks.named("jvmProcessResources").configure {
    dependsOn(collectJvmNativeLibs)
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
