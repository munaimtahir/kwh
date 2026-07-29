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

## Derived Views
- Latest reading per meter (LEFT JOIN; nullable if none).
- Active meter per environment: the meter in that environment with an open (`ended_at IS NULL`) activation row.
- Active activation per meter: the meter's activation row with `ended_at IS NULL`, if any.

## Migrations
- 1→2: added `billing_anchor_day`, `thresholds_csv` to `meters`.
- 2→3: added `environments` table, `environment_id` + `safety_buffer_days` to `meters`, `meter_activations` table (FK cascade on `meter_id`). Fully additive — meters with `environment_id = null` are unaffected.
