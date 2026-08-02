# DataModel.md
## Tables
### meters
- id (PK, auto)
- name (TEXT, not null)
- reminder_enabled (BOOLEAN)
- reminder_frequency_days (INT)
- reminder_hour (INT)
- reminder_minute (INT)
- billing_anchor_day (INT, default 1)
- thresholds_csv (TEXT, default "200,300")
- environment_id (LONG, nullable, default NULL) — FK to `environments.id` (no enforced FK constraint; null means the meter is not part of an environment and behaves exactly as before this feature)
- safety_buffer_days (INT, default 3) — see `ENVIRONMENT_SWITCHING_SPEC.md`

### meter_readings
- id (PK, auto)
- meter_id (FK → meters.id, CASCADE)
- value (REAL, not null)
- notes (TEXT, nullable)
- recorded_at (LONG epoch millis)

### environments
_Added in schema v3 (Environment Switching M6)._
- id (PK, auto)
- name (TEXT, not null)

### meter_activations
_Added in schema v3 (Environment Switching M6)._
- id (PK, auto)
- meter_id (FK → meters.id, CASCADE)
- started_at (LONG epoch millis, not null)
- ended_at (LONG epoch millis, nullable — null means this is the meter's currently open/active activation)

### switch_outcomes
_Added in schema v4 (Environment Switching M9)._
- id (PK, auto)
- meter_id (FK → meters.id, CASCADE)
- recommended_date (LONG epoch millis, not null) — the computed `switchByDate` at the moment of switching away
- actual_switch_date (LONG epoch millis, not null) — when the user actually switched away
- units_at_switch (REAL, not null) — the outgoing meter's cycle usage at that moment
- threshold_target (INT, not null) — the lowest configured threshold this switch was measured against

## Derived Views
- Latest reading per meter (LEFT JOIN; nullable if none).
- Active meter per environment: the meter in that environment with an open (`ended_at IS NULL`) activation row.
- Active activation per meter: the meter's activation row with `ended_at IS NULL`, if any.
- Switch recommendation per environment (M8, not persisted): computed on the fly from the active meter's usage/rate against its *lowest* configured threshold and `safety_buffer_days` — see `ALGORITHMS.md` and `MeterRepository.environmentOverviews`. Uses `SwitchAdvisor.switchThresholdForecast`, not `CycleStats.nextThreshold` directly (the latter can retarget to a higher slab once the lowest is crossed).

## CSV export/import (M10)
Per-meter reading export (`HistoryViewModel`, format v2) embeds `billing_anchor_day`, `thresholds_csv` (quoted, since it's itself comma-separated), `environment_name`, and `safety_buffer_days` on every reading row, plus a trailing `# activations` section with `started_at,ended_at` rows when the meter has activation history. Import is additive/backward compatible: v1 files (no environment/buffer columns, no activations section) still import readings/anchor/thresholds exactly as before; an environment name that doesn't exist yet is created on import.

## Migrations
- 1→2: added `billing_anchor_day`, `thresholds_csv` to `meters`.
- 2→3: added `environments` table, `environment_id` + `safety_buffer_days` to `meters`, `meter_activations` table (FK cascade on `meter_id`). Fully additive — meters with `environment_id = null` are unaffected.
- 3→4: added `switch_outcomes` table (FK cascade on `meter_id`). Fully additive.
