// Vendored from Metrolist (github.com/MetrolistGroup/Metrolist, GPL-3.0) at commit 6d08504.
// YouTube Music InnerTube API models and pages. Stream extraction itself is InnerTubeX.
plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.metrolist.innertube"
    compileSdk = 37
    defaultConfig { minSdk = 26 }
    compileOptions {
        isCoreLibraryDesugaringEnabled = true
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
}

kotlin { jvmToolchain(21) }

dependencies {
    api(libs.innertubex)
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.okhttp)
    implementation(libs.ktor.client.content.negotiation)
    implementation(libs.ktor.serialization.json)
    implementation(libs.ktor.client.encoding)
    implementation(libs.timber)
    testImplementation(libs.junit)
    testImplementation(libs.ktor.client.mock)
    coreLibraryDesugaring(libs.desugaring)
}
