# KWH Android App — Data Model & Persistence Audit

## 1. Persistence Overview

The application utilizes two persistence mechanisms:
1. **AndroidX Room (SQLite)**: Primary structured database named `"meter.db"` storing meters, readings, environments, activations, and switch outcomes. Database schema version is **4**.
2. **Jetpack DataStore Preferences**: Key-value store named `"settings"` storing user interface and reminder behavior preferences.

---

## 2. Room Database Entities

### 2.1 `MeterEntity` (Table: `meters`)
Represents an electricity meter configured by the user.

| Field | Column Name | Type | Nullable | Default | Purpose / Constraints |
|---|---|---|---|---|---|
| `id` | `id` | `Long` | No | `0` (autogen) | Primary Key (`autoGenerate = true`). Unique identifier. |
| `name` | `name` | `String` | No | — | User-assigned label (e.g., "Ground Floor"). Validated non-blank. |
| `reminderEnabled` | `reminder_enabled` | `Boolean` | No | `false` | Whether periodic reading reminder worker is active. |
| `reminderFrequencyDays` | `reminder_frequency_days` | `Int` | No | `7` | Days between scheduled reminders. Coerced to `>= 1`. |
| `reminderHour` | `reminder_hour` | `Int` | No | `9` | Hour of the day (0–23) to trigger notification. |
| `reminderMinute` | `reminder_minute` | `Int` | No | `0` | Minute of the hour (0–59) to trigger notification. |
| `billingAnchorDay` | `billing_anchor_day` | `Int` | No | `1` | Day of month (1–31) utility resets billing cycle. Added in v2. |
| `thresholdsCsv` | `thresholds_csv` | `String` | No | `'200,300'` | Comma-separated consumption slab limits (e.g., "200,300"). Added in v2. |
| `environmentId` | `environment_id` | `Long?` | Yes | `NULL` | Soft reference to parent `environments.id`. Added in v3. |
| `safetyBufferDays` | `safety_buffer_days` | `Int` | No | `3` | Lead days before threshold ETA to recommend switching. Clamped `1..10`. Added in v3. |

*Note*: `environment_id` does NOT have an explicit SQLite foreign key constraint in `MeterEntity`; unassignment is managed in repository code upon environment deletion.

---

### 2.2 `MeterReadingEntity` (Table: `meter_readings`)
Represents a cumulative cumulative meter reading logged at a point in time.

| Field | Column Name | Type | Nullable | Default | Purpose / Constraints |
|---|---|---|---|---|---|
| `id` | `id` | `Long` | No | `0` (autogen) | Primary Key (`autoGenerate = true`). |
| `meterId` | `meter_id` | `Long` | No | — | Foreign Key to `meters(id)` with `onDelete = CASCADE`. |
| `value` | `value` | `Double` | No | — | Cumulative meter reading in kWh. Validated `> 0.0`. |
| `notes` | `notes` | `String?` | Yes | `null` | Optional user annotations (e.g. "After AC cleaning"). |
| `recordedAt` | `recorded_at` | `Long` | No | — | Epoch millisecond timestamp of the reading. |

*Indices*:
- `index_meter_readings_meter_id` on `meter_id`.
- *No uniqueness constraint*: Multiple readings with identical timestamps or identical values can be inserted without collision.

---

### 2.3 `EnvironmentEntity` (Table: `environments`)
Logical grouping of meters serving the same premise where only one meter is active at a time (introduced in Schema v3).

| Field | Column Name | Type | Nullable | Default | Purpose / Constraints |
|---|---|---|---|---|---|
| `id` | `id` | `Long` | No | `0` (autogen) | Primary Key (`autoGenerate = true`). |
| `name` | `name` | `String` | No | — | User-assigned environment name (e.g. "Main House"). |

---

### 2.4 `MeterActivationEntity` (Table: `meter_activations`)
Logs activation time ranges during which a specific meter was actively carrying electrical load (introduced in Schema v3).

| Field | Column Name | Type | Nullable | Default | Purpose / Constraints |
|---|---|---|---|---|---|
| `id` | `id` | `Long` | No | `0` (autogen) | Primary Key (`autoGenerate = true`). |
| `meterId` | `meter_id` | `Long` | No | — | Foreign Key to `meters(id)` with `onDelete = CASCADE`. |
| `startedAt` | `started_at` | `Long` | No | — | Epoch millisecond when meter was set as active. |
| `endedAt` | `ended_at` | `Long?` | Yes | `null` | Epoch millisecond when deactivated. `null` indicates ongoing activation. |

*Indices*:
- `index_meter_activations_meter_id` on `meter_id`.

---

### 2.5 `SwitchOutcomeEntity` (Table: `switch_outcomes`)
Historical log of predicted vs. actual switches used for adaptive calibration (introduced in Schema v4).

