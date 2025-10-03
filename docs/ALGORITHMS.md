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
