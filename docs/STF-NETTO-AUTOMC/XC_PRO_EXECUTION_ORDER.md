
# XCPro Feature Execution Order (Agent Execution Contract)

This file defines the **mandatory execution and dependency order** for Codex when evaluating or modifying the XCPro codebase.

Codex must treat this as a **hard contract**, not guidance.

---

## Purpose

XCPro contains tightly coupled flight algorithms. Implementing features out of order leads to:
- incorrect glide-netto,
- unstable speed-to-fly,
- broken Auto-MacCready behaviour,
- hard-to-diagnose feedback loops.

This document prevents that.

---

## Mandatory Execution Order

Codex must implement, verify, or refactor the following **strictly in order**.

### 1. Wind Estimation + Confidence

Required outputs:
- `wind_vector` (m/s, earth frame)
- `wind_confidence` in [0.0 - 1.0]

Rules:
- Wind must be solvable during circling (heading diversity)
- Wind confidence must decay with **time or distance** after leaving a thermal
- No downstream logic may assume perfect wind

STOP if wind confidence is missing or unused.

---

### 2. TAS / IAS Estimation

Glider profile must provide:
- active polar reference
- `IAS_min` and `IAS_max` bounds used by speed-to-fly


Required outputs:
- `TAS_estimate` (m/s)
- `IAS_estimate` (m/s)
- `airspeed_confidence`

Rules:
- TAS/IAS must be solved primarily during circling
- ISA temperature model must be used if no OAT is available
- Phone internal temperature sensors must NOT be used

STOP if IAS used by polar lookup is not clearly defined.

---

### 3. Glide-Netto

Specification source:
- `GLIDE_NETTO_AND_AUTO_MC_SPEC.md`

Required outputs:
- `glideNetto_fast` (distance-based, default 0.6 km)
- `glideNetto_trend` (optional, ~1.5 km)
- `glideNetto_confidence`

Rules:
- Glide-netto must be distance-based, not time-based
- Glide-netto must be gated to straight glide only
- Glide-netto must respect wind confidence

STOP if glide-netto updates during circling or poor-confidence glide.

---

### 4. Auto-MacCready (Auto-MC)

Specification source:
- `GLIDE_NETTO_AND_AUTO_MC_SPEC.md`

Required outputs:
- `MC_auto`
- `MC_confidence`

Rules:
- Auto-MC updates ONLY at thermal exit
- Auto-MC is based on achieved climb in thermals
- Glide-netto must NOT directly drive Auto-MC
- Rate limiting and hysteresis are mandatory

STOP if Auto-MC reacts continuously during glide.

---

### 5. Speed-to-Fly

Specification source:
- `SPEED_TO_FLY_SPEC.md`

Required outputs:
- `IAS_target`
- `delta_speed`
- `speed_to_fly_confidence`

Rules:
- Speed-to-fly must consume:
  - `MC_auto`
  - `glideNetto_fast` (authority limited by confidence)
- Numerical optimization over polar required
- Output smoothing and rate limiting required

STOP if speed commands oscillate or exceed safety limits.

---

## Failure Handling Rules (Global)

If ANY upstream dependency is:
- missing,
- low confidence,
- or unstable,

then downstream modules must:
- degrade gracefully,
- reduce authority,
- or freeze outputs.

Under no circumstances should XCPro:
- invent data,
- extrapolate blindly,
- or violate glide / thermal gating.

---

## Validation Order (Required)

Codex must validate features in this same order using IGC replay:

1. Wind correctness and decay
2. TAS/IAS stability after circling
3. Glide-netto correctness in still air, sink, lift
4. Auto-MC update timing and stability
5. Speed-to-fly smoothness and sanity

---

## Non-Negotiable Rule

If Codex encounters ambiguity or missing signals:

**STOP and fix the dependency first.**

Do NOT patch around missing logic.

---

**End of execution contract**

