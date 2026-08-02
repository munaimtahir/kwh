# Architecture.md
- **Data:** Room (`MeterEntity`, `MeterReadingEntity`) with `MeterDao` queries and flows.
- **Domain:** `MeterRepository` provides high-level APIs and maps Room models to UI models.
- **Reminders:** `ReminderScheduler` + `MeterReminderWorker` enqueue one-time work and reschedule.
- **Presentation:** Compose `HomeScreen`, `HomeViewModel`, `Inputs` components, `HistoryScreen` (to add).

**DI (M2):** Introduce Hilt modules for `Database`, `Dao`, `Repository`, and `Scheduler` singletons.

**Environment Switching (M6–M10, complete):** `EnvironmentEntity`/`MeterActivationEntity` group meters and track which one is active; `MeterRepository` exposes environment CRUD, `activateMeter()`, and grouped `environmentOverviews`/`ungroupedMeterOverviews` flows consumed by `HomeScreen` (environment grouping + active-meter indicator + switch-recommendation banner) and `MeterSettingsScreen` (assignment, activation history, safety-buffer field, accuracy indicator). `billing/SwitchAdvisor` turns the active meter's usage/rate into a dated recommendation; `reminders/SwitchReminderWorker` + `SwitchReminderScheduler` post a daily-checked notification, scheduled/cancelled from each `activateMeter()` call site rather than a user toggle. `SwitchOutcomeEntity` logs each switch's predicted-vs-actual outcome, which `MeterRepository` uses to bound-adjust `safety_buffer_days` (±1/switch, clamped 1–10). See `docs/ENVIRONMENT_SWITCHING_SPEC.md` and `docs/ALGORITHMS.md`.
