# AGENT_ENVIRONMENT_SWITCHING.md — Build Instructions for the Environment Switching Feature
**Project:** Kwh Logger
**Scope:** Milestones M6–M10 in `docs/Roadmap.md` (the multi-meter switch-recommendation initiative)

Any agent (human or AI) picking up work in this area should read this file first, alongside `docs/ENVIRONMENT_SWITCHING_SPEC.md` (the design) and `CLAUDE.md` (general repo conventions). This file governs *process*; the spec governs *design*.

## Mission
Implement `docs/ENVIRONMENT_SWITCHING_SPEC.md` in the milestone order laid out in `docs/Roadmap.md` (M6 → M10), without breaking any existing behavior for meters that are not part of an environment.

## Before starting any work
1. Read `docs/ENVIRONMENT_SWITCHING_SPEC.md` in full — it has the data model, algorithms, and the three open questions listed under "Open questions." If an open question is still unresolved and your task depends on the answer, ask the user rather than guessing; don't silently pick an interpretation for a still-open question.
2. Read `docs/TASKS.md`'s "Environment Switching Initiative" section and pick up the next unchecked item, in order. Do not skip ahead to a later milestone's tasks while earlier ones are incomplete — later milestones assume earlier schema/repository work already exists and is tested.
3. Confirm which milestone (M6–M10) the task belongs to and re-read that milestone's "Exit" criteria in `docs/Roadmap.md` before starting, so you know what "done" looks like.

## Process per task
1. Implement the smallest change that satisfies the task item, following the existing architecture layering (`data/` → `billing/` → `repository/` → `reminders/` / `ui/`) exactly as described in `CLAUDE.md`.
2. Write or update tests alongside the change — this repo has no separate "testing pass" milestone; each milestone's exit criteria includes its own tests being green.
3. Run `./gradlew assembleDebug test` and confirm it passes before considering the task done.
4. Check off the corresponding box in `docs/TASKS.md`.
5. If the change affects the schema, formulas, or UI flows described in `docs/DataModel.md`, `docs/ALGORITHMS.md`, or `docs/application_structure.md`, update those docs in the same change (don't let them drift — this repo has been burned by stale docs before, per `docs/CHANGELOG.md`).
6. Add a `docs/CHANGELOG.md` entry under `[Unreleased]` describing what shipped.

## Guardrails
- **Local-only, always.** No network calls, no analytics, no telemetry — this is a hard constraint carried over from the base app (`docs/AppDescription.md`), not new for this feature.
- **Backward compatible migrations.** Every schema change must be additive. A meter with `environment_id = null` must behave in every screen and calculation exactly as it does today — this feature must never be a breaking change for users who don't use it.
- **One active meter per environment at a time** (per the spec's Open Question 1, currently assumed true) — `activateMeter()` must close any other open activation row for meters in the same environment before opening a new one. Don't leave two open activation rows in one environment.
- **Unique WorkManager work names** per meter/environment for the new switch-reminder worker, matching the existing `meter_reminder_{id}` convention in `ReminderScheduler`/`MeterReminderWorker` — no duplicate notifications.
- **Don't reach for ML.** M9's adaptive calibration is a bounded rolling-average adjustment, not a model. Keep it simple and explainable — the user should be able to see *why* the safety buffer changed.
- Validate all new user input (safety buffer days, environment name) with the same rigor as existing meter/reading validation in `MeterRepository`/`MeterSettingsViewModel`.

## Output contract
- All code compiles via `./gradlew assembleDebug` and `./gradlew test` passes.
- `docs/TASKS.md` checkboxes reflect true state.
- `docs/CHANGELOG.md` updated per change.
- Docs (`DataModel.md`, `ALGORITHMS.md`, `Architecture.md`, `application_structure.md`) updated whenever the change they describe lands, not deferred to M10 — M10 is a final pass/audit, not the only time docs get touched.
