# Barometric Altitude Variation Diagnostic Guide

## Problem Statement
Barometric altitude varies significantly when phone is stationary on desk indoors (no movement, no wind).

## Scientific Approach
We need to **measure and log actual data** to identify the root cause, not assume.

## Step 1: Enable Detailed Logging

### Add Enhanced Diagnostics to FlightDataCalculator.kt

Add this logging to capture all relevant variables:

```kotlin
// In calculateFlightData(), after barometer calculation, add:
if (currentTime % 1000 < 100) { // Log every 1 second (not 5 seconds)
    if (baroResult != null && baro != null) {
        Log.d(TAG, "===== BARO DIAGNOSTICS =====")
        Log.d(TAG, "Raw Pressure: ${String.format("%.2f", baro.pressureHPa)} hPa")
        Log.d(TAG, "Baro Altitude: ${String.format("%.2f", baroResult.altitudeMeters)} m")
        Log.d(TAG, "QNH: ${String.format("%.2f", baroResult.qnh)} hPa (${if (baroResult.isCalibrated) "CALIBRATED" else "UNCALIBRATED"})")
        Log.d(TAG, "Confidence: ${baroResult.confidenceLevel}")
        Log.d(TAG, "GPS Altitude: ${String.format("%.2f", gps.altitude)} m")
        Log.d(TAG, "GPS Accuracy: ${String.format("%.1f", gps.accuracy)} m")
        Log.d(TAG, "GPS Fixed: ${gps.isHighAccuracy}")
        Log.d(TAG, "GPS Speed: ${String.format("%.2f", gps.speed)} m/s")
        Log.d(TAG, "Vario (Optimized): ${String.format("%.2f", varioResults["optimized"])} m/s")
        Log.d(TAG, "Vario (Raw): ${String.format("%.2f", varioResults["raw"])} m/s")
        Log.d(TAG, "Delta Time: ${String.format("%.3f", deltaTime)} s")
        Log.d(TAG, "Kalman R_altitude: ${modernVarioFilter.diagnosticsCollector.getStats()}")
        Log.d(TAG, "============================")
    }
}
```

## Step 2: Collect Data for 5 Minutes

Run the app with logging:

```bash
adb logcat -s "FlightDataCalculator" -v time > baro_diagnostic_log.txt
```

Let it run for 5 minutes while phone sits stationary on desk.

## Step 3: Analysis Checklist

From the log, we need to determine:

### ✅ GPS Status
- [ ] Is GPS actually working? (GPS Altitude > 0)
- [ ] What is GPS accuracy? (< 10m = good, > 50m = poor)
- [ ] Is GPS Fixed = true?
- [ ] What is GPS speed? (should be ~0.0 m/s when stationary)

### ✅ QNH Calibration Status
- [ ] Is QNH calibrated? (CALIBRATED vs UNCALIBRATED)
- [ ] What is the QNH value? (should be 950-1050 hPa range)
- [ ] Does QNH change during the test?

### ✅ Barometer Readings
- [ ] What is the raw pressure? (should be stable ±1-2 hPa)
- [ ] How much does raw pressure vary? (calculate std dev)
- [ ] Does pressure drift linearly? (temperature drift)
- [ ] Does pressure oscillate? (HVAC/ventilation)

### ✅ Altitude Variation
- [ ] What is the altitude range? (max - min)
- [ ] Is variation random noise or systematic drift?
- [ ] Pattern: random spikes, slow drift, oscillation?

### ✅ Filter Behavior
- [ ] What is Kalman R_altitude being used? (should be 5.0m or 20.0m stationary)
- [ ] What is the sample rate? (Delta Time should be ~0.1-1.0s)
- [ ] Is vario (Raw) noisy but vario (Optimized) smooth?

## Step 4: Root Cause Determination Matrix

Based on the data patterns:

