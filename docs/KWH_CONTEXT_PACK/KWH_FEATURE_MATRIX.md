# KWH Android App — Authoritative Feature Matrix

This document provides a comprehensive audit of all planned, expected, and discovered capabilities in the KWH Android application codebase as of version **1.1.0** (September 2026).

## Status Definitions
- **COMPLETE**: Fully functional, UI integrated, persistent, and validated with automated tests.
- **WORKING BUT LIMITED**: Functional in the core path, but contains edge-case limitations, simplified heuristics, or specific corner-case bugs.
- **PARTIAL**: Architectural foundation or UI pieces exist, but end-to-end integration is incomplete.
- **PLACEHOLDER**: UI stub or code stub present without backing functionality.
- **NOT IMPLEMENTED**: Feature mentioned in vision or roadmap, but zero production code exists.
- **BROKEN / REQUIRES INVESTIGATION**: Code exists but contains a defect that blocks intended execution or risks crashes.

---

## 1. Feature Matrix

| Feature | Category | Status | Primary Implementation | Notes / Technical Audit |
|---|---|---|---|---|
| **Add / Edit Meter** | Meter Management | **COMPLETE** | `HomeScreen`, `MeterSettingsScreen`, `MeterRepository.addMeter()` | Configures name, reminder frequency, reminder time (hour/min). |
| **Delete Meter** | Meter Management | **COMPLETE** | `MeterSettingsScreen`, `HistoryScreen`, `MeterDao.deleteMeter()` | Cascades via SQLite FK to delete readings, activations, and switch outcomes. Has confirmation dialog. |
| **Meter Settings & Configuration** | Meter Management | **COMPLETE** | `MeterSettingsScreen`, `MeterSettingsViewModel` | Allows editing meter name, billing anchor day (1-31), thresholds CSV, reminder cadence, and safety buffer. |
| **Manual Reading Entry** | Consumption Logging | **COMPLETE** | `MainActivity` (`AddReadingDialog`), `MeterRepository.addReading()` | Supports floating-point values, optional string notes, and backdating via `DatePickerDialog` (max date today). |
| **Reading History View** | Consumption Logging | **COMPLETE** | `HistoryScreen`, `HistoryViewModel` | Lists entries sorted descending by date. Includes time-range filter chips (All, 7d, 30d, 365d). |
| **Delete Reading with Undo** | Consumption Logging | **COMPLETE** | `HistoryScreen`, `HistoryViewModel.deleteReading()` | Deletes reading immediately with Snackbar "Undo" action restoring the record. |
| **Reading Edit / Correction** | Consumption Logging | **WORKING BUT LIMITED** | `HistoryViewModel.deleteReading()` + re-entry | No direct in-place edit dialog for an existing reading. User must delete and re-enter. |
| **Current / Latest Meter Reading** | Consumption Logging | **COMPLETE** | `HomeScreen`, `MeterDetailScreen` | Displays the most recent reading value and timestamp. |
| **Monthly Cycle Consumption Calculation** | Billing Engine | **WORKING BUT LIMITED** | `MeterRepository.buildCycleStats()`, `CycleStats` | Calculates `latest.value - baseline.value`. If only 1 reading exists in cycle, usage evaluates to 0.0 because baseline chooses earliest reading in window. |
| **Utility Billing Anchor Day (1–31)** | Billing Engine | **WORKING BUT LIMITED** | `DefaultBillingCycleCalculator` | Resolves monthly billing window `[start, end)`. Has defect on leap/short month transitions (e.g. anchor 31 in March resolves Feb 28 to March 28, leaving March 29-31 orphaned). |
| **Per-Meter Threshold Configuration** | Threshold Engine | **COMPLETE** | `MeterEntity.thresholdsCsv`, `MeterSettingsScreen` | Comma-separated thresholds (e.g., "200,300"). Persisted and validated. |
| **Threshold Crossing Date Projection (ETA)** | Forecasting Engine | **WORKING BUT LIMITED** | `MeterRepository.resolveNextThreshold()`, `ThresholdForecast` | Extrapolates using linear daily rate: `ceil((threshold - usedUnits) / rate)`. Only projects next uncrossed threshold within current cycle. |
| **Periodic Reading Reminder Notifications** | Reminders | **COMPLETE** | `ReminderScheduler`, `MeterReminderWorker`, WorkManager | Enqueues unique background work (`meter_reminder_{id}`). Reschedules itself on completion. |
| **Interactive Notification Snooze** | Reminders | **COMPLETE** | `SnoozeReceiver`, `MeterReminderWorker.addAction` | Snooze action in notification banner enqueues one-time delay based on user preference (15–240 mins). |
| **Threshold Warning Notification Alert** | Reminders | **COMPLETE** | `MeterReminderWorker.buildAdditionalMessages()` | Displays proactive alert in reminder notification if projected threshold crossing is within 7 days. |
| **No-Reading Nudge Alert** | Reminders | **COMPLETE** | `MeterReminderWorker.buildAdditionalMessages()` | Injects reminder text if 10 days have elapsed in the billing cycle without a logged reading. |
| **Environment Meter Grouping** | Multi-Meter (M7) | **COMPLETE** | `EnvironmentEntity`, `MeterRepository.environmentOverviews` | Groups physical meters into conceptual environments (e.g., "Main House"). Ungrouped meters remain independent. |
| **Active Meter Tracking** | Multi-Meter (M7) | **COMPLETE** | `MeterActivationEntity`, `MeterRepository.activateMeter()` | Enforces single active meter per environment. Logs `started_at` and `ended_at` timestamps. |
| **Active-Day-Weighted Daily Rate** | Multi-Meter (M8) | **COMPLETE** | `MeterRepository.activeDays()` | Replaces calendar-elapsed days with cumulative active days so idle meter periods do not drag down consumption rate. |
| **Switch Recommendation Advisor** | Multi-Meter (M8) | **COMPLETE** | `SwitchAdvisor`, `MeterRepository.buildSwitchRecommendation()` | Generates "Switch to Meter X by Date" based on lowest threshold minus safety buffer. Flags urgency: NORMAL, SOON, OVERDUE. |
| **Daily Switch Check Background Worker** | Multi-Meter (M8) | **COMPLETE** | `SwitchReminderWorker`, `SwitchReminderScheduler` | Daily self-rescheduling WorkManager job (`switch_reminder_{id}`) notifying when switch urgency reaches SOON or OVERDUE. |
| **Adaptive Calibration (Self-Tuning Safety Buffer)** | Multi-Meter (M9) | **COMPLETE** | `SwitchOutcomeEntity`, `MeterRepository.recalibrateSafetyBuffer()` | Logs switch outcomes. After 3+ switches, automatically nudges `safety_buffer_days` (+1 day if over target, -1 day if comfortably under). |
| **Calibration Accuracy Indicator** | Multi-Meter (M9) | **COMPLETE** | `MeterSettingsScreen`, `CalibrationAccuracy` | Shows average days early/late, average units margin, and sample size on Meter Settings screen. |
| **CSV Export & Import (Format v2)** | Data Safety | **COMPLETE** | `HistoryViewModel.buildCsv()`, `importFromCsv()` | Exports/imports readings, anchor day, thresholds, environment name, safety buffer, and activation log (`# activations`). Backward-compatible with v1. |
| **Dark Theme Setting** | Settings & UI | **COMPLETE** | `SettingsRepository`, `SettingsScreen`, `KwhTheme` | Persisted via DataStore Preferences. Applied across entire app. |
| **Trend Line Chart** | Visualization | **WORKING BUT LIMITED** | `HistoryScreen.TrendChart` | Custom Canvas polyline displaying readings and dashed projection line within cycle window. Lacks axes, ticks, and date labels. |
| **Database Migrations in DI** | Database / Core | **BROKEN / REQUIRES INVESTIGATION** | `DatabaseModule.kt` vs `MeterDatabase.kt` | `DatabaseModule.provideDatabase()` registers only `MIGRATION_1_2`, omitting `MIGRATION_2_3` and `MIGRATION_3_4`. Upgrading app from v2 or v3 via Hilt crashes. |
| **Multi-Year / Annual Consumption Trends** | Analytics | **NOT IMPLEMENTED** | — | Persistent data can support it, but no query, aggregation, or UI exists. |
| **Monetary Tariff Rates & Cost Calculation** | Tariff Modeling | **NOT IMPLEMENTED** | — | No concept of currency, billing slabs in monetary units (e.g. $/kWh), taxes, or fuel charges. |
| **Camera / OCR Meter Reading Entry** | Automation | **NOT IMPLEMENTED** | — | No CameraX, ML Kit, or image processing code present in repository. |
| **Voice / Conversational Assistant** | AI / Automation | **NOT IMPLEMENTED** | — | No speech recognition, audio recording, or conversational parsing present. |
| **Cloud Synchronization / Remote Backup** | Cloud Integration | **NOT IMPLEMENTED** | Android OS Auto Backup only | Zero proprietary cloud sync; 100% offline. Relies solely on Android OS cloud backup rules. |

---

## 2. Capability Gap Summary

| Area | What Is Implemented | What Is Missing |
|---|---|---|
| **Consumption Calculation** | Delta between earliest and latest reading in cycle window. | Zero handling for single-reading cycles (reports 0 usage); no interpolation between previous cycle reading and current cycle. |
| **Forecasting** | Linear extrapolation to next threshold within current cycle. | Rolling averages, seasonal smoothing, acceleration detection, multi-threshold forecasts, cross-cycle projections. |
| **Tariff Logic** | Threshold unit boundaries (e.g., 200, 300 units). | Monetary rates, slab pricing tiers, fuel charges, peak/off-peak rates. |
| **Visualization** | Single-cycle Canvas line with dashed projection. | Multi-cycle graphs, monthly comparison bar charts, tariff slab horizontal lines, interactive tooltips, zoom/pan. |
| **Input Methods** | Manual numeric keyboard entry with date picker. | Camera OCR, photo verification, voice commands, bulk CSV import from utility portal. |
