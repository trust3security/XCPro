# Glider Computer Polar Research

Date: 2026-03-12

## Purpose

This note summarizes how a glider computer normally uses polar data for:

- L/D
- speed to fly
- final glide
- arrival height
- safety margins

## 1. What a Polar Is

A glider polar is the still-air sink rate as a function of airspeed for a defined aircraft state.

That state usually assumes:

- a specific wing loading or reference mass
- a clean glider or a known bugs penalty
- a specific flap configuration or flap schedule
- no climb or sink in the surrounding airmass

In simple terms:

- low point on the polar = minimum sink
- tangent from origin to the polar = best L/D speed
- the whole curve is the base input for STF and final glide

Useful SI form:

```text
still_air_sink_ms = polar(speed_ms, ballast, bugs)
still_air_ld = speed_ms / still_air_sink_ms
```

## 2. What the Computer Derives From Polar

### Best L/D

Theoretical best L/D is derived from the polar, not from recent flight history.

Practical meaning:

- "How efficient can this glider be in still air at the best point on the polar?"

### Minimum sink

Also derived from the polar.

Practical meaning:

- "What speed minimizes altitude loss per second?"

### Speed to fly

The computer uses:

- polar
- MacCready
- wind
- sometimes netto or total-energy vario

to compute the cruise speed that minimizes time or optimizes cross-country progress.

### Final glide

The computer uses:

- polar
- target elevation
- safety arrival height
- wind component to target
- chosen altitude source
- MacCready or safety MC

to compute whether the glider can reach the target and how much height margin remains.

## 3. Measured L/D vs Polar L/D vs Required L/D

These are different values and should not be mixed.

Measured L/D:

- actual recent distance over actual altitude loss
- retrospective performance metric

Polar L/D:

- theoretical still-air efficiency from the glider polar
- depends on chosen speed and configuration

Required L/D:

- distance remaining divided by available height to the target
- navigation demand metric

If the app shows only one "L/D" number, pilots can easily misread what it means.

## 4. Final Glide Core Quantities

Given a target, the computer usually needs:

```text
available_height_m =
    current_nav_altitude_m
    - target_elevation_m
    - safety_arrival_height_m

required_glide_ratio =
    distance_remaining_m / available_height_m

groundspeed_to_goal_ms =
    along_track_speed_ms
    - headwind_component_ms

time_to_goal_s =
    distance_remaining_m / groundspeed_to_goal_ms

height_loss_m =
    still_air_sink_ms * time_to_goal_s

arrival_height_m =
    current_nav_altitude_m
    - target_elevation_m
    - safety_arrival_height_m
    - height_loss_m
```

Notes:

- If groundspeed to goal is near zero or negative, final glide is invalid.
- More advanced computers include terrain clearance, routeing, and energy compensation.
- Some systems also show arrival at `MC=0` because it is a conservative benchmark.

## 5. Why Ballast and Bugs Matter

Ballast shifts the polar to the right:

- higher minimum sink speed
- higher best L/D speed
- similar or near-similar best L/D ratio
- better cruise in strong conditions
- worse climb in weak conditions

Bugs degrade the polar:

- more sink at a given speed
- worse L/D
- worse arrival margin

That means final glide, STF, and reach calculations should all use the same degraded active polar, not just the clean reference polar.

## 6. Why Safety Height and Safety MC Exist

Glide computers often separate:

- task MC or STF MC
- safety MC or final-glide MC

Reason:

- the speed that is ideal for racing is not always the right speed for conservative arrival calculations
- pilots often want a reserve height above the field or terrain
- some systems reduce MC or add safety offset to avoid over-optimistic final-glide indications

## 7. Altitude Source Matters

A final glide computer is only as good as its altitude reference.

Important choices:

- GPS altitude
- barometric altitude
- total-energy or energy-compensated altitude for some displays

The chosen source must be consistent and explicitly documented, because final glide errors can come from:

- wrong QNH
- switching altitude sources mid-flight
- using a noisy source for reach calculations

## 8. What Good Glide-Computer UX Usually Shows

Common outputs:

- arrival height
- arrival height at MC 0
- required altitude
- required glide ratio
- head or tail wind to target
- required speed to use excess height
- "on final glide" state
- reachable airport list sorted by arrival margin

This is separate from basic vario outputs.

## 9. Practical Sources

FAA Glider Flying Handbook:

- Explains the polar as still-air sink vs airspeed and explicitly states that the polar is the basis for speed-to-fly and final-glide tools.
- Shows how best glide speed, minimum sink, headwind adjustment, and ballast change the polar.
- Link:
  - https://www.faa.gov/sites/faa.gov/files/regulations_policies/handbooks_manuals/aviation/glider_handbook/faa-h-8083-13a.pdf
- Relevant pages from the 2022 handbook PDF:
  - pages 38-39
  - pages 76-79

LXNAV LX90xx/80xx User Manual, rev59, January 2025:

- Describes separate MacCready for STF and final glide.
- Documents safety altitude, safety MC, arrival altitude, required altitude, required glide ratio, and required speed-to-fly.
- Describes polar setup with coefficients, reference weight, speeds, ballast, and bugs as glider-computer inputs.
- Link:
  - https://gliding.lxnav.com/wp-content/uploads/manuals/lx90xx-80xxUserManualEnglishVer950rev59.pdf
- Relevant pages:
  - pages 37-40
  - pages 115-118
  - pages 188-191
  - pages 223-224
  - pages 233-234

XCSoar User Manual 6.7.1:

- Documents arrival height, terrain height, polar degradation, safety MC, STF risk factor, and required glide ratio.
- Useful as a vendor-neutral cross-check that the same glide-computer concepts appear in another mature implementation.
- Link:
  - https://download.xcsoar.org/releases/6.7.1/XCSoar-manual.pdf
- Relevant pages:
  - pages 42
  - pages 58-59
  - pages 76
  - pages 123
  - pages 135
  - pages 138-140

## Bottom Line

In a serious glider computer, the polar is not just a preview curve.

It is the core model behind:

- theoretical glide performance
- STF
- final glide
- arrival height
- safety reserve calculations
- landable reach and alternate sorting
