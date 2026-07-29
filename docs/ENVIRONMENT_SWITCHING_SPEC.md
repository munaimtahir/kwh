# Environment Switching — Feature Spec (v1.0)
_Created: 2026-07-29_

## Problem

The user's electricity utility bills in **slabs that re-rate the entire month's consumption**, not just the marginal units: e.g. 0–100 units at rate A, but 101–200 units at rate B *applied to all units*, 201–300 at rate C *applied to all units*, and so on (the actual per-unit rates and the 100-unit slab width are configurable — the numbers used for illustration are arbitrary). Crossing a slab boundary by even one unit is expensive, since the higher rate applies retroactively to the whole month, not just the excess.

To stay in a lower slab, households run **more than one physical meter/connection** and manually move their load from one meter to another partway through the month, aiming to keep *each* meter's monthly usage under its slab boundary (commonly 100 units). The failure mode this feature targets: people forget to switch in time and a meter creeps past 100 (e.g. 103, 210) and gets re-rated at the next slab for the whole month.

## Goal

Group a household's meters into an **Environment**, track which meter currently carries the load, and have the app proactively recommend **when to switch** to the next meter — early enough to land under the slab boundary — using the billing-cycle math the app already has, refined to account for the fact that only one meter accumulates usage at a time. Recommendation accuracy should improve over time using the app's own history of past switches.

## Relationship to the existing app

This reuses, rather than replaces, the billing-cycle engine described in `FEATURE_SPEC.md` / `ALGORITHMS.md`: `BillingCycleCalculator`, cycle-window resolution, baseline/latest reading selection, `ratePerDay`, `projectedUnits`, and `nextThreshold` ETA are all still per-meter and still apply. What's new is (1) grouping meters into a household, (2) knowing which meter is currently in use, (3) turning "ETA to cross threshold" into an actionable "switch by this date" recommendation, and (4) correcting the rate estimate so idle periods (when a meter isn't carrying load) don't distort it.

## Concepts

| Term | Meaning |
|---|---|
| **Environment** | A physical site/home containing 2+ meters that share a switching strategy. |
| **Meter** | Existing entity — one physical connection with its own bill, billing anchor day, and slab thresholds (`thresholds_csv`). Independent of other meters in its environment. |
| **Active meter** | The one meter in an environment currently carrying the household load. Exactly one active meter per environment at a time (see Open Question 1). |
| **Activation log** | Timestamped record of when each meter was switched on (`started_at`) and off (`ended_at`). The ground truth for "how many days was this meter actually in use." |
| **Switch recommendation** | "Switch off Meter A by `<date>`" — the active meter's existing threshold ETA, minus a safety buffer. |
| **Safety buffer** | Days of margin subtracted from the raw threshold-crossing ETA, since real usage isn't perfectly linear. Starts as a configurable constant; Milestone M9 makes it self-tuning per meter. |

## Data model additions

```kotlin
@Entity(tableName = "environments")
data class EnvironmentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String
)

// MeterEntity gains:
@ColumnInfo(name = "environment_id", defaultValue = "NULL") val environmentId: Long? = null,
@ColumnInfo(name = "safety_buffer_days", defaultValue = "3") val safetyBufferDays: Int = 3

@Entity(
    tableName = "meter_activations",
    foreignKeys = [ForeignKey(entity = MeterEntity::class, parentColumns = ["id"],
        childColumns = ["meter_id"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("meter_id")]
)
data class MeterActivationEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "meter_id") val meterId: Long,
    @ColumnInfo(name = "started_at") val startedAt: Long,
    @ColumnInfo(name = "ended_at") val endedAt: Long? = null // null = currently active
)

// Milestone M9 only:
@Entity(tableName = "switch_outcomes")
data class SwitchOutcomeEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "meter_id") val meterId: Long,
    @ColumnInfo(name = "recommended_date") val recommendedDate: Long,
    @ColumnInfo(name = "actual_switch_date") val actualSwitchDate: Long,
    @ColumnInfo(name = "units_at_switch") val unitsAtSwitch: Double,
    @ColumnInfo(name = "threshold_target") val thresholdTarget: Int
)
```

