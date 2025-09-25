# Kwh Logger

An Android application for logging electricity meter readings and scheduling reminders to capture usage data.

## Features

- Manage multiple meters with individual reminder preferences.
- Log readings with optional notes and automatic timestamping.
- View the latest reading and next scheduled reminder for each meter.
- Schedule or disable reminders using WorkManager notifications.
- Built with Jetpack Compose, Room, and WorkManager.

## Project structure

```
app/
  src/main/java/com/example/kwh/
    data/          # Room entities, DAO, and database access
    reminders/     # WorkManager worker and reminder scheduler
    repository/    # Repository facade for data + reminder updates
    ui/            # Compose UI screens, components, and theme
  src/main/res/    # Resources (strings, themes, icons)
```

## Getting started

1. Ensure the Android SDK is installed and configure `local.properties` with the `sdk.dir` path or set the `ANDROID_HOME` environment variable.
2. Generate the Gradle wrapper JAR (omitted from version control to avoid binary files) using a local Gradle installation:

   ```bash
   gradle wrapper
   ```

3. Use the wrapper to build and run unit checks:

   ```bash
   ./gradlew assembleDebug
   ```

4. Open the project in Android Studio to run on a device or emulator.

## Reminders

Reminders are delivered via WorkManager. When a reminder is enabled for a meter, the app schedules a one-time work request at the next desired time and reschedules after every execution.
