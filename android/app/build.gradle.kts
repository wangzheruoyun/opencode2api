@file:Suppress("UnstableApiUsage")

import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.agp.app)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.kotlin.serialization)
}

val androidCompileSdkVersion = rootProject.extra["androidCompileSdkVersion"] as Int
val androidCompileSdkVersionMinor = rootProject.extra["androidCompileSdkVersionMinor"] as Int
val androidMinSdkVersion = rootProject.extra["androidMinSdkVersion"] as Int
val androidTargetSdkVersion = rootProject.extra["androidTargetSdkVersion"] as Int
val androidSourceCompatibility = rootProject.extra["androidSourceCompatibility"] as JavaVersion
val androidTargetCompatibility = rootProject.extra["androidTargetCompatibility"] as JavaVersion

// Injected by the GitHub Actions workflow; falls back for local builds.
val appVersionName: String = providers.gradleProperty("O2A_VERSION_NAME").getOrElse("1.0.0")
val appVersionCode: Int = providers.gradleProperty("O2A_VERSION_CODE").getOrElse("1").toInt()

android {
    namespace = "com.opencode2api.android"

    // Written as the release/minor form rather than the bare `compileSdk = 37`
    // because API 37 has no plain platform package: AGP has to be told which
    // minor release of the level to compile against.
    compileSdk {
        version =
            release(androidCompileSdkVersion) {
                minorApiLevel = androidCompileSdkVersionMinor
            }
    }

    defaultConfig {
        applicationId = "com.opencode2api.android"
        minSdk = androidMinSdkVersion
        targetSdk = androidTargetSdkVersion
        versionCode = appVersionCode
        versionName = appVersionName

        // The Go gateway ships as a per-ABI "lib*.so" so the Android package
        // manager extracts it into nativeLibraryDir with the executable bit
        // set. Only ABIs we actually build a gateway for are kept, otherwise
        // the app would install on a device with no bundled binary.
        ndk {
            abiFilters += listOf("arm64-v8a", "x86_64")
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
            isMinifyEnabled = false
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    // useLegacyPackaging is what makes the package manager unpack the bundled
    // Go binary to disk with the executable bit set, instead of mapping it
    // straight out of the APK, which would leave nothing to exec. It sets
    // android:extractNativeLibs="true" in the merged manifest, and it is the
    // only thing that does: writing the attribute into the source manifest as
    // well earns a manifest-merger warning, so the APK is checked for it in CI
    // rather than asserted in two places.
    packaging {
        jniLibs {
            useLegacyPackaging = true
        }
        resources {
            excludes += setOf(
                "/META-INF/{AL2.0,LGPL2.1}",
                "/META-INF/DEPENDENCIES",
                "/META-INF/INDEX.LIST",
            )
        }
    }

    compileOptions {
        sourceCompatibility = androidSourceCompatibility
        targetCompatibility = androidTargetCompatibility
    }

    buildFeatures {
        compose = true
        // The about screen reads VERSION_NAME back out of BuildConfig so it
        // cannot drift from what the packaging block actually stamped.
        buildConfig = true
    }

    androidResources {
        // Keep the Go binary byte-identical; compressing it would force a
        // decompress-to-disk step the loader does not do for native libs.
        noCompress += listOf("so")
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.activity.compose)

    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    debugImplementation(libs.androidx.compose.ui.tooling)

    implementation(libs.miuix.ui)
    implementation(libs.miuix.icons)

    implementation(libs.material.kolor)

    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.okhttp)

    testImplementation(libs.junit)
}
