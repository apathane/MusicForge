plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.ksp)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.room)
    alias(libs.plugins.baselineprofile)
}

// Phase codename doubles as the launcher label until the app gets a real name. The version moves
// with the phase: phase 14 Sonata is 0.14.0-sonata, code 1400 (patch builds within a phase: +1).
val phase = 14
val codename = "Sonata"

android {
    namespace = "dev.musicforge"
    compileSdk = 37

    defaultConfig {
        applicationId = "dev.musicforge.app"
        minSdk = 26
        targetSdk = 36
        versionCode = phase * 100
        versionName = "0.$phase.0-${codename.lowercase()}"
        resValue("string", "app_name", codename)
        buildConfigField("String", "CODENAME", "\"$codename\"")
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    // One permanent release key, or Android refuses to update one build over another: the old
    // laptop, this one and every CI runner each had their own debug key, so updates failed at
    // random. The key comes from the environment (CI secrets, or keys/signing.env locally);
    // without it a release build falls back to the debug key, fine for testing, never published.
    val releaseKeystore = System.getenv("MUSICFORGE_KEYSTORE")?.let(::file)?.takeIf { it.isFile }
    signingConfigs {
        if (releaseKeystore != null) {
            create("release") {
                storeFile = releaseKeystore
                storePassword = System.getenv("MUSICFORGE_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("MUSICFORGE_KEY_ALIAS") ?: "musicforge"
                keyPassword = System.getenv("MUSICFORGE_KEYSTORE_PASSWORD")
            }
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.findByName("release") ?: signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        isCoreLibraryDesugaringEnabled = true
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    buildFeatures {
        compose = true
        buildConfig = true
        resValues = true
    }

    testOptions {
        unitTests.isIncludeAndroidResources = true
    }

    // MigrationTestHelper reads the exported Room schemas as assets, and Robolectric only sees the
    // app's merged assets, so they ride in debug builds (about 100 KB); release ships without them.
    sourceSets.getByName("debug").assets.directories.add("$projectDir/schemas")

    packaging {
        resources.excludes += setOf("/META-INF/{AL2.0,LGPL2.1}", "/META-INF/INDEX.LIST", "/META-INF/*.version")
    }
}

kotlin { jvmToolchain(21) }

room { schemaDirectory("$projectDir/schemas") }

dependencies {
    implementation(project(":innertube"))
    implementation(libs.profileinstaller)
    baselineProfile(project(":baselineprofile"))

    implementation(libs.activity.compose)
    implementation(libs.compose.runtime)
    implementation(libs.compose.foundation)
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.util)
    implementation(libs.compose.animation)
    implementation(libs.compose.ui.tooling.preview)
    debugImplementation(libs.compose.ui.tooling)
    implementation(libs.material3)
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.lifecycle.runtime.compose)
    implementation(libs.lifecycle.process)
    implementation(libs.navigation.compose)

    implementation(libs.media3.exoplayer)
    implementation(libs.media3.session)
    implementation(libs.media3.okhttp)
    implementation(libs.media3.database)

    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)
    implementation(libs.datastore)

    implementation(libs.coil)
    implementation(libs.coil.network.okhttp)
    implementation(libs.okhttp)
    implementation(libs.serialization.json)
    implementation(libs.coroutines.android)
    implementation(libs.palette)
    implementation(libs.reorderable)
    implementation(libs.timber)
    coreLibraryDesugaring(libs.desugaring)

    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.truth)
    testImplementation(libs.coroutines.test)
    testImplementation(libs.room.testing)
}

// Robolectric's native SQLite needs these on JDK 17+.
tasks.withType<Test>().configureEach {
    jvmArgs(
        "--add-opens=java.base/java.io=ALL-UNNAMED",
        "--add-opens=java.base/java.lang=ALL-UNNAMED",
        "--add-opens=java.base/java.util=ALL-UNNAMED",
        "--add-exports=java.base/jdk.internal.access=ALL-UNNAMED",
    )
    maxHeapSize = "2g"
}
