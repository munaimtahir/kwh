# Electricity Meter Tracker — Feature Spec (v1.0)
_Last updated: 2025-10-03

## Goal
Track per‑meter monthly kWh usage based on each meter’s custom billing cycle **anchor date** (the “reading date” set by the utility), show projections, and warn users when configurable unit **thresholds** (e.g., 200, 300) might be crossed.

## What already exists in the repo
- Kotlin + Jetpack Compose UI, Hilt DI, Room DB.
- `MeterEntity`, `MeterReadingEntity`, `MeterDao`, `MeterRepository`.
- Screens: Home, History, Settings. CSV import/export present.
- Reminder infrastructure via `ReminderScheduler` + `MeterReminderWorker` with DataStore snooze.
- Unit & instrumentation tests scaffolding.

This is a strong foundation to add billing‑cycle logic and threshold projections.

---

## New Concepts

### 1) Billing Cycle
Each meter defines its **billing anchor** as the day‑of‑month (1–28/29/30/31) when the utility captures/rolls the reading.

- Store **`billingAnchorDay`** (Int 1–31).
- Derive the **current cycle window**: `[cycleStart, cycleEnd)` such that `cycleStart` is the most recent date with `day == billingAnchorDay` not after **today**, and `cycleEnd` is `cycleStart + 1 month`.

**Edge cases**
- Months lacking that day (e.g., 31st in February): use the last day of month as the anchor for that month.
- User can override the anchor later; we recompute cycle windows on the fly.

### 2) Baseline (Cycle Start Reading)
To compute usage inside the current cycle, we need a **baseline reading** at (or immediately before) `cycleStart`.
- If a reading exists **on/after** `cycleStart` and is the earliest in the window, use it as baseline.
- Else, use the **latest reading before `cycleStart`** as baseline (value carried forward).

### 3) Thresholds
Per meter, a **list of thresholds** in units (e.g., `[200, 300, 700]`) with on/off toggle.
- Default: Pakistan slabs commonly at 200/300/etc., but let users edit freely.
- We’ll warn when projected usage is expected to cross a threshold within the cycle.

### 4) Projection
At any time `t`:
- `daysElapsed = max(1, daysBetween(cycleStart, t))`
- `unitsConsumed = latestReading.value - baseline.value`
- `rate = unitsConsumed / daysElapsed  (units/day)`
- `cycleLengthDays = daysBetween(cycleStart, cycleEnd)`
- `projectedCycleUnits = round(rate * cycleLengthDays)`

We display:
- **Units so far** (since cycle start)
- **Projected units** by cycle end
- **Next threshold ETA** (if any): for a threshold `T > unitsConsumed`, daysUntil = `(T - unitsConsumed) / rate`. If `rate == 0` show “not expected this cycle.”

---

## UX Changes

### Home Screen (per meter card)
- Title: meter name.
- Subtitle: "Cycle {{DD Mon}}–{{DD Mon}}" (e.g., "Cycle 08 Oct–08 Nov").
- Chips: "Used: 143 kWh", "Projected: 287 kWh".
- Threshold progress bar with tick marks at thresholds (optional v2).
- Button: **Add Reading** (opens dialog; already exists).
- Icon buttons: **History**, **Settings** (per‑meter).

### History Screen
- Show current cycle, baseline, latest.
- Small line graph of readings over the cycle with slope (units/day).

### Meter Settings (new or extend existing)
- Billing anchor day (spinner 1–31).
- Thresholds editor (comma‑separated list or chips).
- Reminder: frequency preset **7 days** by default (still editable).

### Notifications
- Weekly reminder via existing WorkManager.
- Threshold alert: "Projected to cross 200 units on 21 Oct. Add a reading or reduce usage."
- "Missed reading this cycle?" smart nudge 5 days before cycle end if no readings yet.

---

## Data Model Changes (Room)

