# Algorithms & Pseudocode

## Cycle Window (anchor day → window)
```kotlin
fun currentWindow(anchorDay: Int, clock: Clock): CycleWindow {
    val now = ZonedDateTime.now(clock)
    val candidate = now.withDayOfMonth(min(anchorDay, now.toLocalDate().lengthOfMonth()))
    val start = if (candidate.isAfter(now)) candidate.minusMonths(1) else candidate
    val end = start.plusMonths(1)
    return CycleWindow(start.toInstant(), end.toInstant())
}
```

## Baseline & Latest
- Baseline = earliest reading ≥ start (if any) else latest reading < start (carry forward).
- Latest = last reading within `[start, end)`.

## Projection
```text
daysElapsed = max(1, daysBetween(start, now))
units = latest.value - baseline.value
rate = units / daysElapsed
projected = rate * daysBetween(start, end)
```

## Threshold ETA
```text
for T in thresholds.sortedAscending():
  if T > units and rate > 0:
    daysUntil = ceil((T - units)/rate)
    eta = today + daysUntil
    if eta < end: return (T, eta)
return null
```

## Active-day-weighted rate (Environment Switching, M8)
Only applies when a meter has `environment_id != null` **and** has activation rows; otherwise the
calendar `daysElapsed` above is used unchanged (fully backward compatible).
```text
activeDays = sum over this meter's activation rows of:
    overlap(max(row.startedAt, cycleStart), min(row.endedAt ?: now, cycleEnd))
rate = units / max(1, activeDays)
```
This replaces `daysElapsed` in the projection formula above; `projected` and threshold ETA are
computed from the resulting `rate` exactly as before. Idle periods (another meter carrying the
load) are excluded so they don't drag the rate down.

## Switch recommendation (Environment Switching, M8)
```text
forecast = switchThresholdForecast(thresholdsCsv, usedUnits, ratePerDay, cycleEnd, today)
  target = min(thresholds)   # the switch target is always the LOWEST configured threshold,
                             # not whichever threshold the generic nextThreshold ETA points at next
  daysUntil = ceil((target - usedUnits) / rate)   # may be negative/zero if already crossed
  eta = today + daysUntil
  if usedUnits < target and eta >= cycleEnd: return null   # not yet crossed and won't be this cycle
  return (target, eta)                                     # already-crossed => always returned (overdue)
if forecast == null or rate <= 0: no recommendation
switchByDate = forecast.eta - safetyBufferDays
urgency =
    OVERDUE if today >= switchByDate
    SOON    if (switchByDate - today) <= 2 days
    NORMAL  otherwise
switchTarget = the environment's non-active meter with the lowest current-cycle usedUnits
               (assumption for environments with more than 2 meters; not specified in the spec)
```
Note: `switchThresholdForecast` deliberately does **not** reuse `CycleStats.nextThreshold` — for a
meter with multiple thresholds (e.g. "100,200"), `nextThreshold` would retarget to 200 once 100 is
crossed, silently hiding the fact that the intended switch point was already missed.

## Adaptive calibration (Environment Switching, M9)
At each switch (via `activateMeter`), before closing the outgoing meter's activation: if it had an
active switch recommendation, record `(recommendedDate=switchByDate, actualSwitchDate=today,
unitsAtSwitch=usedUnits, thresholdTarget)` as a `switch_outcomes` row, then recalibrate that meter's
`safety_buffer_days` from its most recent outcomes:
```text
outcomes = last 6 switch_outcomes for this meter (need >= 3 to calibrate at all)
averageUnitsMargin = average(thresholdTarget - unitsAtSwitch)   # positive = landed under with room to spare
marginRatio = averageUnitsMargin / average(thresholdTarget)
adjustment =
    +1 if averageUnitsMargin < 0        # landed over the threshold on average -> widen
    -1 if marginRatio > 0.15            # consistently comfortable margin -> tighten
     0 otherwise
safetyBufferDays = clamp(safetyBufferDays + adjustment, 1, 10)
```
A bounded rolling-average nudge, not a model — see `docs/AGENT_ENVIRONMENT_SWITCHING.md` guardrails.
