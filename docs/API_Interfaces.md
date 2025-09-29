# API / Interfaces
- `MeterDao`: observe meters with latest reading; observe readings for meter; insert/update/delete.
- `MeterRepository`: high-level CRUD; reminder toggling; history fetch.
- `ReminderScheduler`: schedule/cancel unique work per meter; compute next run time.
- `HomeViewModel`: exposes `UiState` (meters, dialog state, errors).

Public surface is app-internal; no external network APIs.
