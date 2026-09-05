# KWH Android App — Technical Debt & Architectural Risks

This audit itemizes and prioritizes technical debt, latent bugs, data integrity hazards, and architectural weaknesses discovered in the KWH codebase.

---

## Priority Classification

| Priority | Definition | Impact |
|---|---|---|
| **P0** | **Critical Blockers / Data Integrity / Fatal Crashes** | Causes crashes on updates, drops historical user data, or breaks core app launch. |
| **P1** | **Important Reliability & Calculation Defects** | Corrupts calculation output, causes incorrect switch advice, or loses user intent. |
| **P2** | **Maintainability & Architectural Coupling** | Fragile design, bloated classes, missing boundaries, or build configuration drift. |
| **P3** | **Cosmetic & Minor Polish** | UI presentation limitations, lack of polish, or minor string inconsistencies. |

---

## P0 — Critical Blockers & Fatal Crashes

### 1. Missing Database Migrations in Hilt `DatabaseModule`
- **Location**: `app/src/main/java/com/example/kwh/di/DatabaseModule.kt` (Line 26)
- **Defect**:
  ```kotlin
  Room.databaseBuilder(context, MeterDatabase::class.java, "meter.db")
      .addMigrations(MeterDatabase.MIGRATION_1_2) // <--- MIGRATION_2_3 and 3_4 OMITTED!
      .build()
  ```
  While `MeterDatabase.get()` in the companion object registers all three migrations (`MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4`), the companion method is **never** used by the application runtime. Production dependency injection runs exclusively through `DatabaseModule`.
- **Impact**: Any user updating the application from database schema version 2 or 3 to version 4 will immediately crash on launch with an unhandled `IllegalStateException: A migration from X to 4 was required but not found`.
- **Remedy**: Register `MeterDatabase.MIGRATION_2_3` and `MeterDatabase.MIGRATION_3_4` in `DatabaseModule.kt`.

### 2. Short-Month Anchor Day Boundary Void Bug
- **Location**: `app/src/main/java/com/example/kwh/billing/BillingCycleCalculator.kt` (Lines 46–54)
- **Defect**:
  When calculating the cycle window for anchor day 31 on March 30:
  1. `anchorThisMonth` (March 31) > March 30.
  2. Fallback calculates `previousMonth` (February, 28 days) -> `cycleStartDate` = 2023-02-28.
  3. `cycleEndDate = cycleStartDate.plusMonths(1)` -> Java `LocalDate` produces **2023-03-28**.
  4. The returned window is `[2023-02-28, 2023-03-28)`.
- **Impact**: Today (March 30) is outside the window. Any readings logged on March 29, 30, or 31 fall into an unassigned void and are completely excluded from the active cycle stats and daily rate!
- **Remedy**: Refactor `BillingCycleCalculator` to calculate `cycleEndDate` by resolving the anchor against the next calendar month rather than calling `.plusMonths(1)` on a short-month boundary date.

---

## P1 — Calculation Integrity & Reliability

### 3. Single-Reading Cycle Reports Zero Consumption
- **Location**: `app/src/main/java/com/example/kwh/repository/MeterRepository.kt` (Lines 394–418)
- **Defect**:
  When the cycle baseline is resolved:
  ```kotlin
  val baseline = sorted.firstOrNull { it.recordedAt in start until end } ?: sorted.lastOrNull { it.recordedAt < start }
  val latest = sorted.lastOrNull { it.recordedAt in start until end }
  val usedUnits = if (baseline != null && latest != null) latest.value - baseline.value else 0.0
  ```
  If the user logs only one reading in the new cycle (e.g. Day 10), `baseline` is that reading, and `latest` is that reading. `usedUnits` evaluates to `latest.value - baseline.value = 0.0`.
- **Impact**: Users who read meters once a month or once every few weeks see 0 kWh usage and 0 rate until they log a second reading in the same cycle.
- **Remedy**: If only one reading exists in the cycle window, baseline should use the latest reading from *before* the cycle start (`carryForward`), or linearly interpolate the value at the exact cycle boundary.

### 4. No Monotonicity Check or Meter Rollover Handling
- **Location**: `app/src/main/java/com/example/kwh/ui/home/HomeViewModel.kt` & `MainActivity.kt`
- **Defect**: The app validates `reading > 0`, but does not verify whether `reading >= previous_reading`.
- **Impact**: A typo entering a lower reading produces negative `usedUnits` and negative rates. Physical meter replacements or meter resets are unsupported and distort all subsequent stats.
- **Remedy**: Add a confirmation prompt when a reading is entered that is lower than the previous reading.

### 5. Non-Unique Timestamp & Duplicate Reading Submissions
- **Location**: `app/src/main/java/com/example/kwh/data/MeterReadingEntity.kt`
- **Defect**: The `meter_readings` table lacks a composite unique constraint or index on `(meter_id, recorded_at)`.
- **Impact**: Accidental double-taps on the "Save" button insert identical readings, skewing point counts.

### 6. No In-Place Reading Edit Capability
- **Location**: `app/src/main/java/com/example/kwh/ui/history/HistoryScreen.kt`
- **Defect**: There is no UI dialog to edit a previously entered reading value, date, or note. Users must delete the reading and re-enter it from scratch.

---

## P2 — Maintainability & Architecture

### 7. Dual Database Instantiation Antipattern
- **Location**: `MeterDatabase.kt` companion object (`INSTANCE ?: synchronized`) vs `DatabaseModule.kt`.
- **Impact**: Two competing singleton patterns. The companion object singleton is redundant and risks divergence.

### 8. `MeterRepository` Monolithic Scope
- **Location**: `MeterRepository.kt` (581 lines)
- **Defect**: Handles Room DAO aggregation, cycle window resolution, active-day calculation, switch recommendation orchestration, adaptive calibration math, and entity CRUD.
- **Remedy**: Extract domain use cases: `GetCycleStatsUseCase`, `CalculateSwitchRecommendationUseCase`, `RecalibrateSafetyBufferUseCase`.

### 9. AGP 8.4.0 vs CompileSdk 36 Warning
- **Location**: `build.gradle.kts`
- **Defect**: AGP 8.4.0 officially supports up to compileSdk 34. While it builds compileSdk 36, it emits build warnings.
- **Remedy**: Suppress via `android.suppressUnsupportedCompileSdk=36` or schedule clean upgrade to AGP 8.6+ when stable.

### 10. Direct WorkManager Injection via `EntryPointAccessors`
- **Location**: `MeterReminderWorker.kt` & `SwitchReminderWorker.kt`
- **Defect**: Workers manually retrieve `WorkerEntryPoint` from `applicationContext` via reflection rather than using standard `@HiltWorker` + `HiltWorkerFactory`.
- **Impact**: Works in production, but bypasses standard Hilt testing harnesses and requires explicit ProGuard keep rules.

---

## P3 — Cosmetic & Minor Polish

### 11. Minimalist Canvas Trend Chart
- **Location**: `app/src/main/java/com/example/kwh/ui/history/HistoryScreen.kt`
- **Defect**: Canvas line chart draws raw polyline without Y-axis units, X-axis dates, grid lines, or touch scrubbing.

### 12. Inconsistent String Resource Usage
- **Location**: `HistoryViewModel.kt` (Line 173: `"Unable to delete meter"`, Line 255: `"No valid rows to import"`).
- **Defect**: Hardcoded English string literals passed to events instead of localized string resource IDs.
