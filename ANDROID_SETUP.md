# Android Project Setup Guide

## Overview
This project follows the standard Android application structure using the Android Gradle Plugin and modern Android development practices.

## Project Structure Explanation

### Root Level Files
- `build.gradle` - Project-level build configuration
- `settings.gradle` - Gradle settings defining project modules
- `gradlew` / `gradlew.bat` - Gradle wrapper scripts for cross-platform builds
- `local.properties.template` - Template for local environment configuration

### App Module (`app/`)
- `build.gradle` - App-level build configuration with dependencies
- `proguard-rules.pro` - ProGuard configuration for code obfuscation/optimization
- `src/main/` - Main source code and resources
- `src/test/` - Unit tests
- `src/androidTest/` - Instrumentation tests

### Source Code Structure
```
app/src/main/
├── AndroidManifest.xml              # App configuration and permissions
├── java/com/kwh/                    # Java source code
│   └── MainActivity.java            # Main activity class
└── res/                             # Resources
    ├── layout/                      # XML layouts
    │   └── activity_main.xml        # Main activity layout
    ├── values/                      # App values
    │   ├── colors.xml               # Color definitions
    │   ├── strings.xml              # String resources
    │   └── themes.xml               # App themes
    └── xml/                         # XML configurations
        ├── backup_rules.xml         # Backup configurations
        └── data_extraction_rules.xml # Data extraction rules
```

## Key Features Included

### 1. Modern Android Stack
- **Target SDK**: Android 14 (API 34)
- **Min SDK**: Android 7.0 (API 24)
- **Gradle**: 8.2
- **Android Gradle Plugin**: 8.2.0

### 2. Dependencies
- **AndroidX AppCompat**: 1.6.1 - Backward compatibility
- **Material Design**: 1.11.0 - Material Design components
- **ConstraintLayout**: 2.1.4 - Flexible layouts

### 3. Testing Framework
- **JUnit**: 4.13.2 - Unit testing
- **AndroidX Test**: 1.1.5 - Instrumentation testing
- **Espresso**: 3.5.1 - UI testing

### 4. Build Configuration
- **Java 8** compatibility
- **ProGuard** ready for release builds
- **Material Design 3** theme implementation

## Getting Started

### Prerequisites
1. Android Studio (latest version)
2. Android SDK with API 24+ installed
3. Java 8 or higher

### Setup Steps
1. Copy `local.properties.template` to `local.properties`
2. Update the SDK path in `local.properties`:
   ```
   sdk.dir=/path/to/your/android/sdk
   ```
3. Open project in Android Studio
4. Sync with Gradle files
5. Build and run

### Command Line Build
```bash
# Build debug APK
./gradlew assembleDebug

# Run unit tests
./gradlew test

# Run instrumentation tests (requires connected device/emulator)
./gradlew connectedAndroidTest
```

## Package Structure
- **Package Name**: `com.kwh`
- **App Name**: "KWH"
- **Main Activity**: `MainActivity`

This scaffold provides a solid foundation for developing an Android application with modern tools and best practices.