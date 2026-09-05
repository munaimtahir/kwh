# KWH Android App — Future Feature Readiness Assessment

This document evaluates the architectural readiness of the existing KWH codebase to support high-priority future capabilities without requiring destructive refactoring.

---

## 1. Advanced Visualization & Analytics

### Requirements
- Daily, weekly, and monthly consumption breakdowns.
- Year-over-year and month-over-month comparisons.
- Tariff slab horizontal threshold overlays.
- Projected threshold crossing trajectories.
- Annual review and seasonal peak usage detection.

### Architectural Readiness: **MODERATE (60%)**
- **Existing Strengths**:
  - `meter_readings` stores raw timestamped readings (`recordedAt: Long`, `value: Double`). Historical data accumulates indefinitely without automatic purging.
  - The repository reactive pipeline can easily expose new analytical flows.
- **Required Changes**:
  1. **Data Aggregation**: Raw readings represent cumulative values (e.g. 15420.5 kWh), not interval consumption. An analytical query engine or SQLite window functions (e.g. `LAG(value) OVER (ORDER BY recorded_at)`) must be introduced to compute discrete daily/weekly usage.
  2. **Chart Library Integration**: The existing `HistoryScreen` uses a raw custom Canvas drawing 6px polylines. It lacks touch-scrubbing, scale axes, and gridlines. A dedicated Compose chart library (e.g., **Vico** or **ComposeCharts**) should be adopted rather than hand-coding complex multi-series Canvas graphs.
  3. **Multi-Cycle Boundary Handling**: The current `BillingCycleCalculator` only computes the current cycle. It must be expanded to generate historical cycle windows `[start_k, end_k)` given an anchor day.

---

## 2. OCR Meter Reading (Camera Capture)

### Requirements
Flow: Camera viewfinder → image frame capture → on-device text recognition (OCR) → extracted numeric digits → user confirmation dialog → canonical save pipeline.

### Architectural Readiness: **HIGH (80%)**
- **Existing Strengths**:
  - Reading creation is already centralized in `MainActivity.AddReadingDialog` calling `HomeViewModel.addReading()`, which routes directly to `MeterRepository.addReading()`.
  - Manual review is already a core design pattern: the user always inspects and confirms the number before persisting.
- **Required Changes**:
  1. **Dependencies & Permissions**:
     - Declare `<uses-permission android:name="android.permission.CAMERA" />`.
     - Add CameraX (`androidx.camera:camera-camera2`, `camera-lifecycle`, `camera-view`) and Google ML Kit Text Recognition (`com.google.android.gms:play-services-mlkit-text-recognition` or bundled equivalent for 100% offline).
  2. **Integration Point**:
     - Add a camera icon button next to the `readingText` input in `AddReadingDialog`.
     - Tap launches a full-screen or bottom-sheet camera overlay with a bounding box guide.
     - Extracted digits pre-populate the `NumberField`, allowing the user to review, edit, or accept before saving.
  3. **Validation Pipeline**:
     - Must reuse existing repository validation (`> 0.0`, `recordedAt <= now`).
     - Should add a monotonicity sanity check comparing OCR value against `latestReading.value`.

---

## 3. Personalized Goals & Budgeting

### Requirements
- Monthly consumption targets (e.g., "Keep usage under 250 units this month").
- Monetary spending caps (e.g., "Stay under $50").
- Real-time pacing indicators ("You are 15% ahead of your monthly pace").

### Architectural Readiness: **HIGH (85%)**
- **Existing Strengths**:
  - `MeterEntity` already stores custom configuration columns (`billing_anchor_day`, `thresholds_csv`, `safety_buffer_days`).
  - `CycleStats` already computes daily burn rate (`ratePerDay`) and end-of-cycle projection (`projectedUnits`).
- **Required Changes**:
  1. **Schema Addition (Migration 4 → 5)**:
     - Add `monthly_target_units REAL DEFAULT NULL` to `MeterEntity`.
  2. **Pacing Math**:
     - Pacing ratio: $\text{pacing} = \frac{\text{projectedUnits}}{\text{monthlyTargetUnits}}$.
     - Color-code gauge: Green ($<0.9$), Amber ($0.9 - 1.0$), Red ($>1.0$).

---

## 4. Voice & Conversational Assistant

### Requirements
Natural language interactions:
- *"Add 4231.5 to Meter A."*
- *"How many units have I used this month on the Ground Floor?"*
- *"When will Meter B reach 300 units?"*
- *"Which meter should I switch to today?"*

### Architectural Readiness: **EXCELLENT (90%)**
- **Existing Strengths**:
  - Clean API boundaries: The domain operations are already encapsulated in clean, suspendable methods on `MeterRepository` that return typed immutable objects.
- **Direct API Mappings**:
  | Intent | Target Domain Method | Return Value |
  |---|---|---|
  | Log Reading | `MeterRepository.addReading(meterId, value, notes, timestamp)` | Reading ID |
  | Query Consumption | `MeterRepository.getCycleStats(meterId)` | `usedUnits`, `ratePerDay` |
  | Query Projection / ETA | `MeterRepository.getCycleStats(meterId).nextThreshold` | `threshold`, `eta` |
  | Query Switch Advice | `MeterRepository.getSwitchRecommendation(meterId)` | `switchByDate`, `urgency`, target meter |
  | Execute Switch | `MeterRepository.activateMeter(meterId)` | Activated meter entity |
- **Required Changes**:
  - Natural language intent parser / tool-calling bridge (can be an on-device SLM or rule-based regex parser). Zero modification required in the persistence or calculation core.

---

## 5. Multi-Meter Tariff & Cost Optimization

### Requirements
- Optimal load-distribution recommendation: *"Shift heavy appliances to Meter B; Meter A is within 15 units of slab 3."*
- Monetary savings quantification: *"Switching saved an estimated $42 last month."*

### Architectural Readiness: **MODERATE (65%)**
- **Existing Strengths**:
  - `SwitchAdvisor` already handles the physical switching advice based on units and safety buffers.
  - `MeterActivationEntity` tracks exact timestamps of which meter carried load when.
- **Required Changes**:
  1. **Tariff Slab Entity Model**:
     - Currently, the app only stores unit thresholds ("200,300"), with no pricing concepts.
     - A new `TariffPlanEntity` or `TariffSlabEntity` is required:
       ```kotlin
       data class TariffSlab(
           val slabIndex: Int,
           val upToUnits: Int,
           val ratePerUnit: Double,
           val fixedCharge: Double
       )
       ```
  2. **Cost Calculation Engine**:
     - A dedicated `BillingEngine` that maps `usedUnits` across tiered price slabs and computes total monetary cost.
