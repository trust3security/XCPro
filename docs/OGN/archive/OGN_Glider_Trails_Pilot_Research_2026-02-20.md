# OGN Glider Trails Pilot Research (2026-02-20)

## 0) Purpose

Define what glider pilots expect from vario-colored map trails, then translate that into a concrete visual contract for OGN glider trails in XCPro.

This is a research and planning document only. No implementation is included here.

## 1) Primary Sources Reviewed

### 1.1 XCSoar user manual and settings text (primary benchmark)

- `C:\Users\Asus\AndroidStudioProjects\XCSoar\doc\manual\en\ch11_configuration.tex:169`
- `C:\Users\Asus\AndroidStudioProjects\XCSoar\doc\manual\en\ch11_configuration.tex:180`
- `C:\Users\Asus\AndroidStudioProjects\XCSoar\doc\manual\en\ch11_configuration.tex:190`
- `C:\Users\Asus\AndroidStudioProjects\XCSoar\doc\manual\en\ch03_navigation.tex:383`
- `C:\Users\Asus\AndroidStudioProjects\XCSoar\doc\manual\en\ch03_navigation.tex:391`
- `C:\Users\Asus\AndroidStudioProjects\XCSoar\src\Dialogs\Settings\Panels\SymbolsConfigPanel.cpp:80`
- `C:\Users\Asus\AndroidStudioProjects\XCSoar\src\Dialogs\Settings\Panels\SymbolsConfigPanel.cpp:85`
- `C:\Users\Asus\AndroidStudioProjects\XCSoar\src\Dialogs\Settings\Panels\SymbolsConfigPanel.cpp:95`
- `C:\Users\Asus\AndroidStudioProjects\XCSoar\NEWS.txt:3682`

Public mirror links:

- https://github.com/XCSoar/XCSoar/blob/master/doc/manual/en/ch11_configuration.tex
- https://github.com/XCSoar/XCSoar/blob/master/doc/manual/en/ch03_navigation.tex
- https://github.com/XCSoar/XCSoar/blob/master/src/Dialogs/Settings/Panels/SymbolsConfigPanel.cpp
- https://github.com/XCSoar/XCSoar/blob/master/NEWS.txt

### 1.2 XCPro existing trail and OGN palette implementation

- `feature/map/src/main/java/com/trust3/xcpro/map/trail/SnailTrailPalette.kt`
- `feature/map/src/main/java/com/trust3/xcpro/map/trail/SnailTrailMath.kt`
- `feature/map/src/main/java/com/trust3/xcpro/map/trail/SnailTrailSegmentBuilder.kt`
- `feature/map/src/main/java/com/trust3/xcpro/ogn/OgnThermalColorScale.kt`
- `feature/map/src/main/java/com/trust3/xcpro/ogn/OgnTrafficModels.kt`

## 2) What Pilots Consistently Want (Evidence-Based)

From XCSoar behavior and descriptions, the durable pilot-facing expectations are:

1. Lift vs sink must be readable at a glance.
- Lift is shown with warmer/brighter colors and thicker traces.
- Sink is shown with cooler/darker colors and thinner traces.

2. Zero-lift must be a neutral pivot color.
- XCSoar uses neutral line colors around zero lift (for example yellow/grey depending on scheme).

3. Width scaling by vario magnitude is important, not optional eye candy.
- XCSoar exposes "Trail scaled" specifically for this purpose.

4. Climb quality should represent air mass movement when possible.
- XCSoar explicitly uses Netto vario if available for trail coloring/width, because pilots care about airmass quality more than glider-only climb.

5. Display clutter must be managed.
- Trail length controls, drift-only-in-circling behavior, and sink dot variants exist specifically to reduce clutter while keeping tactical meaning.

## 3) Implication for Your Requested Visual Contract

Your requested contract:

- Sink: more sink -> thinner line + darker navy.
- Climb: more climb -> thicker line + yellow -> dark purple (thickest at strongest climb).

This is aligned with pilot expectations above and already aligned with XCPro's existing vario palette direction.

## 4) Proposed OGN Trail Visual Encoding

## 4.1 Color

Use existing 19-step XCPro vario ramp (already used by snail trail and thermal hotspot color index):

- Low end: deep navy (strong sink)
- Midpoint: yellow (near zero lift)
- High end: dark purple (strong climb)

Reference:

- `feature/map/src/main/java/com/trust3/xcpro/map/trail/SnailTrailPalette.kt:58`
- `feature/map/src/main/java/com/trust3/xcpro/ogn/OgnThermalColorScale.kt:11`

## 4.2 Width

Current XCPro ownship logic keeps sink-side widths mostly flat. For OGN glider trails, that does not match your requirement.

Recommended explicit asymmetric width mapping:

- `maxAbsVarioMps = 12 kt * 0.514444 = 6.173328`
- `baseWidthPx = 2.0`
- Sink side: `0.8 px .. 2.0 px` (strong sink is thinnest)
- Climb side: `2.0 px .. 7.5 px` (strong climb is thickest)

Piecewise mapping:

- If `v <= 0`:
  - `t = clamp(abs(v) / maxAbsVarioMps, 0..1)`
  - `width = lerp(2.0, 0.8, t)`
- If `v > 0`:
  - `t = clamp(v / maxAbsVarioMps, 0..1)`
  - `width = lerp(2.0, 7.5, t)`

This directly satisfies "greater sink = thinner darker navy" and "greater climb = thicker toward dark purple."

## 5) OGN Data Reality and Limits

1. OGN currently provides `verticalSpeedMps` per target sample in XCPro parsing/model.
- `feature/map/src/main/java/com/trust3/xcpro/ogn/OgnTrafficModels.kt:42`

2. OGN trail style will represent observed glider vertical movement from OGN samples.
- It is useful tactically, but not guaranteed to be pure airmass Netto.

3. For high confidence pilot interpretation, UI text should explicitly label this as "OGN vertical trend", not "netto."

## 6) Practical Pilot-Centric Defaults

Recommended defaults for first release:

1. Show OGN trails toggle default `OFF` (avoid instant clutter/perf hit on all users).
2. Trail history default 20 minutes.
3. Render all tracked gliders within OGN receive area, but cap and thin for performance.
4. Keep thermal hotspot feature independent from trails; both can be shown together.

## 7) Research Conclusions

1. Your requested encoding is valid and aligned with mainstream soaring computer behavior.
2. The existing XCPro palette already matches the requested sink-to-climb color direction.
3. The main gap is sink-side width scaling and OGN per-glider trail history/rendering.
4. Implementation should reuse existing trail color semantics but use a dedicated OGN trail pipeline for SSOT and performance control.

## 8) Next Artifact

Implementation-ready plan:

- `docs/OGN/OGN_Glider_Trails_Implementation_Plan_2026-02-20.md`
