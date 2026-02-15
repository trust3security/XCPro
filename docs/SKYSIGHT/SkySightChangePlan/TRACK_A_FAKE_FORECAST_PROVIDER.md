# Track A — FakeForecastProvider + Forecast Overlay Runtime (Codex-Ready)

This is the **complete implementation plan** to build the XCPro-side plumbing for forecast overlays **without needing SkySight forecast endpoints yet**.

**Defaults (use unless you change them):**
1. Fake layers: **Thermal**, **Cloudbase**, **Wind 850hPa**, **Rain**
2. Time steps: **hourly**, next **24 hours**
3. Overlay order: **under airspace/task**, **above base map**
4. Long-press behavior during task edit: **disable point query in task edit mode**

---

## 0) Definition of Done

You are done when you can:

- Toggle **Forecast overlays** ON/OFF on the map screen
- Select one of the 4 layers
- Pick a time via slider (hourly)
- Adjust opacity (slider) and see it update live
- See a legend for the selected layer
- Long-press on the map (not in task-edit mode) and get a pinned “value here” callout
- All MapLibre work is performed in **UI/runtime controllers** (no MapLibre types in ViewModels)

This is **plumbing validation**. The tiles/values can be fake. The goal is to implement the complete architecture and map wiring so SkySight becomes an adapter swap later.

---

## 1) Architecture requirements (must follow)

- **UI → domain → data** dependency direction.
- ViewModels depend on **UseCases only** (no network, no persistence).
- Domain defines **ports (interfaces)** for provider access; data implements adapters.
- **MapLibre types must stay in UI/runtime controllers**, not in ViewModels.

---

## 2) What to build (high-level deliverables)

### A) Provider-neutral “Forecast overlay” capability
Implement a generic forecast overlay system that can work with any provider.

### B) FakeForecastProviderAdapter
A fake adapter that returns:
- parameter catalog (4 items)
- legend ramps
- tile templates (public XYZ raster templates)
- deterministic point values

### C) UI + runtime integration
Add a map screen bottom-sheet for selecting layer/time/opacity and a runtime controller that adds/removes/updates a MapLibre raster layer.

---

## 3) Data + Domain contracts

### 3.1 Domain models (no Android imports)
Create these models in a domain module/package:

- `ForecastParameterId` (value class/string)
- `ForecastParameterMeta`  
  - `id`, `name`, `category`, `unitLabel`  
  - `supportsLegend: Boolean`, `supportsPointValue: Boolean`, `supportsTiles: Boolean`
- `ForecastTimeSlot`  
  - `validTimeUtcMs: Long` (wall-time semantics, explicitly stored)
  - `displayLabel: String` (computed in UI, not required in domain)
- `ForecastOverlayConfig`  
  - `enabled: Boolean`, `parameterId: ForecastParameterId`, `time: ForecastTimeSlot`, `opacity: Float`
- `ForecastTileSpec`  
  - `urlTemplate: String` (XYZ template with `{z}/{x}/{y}`)
  - `minZoom: Int`, `maxZoom: Int`, `tileSizePx: Int = 256`
  - `attribution: String` (provider-neutral)
- `ForecastLegendSpec`  
  - `unitLabel: String`
  - `stops: List<LegendStop>` where stop is `(value: Double, argb: Int)`
- `ForecastPointValue`  
  - `value: Double`, `unitLabel: String`, `validTimeUtcMs: Long`

### 3.2 Domain ports (interfaces)
- `ForecastCatalogPort`
  - `suspend fun getParameters(): List<ForecastParameterMeta>`
  - `fun getTimeSlots(nowUtcMs: Long): List<ForecastTimeSlot>` (or suspend if provider-based)
- `ForecastTilesPort`
  - `suspend fun getTileSpec(parameterId, timeSlot): ForecastTileSpec`
- `ForecastLegendPort`
  - `suspend fun getLegend(parameterId): ForecastLegendSpec`
- `ForecastValuePort`
  - `suspend fun getValue(lat: Double, lon: Double, parameterId, timeSlot): ForecastPointValue`

---

## 4) SSOT repositories

### 4.1 Preferences SSOT
`ForecastPreferencesRepository` (persisted):
- `enabled: Boolean` (default false)
- `opacity: Float` (default 0.6)
- `selectedParameterId: ForecastParameterId` (default THERMAL)
- `selectedTimeUtcMs: Long?` (optional; otherwise computed default is “nearest slot >= now”)

Expose:
- `StateFlow<ForecastPrefs>`

### 4.2 Overlay state SSOT
`ForecastOverlayRepository` (derived state):
- Composes:
  - prefs
  - catalog
  - legend
  - tile spec
- Exposes:
  - `StateFlow<ForecastOverlayState>` (Disabled / Loading / Ready / Error)

**Important:** if disabled, do not compute tiles/legend eagerly.

---

## 5) UseCases

Implement these usecases (each small and testable):

