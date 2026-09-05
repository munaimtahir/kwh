# KWH Android App — Development Context & AI Agent Handoff

> **CRITICAL INSTRUCTION FOR ALL CODING AGENTS**:  
> Read this document in full before proposing or executing code changes. This is the authoritative engineering handoff for KWH. Respect all architecture patterns, data safety rules, and canonical calculation pipelines.

---

## 1. Product Overview & Core Problem

**KWH** (`com.vexel.kwh`, namespace `com.example.kwh`, UI title "kWh Logger") is a production-grade, 100% offline native Android application designed for households and small facilities that manage **multiple electricity meters** under non-linear, tiered (slab-based) electricity tariffs.

### The Problem It Solves
Electricity tariffs in many regions feature steep penalties when monthly consumption crosses defined thresholds (e.g. 200, 300, 400 kWh). If a property has two or more meters, keeping both meters under lower-tier slabs (e.g. 195 kWh on Meter A and 180 kWh on Meter B) can save 40–60% compared to allowing one meter to surge into high-cost slabs (e.g. 375 kWh on Meter A).

KWH enables users to:
1. Log readings manually (or via future OCR/voice).
2. Calculate floating monthly billing cycle consumption.
3. Group meters into environments where one meter is actively in use.
4. Receive predictive recommendations on the exact calendar date to switch meters before entering expensive slabs.
5. Auto-calibrate the safety margin based on past switching behavior.

---

## 2. Current MVP State (Version 1.1.0)

The codebase is a fully functioning, tested MVP:
- **Multiple Meters**: Add, rename, delete meters; custom anchor day (1–31); custom thresholds CSV (e.g. "200,300").
- **Readings**: Add readings with past dates and notes; delete reading with instant Snackbar undo; latest reading display.
- **Environments & Switching**: Group meters into environments; enforce one active meter; log activation intervals.
- **Switch Advisor**: Recommends "Switch to [Sibling] by [Date]" with urgency (`NORMAL`, `SOON`, `OVERDUE`).
- **Adaptive Calibration**: Auto-tunes `safety_buffer_days` based on whether past switches landed over or under threshold.
- **Background Reminders**: WorkManager-scheduled periodic reading reminders and daily switch checks with interactive notification snooze.
- **Data Portability**: Full CSV Export and Import (Format v2) covering meters, readings, and activation histories.
- **Settings**: Dark mode toggle (DataStore Preferences) and notification snooze length slider.

---

## 3. Architecture & Code Structure

```
UI (Jetpack Compose + Material 3)
      ↓ (StateFlow & Channel Events)
ViewModels (@HiltViewModel)
      ↓
Domain Engines (BillingCycleCalculator, SwitchAdvisor)
      ↓
MeterRepository (@Singleton) & SettingsRepository
      ↓
Persistence (Room SQLite: MeterDatabase v4 & Preferences DataStore)
```

### Key Classes & Canonical Responsibilities:
- `com.example.kwh.MainActivity`: Single activity hosting Compose navigation, permission contracts, and `AddReadingDialog`.
- `com.example.kwh.billing.DefaultBillingCycleCalculator`: Calculates `CycleWindow(start, end)` from utility anchor day.
- `com.example.kwh.billing.SwitchAdvisor`: Pure domain logic that turns threshold forecasts into dated switch recommendations.
- `com.example.kwh.repository.MeterRepository`: **The canonical data coordinator**. Combines Room flows into reactive overviews, calculates cycle stats, orchestrates switches, and recalibrates safety buffers.
- `com.example.kwh.data.MeterDao`: Room DAO executing SQLite queries.
- `com.example.kwh.reminders.MeterReminderWorker`: WorkManager task posting reading reminders and 7-day threshold warnings.
- `com.example.kwh.reminders.SwitchReminderWorker`: Daily WorkManager check posting switch alerts when urgency is `SOON` or `OVERDUE`.

---

## 4. Persistence & Database Schema (Room v4)

