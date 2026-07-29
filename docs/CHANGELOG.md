# Changelog
## [Unreleased] — 2025-09-30
### Environment Switching — M6 Schema Groundwork (2026-07-29)
- Added `EnvironmentEntity` (environments table) and `MeterActivationEntity` (meter_activations table, FK cascade on meter delete) to support grouping meters and tracking active-meter switches.
- Added nullable `environment_id` and `safety_buffer_days` (default 3) columns to `MeterEntity`; existing ungrouped meters are unaffected.
- Added Room migration 2→3 (additive, backward compatible).
- Added DAO support for environment CRUD and active-activation/active-meter queries.
- Added Robolectric-backed migration test (`MeterDatabaseMigrationTest`) and real in-memory Room DAO round-trip tests (`MeterActivationDaoTest`); pinned `@Config(sdk = [34])` since installed Robolectric 4.11.1 doesn't support API 35 (the app's compileSdk/targetSdk).
- Fixed a pre-existing `HomeViewModelTest` compile break (missing `recordedAt` argument) that was blocking `./gradlew test` for unrelated reasons.

### Build System & CI Fixes
- Fixed invalid Compose BOM version (2025.09.01 → 2024.10.00)
- Fixed non-existent Android Gradle Plugin version (8.6.1 → 8.4.0)
- Migrated from plugins DSL to buildscript approach for better AGP compatibility
- Added missing test dependencies: Robolectric, WorkManager testing, coroutines-test, Turbine
- Added Hilt testing dependencies for instrumentation tests
- Configured testOptions for Robolectric with includeAndroidResources
- Streamlined GitHub Actions workflow by removing unnecessary android-actions/setup-android
- Added Gradle caching via gradle/actions/setup-gradle for faster CI builds
- Added unit test execution and test results artifact upload to CI workflow

## [1.0.1] — 2024-11-26
- Added CSV import success snackbar and pluralized messaging for imported readings.
- Tuned reminder notification snooze action to reflect configured duration.
- Added Robolectric coverage for History CSV imports and reminder snooze scheduling.
- Completed QA checklist across API 26/30/34 devices and documented results.

## [1.0.0] — 2024-11-24
- Added history screen with 7/30/90 day filters, undoable delete, CSV export/import, and trend chart.
- Implemented reminder deletion cascades, validation, and permission-safe scheduling updates.
- Integrated Hilt dependency injection, DAO and ViewModel unit tests, and Compose UI smoke tests.
- Added snooze actions to reminder notifications with configurable duration.
- Introduced Settings screen for dark theme toggle and snooze length, plus refreshed app theme and launcher icon.
- Updated README with privacy notice, bumped version to 1.0.0, and logged release tasks.