- `ObserveForecastOverlayStateUseCase`
- `SetForecastEnabledUseCase`
- `SelectForecastParameterUseCase`
- `SetForecastTimeUseCase`
- `SetForecastOpacityUseCase`
- `QueryForecastValueAtPointUseCase`

Rules:
- clamp time selection to available time slots
- opacity clamped to `[0.0, 1.0]`
- “default time” = nearest available slot >= now (or last saved selection if valid)

---

## 6) ViewModel + UI state

### 6.1 ViewModel
Either:
- extend existing Map screen ViewModel, or
- add a small dedicated overlay ViewModel used by Map screen

Expose:
- `ForecastOverlayUiState`
  - enabled
  - parameters list + selected id
  - time slots list + selected slot index
  - opacity
  - legend UI model
  - pinned callout state (lat/lon/value/unit)
  - loading/error

### 6.2 UI controls (Compose)
Add a “Forecast overlays” sheet:
- Toggle (enabled)
- Parameter list (radio/selector)
- Time slider (0..N-1)
- Opacity slider
- Legend display

**Default time steps:** hourly slots for 24h → 25 slots (including now-rounded) or 24; choose one and be consistent.

### 6.3 Long-press handling
- If task-edit mode is active → **do nothing** (default).
- Else → call `QueryForecastValueAtPointUseCase` and show pinned callout.

---

## 7) MapLibre runtime controller (UI-owned)

### 7.1 ForecastOverlayController
A runtime controller that owns MapLibre objects:

Responsibilities:
- On `enabled=false`: remove raster source + raster layer
- On `enabled=true` and tileSpec changes:
  - add/update raster source with `tiles = [urlTemplate]`, `tileSize=256`
  - add raster layer pointing to that source
  - set `raster-opacity` to current opacity
- On style reload: re-add if enabled

### 7.2 Overlay z-order (default)
Insert the raster layer:
- **above the base map**
- **below airspace/task layers**

Implementation approach:
- If you have known layer IDs for airspace/task, insert forecast layer **before** the first of those.
- If not, insert near the top of base map layers and document the chosen insertion point.

---

## 8) FakeForecastProviderAdapter (the key for Track A)

### 8.1 Parameters
Return 4 parameters:

| id | name | category | units | supportsLegend | supportsPointValue |
|---|---|---|---|---|---|
| THERMAL | Thermal | Thermal | m/s | true | true |
| CLOUDBASE | Cloudbase | Cloud | m | true | true |
| WIND_850 | Wind 850hPa | Wind | kt | true | true |
| RAIN | Rain | Precip | mm/h | true | true |

### 8.2 Time slots
Generate hourly slots from `now` to `now + 24h`:
- Round `now` down to the hour (or to nearest hour) then add 0..24 hours.

### 8.3 Tile templates
Use any stable public XYZ raster template. For Track A, you can use the same template for all layers (plumbing demo).

Example patterns:
- `https://tile.openstreetmap.org/{z}/{x}/{y}.png`
- Any other public XYZ tile server you already use in the app (preferred).

Return:
- minZoom: 0–2
- maxZoom: 18–19
- attribution: keep generic (“Map tiles provider”)

### 8.4 Legend ramps
Return static ramps per parameter (6–10 stops). Values do not need to match real meteorology.

### 8.5 Point values (deterministic)
Compute a deterministic value from `(lat, lon, timeUtcMs, parameterId)`:
- hash → map to range
- e.g., thermal 0..6 m/s, wind 0..40 kt, cloudbase 500..3500 m, rain 0..20 mm/h

---

## 9) Tests (minimum)

### Unit tests (JVM)
- time slot generation and clamping
- prefs updates drive overlay state changes
- fake provider point values stable for same inputs
- tileSpec computed only when enabled (no waste)

### Manual smoke steps
1) Enable overlay → raster tiles appear
2) Change parameter → controller update path runs
3) Change time slot → controller update path runs
4) Opacity slider changes the raster opacity
5) Long-press on map → pinned callout appears
6) Enter task edit mode → long-press does nothing

---

## 10) Codex execution instructions (copy/paste)

**Instruction to Codex:**

> Implement Track A: provider-neutral Forecast overlays feature + FakeForecastProviderAdapter + MapLibre runtime ForecastOverlayController + Map screen UI sheet. Use defaults: layers Thermal/Cloudbase/Wind 850hPa/Rain; hourly next 24h; overlay order under airspace/task above base map; disable long-press point query in task edit mode. Follow repo architecture rules (SSOT, UI runtime owns MapLibre types, ViewModels use UseCases only). Add unit tests for time clamping and overlay state derivation. Verify with enforceRules + unit tests + assembleDebug.

---

## 11) What this unlocks

Once Track A is done, SkySight integration becomes:
- replace FakeForecastProviderAdapter with SkySight adapter
- plug in real tileTemplate + legend + value endpoints
- everything else stays the same
