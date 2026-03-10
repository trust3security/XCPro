# PROFILE_FILE_PORTABILITY_STRATEGY_2026-03-10

## Purpose

Define the user-facing profile-file model for XCPro so pilots can back up,
move, import, and switch between aircraft-specific setups without ambiguity
about runtime storage ownership.

Engineering execution for this strategy is tracked in:

- `docs/PROFILES/PROFILE_PRODUCTION_GRADE_PHASED_IP_AIRCRAFT_PROFILE_FILES_2026-03-10.md`

## Decision Summary

1. The user-facing portable artifact is a profile file.
2. In product/UI language, this should be presented as an "Aircraft Profile"
   file, not primarily as a "bundle".
3. Runtime SSOT remains in internal repositories and DataStore/SharedPreferences
   stores.
4. The portable JSON file is an import/export artifact generated from runtime
   SSOT and restored back into runtime SSOT.
5. One XCPro profile should represent one aircraft setup:
   pilot identity + aircraft type/model + profile-scoped settings.

## Non-Negotiable Architecture Policy

- Do not make an external JSON file the live runtime SSOT during normal app use.
- Keep profile switching fast by switching internal profile identity, then
  hydrating profile-scoped repositories from internal storage.
- Keep Android file I/O in adapter/UI edges, not in ViewModel/domain logic.
- Keep exported/imported files versioned and validation-first.

This aligns with current architecture guidance that profile bundle files are
portability artifacts, not runtime SSOT.

## Product Model

### 1. What the user owns

The user should think in terms of aircraft profiles:

- `John - ASG 29`
- `John - Ventus 3`
- `John - Moyes Litespeed`
- `John - Ozone Enzo 3`

Each aircraft profile should carry:

- profile identity metadata
- aircraft type/model metadata
- profile-scoped cards/templates/visibilities
- glider/polar/config data where relevant
- look and feel/theme/layout preferences
- other settings sections explicitly included by the profile export policy

### 2. What "switch aircraft" means

Switching aircraft inside the app should:

1. change active profile id
2. resolve the selected aircraft profile from internal storage
3. hydrate profile-scoped repositories for that profile id
4. update UI from existing flows/state holders

It should not require reading a JSON file from external storage on each switch.

### 3. What "export aircraft profile" means

Export should create one portable JSON document for one selected profile by
default.

Recommended filename shape:

`xcpro-aircraft-profile-<aircraft-type>-<aircraft-model-or-name>-YYYY-MM-DD.json`

Example:

`xcpro-aircraft-profile-sailplane-asg-29-2026-03-10.json`

### 4. What "backup all profiles" means

The app may also offer a multi-profile export for full migration, but that
should be secondary to the single-aircraft-profile export/import flow.

## Storage Model

### Runtime storage

Runtime storage should continue to use the existing internal owners:

- `ProfileStorage` / `ProfileRepository` for profile list + active profile id
- profile-scoped repositories for cards, theme, look-and-feel, glider config,
  units, layouts, and other supported settings domains

### Portable storage

Portable storage should use one canonical JSON schema with:

- schema version
- export timestamp
- one or more profiles
- selected settings snapshot sections
- integrity metadata/checksum when introduced

Single-profile export should use the same schema as multi-profile export, with
exactly one profile in the document.

## Recommended User Flow

### Primary

- `Switch Aircraft`
- `Export Aircraft Profile`
- `Import Aircraft Profile`
- `Duplicate Aircraft Profile`
- `Backup All Profiles`

### Import behavior

Import should:

1. parse and validate the selected JSON
2. show a preview before apply
3. allow user to import as new or replace on collision
4. optionally activate the imported profile immediately
5. report section-level restore failures clearly

Preview should show:

- profile name
- aircraft type
- aircraft model
- export date
- schema version
- import scope

## Android Platform Guidance

Preferred Android file flows:

- one-off export: `ActivityResultContracts.CreateDocument`
- one-off import: `ActivityResultContracts.OpenDocument`
- optional user-selected recurring backup folder: `ACTION_OPEN_DOCUMENT_TREE`

Rationale:

- these are user-controlled, permission-minimizing flows
- they work well for explicit backup/migration
- they avoid treating shared storage as hidden runtime state

System Auto Backup/device transfer may remain enabled as a secondary safety net,
but it must not be the primary aircraft-profile migration story.

## Starter File Baseline

XCPro should keep a basic, real starter workflow working at all times:

1. two single-profile JSON files exist as concrete examples
2. each file imports cleanly through the current import flow
3. the app can switch between the imported profiles without re-reading external
   storage on each switch
