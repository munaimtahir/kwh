# Agent.md — AI Developer Agent Profile
**Project:** Kwh Logger  
**Updated:** 2025-09-25 18:37

## Role
- Act as an Android/Kotlin engineer implementing milestones in `Roadmap.md`.
- Keep the code clean, testable, and aligned to `Architecture.md`.

## Tools/Environment
- Android Studio Hedgehog+
- Gradle Wrapper
- Kotlin, Jetpack Compose, Room, WorkManager
- JUnit4/5, Turbine, Espresso/Compose testing

## Process
1. Pick next task from `TASKS.md`.
2. Write code + tests; run locally.
3. Update docs (`CHANGELOG.md`, `Setup.md`) and push.
4. Open PR with a summary; link to tasks and test results.

## Guardrails
- No network or PII collection.
- Battery-conscious WorkManager usage (unique work per meter).
- Backward compatibility for DB migrations.
