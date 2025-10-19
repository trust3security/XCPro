# HAWK-UI-DESC.md

## Overview
The **LXNAV HawK variometer** is an advanced total energy (TE) variometer designed for gliders. It fuses data from **barometric**, **inertial (IMU)**, and **GPS** sensors to deliver accurate, real-time measurements of air mass movement, total energy changes, and glider performance.

The HawK's display provides pilots with essential flight performance data, including **lift/sink rate**, **Netto**, **average vario**, **speed-to-fly**, and **final-glide information**. It’s optimized for both thermal and cross-country gliding.

---

## Functional Description

### 1. Total Energy Variometer (TE)
- Measures rate of climb/sink corrected for glider energy changes.
- Compensates for stick inputs and airspeed variations.
- Provides accurate **airmass movement** indication, not just glider motion.

### 2. Inertial Compensation
- Uses IMU data to predictively smooth vario response.
- Reduces lag and false signals (“stick thermals”).
- Delivers instantaneous feedback for surge detection and centering.

### 3. Netto Variometer
- Subtracts glider polar sink from TE climb rate.
- Displays **airmass vertical speed** independent of glider performance.
- Ideal for optimizing cruise speed (dolphin flying).

### 4. Average Vario (Avvario)
- Rolling average over a time window (typically 20–30s).
- Shows the **average lift** over the last thermal or flight segment.
- Useful for judging **thermal quality**.

### 5. Speed-to-Fly (STF)
- Provides cue (needle/bar) for optimal flight speed based on:
  - Current **MacCready setting**
  - **Polar data**, **ballast**, and **bug degradation**
- Tells you when to speed up (in sink) or slow down (in lift).

### 6. MacCready Setting
- Defines expected climb rate for next thermal (m/s or kt).
- Drives STF, final-glide, and energy management algorithms.
- Adjustable in-flight or via linked flight computer.

### 7. Bugs and Ballast
- Adjusts polar performance for real-world conditions.
- Bugs reduce performance; ballast increases wing loading.
- Impacts STF and glide slope calculations.

### 8. Altitude Display
- Pressure-based altitude (QNH).
- Optionally shows **AGL** if terrain data is available.
- Typically central or lower on display (e.g., “990 m”).

### 9. Airspeed / TAS
- True Airspeed (TAS) or Indicated Airspeed (IAS) depending on sensor setup.
- Shown in km/h or knots.
- Critical for energy management and cruise performance.

### 10. Thermal Assistant
- Circular or sector display indicating lift distribution around turn.
- Helps optimize centering by marking where strongest lift occurred.

### 11. Quality (Q) Indicator
- Reflects sensor health and data quality.
- Lower “Q” means degraded sensor fusion confidence.

### 12. Lift/Sink Arc (Analog Scale)
- Color-coded arc with moving needle:
  - Green = Lift
  - Yellow = Weak sink
  - Red = Strong sink
- Instantaneous TE vario reference (outer scale).

### 13. Histogram / Lift Trace
- Small vertical bar showing lift/sink trend history.
- Useful for detecting changes entering/exiting thermals.

### 14. Audio Indication
- Audio tones correspond to TE vario reading.
- Features adjustable **deadband**, **pitch**, and **sink alarm**.
- Predictive tones trigger earlier due to IMU assistance.

### 15. Wind and Task Data (if connected)
- Shows computed **wind speed/direction**.
- Integrates with LX flight computer for **task**, **final glide**, and **arrival height** data.

---

## Example Display Elements (from image)

| Element | Example Value | Meaning |
|----------|----------------|----------|
| **Avvario** | +0.7 m/s | Average climb rate over last 20–30s |
| **Netto** | +2.3 m/s | Airmass lift rate (corrected for glider sink) |
| **Altitude** | 990 m | Barometric altitude (QNH) |
| **Speed (TAS)** | 138 km/h | True Airspeed |
| **Lift/Sink Arc** | Green ~ +2 m/s | Instantaneous TE vario |
| **Q Indicator** | 131 | Data quality |
| **MC (MacCready)** | 1.5 | Expected climb rate for STF computation |

---

## Typical Use Cases

- **Thermal climbing:** Focus on *Avvario* and *Thermal Assistant* to optimize centering.
- **Cruise flight:** Use *Netto* and *STF cue* to fly efficiently between thermals.
- **Final glide:** Rely on *Altitude*, *Wind*, and *Glide margin* for arrival accuracy.
- **Performance analysis:** Monitor *Average* and *Netto* values to identify strong areas of lift.

---

## Summary

The LXNAV HawK is a high-performance flight instrument that merges classic TE vario logic with modern IMU/GPS fusion for precision and responsiveness. It provides a complete airdata solution, ideal for modern soaring — from tight thermal work to high-speed cross-country and competition flying.
