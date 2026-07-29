# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project

Kwh Logger is a native Android app (Kotlin + Jetpack Compose) for logging electricity meter readings, tracking billing-cycle usage, and scheduling reminders. All data is local (Room); there is no backend/network layer.

## Build & test commands

The Gradle wrapper JAR is not committed. Before building for the first time, fetch it:

```bash
./scripts/download-gradle-wrapper.sh
```

(or `gradle wrapper` if Gradle is installed locally). Also requires `local.properties` with `sdk.dir` set, or `ANDROID_HOME` in the environment.

```bash
./gradlew assembleDebug          # build debug APK
./gradlew test                   # run all JVM unit tests (app/src/test)
./gradlew connectedAndroidTest   # run instrumented tests (app/src/androidTest), needs device/emulator
./gradlew assembleRelease bundleRelease  # release APK + AAB (used by the manual GitHub Actions workflow)
```

Run a single unit test class or method:

```bash
./gradlew test --tests "com.example.kwh.repository.MeterRepositoryCycleStatsTest"
./gradlew test --tests "com.example.kwh.billing.DefaultBillingCycleCalculatorTest.someMethodName"
```

Unit tests use JUnit4 + Robolectric (`isIncludeAndroidResources = true`), `kotlinx-coroutines-test`, and Turbine for Flow assertions. There is no configured lint/ktlint/detekt step — CI only builds and runs `./gradlew assembleDebug test`.

## Architecture

Single Gradle module (`app/`), package root `com.example.kwh`. Hilt provides DI (`di/*Module.kt`, one module per singleton dependency: Database, Repository, Reminder, Settings, Billing, Ui). Layers, in dependency order:

- **`data/`** — Room persistence. `MeterEntity` and `MeterReadingEntity` are the two tables (see `docs/DataModel.md`); `MeterDao` exposes both suspend functions and `Flow`-returning queries; relation wrappers (`MeterWithReadings`, `MeterWithLatestReading`) join meters to their readings. `MeterDatabase` holds migrations (e.g. `MIGRATION_1_2`).
- **`billing/`** — Pure calculation layer, no Android dependencies. `BillingCycleCalculator` computes the current billing-cycle window from a meter's anchor day; `CycleStats`/`ReadingSnapshot`/`ThresholdForecast` are the resulting value types. Injected as a singleton via `Clock` (also DI-provided) so time is controllable in tests.
- **`repository/`** — `MeterRepository` is the single domain facade the UI talks to. It combines DAO flows with `BillingCycleCalculator` to produce `MeterOverview` (meter + latest reading + `CycleStats`), and owns all writes (add/update/delete meters and readings, reminder config updates). Business logic like threshold-ETA forecasting and cycle-usage rate lives here, not in ViewModels — see `docs/ALGORITHMS.md` for the cycle-window/projection/threshold math this implements.
- **`reminders/`** — WorkManager-based scheduling. `ReminderScheduler` computes next-run time and enqueues a uniquely-named one-time `WorkRequest` per meter; `MeterReminderWorker` posts the notification and re-enqueues itself for the next occurrence (reminders are self-rescheduling, not periodic WorkManager jobs). `SnoozeReceiver` handles the notification's snooze action, delaying the next run using the user's configured snooze length from `SettingsRepository`.
- **`settings/`** — `SettingsRepository` (DataStore-backed) holds dark-theme and snooze-length preferences; exposed to reminders via the narrower `SnoozePreferenceReader` interface.
- **`ui/`** — Compose screens, one package per screen (`home`, `history`, `meterdetail`, `metersettings`), each with a `Screen.kt` + paired `ViewModel.kt` that exposes `StateFlow` UI state and maps repository/domain models to view state. `ui/components` holds shared inputs; `ui/app/KwhTheme.kt` is the Compose theme; `ui/common/StringResolver` abstracts Android string-resource lookup so ViewModels stay testable outside Android.

### Key flows worth knowing before changing behavior

- Reminders are **not** periodic WorkManager work — each worker run schedules its own successor, and toggling a meter's reminder off must cancel the correspondingly-named unique work request in `ReminderScheduler`.
- Notification permission (Android 13+/API 33) is requested in `MainActivity`, which defers applying a reminder toggle until the permission result arrives, reverting the toggle in UI state if denied.
- Billing-cycle math (baseline/latest reading selection, usage rate, threshold ETA) is centralized in `MeterRepository` + `BillingCycleCalculator`; changes to cycle logic should update `docs/ALGORITHMS.md` alongside the code.
- `MeterDao.deleteMeter` cascades to delete that meter's readings (FK cascade); reading deletes support undo via `MeterRepository.deleteReading`/`restoreReading` returning/accepting the deleted entity.

## Docs

`docs/` contains living design docs worth checking before larger changes: `Architecture.md`, `DataModel.md`, `ALGORITHMS.md` (cycle/projection formulas), `FEATURE_SPEC.md`, `Tests.md` (test plan/acceptance checklist), `TASKS.md`/`Roadmap.md`/`TODO.md` (in-flight work).

## In-flight initiative: Environment Switching (M6–M10)

The app is being extended so households running multiple meters (to stay under slab-billing thresholds by switching load between connections) get a proactive "switch to the next meter by this date" recommendation. This is a multi-milestone build, not a single task:

- **Design/data-model/algorithms:** `docs/ENVIRONMENT_SWITCHING_SPEC.md` — read this before touching anything related to environments, activation logs, or switch recommendations.
- **Process/guardrails for implementing it:** `docs/AGENT_ENVIRONMENT_SWITCHING.md` — follow this instead of improvising; it defines the required order of work and what "done" means per milestone.
- **Milestones + exit criteria:** `docs/Roadmap.md` (M6–M10).
- **Granular checklist:** `docs/TASKS.md` ("Environment Switching Initiative" section) — pick up the next unchecked item in order; later milestones assume earlier ones are complete.

Key constraint carried through all milestones: meters not assigned to an environment (`environment_id = null`) must keep behaving exactly as they do today — this feature is strictly additive.
