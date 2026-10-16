plugins {
    alias(libs.plugins.agp.app) apply false
    alias(libs.plugins.kotlin) apply false
    alias(libs.plugins.compose.compiler) apply false
    alias(libs.plugins.kotlin.serialization) apply false
}

extra["androidMinSdkVersion"] = 26
extra["androidTargetSdkVersion"] = 36
extra["androidCompileSdkVersion"] = 36
extra["androidBuildToolsVersion"] = "36.0.0"
extra["androidSourceCompatibility"] = JavaVersion.VERSION_17
extra["androidTargetCompatibility"] = JavaVersion.VERSION_17
