# Changelog
## [1.1.0] — 2026-07-30
### Play Store Release Readiness
- Changed `applicationId` from `com.example.kwh` (Play Console hard-rejects `com.example.*`) to `com.vexel.kwh`. `namespace`/Kotlin package remain `com.example.kwh` — unaffected, since `applicationId` and `namespace` are independent in AGP.
- Generated a dedicated release keystore (`release-keystore.jks`, 2048-bit RSA, 27-year validity) and wired real release signing via `keystore.properties` (both gitignored, never committed); falls back to debug signing when the file is absent so CI/contributor checkouts keep building. **The keystore + its password must be backed up by the developer outside this repo — losing it means the app can never be updated again post-launch** (unless enrolled in Play App Signing, which allows Google-assisted recovery).
- Bumped `compileSdk`/`targetSdk` 35 → 36 (Android 16) for Play's target-API policy.
- Enabled edge-to-edge (`enableEdgeToEdge()` in `MainActivity`) — verified safe since every screen already routes through Material3 `Scaffold`, which applies safe-drawing-inset padding by default. While in there, also fixed a pre-existing bug: the Settings "Dark theme" toggle was persisted but never actually applied to `KwhTheme` (the app always followed system theme); it's now wired through.
- Enabled R8 minification + resource shrinking for release builds (`isMinifyEnabled`/`isShrinkResources = true`), cutting the release APK from ~33MB to ~3MB. Added explicit `-keep` rules for the two WorkManager workers (reflection-instantiated by class name) and `data/**` (Room), plus `-keepattributes SourceFile,LineNumberTable` so crash stack traces stay symbolicated against the generated `mapping.txt` (upload this file to Play Console for deobfuscated crash reports).
- Verified the actual signed, minified release APK end-to-end on a real emulator (not just the debug build): meter/environment CRUD, activation, and — critically — forced a `SwitchReminderWorker` job to execute via `adb shell cmd jobscheduler run`, confirming it completed and re-scheduled itself (proof Hilt/Room/WorkManager all survived R8 intact).
- **Still outside code scope**: a publicly hosted Privacy Policy URL for the Play Console listing (README has a privacy note, but Console requires a live URL), and the Play Console store-listing assets (screenshots, descriptions, Data Safety form).

### Full Regression + Manual QA (emulator/adb)
- Fixed a pre-existing, unrelated `HomeScreenTest` (instrumented) that referenced a long-removed `HomeScreen` API signature — it predated this initiative and had been silently blocking `./gradlew connectedAndroidTest` entirely (CI only runs `assembleDebug test`, so this went unnoticed). Rewritten against the current `HomeScreen`/`HomeUiState` API; instrumented suite is green again (2/2).
- Manual pass on a real Android 15 (API 35) emulator: environment creation/assignment, grouped Home display, manual activation, the switch-recommendation banner (both the "not yet due" and OVERDUE cases), "Mark switched" end-to-end (activation swap + outcome logging + calibration), the switch-accuracy indicator, CSV export, History, and Settings — zero crashes; full logcat scan showed no app exceptions. See `docs/QA-Checklist.md`.
- Renamed the Meter Settings environment-picker button from "Environment" (same text as its section header) to "Change" for clarity.

