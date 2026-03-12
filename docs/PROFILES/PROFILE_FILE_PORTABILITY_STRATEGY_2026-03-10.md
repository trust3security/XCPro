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

### 5. Aircraft Profile Contract (Normative)

An `Aircraft Profile` file is a portable aircraft/setup artifact, not a
whole-app snapshot.

Include in the aircraft-profile file:

- profile identity metadata
- aircraft type/model metadata
- card templates, profile template mappings, selected template per flight mode,
  card positions, and per-mode visibility
- flight-management state that is already profile-scoped
- look and feel and theme
- map widget layout
- variometer widget layout
- glider/polar/config data
- units
- map style
- snail trail
- orientation
- QNH

Exclude from the aircraft-profile file:

- `Levo`, `Thermalling`, `OGN`, `OGN trail`, `ADS-B`, `Weather`,
  `Forecast`, and `Manual wind override`
- `home waypoint`
- waypoint and airspace file selections
- raw waypoint and airspace files
- managed backup metadata
- replay/session/history data
- secrets, credentials, URI permissions, cache/checkpoint state

Those excluded domains belong either to runtime-only state or to a separate
`Full Backup` flow.

### 6. What "full backup" means

`Full Backup` is a separate artifact for device migration or disaster recovery.

It may include:

- all aircraft profiles
- global settings sections
- managed backup metadata
- later, optional packaged file-backed assets if XCPro introduces that feature

It should not be presented as the same promise as an `Aircraft Profile` file.

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

Target status: `Exclude from aircraft profile`

Exclude:

- home waypoint selection
- waypoint file enabled/disabled registry
- airspace file enabled/disabled registry
- selected waypoint/airspace file references

Explicit exclude:

- raw imported waypoint file binaries
- raw document URIs and persistable URI permissions
- replay/import working files

Notes:

- Current `Files` UX is mostly a container/entrypoint, not a portable
  aircraft-profile settings section.
- If waypoint/airspace portability is required, treat it as a separate full
  backup or packaged-asset problem, not a normal aircraft-profile JSON toggle.

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

Target status: `Global app setting, not aircraft profile`

Notes:

- These settings are not aircraft identity/setup.
- If XCPro needs cross-device migration for them, include them in `Full Backup`,
  not in the normal aircraft-profile file.

### General -> Hawk Vario

Target status: `Global app setting, not aircraft profile`

Notes:

- Hawk controls are currently persisted inside the Levo vario preferences
  storage path and follow the same global-only policy.

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

Target status: `Global app setting, not aircraft profile`

Notes:

- Forecast and SkySight overlay behavior should remain global.
- Provider credentials remain excluded from both aircraft profile and normal
  portable JSON.

### General -> Hotspots

Target status: `Global app setting, not aircraft profile`

Notes:

- Hotspots are currently persisted through OGN-related preference ownership, so
  they should stay grouped with OGN in the global/full-backup model.

### General -> Weather

Target status: `Global app setting, not aircraft profile`

Notes:

- Weather overlay behavior is intentionally global.

### General -> OGN

Target status: `Global app setting, not aircraft profile`

Notes:

- OGN traffic and trail selection are intentionally global.
- Hotspot-related controls stay with that global ownership.

### General -> ADS-b

Target status: `Global app setting, not aircraft profile`

Notes:

- ADS-B behavior is intentionally global.
- Provider/account credentials remain excluded from normal portable JSON.

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

Target status: `Include`

Include:

- cruise orientation mode
- circling orientation mode
- glider screen percent
- map shift bias mode
- map shift bias strength
- auto reset enabled
- auto reset timeout
- minimum speed threshold
- bearing smoothing enabled
- bearing smoothing alpha

Notes:

- Orientation is approved aircraft-profile content and should remain in the
  portable file.

### General -> Thermalling

Target status: `Global app setting, not aircraft profile`

Notes:

- Thermalling behavior is intentionally global.

## Outside General But Still Part Of Aircraft Profile

These domains are outside the `General` taxonomy but are approved aircraft
profile content:

- map style selection
- QNH manual value
- snail-trail settings:
  - trail length
  - trail type
  - wind drift enabled
  - scaling enabled

Likely exclude from aircraft profile:

- home waypoint
- nav drawer expansion state
- recent waypoint history
- task working state
- cache/sync checkpoint data
- credential and secret stores

## Implementation Priority

To move toward a true full aircraft profile file without destabilizing runtime
ownership, priority should be:

1. codify the approved aircraft-profile whitelist in code and tests
2. separate aircraft-profile export from full-backup snapshot generation
3. align UI text and docs with the aircraft-profile vs full-backup split
4. keep file-backed state, secrets, caches, and replay/session data out of the
   aircraft-profile file
5. add `Full Backup` as a separate migration flow when needed

## Explicit Recommendation

For XCPro, the correct implementation direction is:

1. keep internal repositories as runtime SSOT
2. treat the portable JSON as the user-owned aircraft profile file
3. make aircraft-profile import/export the supported aircraft move workflow
4. treat full backup as a separate whole-app migration flow
5. avoid a design where normal profile switching depends on external file reads

This gives users the profile file they expect while preserving deterministic
runtime behavior and current architecture boundaries.
