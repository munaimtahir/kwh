# TASKS — Issue Checklist
## M1
- [x] HistoryScreen: list by meter, filters 7/30/90 days
- [x] Delete meter (confirm + cascade), delete reading (undo)
- [x] Empty states & validation
- [x] Reminder next-run edge cases
- [x] Worker logging

## M2
- [x] Add Hilt DI
- [x] DAO unit tests
- [x] ViewModel unit tests
- [x] Compose UI tests
- [x] Crash handling surfaced to UI

## M3
- [x] CSV export
- [x] Trend chart
- [x] CSV import (optional)

## M4
- [x] Snooze actions
- [x] Settings screen
- [x] Theme polish

## M5
- [x] Versioning + icons + changelog
- [x] Privacy note in README
- [x] QA runs across API 26/30/34
    - Pixel 6 (API 34), Pixel 4a (API 30), Nexus 5X emulator (API 26)

---

## Environment Switching Initiative
Spec: `docs/ENVIRONMENT_SWITCHING_SPEC.md` — Process: `docs/AGENT_ENVIRONMENT_SWITCHING.md` — Milestones: `docs/Roadmap.md` (M6–M10).

## M6 — Environment & Activation Groundwork
- [x] `EnvironmentEntity` (id, name)
- [x] `MeterEntity`: add `environment_id` (nullable) + `safety_buffer_days` (default 3)
- [x] `MeterActivationEntity` (meter_id, started_at, ended_at nullable)
- [x] Room migration 2→3, additive/backward compatible
- [x] DAO: environment CRUD; active-activation-per-meter and active-meter-per-environment queries
- [x] Migration test + DAO round-trip tests

## M7 — Environments & Manual Activation
- [ ] Repository: create/rename/delete environment; assign/unassign meter
- [ ] Repository: `activateMeter()` (closes prior open activation, opens new one), `getActiveMeter()`, `activationHistory()`
- [ ] Home screen: group by environment + "Ungrouped" bucket; active-meter indicator
- [ ] Meter Settings: environment assignment + activation history view
- [ ] Repository/ViewModel tests for activation transitions (idempotency, re-activation)

## M8 — Switch Recommendation Engine
- [ ] Active-day-weighted rate calculation (activation-log aware)
- [ ] `SwitchAdvisor` domain class (switchByDate + urgency from threshold ETA + safety buffer)
- [ ] Recommendation banner (Home + Meter Detail) with "Mark switched" action
- [ ] Switch-reminder notification (new WorkManager worker, reusing existing pattern)
- [ ] Unit tests: rate math with gaps; `SwitchAdvisor` combinations incl. zero-rate/overdue

## M9 — Adaptive Calibration
- [ ] `SwitchOutcomeEntity` + logging on each switch
- [ ] Rolling-error calculation; bounded auto-adjustment of `safety_buffer_days`
- [ ] Accuracy indicator in settings UI
- [ ] Calibration convergence tests (synthetic switch histories)

## M10 — Polish & Release
- [ ] CSV export/import: environment + activation log fields
- [ ] Update `DataModel.md` / `Architecture.md` / `ALGORITHMS.md`
- [ ] Full regression pass + QA checklist re-run
- [ ] Version bump + changelog entry
