# FINAL AI DEVELOPER PROMPT — Kwh Logger

## Mission
Implement the staged roadmap (M1→M5) for the Android app described in `AppDescription.md` using the current codebase.

## Codebase
- Package: `com.example.kwh`
- Stack: Kotlin, Android, Jetpack Compose, Room (SQLite), WorkManager, Gradle Kotlin DSL
- Key layers: Room (data), Repository (domain), WorkManager (reminders), Jetpack Compose (UI).

## Constraints
- Keep all data **local**; no network calls.
- Use **Hilt** for DI in M2.
- Ensure **unique work** per meter in WorkManager to avoid duplicate reminders.
- Provide **tests** as specified in `Tests.md`.
- Follow `Architecture.md` and `DataModel.md` for structure and schema.

## Deliverables by Milestone
- M1: History screen, delete flows, validation/empty states, reminder edge-case fixes.
- M2: Hilt DI, unit & UI tests, crash handling.
- M3: CSV export, line chart, optional import.
- M4: Snooze, settings, theme polish.
- M5: Release artifacts (icons, versioning, changelog), QA checklist pass.

## Output Contract
- Update `CHANGELOG.md` per milestone.
- Keep `README.md` and `Setup.md` current.
- Open/close tasks from `TASKS.md`.
- All code compiles with `./gradlew assembleDebug` and tests pass with `./gradlew test`.

## Guardrails
- Don’t introduce analytics SDKs or network I/O.
- Respect Android power constraints; schedule the next reminder strictly **after** now.
- Validate inputs; never crash on empty/invalid values.
