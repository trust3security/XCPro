# XCSoar TAS/IAS and Wind EKF Notes

This note documents how XCSoar handles TAS/IAS and wind EKF gating, based on
local source code in:
`C:\Users\Asus\AndroidStudioProjects\XCSoar`

Use this as a quick reference for parity decisions. If you need deeper detail,
read the files listed below in that repo.

---

## High-level behavior

- "Real" airspeed (IAS/TAS) is only set by external air data (or pitot/dynamic
  pressure). Phone-only or GPS/wind-derived airspeed is explicitly marked as
  NOT real.
- The wind EKF only runs when "real" airspeed is available and recently
  updated, and only while flying.
- Airspeed data expires after ~30 seconds; expired airspeed clears the
  "real" flag.

---

## Where this is implemented (XCSoar repo)

### Airspeed input and "real" flag
- `src\NMEA\Info.cpp`
  - `ProvideTrueAirspeedWithAltitude()`
  - `ProvideIndicatedAirspeedWithAltitude()`
  - `ProvideTrueAirspeed()`
  - `ProvideIndicatedAirspeed()`
  - These set `airspeed_real = true` when IAS or TAS comes from an instrument.

### Airspeed expiration
- `src\NMEA\Info.cpp`
  - `Expire()` expires airspeed after 30s:
    `airspeed_available.Expire(clock, 30s)` then `airspeed_real = false`.

### Estimated airspeed (not real)
- `src\Computer\BasicComputer.cpp`
  - `ComputeAirspeed()` computes airspeed from:
    - dynamic/pitot pressure when available (sets `airspeed_real = true`)
    - otherwise wind + ground speed (sets `airspeed_real = false`)
  - This means phone-only or wind-derived airspeed is not treated as "real".

### Wind EKF gating
- `src\Computer\Wind\WindEKFGlue.cpp`
  - Requires:
    - `derived.flight.flying`
    - `basic.airspeed_available && basic.airspeed_real`
    - updated airspeed and ground speed samples
  - Rejects time warps and duplicate sample timestamps.
  - Ignores samples during circling/turning and g-load spikes.

### VTakeoff gate
- `src\Computer\Wind\Computer.cpp`
  - EKF runs only if `basic.true_airspeed > VTakeoff`.
  - Fallback VTakeoff is 10 m/s if no polar is available.

---

## Summary for parity

- XCSoar will not feed the wind EKF unless airspeed is "real".
- Airspeed derived from wind/GPS is available for calculations but is NOT
  marked real and does not unlock EKF.
- Airspeed data becomes stale after 30 seconds and loses the "real" flag.