```kotlin
// Add to MeterEntity
@ColumnInfo(name = "billing_anchor_day") val billingAnchorDay: Int = 1,
@ColumnInfo(name = "thresholds_csv") val thresholdsCsv: String = "200,300", // simple storage
```

**Migration (Room 1 → 2):**
- Add columns with defaults; backfill existing meters with `billingAnchorDay = 1`, `thresholdsCsv = "200,300"`.

Alternative (advanced): use a `MeterThresholdEntity` child table for richer per‑threshold metadata. CSV keeps it simple for v1.

---

## Repository API (additions)

```kotlin
data class CycleWindow(val start: Instant, val end: Instant)
data class CycleStats(
    val window: CycleWindow,
    val baseline: MeterReadingEntity?, // can be null if no data
    val latest: MeterReadingEntity?,
    val usedUnits: Double,
    val projectedUnits: Double?,
    val nextThreshold: Int?, // e.g., 200
    val nextThresholdDate: LocalDate?
)

interface BillingCycleCalculator {
    fun currentWindow(anchorDay: Int, clock: Clock = Clock.systemDefaultZone()): CycleWindow
    suspend fun cycleStats(meterId: Long): CycleStats
}

// MeterRepository additions
suspend fun getCycleStats(meterId: Long): CycleStats
```

**DAO helpers**
```sql
-- Latest reading strictly before start
SELECT * FROM meter_readings 
WHERE meter_id = :meterId AND recorded_at < :start 
ORDER BY recorded_at DESC LIMIT 1;

-- Earliest reading on/after start within the window
SELECT * FROM meter_readings 
WHERE meter_id = :meterId AND recorded_at >= :start AND recorded_at < :end
ORDER BY recorded_at ASC LIMIT 1;

-- Latest reading in the window
SELECT * FROM meter_readings 
WHERE meter_id = :meterId AND recorded_at >= :start AND recorded_at < :end
ORDER BY recorded_at DESC LIMIT 1;
```

---

## Threshold Prediction Algorithm (v1)

1. Compute `usedUnits` and `rate` as above.  
2. For each threshold `T` > `usedUnits`:
   - If `rate <= 0` → skip (not expected this cycle).
   - `daysUntil = ceil((T - usedUnits) / rate)`
   - `eta = today + daysUntil`
   - If `eta < cycleEnd`, choose the **nearest** upcoming threshold as `nextThreshold`.
3. Expose to UI and `MeterReminderWorker` for notifications.

---

## WorkManager Integration

- Keep existing reminder scheduling. Default to **7 days** for new meters.
- Extend `MeterReminderWorker` to **also** compute `CycleStats`:
  - If `nextThresholdDate` is within the next **7 days**, show a proactive alert.
  - If **no readings** recorded this cycle and we’re > 10 days into the cycle → nudge.

Use unique work name per meter: "meter-reminder-{meterId}" (already follows this style).

---

## Graphs

For Compose, use either:
- MPAndroidChart with Compose wrapper, or
- A lightweight Canvas line chart (since data points are sparse).

MVP: simple line plot of readings (x: date, y: value), with a dashed line for projection to `cycleEnd`.

---

## Testing

- Unit tests for `currentWindow()`, February edge cases, anchors 29–31.
- Repo tests for baseline selection and projections.
- Worker test with fake clock to validate threshold ETA notifications.

---

## Migration Plan (safe & incremental)

1. **DB migration**: add `billingAnchorDay`, `thresholdsCsv`. Bump Room version.
2. **Calculator**: implement `BillingCycleCalculator` + repo plumbing.
3. **UI**: show cycle chips and projections on Home/History.
4. **Settings UI**: add anchor + thresholds editor.
5. **Worker**: enrich reminder with threshold checks.
6. **Polish**: CSV import/export include anchor & thresholds.
7. **Docs**: update README and tests.

---

## Nice‑to‑haves (v2)
- Per‑meter timezone.
- Meter rollover handling (reset to 0) with “manual correction” flag.
- Multi‑user/cloud sync.
- Widgets for quick add reading.
