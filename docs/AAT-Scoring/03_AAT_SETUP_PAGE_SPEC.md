# 03 — AAT Setup Page Spec

## 1. Goal

Add an organizer/admin-facing setup page that makes AAT scoring behavior explicit, auditable, and safe.

This screen is required because real-time AAT depends on rules and local-procedure choices that should never be hidden in code defaults.

## 2. Core design principles

1. **Organizer-facing first**
2. **FAI-safe by default**
3. **Custom rules allowed, but clearly labeled**
4. **No silent fallback from unsupported scoring modes**
5. **Persist a config hash + algorithm version**
6. **Preview distances and compliance before publish**

## 3. Screen structure

Recommended sections:

1. Competition & Rules
2. Task Geometry
3. Handicaps
4. Live Leaderboard
5. Validation & Publish

## 4. Section-by-section field spec

## 4.1 Competition & Rules

| Field | Type | Default | Required | Validation | Notes |
|---|---|---:|---:|---|---|
| Task Name | text | empty | yes | non-blank | Human-readable title |
| Competition ID | text | derived | yes | stable ID | For persistence/export |
| Class ID | text/select | existing class | yes | non-blank | Per-class scoring |
| Rules Profile | enum | `FAI_ANNEX_A_CURRENT` | yes | one of known profiles | Controls available geometry |
| Scoring System | enum | `CLASSIC` | yes | if `ALTERNATIVE`, feature must exist | Do not silently fall back |
| Minimum Task Time | duration | existing task value or 3h | yes | > 0 | Must drive scoring |
| Finish Closure Time | datetime | empty | recommended | must be after likely starts | Needed for automatic closure handling |
| Cylinder Start Waiver Ref | text | empty | conditional | required if cylinder start chosen under FAI profile | Could store waiver id or free text |
| Algorithm Version | readonly text | app build value | yes | auto-filled | Persist with results |
| Algorithm Hash | readonly text | computed | yes | auto-generated on save/publish | Hash of relevant scoring config |

### UX behavior

- If `Rules Profile = FAI_ANNEX_A_CURRENT`, show a compliance badge.
- If `Scoring System = ALTERNATIVE` and unsupported, disable Publish and show a blocking warning.
- If `Cylinder Start` is chosen without a waiver reference, block Publish in FAI mode.

## 4.2 Task Geometry

This section should reuse existing task-editing components where possible.

| Field | Type | Default | Required | Validation | Notes |
|---|---|---:|---:|---|---|
| Start Geometry | enum | `LINE` | yes | FAI mode: `LINE` or `CYLINDER` only | Cylinder gated by waiver |
| Start Length / Radius | numeric | app default | yes | > 0 | App default is not the same as FAI requirement |
| Assigned Areas | list | existing task | yes | at least 2 areas | Core AAT structure |
| Area Type | enum per area | `CIRCLE` | yes | FAI mode: `CIRCLE` or geometric `SECTOR` only | Custom types behind custom profile |
| Circle Radius | numeric | existing | conditional | > 0 | |
| Sector Inner Radius | numeric | existing | conditional | >= 0 | |
| Sector Outer Radius | numeric | existing | conditional | > inner radius | |
| Sector Start Bearing | numeric | existing | conditional | 0..360 | |
| Sector End Bearing | numeric | existing | conditional | 0..360 | |
| Finish Geometry | enum | `RING` | yes | `RING` or `LINE` | Ring preferred default |
| Finish Radius / Length | numeric | app default | yes | if ring, enforce minimum configured by rule profile | |
| Steering Point | optional geo point | empty | no | if present, valid coordinates | Optional alignment aid |

### Geometry rules in FAI profile

- At least 2 assigned areas
- consecutive areas separated by at least 1 km
- no keyhole or start sector under default FAI mode
- finish ring preferred, but line allowed
- start cylinder only if waiver reference supplied

### Geometry rules in custom profile

- allow existing custom repo features
- always show warning badge:
  - `Custom / local rules`
- export config should record that official FAI compliance is not assumed

## 4.3 Handicaps

| Field | Type | Default | Required | Validation | Notes |
|---|---|---:|---:|---|---|
| Handicapping Enabled | boolean | based on class | yes | always explicit | Club class usually on |
| Handicap Source | enum | `MANUAL_TABLE` | yes | must resolve to a table | Future: importable |
| Handicap Table | list / file / picker | empty | conditional | every ranked pilot must resolve to a handicap | |
| Missing Handicap Behavior | enum | `BLOCK_SCORING` | yes | one of supported values | Strongly recommend blocking |

### UX behavior

- if handicapping is on and any pilot lacks a handicap:
  - show blocking warning in Validation section
  - allow draft save
  - block Publish / Live Leaderboard activation

## 4.4 Live Leaderboard

