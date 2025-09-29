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
