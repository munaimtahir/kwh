# KWH Context Bundle — Authoritative Discovery Baseline

**Baseline:** KWH v1.1.0 Discovery Baseline  
**Purpose:** Canonical project context and AI/developer handoff pack for future KWH development.

## Included Documents

1. `KWH_PROJECT_BASELINE.md` — High-level product, stack, repository and project status baseline.
2. `KWH_ARCHITECTURE.md` — Technical architecture, data flow, DI graph and structural findings.
3. `KWH_DATA_MODEL.md` — Room/DataStore entities, migrations and persistence/data-integrity audit.
4. `KWH_FORECASTING_BASELINE.md` — Consumption, billing-cycle, threshold, forecasting and switch algorithms.
5. `KWH_FEATURE_MATRIX.md` — Authoritative implemented/limited/partial/not-implemented feature status matrix.
6. `KWH_TECHNICAL_DEBT.md` — Prioritized P0-P3 defects, risks and technical debt.
7. `KWH_FUTURE_FEATURE_READINESS.md` — Readiness assessment for analytics, OCR, goals, voice and tariff optimization.
8. `KWH_DEVELOPMENT_CONTEXT.md` — Primary compact engineering/AI-agent handoff document.

## Authority Order

Where documents differ in level of precision, use the following order:

1. Detailed technical audits (`KWH_DATA_MODEL.md`, `KWH_FORECASTING_BASELINE.md`, `KWH_TECHNICAL_DEBT.md`, `KWH_ARCHITECTURE.md`)
2. `KWH_FEATURE_MATRIX.md`
3. `KWH_DEVELOPMENT_CONTEXT.md`
4. `KWH_PROJECT_BASELINE.md`

The detailed technical audits take precedence over broader summaries when a contradiction exists.

## Current Development Position

KWH v1.1.0 is a functional offline multi-meter electricity-consumption and switching assistant with manual cumulative readings, configurable billing cycles and thresholds, linear forecasting, environment/active-meter tracking, adaptive switch recommendations, WorkManager reminders, and portable historical data.

Before major feature expansion, the discovery baseline identifies stabilization work around database migration registration, short-month billing-cycle handling, single-reading cycle calculations, and cumulative-reading integrity checks.

## Use in Future Development

Any coding agent or developer should read `KWH_DEVELOPMENT_CONTEXT.md` first, then consult the specialized audit document relevant to the task before changing production code.

Do not replace or rewrite the included source documents without an explicit new baseline review.
