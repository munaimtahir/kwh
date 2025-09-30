// Top level build configuration.
//
// The Android Gradle Plugin (AGP) version defined here must match the
// version declared in settings.gradle.kts. The original file used
// AGP 8.5.1 which conflicted with the pluginManagement block in
// settings.gradle.kts (8.6.1). This mismatch prevented the project
// from syncing and building on CI. To fix the issue we upgrade
// the AGP version here to 8.6.1 and leave the other plugin versions
// unchanged. See settings.gradle.kts for the authoritative versions.
plugins {
    id("com.android.application") version "8.6.1" apply false
    id("org.jetbrains.kotlin.android") version "1.9.25" apply false
    id("com.google.devtools.ksp") version "1.9.25-1.0.20" apply false
    id("com.google.dagger.hilt.android") version "2.52" apply false
}