- **`meters`**: `id`, `name`, `reminder_enabled`, `reminder_frequency_days`, `reminder_hour`, `reminder_minute`, `billing_anchor_day`, `thresholds_csv`, `environment_id`, `safety_buffer_days`.
- **`meter_readings`**: `id`, `meter_id` (FK cascade), `value`, `notes`, `recorded_at` (epoch ms).
- **`environments`**: `id`, `name`.
- **`meter_activations`**: `id`, `meter_id` (FK cascade), `started_at`, `ended_at`.
- **`switch_outcomes`**: `id`, `meter_id` (FK cascade), `recommended_date`, `actual_switch_date`, `units_at_switch`, `threshold_target`.

---

## 5. Canonical Engines & Mathematical Rules

### 5.1 Consumption Engine
- **Window**: Half-open `[start, end)` resolved by `BillingCycleCalculator.currentWindow(anchorDay, clock)`.
- **Baseline**: First reading in window, fallback to latest reading before window (`carryForward`).
- **Latest**: Last reading in window.
- **Used Units**: `latest.value - baseline.value` (if both exist, else `0.0`).
- **Rate**: `usedUnits / activeDays` (for meters in environment with activations) or `usedUnits / elapsedCalendarDays`.
- **Projection**: `round(rate * cycleLengthDays)`.

### 5.2 Threshold & Forecasting Engine
- Thresholds are parsed from comma-separated integers (`thresholds_csv`).
- ETA is linearly extrapolated: $\text{daysUntil} = \lceil (T - \text{usedUnits}) / \text{rate} \rceil$.
- If $\text{ETA} < \text{cycleEnd}$, returns `ThresholdForecast(T, ETA)`; if after cycle end, returns `null`.

### 5.3 Switch Recommendation Engine
- Pinpointed to the **lowest** configured threshold.
- $\text{switchByDate} = \text{forecast.ETA} - \text{safetyBufferDays}$.
- Urgency is `OVERDUE` if $\text{today} \ge \text{switchByDate}$, `SOON` if remaining days $\le 2$, else `NORMAL`.
- Switch target is the environment sibling with the lowest current cycle usage.

### 5.4 Adaptive Calibration
- Outgoing meter switch outcomes are logged to `switch_outcomes`.
- Evaluates average margin over last 6 outcomes:
  - If average margin $< 0$ (breached slab): widen buffer by +1 day.
  - If margin ratio $> 15\%$ (comfortable): tighten buffer by -1 day.
  - Clamped between 1 and 10 days.

---

## 6. Known Limitations & Technical Debt (Immediate Fixes Needed)

1. **P0 Crash Hazard in `DatabaseModule.kt`**:
   `DatabaseModule.provideDatabase()` registers only `MIGRATION_1_2`. It is missing `MIGRATION_2_3` and `MIGRATION_3_4`. Upgrading existing devices from v2/v3 will crash.
2. **P0 Short-Month Void Bug in `DefaultBillingCycleCalculator.kt`**:
   Anchor 31 on months following February calculates cycle end as March 28 instead of March 31, orphaning readings logged on March 29–31.
3. **P1 Single Reading Cycle Reports Zero**:
   If a user enters only 1 reading in a cycle, `baseline` and `latest` are identical, reporting 0.0 units consumed.
4. **P1 No Monotonicity Validation**:
   Entering a lower reading produces negative usage; meter swaps/resets are not natively handled.

---

## 7. Development Rules for Future AI Coding Agents

When working on this codebase, **strict adherence to the following rules is mandatory**:

1. **Preserve User Data at All Costs**:
   - Never use `fallbackToDestructiveMigration()` in Room.
   - Always write explicit SQLite migrations with corresponding unit tests in `MeterDatabaseMigrationTest`.
2. **Never Duplicate Consumption Logic in UI**:
   - All consumption, projection, and rate calculations MUST originate from `MeterRepository` / domain calculators.
   - Never write ad-hoc calculations inside Composable functions or ViewModels.
