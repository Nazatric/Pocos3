// PocoS3 UI app module.
//
// The native emulator core (libpocos3-core.so) is built OUTSIDE Gradle by
// android/configure.sh. Gradle only builds the JNI glue
// (src/main/cpp/native-lib.cpp) and packages the prebuilt core .so from
// jniLibs/arm64-v8a/.
//
// This keeps Gradle's CMake/Ninja work to ~2 seconds per sync instead of
// dragging LLVM into every IDE build.

import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.pocos3"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.pocos3"
        // Set per variant by android/build-variants.sh: 33 for the A13 build (NDK 28),
        // 35 for the A15 build (NDK 29). Must agree with the API the core was compiled
        // against or dlopen fails at boot.
        minSdk = (project.findProperty("pocos3.minSdk") as String?)?.toInt() ?: 33
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0-dev"

        // Build the JNI glue via CMake. The glue .so is small and fast to compile.
        externalNativeBuild {
            cmake {
                path = file("../../CMakeLists.txt")
                version = "3.30.0+"
                // Build only the JNI glue target; the core .so is built out-of-band.
                targets += listOf("pocos3-glue")
                arguments += listOf(
                    "-DANDROID_STL=c++_shared",
                    "-DCMAKE_BUILD_TYPE=Release"  // overridden per-variant below
                )
                cppFlags += listOf("-std=c++23", "-fno-exceptions", "-fno-rtti")
            }
        }

        // ABI filter: arm64-v8a only. PocoS3 does not ship for other ABIs.
        ndk {
            abiFilters += listOf("arm64-v8a")
        }

        // PocoS3 UI reads these to decide which code paths to expose.
        // Default: github variant. Play flavor (if added later) overrides.
        buildConfigField("boolean", "IN_APP_UPDATER", "false")
        buildConfigField("boolean", "STORAGE_ALL_FILES", "false")

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables { useSupportLibrary = true }
    }

    // Build types. Debug is for device-side iteration; release strips + R8s.
    buildTypes {
        debug {
            isMinifyEnabled = false
            isDebuggable = true
            packaging { jniLibs { keepDebugSymbols += "**/*.so" } }
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            isDebuggable = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            // The native core .so is prebuilt; do not let R8 strip it.
            packaging {
                jniLibs {
                    useLegacyPackaging = false
                }
            }
        }
    }

    // The Compose compiler is wired through the Kotlin plugin from 2.0+,
    // so we don't need a separate compiler extension repo here.
    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        isCoreLibraryDesugaringEnabled = false
    }

    kotlinOptions {
        jvmTarget = "17"
        freeCompilerArgs = freeCompilerArgs + listOf(
            "-opt-in=kotlin.RequiresOptIn",
            "-opt-in=androidx.compose.material3.ExperimentalMaterial3Api",
            "-opt-in=androidx.compose.animation.ExperimentalAnimationApi",
            "-opt-in=kotlinx.serialization.ExperimentalSerializationApi"
        )
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            excludes += "/META-INF/DEPENDENCIES"
            excludes += "/META-INF/LICENSE*"
            excludes += "/META-INF/NOTICE*"
        }
    }

    // Source set that ships prebuilt resources (shader patches, icons) and
    // gets a couple of files from the github flavor directory.
    sourceSets {
        getByName("main") {
            assets.srcDirs("src/main/assets")
            // The prebuilt core .so lives in jniLibs/arm64-v8a/ after the
            // user runs android/configure.sh. Gradle picks it up automatically.
        }
    }
}

dependencies {
    // Core Android
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.documentfile)

    // Compose
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons.extended)
    debugImplementation(libs.compose.ui.tooling)

    // Audio (AAudio backend for PS3 audio)
    implementation(libs.oboe)

    // Image loading (game covers)
    implementation(libs.coil.compose)

    // Serialization for profile JSON
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.fragment.ktx)

    // Material (for XML splash)
    implementation(libs.material)

    // Test
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso)
}