| Field | Type | Default | Required | Validation | Notes |
|---|---|---:|---:|---|---|
| Enable Live AAT Leaderboard | boolean | on | yes | — | Master switch |
| Visible To | enum | `ADMIN_ONLY` | yes | one of supported visibilities | Safer default |
| Leaderboard Metric | enum | `PROJECTED_CLASSIC_SCORE` | yes | if unavailable, fallback to `PROJECTED_HANDICAPPED_SPEED` | |
| Projection Mode | enum | `AUTO` | yes | `AUTO`, `OPTIMIZE_TO_MIN_TIME`, `HEAD_HOME_NOW` | `AUTO` is recommended wrapper |
| Tracker Update Cadence | integer seconds | 5 | yes | > 0 | For UI refresh/debounce |
| Allow Sparse Tracker Interpolation | boolean | on | yes | — | Enables segment-based achievement logic |
| Pending Accounting Handling | enum | `HIDE_FROM_RANKING` | yes | should remain hidden pre-closure | Aligns with scorer note |
| Show Official / Provisional / Projected Labels | boolean | on | yes | — | Must remain on by default |

### Recommended defaults

- `Visible To = ADMIN_ONLY`
- `Leaderboard Metric = PROJECTED_CLASSIC_SCORE`
- `Projection Mode = AUTO`
- `Pending Accounting Handling = HIDE_FROM_RANKING`

### `AUTO` projection behavior

Implement `AUTO` as:

- before minimum task time: `OPTIMIZE_TO_MIN_TIME`
- at/after minimum task time: `HEAD_HOME_NOW`

## 4.5 Validation & Publish

Show a validation summary card with:

- rules profile result
- geometry result
- handicap completeness
- nominal distance
- minimum achievable distance
- maximum achievable distance
- scoring-system support status
- live leaderboard support status
- config hash
- algorithm version

Buttons:

- `Save Draft`
- `Publish Task`
- `Lock For Live Scoring`
- `Export Config`

### Publish rules

Block Publish if:

- AAT structure invalid
- unsupported scoring system selected
- handicaps missing when required
- illegal geometry selected under FAI profile
- cylinder start selected in FAI profile without waiver reference

## 5. Suggested screen-state model

```kotlin
data class AatSetupUiState(
    val isLoading: Boolean,
    val draft: AatCompetitionConfigDraft,
    val validation: AatSetupValidationResult,
    val complianceBadge: ComplianceBadge,
    val distancePreview: AatDistancePreview,
    val publishEnabled: Boolean,
    val lockEnabled: Boolean,
    val blockingIssues: List<String>,
    val warnings: List<String>
)
```

## 6. Suggested persisted draft model

```json
{
  "competitionId": "wgc-demo-2026",
  "classId": "club",
  "taskId": "aat-2026-03-14-club",
  "rulesProfile": "FAI_ANNEX_A_CURRENT",
  "scoringSystem": "CLASSIC",
  "minimumTaskTimeSec": 10800,
  "finishClosureTimeUtc": "2026-03-14T18:30:00Z",
  "startGeometry": {
    "type": "LINE",
    "lengthMeters": 10000
  },
  "assignedAreas": [
    {
      "type": "CIRCLE",
      "center": {"lat": -34.0, "lon": 150.7},
      "radiusMeters": 20000
    },
    {
      "type": "SECTOR",
      "center": {"lat": -34.3, "lon": 151.0},
      "innerRadiusMeters": 5000,
      "outerRadiusMeters": 30000,
      "startBearingDeg": 45,
      "endBearingDeg": 135
    }
  ],
  "finishGeometry": {
    "type": "RING",
    "radiusMeters": 3000
  },
  "handicapEnabled": true,
  "leaderboard": {
    "enabled": true,
    "visibility": "ADMIN_ONLY",
    "metric": "PROJECTED_CLASSIC_SCORE",
    "projectionMode": "AUTO",
    "hidePendingAccounting": true
  },
  "algorithmVersion": "xcpro-aat-v1",
  "algorithmHash": "AUTO_GENERATED"
}
```

## 7. Recommended navigation entry points

One of these should exist:

- Competition > Task Setup > AAT
- Competition > Scoring > AAT Setup
- Admin Tools > Live Scoring > AAT Config

Avoid burying it inside pilot task-editing screens.

## 8. UX notes that matter

### Compliance badge

Show one of:

- `FAI profile`
- `FAI profile with waiver`
- `Custom / local rules`

### Explain projected vs official

The setup page should include a compact explanatory note:

- projected = airborne estimate
- provisional = tracker/log-derived but not accepted
- official = accepted FR-log result

### Do not over-promise

Never label the setup switch as “Official live scoring.”
Use:

- `Live AAT leaderboard`
- `Projected / provisional AAT standings`

## 9. Acceptance criteria

The setup page is done when:

- organizers can explicitly choose rules/scoring behavior
- invalid FAI configurations are blocked
- custom configurations are clearly labeled
- leaderboard visibility and projection behavior are explicit
- config hash and algorithm version are persisted
- the rest of the scoring engine can consume the saved config without hidden defaults
