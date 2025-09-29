# Architecture.md
- **Data:** Room (`MeterEntity`, `MeterReadingEntity`) with `MeterDao` queries and flows.
- **Domain:** `MeterRepository` provides high-level APIs and maps Room models to UI models.
- **Reminders:** `ReminderScheduler` + `MeterReminderWorker` enqueue one-time work and reschedule.
- **Presentation:** Compose `HomeScreen`, `HomeViewModel`, `Inputs` components, `HistoryScreen` (to add).

**DI (M2):** Introduce Hilt modules for `Database`, `Dao`, `Repository`, and `Scheduler` singletons.