4. the user can export either profile back out through the Android save dialog

Current starter examples live in:

- `docs/PROFILES/examples/xcpro-aircraft-profile-sailplane-asg-29-2026-03-10.json`
- `docs/PROFILES/examples/xcpro-aircraft-profile-hang-glider-moyes-litespeed-rs-2026-03-10.json`

These starter files intentionally carry:

- one `UserProfile`
- the current `2.0` bundle wrapper
- an empty settings snapshot

They validate file portability and profile switching first, before claiming
full settings coverage.

## XCPro-Specific Guidance

### Keep

- internal runtime SSOT ownership as documented in
  `PROFILE_STORAGE_AND_SETTINGS_SCOPE.md`
- the existing bundle codec/import/export path as the implementation base
- managed backup mirroring as a convenience path

### Change

1. Make "Aircraft Profile" the primary user-facing term in UI and docs.
2. Make single-profile export/import the primary user flow.
3. Recommend one-file import targets, not index-only files.
4. Keep `*_bundle_latest.json` as the managed latest snapshot artifact for
   internal/download mirroring, but present explicit user-created export files as
   the normal flow.
5. Continue treating per-profile and index mirror files as implementation
   details, not the main UX.

## General-First Coverage Plan

This section defines the move toward a full user-owned aircraft profile file
using the app's existing `General` settings taxonomy.

Rule:

- if a setting is user-configurable in `General` and is aircraft/setup-specific,
  it should be included in the portable aircraft profile unless there is a clear
  portability reason to exclude it
- if a setting depends on device-only state, raw external file handles, secrets,
  or transient runtime data, it should stay out of the aircraft profile file

### General -> Files

Target status: `Partial / pending classification`

Candidate include:

- home waypoint selection
- waypoint file enabled/disabled registry if XCPro can make that data portable

Explicit exclude:

- raw imported waypoint file binaries
- raw document URIs and persistable URI permissions
- replay/import working files

Notes:

- Current `Files` UX is mostly a container/entrypoint, not yet a clear portable
  settings section.
- If waypoint-file portability is required, treat it as a separate file-package
  problem, not just a JSON preference toggle.

### General -> Profiles

Target status: `Include`

Include:

- profile id
- name
- aircraft type
- aircraft model
- description
- createdAt
- lastUsed

Include for multi-profile export/import:

- `activeProfileId`

Explicit exclude from runtime-settings ownership:

- `UserProfile.preferences`
- `UserProfile.polar`
- `UserProfile.isActive`

Notes:

- These compatibility fields must not become shadow runtime SSOT.

### General -> Look & Feel

Target status: `Include`

Include:

- status bar style by profile
- card style by profile
- color theme by profile
- selected theme id by profile
- custom colors by profile and theme

Notes:

- The separate `Colors` path should be treated as part of this profile-file
  coverage, not as a separate product concept.

### General -> Units

Target status: `Include`

Include:

- altitude unit
- vertical speed unit
- speed unit
- distance unit
- pressure unit
- temperature unit

### General -> Polar

Target status: `Include`

Include:

- selected glider model id
- effective model id
- fallback polar active flag
- pilot and gear weight
- water ballast
- bugs percentage
- reference weight
- IAS minimum
- IAS maximum
- three-point polar values
- user polar coefficients
- ballast drain minutes
- hide ballast pill flag

### General -> Levo Vario

Target status: `Include`

Include:

- MacCready
- MacCready risk
- auto MacCready enabled
- TE compensation enabled
- show wind speed on vario
- audio enabled
- audio volume
- audio lift threshold
- audio sink silence threshold
- audio duty cycle
- audio deadband min
- audio deadband max

### General -> Hawk Vario

Target status: `Include`

Include:

- show Hawk card
- enable Hawk UI
- Hawk needle omega min
- Hawk needle omega max
- Hawk needle target tau
- Hawk needle drift tau min
- Hawk needle drift tau max

Notes:

- Hawk controls are currently persisted inside the Levo vario preferences
  storage path, so no separate storage section is required yet.

### General -> Layouts

Target status: `Include`

Include:

- card templates
- profile template card mappings
- profile flight mode template mappings
- profile flight mode visibilities
- profile card positions by mode
- cards-across portrait setting
- card anchor portrait setting
- last active template
- vario smoothing alpha
- map widget offsets and sizes
- variometer widget offset and size

### General -> Proximity

Target status: `Covered indirectly`