### Environment Switching — M10 Polish & Release
- CSV export/import (`HistoryViewModel`, format bumped to v2) now carries environment name, safety buffer days, and the meter's activation log alongside readings; v1 files remain importable unchanged (additive/backward compatible).
- Fixed a latent CSV bug (present since `thresholds_csv`'s "200,300"-style default was introduced): the naive comma split meant a multi-value thresholds field would have silently misaligned any column added after it. Reading rows now quote `thresholds_csv` and the importer parses quoted fields correctly.
- Updated `DataModel.md`, `Architecture.md`, `application_structure.md`, and `ALGORITHMS.md` to reflect the completed M6–M10 Environment Switching feature set.
- Version bump: 1.0.1 (versionCode 2) → 1.1.0 (versionCode 3), marking the Environment Switching initiative's release.

### Environment Switching — M9 Adaptive Calibration (2026-07-30)
- Added `SwitchOutcomeEntity`/`switch_outcomes` table (migration 3→4, FK cascade on meter delete) logging predicted vs. actual switch date/units at each switch.
- `MeterRepository.activateMeter()` now records a switch outcome for the outgoing meter (if it had an active recommendation) before closing its activation, then recalibrates that meter's `safety_buffer_days` from its last up-to-6 outcomes: widens by 1 day (clamped to 10) if the average outcome landed over the threshold, tightens by 1 day (clamped to 1) if the average margin is comfortably (>15%) under it — a bounded rolling-average nudge, not a model, per `docs/AGENT_ENVIRONMENT_SWITCHING.md`.
- **Correctness fix carried in from M8**: switch recommendations previously reused `CycleStats.nextThreshold`, which for multi-threshold meters (or once the lowest threshold is already crossed) retargets to a higher threshold instead of flagging overdue. Added `SwitchAdvisor.switchThresholdForecast`, pinned to the *lowest* configured threshold (per the spec's Open Question 2 assumption) and which still fires (past-dated, i.e. overdue) once that threshold is already exceeded.
- Meter Settings gained an editable "Safety buffer (days)" field (1–10, saved alongside the rest of the form when the meter is in an environment) and a read-only "Switch accuracy" indicator (average days early/late, average units margin, sample size) so the user can see why the buffer changed.
- Added `MeterDatabaseMigrationTest` 3→4 case, `SwitchAdvisorTest` cases for `switchThresholdForecast`, and two calibration-convergence tests in `MeterRepositoryEnvironmentTest` (tightening to the 1-day floor, widening to the 10-day ceiling) using synthetic switch-history sequences.

### Environment Switching — M8 Switch Recommendation Engine (2026-07-30)
- `MeterRepository` cycle-stats math now uses an active-day-weighted rate (`activeDays` over overlapping activation rows, clipped to "now"/cycle end) for meters that belong to an environment and have activation history, instead of naive calendar-elapsed days — idle periods (another meter carrying the load) no longer distort the rate. Meters with no activation rows are unaffected.
- Added `billing/SwitchAdvisor.kt`: turns a meter's existing threshold-ETA forecast into a `switchByDate` + `SwitchUrgency` (NORMAL/SOON/OVERDUE) using its `safety_buffer_days`.
- `EnvironmentOverview` (from `environmentOverviews`) now carries `switchRecommendation` and `switchTargetMeterId` (the non-active sibling with the lowest current-cycle usage — an assumption for 3+-meter environments, since the spec only confirms the 2-meter case).
- Home screen shows a "Switch to X by date" banner per environment (urgency-colored) with a one-tap "Mark switched" action; Meter Detail shows the same banner when viewing the active meter.
- Added `SwitchReminderWorker` + `SwitchReminderScheduler`, a new self-rescheduling daily WorkManager check (`switch_reminder_{meterId}` unique work name) that notifies once urgency reaches SOON/OVERDUE. Scheduling is driven by activation changes (`activateMeter` call sites in `HomeViewModel`/`MeterSettingsViewModel`/`MeterDetailViewModel`), not a user toggle; deleting a meter or unassigning it from an environment cancels its check.
- Added `SwitchAdvisorTest`, active-day-rate and switch-recommendation cases in `MeterRepositoryEnvironmentTest`, and a `HomeViewModelTest` case covering the schedule/cancel wiring on meter switch.

### Environment Switching — M7 Environments & Manual Activation (2026-07-30)
- `MeterRepository` gained environment/activation management: `createEnvironment`/`renameEnvironment`/`deleteEnvironment` (delete unassigns member meters back to "Ungrouped" instead of leaving a dangling reference), `assignMeterToEnvironment`, `activateMeter()` (idempotent; closes any other open activation in the environment before opening a new one), `getActiveMeter()`, `activationHistory()`, `isMeterActive()`, plus reactive `environmentOverviews` / `ungroupedMeterOverviews` flows for grouped UI.
- Added `MeterDao.getMetersForEnvironment`, `getOpenActivationsForEnvironment`, `observeActiveActivations` to support the above.
- Home screen now groups meters by environment (with an "Ungrouped" bucket for meters not yet assigned) and shows an "Active" chip + "Set as active" action per meter; behavior for meters with `environment_id = null` is unchanged.
- Meter Settings gained an Environment section (assign to an existing environment or create one, unassign, set-as-active) and a read-only activation history list.
- Added `MeterRepositoryEnvironmentTest`, `MeterSettingsViewModelTest`, and a `HomeViewModelTest` grouping case covering activation idempotency, switching, environment delete/unassign, and grouped/ungrouped overview separation.

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
