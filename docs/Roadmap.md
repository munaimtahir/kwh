# Kwh Logger — Staged Development Roadmap
_Last updated: 2025-09-25 18:37_

## M1 — MVP Polish
- Reading history screen (per-meter list; filters: last 7/30/90 days).
- Delete flows: delete meter (cascade confirm) and delete reading (with undo/snackbar).
- Empty states and validation (meter name required, numeric input for readings).
- Reminder scheduling edge cases: next run strictly in the future; timezone/clock change handling.
- Telemetry/logging for reminder execution.

**Exit:** Add/edit/delete meters & readings; history visible; reminders reliable next day.

## M2 — Reliability & Tests
- Introduce Hilt DI for `MeterDatabase`, `MeterRepository`, `ReminderScheduler`.
- Unit tests: DAO (Room in-memory), ViewModel (Turbine/runTest), reminder math.
- UI tests: Compose tests for add/delete flows and history display.
- Crash safety: repository try/catch and surfacing errors into UI state.

**Exit:** CI green; deterministic tests; manual test plan passes on 2–3 API levels.

## M3 — Data & Charts
- CSV **Export** (per meter; ISO date columns).
- Trend **Chart** (last 30/90 days) with simple smoothing toggle.
- CSV **Import** (optional, behind developer option).

**Exit:** Data can be exported; trend view exists and matches data.

## M4 — UX Enhancements
- Notification **Snooze** actions (15m/1h).
- **Settings**: default reminder frequency/time, 12/24h, battery optimization help.
- Theme polish and dark-mode parity.

**Exit:** Everyday usability is strong; reduced missed-notification cases.

## M5 — Release Hardening
- Version name/code, changelog, adaptive icons.
- Privacy note in README (local-only data).
- Play-ready build: applicationId, signing, min/targetSdk finalization.
- Manual QA across Android 8–14 (at least one device + emulator).

**Exit:** Ready for internal or Play Store release.

---

## Environment Switching Initiative

Full spec: `docs/ENVIRONMENT_SWITCHING_SPEC.md`. Build process: `docs/AGENT_ENVIRONMENT_SWITCHING.md`. Granular checklist: `docs/TASKS.md`.

## M6 — Environment & Activation Groundwork
- `EnvironmentEntity`, `environment_id` + `safety_buffer_days` on `MeterEntity`, `MeterActivationEntity`.
- Room migration 2→3 (additive, backward compatible for ungrouped meters).
- DAO additions: environment CRUD, activation queries (active activation per meter, active meter per environment).
- Migration + DAO round-trip tests.

**Exit:** Schema in place; existing meters/readings unaffected; new tables/queries covered by tests.

## M7 — Environments & Manual Activation
- Repository: create/rename/delete environment, assign/unassign meter, `activateMeter()` (closes prior open activation in the environment, opens new one), `getActiveMeter(environmentId)`, `activationHistory(meterId)`.
- Home screen groups meters by environment (with an "Ungrouped" bucket); active-meter indicator; Meter Settings gains environment assignment + activation history view.
- Repository + ViewModel tests for activation transitions (including idempotency and same-meter re-activation).

**Exit:** User can group meters into an environment and manually mark which one is active, with a persisted history of switches.

## M8 — Switch Recommendation Engine
- Active-day-weighted rate calculation (activation-log-aware, replacing calendar-elapsed days for meters in an environment).
- `SwitchAdvisor`: turns the active meter's threshold ETA into `switchByDate` + urgency (normal/soon/overdue) using `safety_buffer_days`.
- Recommendation banner on Home/Meter Detail with "Mark switched" action; new switch-reminder notification (reusing `MeterReminderWorker`/WorkManager pattern).
- Unit tests: rate math with activation gaps, `SwitchAdvisor` across rate/threshold/buffer combinations and the zero-rate/overdue edge cases.

**Exit:** Active meter surfaces a dated switch recommendation that reflects real (active-day) usage, with a notification nudge before the deadline.

## M9 — Adaptive Calibration
- `SwitchOutcomeEntity` logging predicted vs. actual switch date/units at each switch.
- Rolling-error calculation and bounded auto-adjustment of `safety_buffer_days` per meter.
- Accuracy indicator in Meter/Environment settings.
- Calibration convergence tests with synthetic switch-history sequences.

**Exit:** Safety buffer self-tunes from the app's own switch history instead of staying a fixed constant.

## M10 — Polish & Release
- CSV export/import extended with environment assignment + activation log rows.
- `DataModel.md`/`Architecture.md`/`ALGORITHMS.md` updated with final schema and formulas.
- Full regression pass (existing + new tests green); QA checklist re-run.
- Version bump + changelog entry.

**Exit:** Feature is documented, tested, and release-ready alongside the rest of the app.