No standalone persistence section is needed if this remains a launcher for:

- OGN
- Hotspots
- Look & Feel
- Colors

### General -> SkySight

Target status: `Include`

Include:

- forecast overlay enabled
- overlay opacity
- wind overlay scale
- wind overlay enabled
- wind display mode
- SkySight satellite overlay enabled
- SkySight imagery enabled
- SkySight radar enabled
- SkySight lightning enabled
- SkySight animation enabled
- satellite history frames
- selected primary forecast parameter
- selected wind parameter
- selected forecast time
- selected region
- follow-time offset
- auto-time enabled

Explicit exclude:

- forecast provider credentials

### General -> Hotspots

Target status: `Include`

Include:

- thermal retention hours
- hotspots display percent
- thermal hotspot visibility controls that are persisted through OGN settings

Notes:

- Hotspots are currently persisted through OGN-related preference ownership, so
  they should stay grouped with OGN in the portable file model.

### General -> Weather

Target status: `Include`

Include:

- weather overlay enabled
- opacity
- animate past window
- animation window
- animation speed
- transition quality
- frame mode
- manual frame index
- smooth rendering
- snow rendering

### General -> OGN

Target status: `Include`

Include:

- OGN enabled
- icon size
- receive radius
- auto receive radius enabled
- display update mode
- show SCIA enabled
- show thermals enabled
- target enabled
- target aircraft key
- own FLARM hex
- own ICAO hex
- client callsign
- trail aircraft selections
- hotspot-related controls

### General -> ADS-b

Target status: `Include`

Include:

- ADS-B enabled
- icon size
- max distance
- vertical alert thresholds above/below
- emergency flash enabled
- emergency audio enabled
- emergency audio cooldown
- emergency audio master enabled
- emergency audio shadow mode
- emergency audio cohort percent
- emergency audio cohort bucket
- emergency audio rollback latched
- emergency audio rollback reason

Explicit exclude:

- provider/account credentials if introduced through a separate secret store

### General -> Navboxes

Target status: `Covered mostly by existing sections`

Covered by:

- card templates
- card positions
- mode visibilities
- cards-across portrait
- last active tab
- last flight mode by profile

Add later only if standalone navbox-specific persisted settings are introduced.

### General -> IGC Replay

Target status: `Exclude from aircraft profile`

Exclude:

- replay library state
- replay working selections
- replay session/transient controls
- imported IGC files themselves

Reason:

- these are workflow/history artifacts, not aircraft setup.

### General -> Orientation

Target status: `Add next`

Add to aircraft profile file:

- cruise orientation mode
- circling orientation mode
- glider screen percent
- map shift bias mode
- map shift bias strength

Explicitly classify before declaring full coverage:

- auto reset enabled
- auto reset timeout
- minimum speed threshold
- bearing smoothing enabled

Notes:

- Orientation is one of the clearest remaining gaps if XCPro wants a true
  aircraft-specific full profile file.

### General -> Thermalling

Target status: `Include`

Include:

- thermalling feature enabled
- switch to thermal mode
- zoom-only fallback when thermal hidden
- enter delay seconds
- exit delay seconds
- apply zoom on enter
- thermal zoom level
- remember manual thermal zoom in session
- restore previous mode on exit
- restore previous zoom on exit

## Outside General But Still Needed For A Full App Profile

If the product goal expands from "all `General` settings" to "full app profile",
these domains should be added after the General-first pass:

- map style selection
- QNH manual value
- snail-trail settings:
  - trail length
  - trail type
  - wind drift enabled
  - scaling enabled

Candidate include after policy decision:

- home waypoint

Likely exclude from aircraft profile:

- nav drawer expansion state
- recent waypoint history
- task working state
- cache/sync checkpoint data
- credential and secret stores

## Implementation Priority

To move toward a true full aircraft profile file without destabilizing runtime
ownership, priority should be:

1. keep the current covered sections as the canonical portable file base
2. add `General -> Orientation`
3. make an explicit `General -> Files` policy decision
4. add non-General map-specific settings (`map style`, `QNH`, `snail trail`)
5. leave secrets, caches, replay working state, and raw external file handles
   out of the aircraft profile file

## Explicit Recommendation

For XCPro, the correct implementation direction is:

1. keep internal repositories as runtime SSOT
2. treat the portable JSON as the user-owned aircraft profile file
3. make import/export the supported move/backup workflow
4. avoid a design where normal profile switching depends on external file reads

This gives users the profile file they expect while preserving deterministic
runtime behavior and current architecture boundaries.