Room migration bumps `MeterDatabase` version (currently 2 → 3 for M6, → 4 if `SwitchOutcomeEntity` lands in a later pass in M9). All new columns/tables default such that existing ungrouped meters keep working exactly as today — this feature is additive, not a replacement for standalone meter tracking.

## Algorithm changes

### Active-day-weighted rate (replaces naive calendar-elapsed days when a meter belongs to an environment)

Today, `MeterRepository.elapsedDays()` uses calendar days since cycle start. That's wrong once switching exists: a meter idle for two weeks mid-cycle would show an artificially low rate. Instead, for meters with activation history in the cycle window:

```
activeDays = sum over activation rows of:
    overlap(max(row.startedAt, cycleStart), min(row.endedAt ?: now, cycleEnd))
rate = usedUnits / max(1, activeDays)
```

Meters with no activation rows (not part of an environment) keep the existing calendar-day behavior — fully backward compatible.

### Switch recommendation

```
forecast = existing nextThreshold ETA for the active meter (already implemented)
switchByDate = forecast.eta - safetyBufferDays
urgency =
    OVERDUE   if today >= switchByDate and threshold not yet crossed
    SOON      if switchByDate - today <= 2 days
    NORMAL    otherwise
```

If `rate <= 0` (no usage yet, or falling), no recommendation is shown — same "not expected this cycle" behavior the threshold forecast already has.

### Adaptive calibration (M9)

At each switch, record `(recommendedDate, actualSwitchDate, unitsAtSwitch, thresholdTarget)`. Maintain a rolling average (last N=3–6 switches) of `actualSwitchDate - recommendedDate` and `unitsAtSwitch - thresholdTarget`, and nudge `safetyBufferDays` up/down within a clamped range (e.g. 1–10 days, ±1 day adjustment per month) so the buffer tightens if the user consistently switches early with margin to spare, and widens if they keep landing close to or over the threshold.

## UI changes

- **Home screen**: group meters by environment (header per environment; an "Ungrouped" bucket for meters not yet assigned). Active meter shown with a distinct chip/indicator.
- **Recommendation banner**: on Home (per environment) and Meter Detail (for the active meter) — "Switch to Meter B by Oct 18 · projected 96/100 units" with urgency styling (normal/soon/overdue) and a one-tap **Mark switched** action that closes the current activation row and opens one on the target meter.
- **Meter Settings**: add to an environment (create new or pick existing), `safetyBufferDays` field, read-only activation history list.
- **Notifications**: new "switch reminder" fires a few days before `switchByDate` and again if overdue, reusing the existing WorkManager + notification-channel pattern from `MeterReminderWorker`.

## Guardrails (carry over from the existing app, per `docs/Agent.md`)

- Stay fully local — no network calls, no analytics.
- Migrations must be additive/backward compatible; meters not in an environment must behave exactly as they do today.
- Unique WorkManager work names per meter/environment to avoid duplicate reminders.
- Validate all new inputs (safety buffer, environment name) the same way existing meter/reading inputs are validated.

## Open questions (confirm before/while building; current assumption noted)

1. **One active meter per environment at a time** — assumed yes. Flag if any environment runs two meters concurrently for different loads (would require overlapping activation periods, changing the model).
2. **Switch target threshold** — assumed to be the *lowest* value in the meter's existing `thresholds_csv` (e.g. 100), not a new separate field. Confirm this reuse is correct rather than wanting a distinct "switch limit" independent of billing thresholds.
3. **Billing anchor per meter** — assumed each meter keeps its own independent `billing_anchor_day` (utilities bill each connection separately) even within one environment. Confirm no shared/aligned-cycle requirement.

## Phased plan

See `docs/Roadmap.md` (milestones M6–M10) for the staged plan and exit criteria, and `docs/TASKS.md` for the granular checklist. See `docs/AGENT_ENVIRONMENT_SWITCHING.md` for the process any agent (human or AI) should follow while implementing it.
