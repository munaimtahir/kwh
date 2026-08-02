# Kwh Logger – Application Structure & Development Status

## Overview
Kwh Logger is an Android application that helps users capture electricity meter readings while configuring periodic reminders. It is built with Jetpack Compose for UI, Room for persistence, and WorkManager for background scheduling.

## Source Layout
- **`app/src/main/java/com/example/kwh/data` – Persistence layer.** Defines Room entities for meters and readings, relation wrappers, the DAO contract for queries/mutations, and a singleton database accessor.
- **`app/src/main/java/com/example/kwh/repository` – Domain facade.** `MeterRepository` exposes streams with latest readings, delegates inserts/updates, and returns updated meter configs for reminder scheduling.
- **`app/src/main/java/com/example/kwh/reminders` – Reminder infrastructure.** Hosts the WorkManager worker that posts notifications and reschedules itself, plus helper APIs to compute timing and enqueue/cancel work.
- **`app/src/main/java/com/example/kwh/ui` – Presentation layer.** Contains the Compose theme, reusable inputs, the home screen surface, and the `HomeViewModel` that binds repository flows to UI state.
- **Entry point & resources.** `MainActivity` wires dependencies, handles notification permissions, and renders the home screen. String resources define UI copy for meters, readings, and reminders.

## Key Workflows
### Application start & dependency wiring
`MainActivity` instantiates the Room database, repository, and reminder scheduler, then sets the Compose content. It observes the `HomeViewModel` state and funnels UI events to the view model. When reminder toggles require notification permissions on Android 13+, the activity defers the update until the permission result arrives, reverting the toggle if denied.

### Meter creation
1. User taps the floating action button, triggering `showAddMeterDialog(true)` in the view model via `HomeScreen` callbacks.
2. Dialog collects the meter name and reminder defaults, then calls `HomeViewModel.addMeter` which delegates to `MeterRepository.addMeter`. The repository inserts a `MeterEntity` with reminders disabled by default.
3. DAO insertion updates the meters flow, which the view model maps to UI items, refreshing the list.

### Reading logging
1. `MeterCard` exposes an "Add reading" action that opens the reading dialog for the selected meter.
2. The dialog captures the numeric reading and optional notes, invoking `HomeViewModel.addReading` upon confirmation.
3. The repository persists a `MeterReadingEntity` with the current timestamp; the latest reading propagates back to the UI via the flow mapping to `MeterWithLatestReading`.

### Reminder configuration & scheduling
1. Each meter card hosts reminder fields (toggle, frequency, time) bound to local state. Saving or toggling submits sanitized values to the `onReminderChanged` callback.
2. `HomeViewModel.updateReminder` writes the new configuration through `MeterRepository.updateReminderConfig`. When reminders are enabled, it invokes `ReminderScheduler.enableReminder`; otherwise it cancels via `disableReminder`.
3. `ReminderScheduler` computes the next execution time and enqueues a unique WorkManager job. When the worker executes, it posts a notification and reschedules the next occurrence, ensuring recurring reminders.
4. On Android 13+, `MainActivity.handleReminderChange` requests the POST_NOTIFICATIONS permission before enabling WorkManager scheduling, reverting the toggle if the user denies the request.【F:app/src/main/java/com/example/kwh/MainActivity.kt†L43-L101】

## Feature Status Snapshot
| Feature | Description | Current Status |
| --- | --- | --- |
| Meter list & latest reading display | Streams all meters with their most recent reading and optional next reminder timestamp. | **Complete** – View model maps Room flows to UI models consumed by `HomeScreen`. |
| Meter creation | Compose dialog collects inputs and persists new meters with default reminder config. | **Complete** – Dialog hooks to repository insert via view model; flow refresh updates UI automatically. |
| Reading capture | Dialog records numeric reading plus notes and timestamps automatically. | **Complete** – Repository writes to Room; latest reading displayed on cards. |
| Reminder configuration | Toggle, frequency, and time fields per meter with WorkManager scheduling. | **Complete** – UI sanitizes inputs, view model persists config, scheduler enqueues/cancels jobs. |
| Notification delivery | Background worker posts reminder notification and re-queues next run. | **Complete** – Worker builds notification channel, shows notification, and reschedules. |
| Notification permission handling | Ensures reminders only activate when `POST_NOTIFICATIONS` is granted. | **Complete** – Activity defers updates until permission result, reverting if denied. |
| Meter deletion | DAO includes deletion API but no UI affordance yet. | **Not integrated** – `MeterDao.deleteMeter` exists, yet no repository/view model/UI path invokes it. |
| Reading history view | DAO streams all readings for a meter, but UI shows only latest value. | **Planned (data ready)** – Flow available via repository, yet no screen consumes it. |
| Theming & design system | Light/dark color schemes and reusable input components. | **Complete** – `KwhTheme` provides palettes and shared components wrap Compose controls. |

## Environment Switching (M6–M10, complete)
Meters can optionally belong to an **Environment** (a household running multiple physical meters). `MeterRepository.environmentOverviews` groups meters by environment with the currently active meter (an open `meter_activations` row); `ungroupedMeterOverviews` covers meters with `environment_id = null`, which render and behave exactly as before this feature. `HomeScreen` renders environment headers with an "Active" chip, a "Set as active" action, and a switch-recommendation banner with a one-tap "Mark switched" action; `MeterSettingsScreen` lets a meter be assigned to an existing or new environment, unassigned, manually activated, shows its read-only activation history, an editable safety-buffer field, and a "switch accuracy" indicator. `MeterDetailScreen` shows the same recommendation banner for the active meter. Switch reminders are posted by a self-rescheduling `SwitchReminderWorker`, scheduled/cancelled wherever `activateMeter()` is called (Home/Settings/Detail ViewModels), not via a user toggle. Each switch's predicted-vs-actual outcome is logged (`SwitchOutcomeEntity`) and used to nudge `safety_buffer_days` (±1 per switch, clamped 1–10) — a bounded rolling-average, not a model. CSV export/import (`HistoryViewModel`, format v2) carries environment name, safety buffer, and the activation log alongside readings. See `docs/ENVIRONMENT_SWITCHING_SPEC.md`, `docs/ALGORITHMS.md`, and `docs/Roadmap.md` (M6–M10) for the full initiative.

## Resource Summary
Key user-facing strings (meter labels, reminder copy, notifications) live in `values/strings.xml`, while the manifest registers `MainActivity` and declares the notifications permission, keeping reminder behavior consistent across configurations.【F:app/src/main/res/values/strings.xml†L1-L18】【F:app/src/main/AndroidManifest.xml†L1-L24】
