// Top level build configuration using buildscript block for compatibility
buildscript {
    repositories {
        maven {
            url = uri("https://maven.google.com")
        }
        mavenCentral()
    }
    dependencies {
        classpath("com.android.tools.build:gradle:8.4.0")
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:1.9.25")
        classpath("com.google.dagger:hilt-android-gradle-plugin:2.52")
    }
}

plugins {
    id("com.google.devtools.ksp") version "1.9.25-1.0.20" apply false
}
