@file:Suppress("UnstableApiUsage")

pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        // InnerTubeX (stream extraction) is only published on JitPack.
        exclusiveContent {
            forRepository { maven("https://jitpack.io") }
            filter { includeGroupByRegex("""com\.github\.MetrolistGroup(\..*)?""") }
        }
        google()
        mavenCentral()
    }
}

rootProject.name = "MusicForge"
include(":app")
include(":innertube")
