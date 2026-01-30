# GENIUS_PHONE_SENSORS_WIND_TAS_IAS_NETTO_SPEC.md

## Status

**Authoritative specification.**  
This document defines the *best physically defensible* implementation of wind, TAS/IAS proxy, TE, and netto
using **phone sensors only** (Samsung S22 Ultra class or better).

If code conflicts with this document, the code is wrong.

This spec is designed to be read by humans **and** AI agents.

---

## Scope

Applies to:
- Wind estimation
- TAS proxy
- IAS proxy
- Total Energy (TE)
- Netto
- Quality/tier gating

Constraints:
- Phone-only sensors
- Deterministic replay
- MVVM + UDF + SSOT
- Injected timebase only

---

## Non-Negotiable Physics

1. **No real IAS**
   - Phones cannot measure dynamic pressure.
   - IAS is always a *proxy*.

2. **No direct TAS**
   - TAS exists only as:
     ```
     TAS_proxy = |ground_vector - wind_vector|
     ```

3. **Turns destroy observability**
   - TAS and wind cannot be re-estimated instantaneously while circling.
   - Hold-and-freeze behavior is required.

4. **Wrong > Missing is forbidden**
   - Confidently wrong outputs destroy pilot trust.
   - Prefer degraded or invalid outputs.

---

## Wind (Phone-Only)

### Allowed source
- Full-circle circling wind only.

### Forbidden sources
- Straight-flight EKF using derived TAS
- Accelerometer-based wind
- Partial-circle fits

### Behavior
- Wind updated only during valid circling
- Wind frozen during glide
- Wind persists ~1 hour
- Wind decays in confidence, not magnitude

---

## Wind Quality

Wind must carry:
- vector
- timestamp
- altitude
- quality score (0..1)
- age

Wind never disappears; it becomes stale.

---

## TAS Proxy

### Definition
```
tas_proxy = |ground_vector - wind_vector|
```

### Validity
TAS is valid only if:
- wind quality >= MEDIUM
- wind age <= max age
- tas_proxy >= min flight speed

No ground-speed substitution allowed.

### Turn handling
- Freeze TAS during circling
- Resume update only when leaving circling

---

## IAS Proxy

### Definition
```
ias_proxy = tas_proxy * sqrt(density_ratio)
```

### Rules
- Exists only if TAS valid
- Display/informational only
- Never used for limits or warnings

---

## Total Energy (TE)

### Rules
- TE computed only when TAS valid
- Never compute TE from ground speed
- Otherwise fall back to pressure/baro vario

---

## Netto

### Definition
```
netto = TE_vario - polar_sink(TAS)
```

### Quality tiers

**Tier A (best phone-only)**
- Wind valid
- TAS valid
- TE valid

**Tier B (degraded)**
- Wind stale or partial
- TAS held
- Heavy smoothing

**Tier C (invalid)**
- No wind
- No TAS
- Netto hidden/invalid

---

## Smoothing Policy

- Domain: physical meaning only
- UI: visual comfort only
- Default phone-only netto avg: 10s
- 5s allowed only in Tier A

---

## Timebase Rules

- Live: monotonic time
- Replay: IGC time
- Wall time: UI only
- Domain must not call system clocks

---

## Architecture Placement

- Repository: wind persistence
- Domain: confidence, tiering, gating
- UI: rendering only

---

## Testing Requirements

- Deterministic replay
- Turn-heavy scenarios
- No flicker gates
- TE/netto honesty checks

---

## End Goal

XC Pro behaves like an avionics instrument:
- stable
- honest
- predictable
- trusted

Phone physics acknowledged. No lies.
