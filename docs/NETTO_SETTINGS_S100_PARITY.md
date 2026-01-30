# NETTO_SETTINGS_S100_PARITY.md

## Goal

Provide **pilot-facing settings** that mimic how glider pilots expect Netto / TE / averaging to behave on an LXNav S100,
while being honest about phone-only limitations.

This file defines:
- Settings names and semantics (vendor-neutral)
- Defaults for phone-only
- How settings affect domain math vs UI-only smoothing

Must comply with `ARCHITECTURE.md` and `CODING_RULES.md`.  

---

## Truth-in-instrumentation rules

- "IAS" in XC Pro is **IAS Proxy** (derived).
- TAS is **TAS Proxy** (derived from wind estimate).
- If wind quality is insufficient:
  - TAS/IAS become invalid (do not display as if correct).
  - Netto is degraded/invalid.

---

## Settings (pilot-facing)

### 1) Netto averaging window (Tier A only)

**Setting key:** `netto_avg_seconds`  
**Options:** `5`, `10`  
**Default (phone-only):** `10`

Semantics:
- Applies to the **domain Netto display window** (not UI-only smoothing).
- Tier A uses selected value.
- Tier B forces >= 10s regardless of user choice.
- Tier C disables netto.

Rationale:
- 5s is "race feel"
- 10s is "decision feel" and better matches phone noise.

---

### 2) Netto mode

**Setting key:** `netto_mode`  
**Options:**
- `AUTO_TIERED` (default)
- `FORCE_DEGRADED` (always tier B behavior)
- `HIDE_UNLESS_TIER_A` (show netto only when tier A)

Default: `AUTO_TIERED`

Semantics:
- AUTO_TIERED: domain decides tier A/B/C based on wind/TAS validity.
- FORCE_DEGRADED: never report tier A (useful for conservative pilots).
- HIDE_UNLESS_TIER_A: absolute honesty; netto appears only when trustworthy.

---

### 3) TE enable policy

**Setting key:** `te_policy`  
**Options:**
- `AUTO` (default)
- `OFF`
- `ON_WHEN_TAS_VALID` (explicit version of AUTO)

Default: `AUTO`

Semantics:
- TE must never run without TAS valid.
- OFF disables TE entirely.
- AUTO and ON_WHEN_TAS_VALID are equivalent unless future hardware adds real IAS.

---

### 4) Airspeed display policy

**Setting key:** `airspeed_display`  
**Options:**
- `SHOW_TAS_IAS_WHEN_VALID` (default)
- `SHOW_TAS_ONLY_WHEN_VALID`
- `HIDE_AIRSPEED` (for pilots who don't want proxies)

Default: `SHOW_TAS_IAS_WHEN_VALID`

---

### 5) Quality indicator display

**Setting key:** `quality_badge`  
**Options:** `ON` / `OFF`  
**Default:** `ON`

Badge:
- `A` = best possible phone-only (wind fresh, quality high)
- `B` = degraded/held (wind stale or partial confidence)
- `C` = invalid (no wind or unusable)

Must be display-only; domain owns tiering.

---

## Domain vs UI application

### Domain-owned (authoritative)
- Tiering (A/B/C)
- TAS/IAS validity
- TE enable/disable
- Netto averaging window (5/10s) where applicable

### UI-only (comfort)
- Needle animation
- Number easing/lerp
- Display fades for stale data

UI must not alter SSOT or feed smoothed values back upstream. 

---

## Suggested defaults (phone-only "S100-like feel")

- Netto averaging: 10s
- TE: AUTO (only with TAS valid)
- Airspeed display: show TAS+IAS only when valid
- Quality badge: ON
- Netto mode: AUTO_TIERED

---

## Edge cases (must handle)

- Immediately after launch:
  - likely tier C (no wind yet) -> netto hidden/invalid
  - wind appears after first valid circle -> tier A becomes possible
- Continuous circling:
  - hold TAS, freeze wind learning, do not oscillate
- Replay mode:
  - identical outputs for identical IGC inputs (determinism gate) 



