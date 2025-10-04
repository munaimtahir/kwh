# Kwh Logger

An Android application for logging electricity meter readings and scheduling reminders to capture usage data.

## Features

- Manage multiple meters with individual reminder preferences.
- Log readings with optional notes and automatic timestamping.
- Browse full history per meter with 7/30/90 day filters, trend chart, and CSV export/import.
- Monitor billing cycle insights with baseline/latest readings, usage projections, and threshold forecasts.
- Delete meters or readings with undo support.
- Snooze reminders directly from notifications.
- Personalize dark theme and snooze length from the Settings screen.
- Built with Jetpack Compose, Room, WorkManager, Hilt, and DataStore.

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
2. Download the Gradle wrapper JAR (omitted from version control to avoid binary files) by running the helper script:

   ```bash
   ./scripts/download-gradle-wrapper.sh
   ```

   If you already have Gradle installed locally, you can instead run `gradle wrapper`.

3. Use the wrapper to build and run unit checks:

   ```bash
   ./gradlew assembleDebug
   ./gradlew test
   ```

4. Open the project in Android Studio to run on a device or emulator.

## Reminders

Reminders are delivered via WorkManager. When a reminder is enabled for a meter, the app schedules a one-time work request at the next desired time and reschedules after every execution.

Users can snooze notifications for a configurable interval (default 60 minutes) without losing future reminder cadence.

## Privacy

All data is stored locally on the device. The application does not send analytics or meter data to any remote service.
