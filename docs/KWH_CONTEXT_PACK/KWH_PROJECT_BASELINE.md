# KWH Android App — Technical Project Baseline

## 1. Executive Summary & Application Purpose

**KWH** (packaged as `com.vexel.kwh`, namespace `com.example.kwh`, app label `kWh Logger`) is an offline-first native Android utility designed for multi-meter residential and commercial properties operating under non-linear, tiered (slab-based) electricity tariffs.

### Core Problem Solved
In regions where utility billing escalates steeply upon crossing monthly consumption slabs (e.g., crossing 200 kWh or 300 kWh doubles or triples the per-unit rate across the entire bill or slab), households with multiple electricity meters can save significantly by distributing electricity consumption across meters. 

### Core Use Cases Supported
1. **Multi-Meter Management**: Independent tracking and configuration for multiple meters.
2. **Manual Reading Logs**: Date-stamped reading entries, supporting past/backdated readings, optional notes, and full edit/delete lifecycle with undo.
3. **Billing Cycle Anchoring**: Floating billing cycles tied to a monthly utility billing anchor day (1–31), automatically accounting for irregular month lengths and leap years.
4. **Consumption & Projection Engine**: Dynamic calculation of current-cycle units consumed, daily consumption rate, and projected end-of-cycle consumption.
5. **Threshold & Slab Monitoring**: Tracking progress toward user-defined consumption thresholds (e.g., 200, 300 units).
6. **Forecasting Engine**: Linear extrapolation predicting the specific calendar date a meter will breach its next threshold.
7. **Environment Grouping & Switch Optimization**: Grouping meters into an "Environment" (e.g., "Main House") where exactly one meter is active at a time. The app generates proactive switch recommendations ("Switch to Meter B by March 24") with automated, adaptive safety buffer calibration.
8. **Automated Background Reminders**: WorkManager-scheduled reading reminders and proactive threshold/switch alerts with interactive snooze capabilities.
9. **Data Portability**: Full CSV export and import (v2 format) encapsulating meter configuration, readings, and activation histories.

---

## 2. Technology Stack & Build Specifications

| Property | Value / Version | Notes |
|---|---|---|
| **Operating System** | Android (Native) | 100% Offline, zero internet permissions |
| **Application ID** | `com.vexel.kwh` | Updated in v1.1.0 for Play Store compliance |
| **Namespace / Package** | `com.example.kwh` | Historical package root |
| **Version Name** | `1.1.0` | Production release baseline |
| **Version Code** | `3` | Incremented with Environment Switching release |
| **Minimum SDK (`minSdk`)** | `24` (Android 7.0 Nougat) | Supported via Java 8+ API desugaring (`desugar_jdk_libs:2.0.4`) |
| **Target SDK (`targetSdk`)** | `36` (Android 16) | Meets latest Google Play target API policy |
| **Compile SDK (`compileSdk`)** | `36` | Build warning emitted by AGP 8.4.0 (tested up to API 34) |
| **Java Target** | `JavaVersion.VERSION_17` | Required for AGP 8.x |
| **Kotlin Version** | `1.9.25` | `kotlinCompilerExtensionVersion = "1.5.15"` |
| **Gradle Version** | `8.7` | Configured via Gradle Wrapper |
| **Android Gradle Plugin (AGP)** | `8.4.0` | Applied via root `buildscript` block |
| **UI Framework** | Jetpack Compose | Material 3 (`androidx.compose:compose-bom:2024.10.00`) |
| **Dependency Injection** | Dagger Hilt `2.52` | AndroidEntryPoint, ViewModels, WorkerEntryPoint |
| **Persistence (Database)** | AndroidX Room `2.6.1` | SQLite database `meter.db`, schema version 4 |
| **Persistence (Preferences)** | Jetpack DataStore Preferences `1.1.1` | Settings datastore (`dark_theme`, `snooze_minutes`) |
| **Background Scheduling** | AndroidX WorkManager `2.9.1` | `CoroutineWorker`, unique one-time chained jobs |
| **Coroutines & Reactive** | Kotlinx Coroutines `1.8.1` | StateFlow, Flow, Channels |
| **Date/Time Handling** | `java.time` (`LocalDate`, `Instant`, `Clock`) | Desugared on API < 26 |
| **Testing Stack** | JUnit 4, Robolectric 4.11.1, Turbine, Room Testing, Compose UI Test | Tests mock/fake Clock and Room DB |

---

## 3. Repository Structure

