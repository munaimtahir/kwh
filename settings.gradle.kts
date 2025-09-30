pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
    plugins {
        // Android Gradle Plugin (supports compileSdk 35)
        id("com.android.application") version "8.6.1" apply false
        id("com.android.library") version "8.6.1" apply false

        // Kotlin
        id("org.jetbrains.kotlin.android") version "1.9.25" apply false
        id("org.jetbrains.kotlin.kapt") version "1.9.25" apply false

        // Hilt (if your app uses it)
        id("com.google.dagger.hilt.android") version "2.52" apply false
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "kwh"
include(":app")
