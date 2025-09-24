# KWH Android App

A mobile application for managing kilowatt-hour (KWH) consumption and tracking energy usage.

## Project Structure

This is a native Android application built with Java and the Android SDK.

### Prerequisites

- Android Studio (latest version recommended)
- Android SDK (API level 24 or higher)
- Java 8 or higher
- Gradle 8.2

### Setup

1. Clone this repository
2. Copy `local.properties.template` to `local.properties` and update the Android SDK path
3. Open the project in Android Studio
4. Sync the project with Gradle files
5. Build and run the app

### Building

To build the project from command line:

```bash
./gradlew build
```

To run tests:

```bash
./gradlew test
```

To build APK:

```bash
./gradlew assembleDebug
```

## Features

- Basic Android app structure
- Material Design themes
- Unit and instrumentation test setup
- ProGuard configuration for release builds

## License

This project is licensed under the Apache License 2.0 - see the [LICENSE](LICENSE) file for details.