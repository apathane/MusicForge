plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.ksp) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.compose.compiler) apply false
    alias(libs.plugins.room) apply false
    alias(libs.plugins.android.test) apply false
    alias(libs.plugins.baselineprofile) apply false
}

// Build outputs live in RAM (/dev/shm): this laptop's disk does ~40 MB/s, and a build writes
// over a gigabyte. Lost on reboot, which only costs one full rebuild (the build cache stays on disk).
val ramBuild = File("/dev/shm/musicforge-build")
if (File("/dev/shm").canWrite()) {
    allprojects { layout.buildDirectory.set(ramBuild.resolve(project.name)) }
}