| Field | Column Name | Type | Nullable | Default | Purpose / Constraints |
|---|---|---|---|---|---|
| `id` | `id` | `Long` | No | `0` (autogen) | Primary Key (`autoGenerate = true`). |
| `meterId` | `meter_id` | `Long` | No | — | Foreign Key to `meters(id)` with `onDelete = CASCADE`. |
| `recommendedDate` | `recommended_date` | `Long` | No | — | Target switch date calculated by `SwitchAdvisor` (epoch ms). |
| `actualSwitchDate` | `actual_switch_date` | `Long` | No | — | Calendar date switch was executed by user (epoch ms). |
| `unitsAtSwitch` | `units_at_switch` | `Double` | No | — | Cumulative cycle consumption recorded at moment of switch. |
| `thresholdTarget` | `threshold_target` | `Int` | No | — | Target threshold slab (e.g. 200). |

*Indices*:
- `index_switch_outcomes_meter_id` on `meter_id`.

---

## 3. Room Relations & In-Memory Pairings

1. **`MeterWithReadings`**:
   Room `@Relation` joining `MeterEntity` (`parentColumn = "id"`) to `List<MeterReadingEntity>` (`entityColumn = "meter_id"`). Used by `MeterDao.observeMetersWithReadings()` to stream complete meter data.
2. **`MeterWithLatestReading`**:
   POJO pairing a `MeterEntity` with an optional single `latestReading: MeterReadingEntity?`.

---

## 4. Jetpack DataStore Preferences (`settings`)

Stored in `settings.preferences_pb`:

| Key | Type | Default | Purpose |
|---|---|---|---|
| `dark_theme` | `Boolean` | `false` | Global dark mode toggle. |
| `snooze_minutes` | `Int` | `60` | Duration (in minutes) to defer reading reminder notifications. |

---

## 5. Database Schema Migrations History

- **`MIGRATION_1_2` (v1 → v2)**:
  - `ALTER TABLE meters ADD COLUMN billing_anchor_day INTEGER NOT NULL DEFAULT 1`
  - `ALTER TABLE meters ADD COLUMN thresholds_csv TEXT NOT NULL DEFAULT '200,300'`
- **`MIGRATION_2_3` (v2 → v3)**:
  - `CREATE TABLE environments (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, name TEXT NOT NULL)`
  - `ALTER TABLE meters ADD COLUMN environment_id INTEGER DEFAULT NULL`
  - `ALTER TABLE meters ADD COLUMN safety_buffer_days INTEGER NOT NULL DEFAULT 3`
  - `CREATE TABLE meter_activations (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, meter_id INTEGER NOT NULL, startedAt INTEGER NOT NULL, ended_at INTEGER, FOREIGN KEY(meter_id) REFERENCES meters(id) ON DELETE CASCADE)`
  - `CREATE INDEX index_meter_activations_meter_id ON meter_activations(meter_id)`
- **`MIGRATION_3_4` (v3 → v4)**:
  - `CREATE TABLE switch_outcomes (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, meter_id INTEGER NOT NULL, recommended_date INTEGER NOT NULL, actual_switch_date INTEGER NOT NULL, units_at_switch REAL NOT NULL, threshold_target INTEGER NOT NULL, FOREIGN KEY(meter_id) REFERENCES meters(id) ON DELETE CASCADE)`
  - `CREATE INDEX index_switch_outcomes_meter_id ON switch_outcomes(meter_id)`

---

## 6. Data Integrity & Safety Findings

### Cascading Deletion
- **Meters**: Deleting a meter triggers SQLite `ON DELETE CASCADE` across `meter_readings`, `meter_activations`, and `switch_outcomes`. All historical readings for that meter are permanently erased from SQLite.
- **Environments**: Deleting an environment does **not** delete its meters. `MeterRepository.deleteEnvironment()` explicitly iterates member meters, sets `environmentId = null` (returning them to "Ungrouped"), and then deletes the environment record.

### Reading Edit, Backdating, & Deletion
- **Backdating**: Supported without constraint. Readings entered for past dates are sorted chronologically by `recordedAt`.
- **Duplicate Prevention**: None. Two identical readings entered on the exact same date will both be stored.
- **Monotonicity**: Not enforced. If an entry is lower than a previous reading, `latest - baseline` will produce a negative value or corrupt the rate calculation.
- **Meter Rollover / Replacement**: Not supported in the data model. Resetting or swapping a physical meter requires deleting the old meter or manually adjusting numbers.

### Backup & Export
- Android Auto Backup (`backup_rules.xml` & `data_extraction_rules.xml`) is configured to include `database` (`meter.db`) and `sharedpref` (DataStore).
- Standalone CSV Export/Import (Format v2) acts as the offline manual backup mechanism.
