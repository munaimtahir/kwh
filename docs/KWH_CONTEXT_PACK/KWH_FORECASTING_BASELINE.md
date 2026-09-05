# KWH Android App — Forecasting, Consumption & Threshold Baseline

## 1. Domain Overview

The KWH calculation engine is responsible for converting raw, cumulative meter readings into actionable consumption insights:
1. Determining billing cycle time windows based on utility anchor days.
2. Determining cycle consumption (`usedUnits`).
3. Calculating daily consumption burn rates (calendar-elapsed vs. active-day-weighted).
4. Projecting end-of-cycle consumption (`projectedUnits`).
5. Projecting threshold breach calendar dates (`ThresholdForecast`).
6. Generating multi-meter switch recommendations (`SwitchAdvisor`).
7. Auto-tuning switch safety buffers via adaptive calibration (`recalibrateSafetyBuffer`).

All calculation components are pure Kotlin code located in `com.example.kwh.billing` and `com.example.kwh.repository`.

---

## 2. Mathematical & Algorithmic Baseline

### 2.1 Billing Cycle Window Calculation (`DefaultBillingCycleCalculator`)
Utility billing periods float around a monthly anchor day $A \in [1, 31]$.

#### Algorithm:
```text
Inputs: today = LocalDate.now(), anchorDay A in 1..31

1. anchorThisMonth = clamp A to length of current month:
   anchorThisMonth = today.withDayOfMonth(min(A, today.lengthOfMonth()))

2. If anchorThisMonth <= today:
       cycleStartDate = anchorThisMonth
   Else:
       previousMonth = today.minusMonths(1)
       cycleStartDate = previousMonth.withDayOfMonth(min(A, previousMonth.lengthOfMonth()))

3. cycleEndDate = cycleStartDate.plusMonths(1)

4. Return CycleWindow(
       start = cycleStartDate.atStartOfDay(ZoneId).toInstant(),
       end   = cycleEndDate.atStartOfDay(ZoneId).toInstant()
   )
```

#### Corner Case & Bug Analysis:
- **The Short-Month Void Bug**: Java's `LocalDate.plusMonths(1)` on the end of a short month clips to the length of the following month. 
  - If $A = 31$ and today is March 30, 2023:
    - `anchorThisMonth` (March 31) > March 30.
    - Fallback: `previousMonth` is February 2023 (28 days).
    - `cycleStartDate` = 2023-02-28.
    - `cycleEndDate` = `2023-02-28.plusMonths(1)` = **2023-03-28**.
    - Result: The calculated cycle ends on **March 28**, even though today is **March 30** and the anchor is **31**!
    - Consequence: Any readings logged on March 29, 30, or 31 fall into an orphaned void outside `[start, end)`.

---

### 2.2 Cycle Consumption Calculation (`buildCycleStats`)
Given a half-open window $[T_{\text{start}}, T_{\text{end}})$ and a list of readings sorted chronologically:

#### Algorithm:
```text
baselineCandidate = readings.firstOrNull { it.recordedAt in [start, end) }
carryForward      = readings.lastOrNull  { it.recordedAt < start }

baseline = baselineCandidate ?: carryForward
latest   = readings.lastOrNull { it.recordedAt in [start, end) }

If baseline != null and latest != null:
    usedUnits = latest.value - baseline.value
Else:
    usedUnits = 0.0
```

#### Corner Case & Flaw Analysis:
1. **Single Reading Cycle Zero-Usage Flaw**:
   If a user logs only one reading in the current cycle (e.g. Day 10: 1500 kWh), and has historical readings from previous cycles (e.g. Day -5: 1400 kWh):
   - `baselineCandidate` is Day 10 (1500 kWh).
   - `latest` is Day 10 (1500 kWh).
   - `usedUnits = 1500 - 1500 = 0.0 kWh`.
   - The 100 kWh consumed since the cycle start is ignored until a *second* reading is entered in the current cycle.
2. **Missing Boundary Carry-Over**:
   When `baselineCandidate` exists on Day 10, the algorithm completely discards `carryForward` from Day -2. The consumption between Day -2 and Day 10 is omitted from the current cycle.
3. **Non-Monotonic Readings**:
   If a user enters a lower reading (typo or meter reset), `usedUnits` evaluates to a negative number. No clamping or validation prevents this.

---

### 2.3 Daily Consumption Rate & Projections

#### Rate Formulas:
- **Ungrouped Meters** (Calendar Elapsed Days):
  $$\text{now}_{\text{clamped}} = \min(\text{Clock.now}(), T_{\text{end}} - 1\text{ms})$$
  $$\text{daysElapsed} = \max\left(1.0, \frac{\text{Duration}(T_{\text{start}}, \text{now}_{\text{clamped}})}{86,400,000}\right)$$
  $$\text{ratePerDay} = \frac{\text{usedUnits}}{\text{daysElapsed}}$$

- **Grouped Meters** (Active-Day-Weighted Days, M8):
  For meters assigned to an environment with activation history:
  $$\text{activeOverlap}_i = \max(0, \min(\text{endedAt}_i \text{ or now}, T_{\text{end}}) - \max(\text{startedAt}_i, T_{\text{start}}))$$
  $$\text{activeDays} = \max\left(1.0, \frac{\sum \text{activeOverlap}_i}{86,400,000}\right)$$
  $$\text{ratePerDay} = \frac{\text{usedUnits}}{\text{activeDays}}$$