```
/home/munaim/srv/apps/kwh/
├── app/
│   ├── build.gradle.kts           # Module build script, dependencies, signing configs, ProGuard
│   ├── proguard-rules.pro         # ProGuard rules (keeps WorkManager workers and Room entities)
│   └── src/
│       ├── main/
│       │   ├── AndroidManifest.xml # Permissions (POST_NOTIFICATIONS), SnoozeReceiver, MainActivity
│       │   ├── java/com/example/kwh/
│       │   │   ├── KwhApplication.kt             # Application entry (@HiltAndroidApp)
│       │   │   ├── MainActivity.kt               # Single Activity hosting Compose NavHost & Dialogs
│       │   │   ├── billing/                      # Cycle math & switch advisor domain logic
│       │   │   │   ├── BillingCycleCalculator.kt # CycleWindow calculation interface & default impl
│       │   │   │   ├── CycleStats.kt             # Cycle calculation snapshots & threshold records
│       │   │   │   └── SwitchAdvisor.kt          # Multi-meter switch recommendation engine
│       │   │   ├── data/                         # Room database layer
│       │   │   │   ├── MeterDatabase.kt          # Room DB (v4), migrations (1->2, 2->3, 3->4)
│       │   │   │   ├── MeterDao.kt               # Complete DAO queries, transactions, Flows
│       │   │   │   ├── MeterEntity.kt            # meters table
│       │   │   │   ├── MeterReadingEntity.kt     # meter_readings table (FK cascade to meters)
│       │   │   │   ├── EnvironmentEntity.kt      # environments table
│       │   │   │   ├── MeterActivationEntity.kt  # meter_activations table (FK cascade to meters)
│       │   │   │   ├── SwitchOutcomeEntity.kt    # switch_outcomes table (FK cascade to meters)
│       │   │   ├── di/                           # Dagger Hilt dependency injection modules
│       │   │   │   ├── BillingModule.kt          # Provides Calculator & Clock
│       │   │   │   ├── DatabaseModule.kt         # Provides MeterDatabase & MeterDao
│       │   │   │   ├── ReminderModule.kt         # Provides Schedulers
│       │   │   │   ├── RepositoryModule.kt       # Provides MeterRepository
│       │   │   │   ├── SettingsModule.kt         # Provides SettingsRepository & SnoozeReader
│       │   │   │   └── UiModule.kt               # Provides StringResolver
│       │   │   ├── reminders/                    # WorkManager background tasks & notifications
│       │   │   │   ├── ReminderScheduler.kt      # Helper to schedule MeterReminderWorker
│       │   │   │   ├── MeterReminderWorker.kt    # Periodic reading reminder & threshold nudge
│       │   │   │   ├── SnoozeReceiver.kt         # BroadcastReceiver for notification snooze action
│       │   │   │   ├── SwitchReminderScheduler.kt# Helper to schedule SwitchReminderWorker
│       │   │   │   └── SwitchReminderWorker.kt   # Daily check for active switch recommendations
│       │   │   ├── repository/                   # Unified domain & data coordination
│       │   │   │   └── MeterRepository.kt        # Reactive pipeline (meterOverviews, cycleStats, switches)
│       │   │   ├── settings/                     # User preferences domain & UI
│       │   │   │   ├── SettingsRepository.kt     # DataStore wrapper for theme & snooze
│       │   │   │   ├── SettingsViewModel.kt      # StateFlow viewmodel for app settings
│       │   │   │   └── SettingsScreen.kt         # Compose settings screen
│       │   │   └── ui/                           # Jetpack Compose UI layer
│       │   │       ├── app/KwhTheme.kt           # Material 3 theme definition
│       │   │       ├── common/                   # StringResolver & Android wrapper
│       │   │       ├── components/               # Inputs, NumberField, PrimaryButton, SectionCard
│       │   │       ├── history/                  # History screen, trend chart, CSV import/export
│       │   │       ├── home/                     # Home dashboard, environment cards, meter items
│       │   │       ├── meterdetail/              # Gauge screen, today's rate, projected units
│       │   │       └── metersettings/            # Meter config, anchor day, thresholds, calibration
│       │   └── res/
│       │       ├── values/strings.xml            # Authoritative app strings (145 strings)
│       │       ├── xml/backup_rules.xml          # Auto Backup configuration (database included)
│       │       └── xml/data_extraction_rules.xml # Cloud backup extraction rules (API 31+)
│       ├── test/                                 # Unit & Robolectric test suite (14 test classes)
│       └── androidTest/                          # Instrumented Compose UI tests (HomeScreenTest)
├── docs/                                         # Historical architecture & specification documents
│   └── discovery/                                # Authoritative discovery baseline deliverables
├── build.gradle.kts                              # Root buildscript configuration
├── gradle.properties                             # JVM args, AndroidX flags
└── settings.gradle.kts                           # Module inclusion
```

---

## 4. Current Project Status

The project is an advanced, fully functional MVP currently tagged at version **1.1.0** (versionCode 3). It has successfully evolved through several milestones:
- **v1.0.0**: Core meter tracking, manual readings, history, linear projections, WorkManager reminders, and Compose UI.
- **v1.0.1**: Polished CSV import/export, notifications snooze, and test stabilization.
- **v1.1.0 (Milestones M6–M10)**: "Environment Switching" initiative completed:
  - Multi-meter grouping under environments.
  - Active meter assignment and activation tracking.
  - Active-day-weighted daily consumption rate calculations.
  - Switch recommendation advisor ("Switch to Meter B by Date").
  - Automated adaptive calibration (self-tuning safety buffer based on historical switch margin).
  - Version 2 CSV schema supporting full environment and activation restoration.
  - Play Store readiness: release signing configs, ProGuard optimization, targetSdk 36.

### Codebase Health
- **Build Status**: Compiles cleanly with Gradle 8.7 / AGP 8.4.0.
- **Test Suite**: Extensive unit test coverage across billing algorithms, migration paths, repositories, and ViewModels (14 unit test classes + Robolectric + UI tests).
- **Network Status**: 100% offline. Zero network calls, zero tracking/telemetry SDKs, zero cloud dependencies.
