plugins {
    alias(libs.plugins.agp.app) apply false
    alias(libs.plugins.kotlin) apply false
    alias(libs.plugins.compose.compiler) apply false
    alias(libs.plugins.kotlin.serialization) apply false
}

extra["androidMinSdkVersion"] = 26

// Compiled against 37 because miuix 0.9.4 and material-kolor 5.0.1 both demand
// it, but still targeting 36: compiling newer and targeting older is the normal
// split, and it keeps the app on the runtime behaviour these screens were
// written for until there is a device to check a target bump on.
extra["androidTargetSdkVersion"] = 36
extra["androidCompileSdkVersion"] = 37
extra["androidBuildToolsVersion"] = "37.0.0"
extra["androidSourceCompatibility"] = JavaVersion.VERSION_17
extra["androidTargetCompatibility"] = JavaVersion.VERSION_17