#### End-of-Cycle Projection:
$$\text{cycleLengthDays} = \frac{\text{Duration}(T_{\text{start}}, T_{\text{end}})}{86,400,000}$$
$$\text{projectedUnits} = \text{round}(\text{ratePerDay} \times \text{cycleLengthDays})$$

---

### 2.4 Threshold Crossing Prediction (ETA)

#### Standard Threshold Forecast (`resolveNextThreshold`):
Iterates user-configured thresholds $T \in \{t_1, t_2, \dots\}$ in ascending order:
1. Filter $T > \text{usedUnits}$.
2. If $\text{ratePerDay} \le 0$, return `null`.
3. Calculate:
   $$\text{daysUntil} = \left\lceil \frac{T - \text{usedUnits}}{\text{ratePerDay}} \right\rceil$$
   $$\text{ETA} = \text{today} + \text{daysUntil}$$
4. If $\text{ETA} < \text{cycleEndDate}$: Return `ThresholdForecast(T, ETA)`.
5. If $\text{ETA} \ge \text{cycleEndDate}$: Return `null` (will not cross during this cycle).

---

### 2.5 Switch Recommendation Engine (`SwitchAdvisor`)

Designed specifically for multi-meter load shifting before entering higher tariff slabs:

#### 1. Target Selection (`switchThresholdForecast`):
Unlike the standard forecast which targets the *next* uncrossed threshold, `SwitchAdvisor` always targets the **lowest configured threshold** (e.g. 200 units).
$$\text{target} = \min(T)$$
$$\text{daysUntil} = \left\lceil \frac{\text{target} - \text{usedUnits}}{\text{ratePerDay}} \right\rceil$$
$$\text{ETA} = \text{today} + \text{daysUntil}$$
- If $\text{usedUnits} < \text{target}$ and $\text{ETA} \ge \text{cycleEndDate}$: Return `null`.
- If $\text{usedUnits} \ge \text{target}$: Always returns $\text{ETA}$ (which is $\le \text{today}$, generating an overdue recommendation).

#### 2. Switch Date & Urgency Calculation (`recommend`):
$$\text{switchByDate} = \text{forecast.ETA} - \text{safetyBufferDays}$$
$$\text{daysUntilSwitch} = \text{ChronoUnit.DAYS.between}(\text{today}, \text{switchByDate})$$

$$\text{Urgency} = \begin{cases} 
\text{OVERDUE} & \text{if } \text{today} \ge \text{switchByDate} \\
\text{SOON} & \text{if } \text{daysUntilSwitch} \le 2 \\
\text{NORMAL} & \text{otherwise}
\end{cases}$$

#### 3. Target Meter Selection:
In an environment with $N \ge 2$ meters, the recommended sibling meter is selected by:
$$\text{targetMeter} = \arg\min_{m \in \text{siblings}} (m.\text{cycleStats}.\text{usedUnits})$$

---

### 2.6 Adaptive Calibration Engine (`recalibrateSafetyBuffer`)

Whenever the user taps "Mark switched" or activates a new meter in an environment:
1. An outcome record is logged in `switch_outcomes`:
   $$(\text{meterId}, \text{recommendedDate}, \text{actualSwitchDate}, \text{unitsAtSwitch}, \text{thresholdTarget})$$
2. The last $N=6$ outcomes for the outgoing meter are inspected (minimum 3 required):
   $$\text{averageUnitsMargin} = \frac{1}{k} \sum_{i=1}^k (\text{thresholdTarget}_i - \text{unitsAtSwitch}_i)$$
   $$\text{averageThreshold} = \frac{1}{k} \sum_{i=1}^k \text{thresholdTarget}_i$$
   $$\text{marginRatio} = \frac{\text{averageUnitsMargin}}{\text{averageThreshold}}$$
3. Buffer adjustment:
   $$\Delta_{\text{buffer}} = \begin{cases} 
   +1 & \text{if } \text{averageUnitsMargin} < 0 \quad \text{(switched over threshold; widen buffer)} \\
   -1 & \text{if } \text{marginRatio} > 0.15 \quad \text{(comfortable margin >15%; tighten buffer)} \\
   0 & \text{otherwise}
   \end{cases}$$
4. Clamping:
   $$\text{safetyBufferDays}_{\text{new}} = \text{clamp}(\text{safetyBufferDays} + \Delta_{\text{buffer}}, 1, 10)$$

---

## 3. Forecasting Limitations & Weaknesses

1. **Strict Linearity**: The model assumes consumption from this point forward will match the average rate of the current cycle. It cannot detect acceleration (e.g. turning on multiple air conditioners on a hot weekend) or deceleration.
2. **Cycle Truncation**: No projections look beyond the current monthly billing cycle.
3. **No Historical Seasonality**: Past months or years of accumulated readings are not factored into the daily rate.
4. **Zero-Rate Deadlock**: If no consumption is recorded or readings are flat, $\text{ratePerDay} = 0$, completely disabling all forecasts and switch recommendations.