3. **Preserve Manual Entry as the Authoritative Fallback**:
   - Any AI-assisted entry (such as Camera OCR or Voice input) must pre-populate the canonical `AddReadingDialog` / `NumberField` for explicit user review and confirmation before calling `MeterRepository.addReading()`.
4. **Maintain 100% Offline Integrity**:
   - Do not add remote analytics, telemetry, crash reporting, or external cloud sync unless explicitly instructed by the user.
5. **Honor Single Source of Truth**:
   - Always query data through `MeterDao` / `MeterRepository`.
   - Never bypass the Room database with temporary in-memory caches that can diverge from disk.
6. **Use Unified Coroutine Dispatchers**:
   - Respect dependency-injected `Clock` and test dispatchers for deterministic unit testing.

---

## 8. Planned Major Features Roadmap

1. **Bug Baseline & Test Stabilization**: Fix `DatabaseModule` migration omissions and short-month calendar calculations.
2. **Advanced Consumption Visualization**: Multi-cycle bar charts, seasonal comparison, and tariff slab overlays using a native Compose charting library (e.g. Vico).
3. **Dynamic Tariff & Cost Engine**: Model monetary slab rates ($/kWh), fuel adjustments, and bill estimations.
4. **Personalized Goals & Budgets**: Monthly kWh budgets with real-time pace indicators.
5. **Camera OCR Meter Reading**: CameraX + on-device ML Kit Text Recognition with user review dialog.
6. **Multi-Meter Premise Optimizer**: Advanced load balancing predicting total household utility bill minimization.
7. **Voice / Conversational Interface**: Natural language intent router calling typed `MeterRepository` methods.

---

## v1.1.1 Stabilization

The v1.1.0 findings above are retained as the historical discovery baseline. The following defects are **resolved in v1.1.1** in the implementation:

- Production Hilt database creation registers all three existing migrations (1→2→3→4).
- Billing boundaries resolve the requested anchor independently for each month, preserving contiguous half-open windows after February and other short months.
- Both repository calculation paths prefer the latest pre-cycle reading as the carry-forward baseline. Without one, the first in-cycle reading remains the baseline; no usage is fabricated before the first known reading and no boundary interpolation is introduced.
- Ordinary reading submissions validate both chronological neighbors. Lower readings, backdated values above the next reading, and non-finite values are rejected with an actionable warning. The dialog preserves the entry and offers Cancel / Correct; it closes only after a successful save.
- Same-meter, same-local-date, same-value duplicates and timestamp collisions are rejected inside a Room transaction. UI and ViewModel guards also prevent rapid Save submissions. Equal readings on different dates remain valid.
- CSV safety checks exposed additional existing defects: commas in notes/environment names shifted metadata columns, escaped quotes were lost, and header text could fabricate an environment for an ungrouped meter. CSV escaping/multiline parsing and valid-row-only metadata handling are corrected without changing the v2 schema.
- Full lint exposed missing notification-permission rejection handling in both reminder workers. Notification posting now catches `SecurityException`, allowing subsequent scheduling to continue.
- Regression coverage includes daily continuity for anchors 1/29/30/31 over normal and leap years, real production-provider v1/v2/v3→v4 migration paths, fresh v4 persistence, carry-forward/backdating/deletion, threshold boundaries, chronological validation, concurrent Room submissions, ViewModel correction, switch selection/outcomes, and a CSV v2 round trip.

Version name is 1.1.1 and version code is 4. Schema remains v4; migration SQL and CSV v2 format are unchanged. Historical readings, environments, activations, and switch outcomes are not rewritten. CSV/undo restore paths intentionally retain existing historical data, including legacy anomalies. Meter reset/replacement handling remains unsupported.

Release validation results and remaining blockers are recorded in `docs/V1_1_1_STABILIZATION_REPORT.md`. Implementation status alone does not establish release readiness.