| Pattern Observed | Most Likely Cause |
|------------------|-------------------|
| Raw pressure drifts steadily (±5+ hPa over minutes) | **Temperature drift** - phone heating up |
| Raw pressure oscillates (±2-5 hPa, period 10-60s) | **HVAC/ventilation** - air conditioning cycles |
| Raw pressure stable, but altitude varies | **QNH not calibrated** OR **bad GPS-baro fusion** |
| QNH = 1013.25 hPa and UNCALIBRATED | **No GPS fix** - not getting good enough GPS |
| GPS Accuracy > 50m | **Indoor GPS poor** - can't calibrate |
| Delta Time varies wildly (0.01s to 5s) | **Sensor timing issue** - irregular updates |
| Vario (Raw) shows ±2 m/s swings | **Normal sensor noise** - not filtered yet |
| Altitude jumps exactly when QNH recalibrates | **GPS altitude error** - bad calibration source |

## Step 5: Calculate Statistics from Log

Use this script to analyze the log:

```python
import re
import statistics

raw_pressures = []
baro_altitudes = []
qnh_values = []
gps_accuracies = []

with open('baro_diagnostic_log.txt', 'r') as f:
    for line in f:
        if 'Raw Pressure:' in line:
            match = re.search(r'Raw Pressure: ([\d.]+)', line)
            if match:
                raw_pressures.append(float(match.group(1)))

        if 'Baro Altitude:' in line:
            match = re.search(r'Baro Altitude: ([\d.]+)', line)
            if match:
                baro_altitudes.append(float(match.group(1)))

        if 'QNH:' in line:
            match = re.search(r'QNH: ([\d.]+)', line)
            if match:
                qnh_values.append(float(match.group(1)))

        if 'GPS Accuracy:' in line:
            match = re.search(r'GPS Accuracy: ([\d.]+)', line)
            if match:
                gps_accuracies.append(float(match.group(1)))

if raw_pressures:
    print(f"Raw Pressure Statistics:")
    print(f"  Mean: {statistics.mean(raw_pressures):.2f} hPa")
    print(f"  Std Dev: {statistics.stdev(raw_pressures):.2f} hPa")
    print(f"  Range: {min(raw_pressures):.2f} - {max(raw_pressures):.2f} hPa")
    print(f"  Variation: {max(raw_pressures) - min(raw_pressures):.2f} hPa")

if baro_altitudes:
    print(f"\nBarometric Altitude Statistics:")
    print(f"  Mean: {statistics.mean(baro_altitudes):.2f} m")
    print(f"  Std Dev: {statistics.stdev(baro_altitudes):.2f} m")
    print(f"  Range: {min(baro_altitudes):.2f} - {max(baro_altitudes):.2f} m")
    print(f"  Variation: {max(baro_altitudes) - min(baro_altitudes):.2f} m")

if qnh_values:
    print(f"\nQNH Statistics:")
    print(f"  Mean: {statistics.mean(qnh_values):.2f} hPa")
    print(f"  Std Dev: {statistics.stdev(qnh_values):.2f} hPa")
    print(f"  Stable: {'YES' if statistics.stdev(qnh_values) < 1.0 else 'NO'}")

if gps_accuracies:
    print(f"\nGPS Accuracy Statistics:")
    print(f"  Mean: {statistics.mean(gps_accuracies):.2f} m")
    print(f"  Std Dev: {statistics.stdev(gps_accuracies):.2f} m")
    print(f"  Quality: ", end='')
    avg = statistics.mean(gps_accuracies)
    if avg < 10:
        print("EXCELLENT")
    elif avg < 20:
        print("GOOD")
    elif avg < 50:
        print("POOR")
    else:
        print("VERY POOR (likely indoors)")
```

## Step 6: Next Steps Based on Results

Once we have the statistics, we can:

1. **Identify the dominant noise source** (pressure variation, QNH drift, GPS errors)
2. **Measure correlation** (does altitude vary with pressure? with QNH changes?)
3. **Determine fix priority** (address the largest contributor first)

## Quick Check Right Now

Can you run this command and paste the output here?

```bash
adb logcat -s "FlightDataCalculator" -v time -d | tail -50
```

This will show the last 50 log lines so we can see:
- Is GPS working?
- Is QNH calibrated?
- What are the actual values?

Then we'll know exactly what's causing the variation.
