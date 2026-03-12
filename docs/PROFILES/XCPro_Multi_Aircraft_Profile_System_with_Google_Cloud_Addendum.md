# XCPro Multi-Aircraft Profile System
**Research-backed architecture and Codex implementation plan for Android**

_Last updated: 2026-03-11_

## 1. Goal

Build a **proper profile system** for XCPro where:

- the app is for **one human user per app install**
- that user can own **multiple aircraft profiles**
- aircraft can be of different types:
  - sailplane
  - hang glider
  - paraglider
- each aircraft can have its own:
  - technical/performance settings
  - IDs and connectivity settings
  - default screen card / layout setup
  - calculation overrides
- profiles can be:
  - created
  - duplicated
  - switched quickly
  - archived / deleted
  - exported
  - imported
  - backed up to external drives / cloud-backed document providers / USB storage

This document is written so a Codex agent can implement the feature inside XCPro.

---

## 2. Executive recommendation

Do **not** build one giant monolithic “profile blob”.

Build a **layered profile system** with four separate concepts:

1. **Pilot Profile**  
   Single local owner of the install.

2. **Aircraft Profiles**  
   One row per aircraft/wingset/glider.  
   This is the main object the pilot switches between.

3. **Layout Presets / Display Profiles**  
   Reusable UI card/screen setups.  
   Aircraft reference them, but layout data is **not** mixed into aircraft physics/config unless needed.

4. **Active Session Selection**  
   Small state for “currently selected aircraft”, “currently selected layout”, and temporary overrides.

### Why this is the right structure

Because your app has **one user** but **many aircraft** and **many layouts**.  
That means the clean model is:

- **one** pilot profile
- **many** aircraft profiles
- **many** layout presets
- **one active selection**

That is much cleaner than pretending each aircraft is a totally separate app account.

This also matches how serious flying apps tend to behave:

- **SeeYou Navigator** keeps an aircraft list where each aircraft is effectively a separate profile with its own settings, and selecting an aircraft applies the relevant flight/logger settings.
- **XCTrack / AIR³** uses profile-based layouts and supports export/import of configuration files.

---

## 3. Research summary that drives this recommendation

### Android storage guidance

Official Android guidance strongly supports a hybrid approach:

- **Room** is recommended for non-trivial structured local data.
- **DataStore** is the modern solution for small app state / settings.
- **Storage Access Framework (SAF)** is the proper way to save/open files in shared storage, cloud providers, SD cards, and transient roots like USB storage.
- **Auto Backup** is useful for small internal profile data, but it is not a replacement for explicit user export/import.

### Similar app patterns

Relevant product patterns from comparable flying apps:

- **SeeYou Navigator**: each aircraft is stored as its own profile with type-specific fields; selecting an aircraft applies settings; deleting a profile does not delete past flight logs.
- **XCTrack / AIR³**: profile choice changes layout behavior; config export/import exists; changing profiles/layout configs can overwrite customizations unless they were exported first.

### What that means for XCPro

XCPro should copy the **good pattern**, not the messy one:

- aircraft identity should be separate from the pilot
- layouts should be first-class objects
- history / flight logs should not be tightly coupled to current editable profile rows
- export/import should use a portable bundle format, not a raw Room database dump
- external-drive support should use SAF, not raw storage paths or broad file permissions

---

## 4. Core product model

## 4.1 Concepts

### A. Pilot Profile
Exactly one local pilot profile for the app install.

Contains things like:

- pilot display name
- club / competition details if needed
- default units
- app-wide defaults
- default calculation preferences that apply unless an aircraft overrides them

### B. Aircraft Profile
Represents one real aircraft or wing.

Examples:

- JS1-C 18m
- Arcus
- Moyes Litespeed
- Ozone wing
- training paraglider
- club glider
- private glider with custom polar

Contains:

- aircraft type
- name
- manufacturer / model
- registration / comp ID / FLARM / OGN IDs where relevant
- performance parameters
- layout links
- connection/sensor defaults
- logging/calculation overrides
- last-used time
- archived state

### C. Layout Preset
Represents a screen-card / page / widget setup.

Examples:

- “Sailplane XC”
- “Sailplane Competition”
- “Hang Glider Ridge”
- “Paraglider Thermal”
- “Minimal Training”
- “Landing Page Set”

A layout preset can be:

- system/built-in
- user-created
- imported
- cloned from another preset

### D. Active Session
Tracks the current aircraft and layout selection.

This is small, fast-changing state, for example:

- active aircraft ID
- active layout ID
- last selected profile
- temporary session override flags

---

## 5. The most important architectural rule

**Do not make the aircraft profile equal to the flight log identity.**

When a flight starts, XCPro should capture an **aircraft/profile snapshot** into the flight record.

That means if the pilot later:

- renames an aircraft
- changes the polar
- changes the layout
- deletes the aircraft profile

…old flights still retain the exact aircraft metadata that was active at takeoff.

This is the correct behavior and matches how serious flight tools avoid corrupting historical records.

### Required outcome

- deleting a profile must **not** delete existing flight logs
- editing a profile must **not** retroactively rewrite old flight records
- flights should store a lightweight immutable snapshot of the selected aircraft/profile at launch

---

## 6. Recommended data architecture

## 6.1 Storage split

### Recommended
Use **Room + Proto DataStore + SAF**.

#### Room
Use Room for all structured profile data:

- pilot profile
- aircraft profiles
- layout presets
- aircraft-layout associations
- import/export history (optional)

#### Proto DataStore
Use Proto DataStore for small typed app state:

- active aircraft ID
- active layout ID
- selected backup folder URI
- last export mode
- profile manager UI state
- small session flags

#### SAF
Use SAF for import/export files and backup folders:

- `ACTION_CREATE_DOCUMENT` for saving a bundle
- `ACTION_OPEN_DOCUMENT` for importing a bundle
- `ACTION_OPEN_DOCUMENT_TREE` for choosing a reusable backup folder

### Not recommended

- SharedPreferences for anything important
- raw file-path access to shared storage
- broad storage permissions as the primary export/import mechanism
- exporting the raw Room DB as the user-facing backup format

---

## 6.2 Optional but smart database split

If XCPro is still early enough architecturally, strongly consider:

- `profiles.db` for pilot/aircraft/layout profile data
- `flights.db` for flight logs, IGCs, traces, replay data, analytics

### Why this is smart

- profile data stays small and backup-friendly
- flight logs can grow large without affecting profile backup
- easier migrations
- easier manual export/import
- easier Android Auto Backup inclusion/exclusion rules

### Minimum acceptable fallback
If you already have one DB and do not want a split yet:

- keep profile tables separate from flight tables
- do **not** use cascade delete from aircraft profile to flights
- still export/import via a profile bundle, not the raw database

---

## 7. Recommended Room schema

This is the cleanest balance between structure and flexibility.

## 7.1 PilotProfileEntity

```kotlin
@Entity(tableName = "pilot_profile")
data class PilotProfileEntity(
    @PrimaryKey
    val id: String = "pilot",
    val displayName: String,
    val club: String?,
    val competitionNumber: String?,
    val defaultUnitsJson: String,
    val appDefaultsJson: String,
    val createdAtUtc: String,
    val updatedAtUtc: String
)
```

## 7.2 AircraftProfileEntity

Use common searchable fields as columns, and type-specific detail as JSON.

```kotlin
@Entity(
    tableName = "aircraft_profile",
    indices = [
        Index("type"),
        Index("name"),
        Index("archived"),
        Index("updatedAtUtc")
    ]
)
data class AircraftProfileEntity(
    @PrimaryKey
    val id: String, // UUID
    val pilotProfileId: String = "pilot",
    val name: String,
    val type: AircraftType,
    val manufacturer: String?,
    val model: String?,
    val registration: String?,
    val competitionId: String?,
    val flarmHex: String?,
    val ognDeviceId: String?,
    val notes: String?,
    val defaultLayoutPresetId: String?,
    val aircraftSpecificsJson: String,      // sealed JSON
    val calcOverridesJson: String?,         // optional sealed JSON
    val sensorBindingsJson: String?,        // optional sealed JSON
    val connectivityJson: String?,          // optional sealed JSON
    val archived: Boolean = false,
    val sortOrder: Int = 0,
    val createdAtUtc: String,
    val updatedAtUtc: String,
    val lastUsedAtUtc: String?
)
```

## 7.3 LayoutPresetEntity

```kotlin
@Entity(
    tableName = "layout_preset",
    indices = [
        Index("name"),
        Index("targetAircraftType"),
        Index("source"),
        Index("updatedAtUtc")
    ]
)
data class LayoutPresetEntity(
    @PrimaryKey
    val id: String, // UUID
    val name: String,
    val targetAircraftType: AircraftType?, // null = reusable for any type
    val source: LayoutPresetSource,        // SYSTEM, USER, IMPORTED
    val schemaVersion: Int,
    val immutable: Boolean,
    val layoutSpecJson: String,
    val createdAtUtc: String,
    val updatedAtUtc: String
)
```

## 7.4 AircraftLayoutCrossRef

Use a join table if you want one aircraft to expose multiple layout choices.

```kotlin
@Entity(
    tableName = "aircraft_layout_cross_ref",
    primaryKeys = ["aircraftId", "layoutId"]
)
data class AircraftLayoutCrossRef(
    val aircraftId: String,
    val layoutId: String,
    val isDefault: Boolean,
    val sortOrder: Int
)
```

## 7.5 ImportHistoryEntity (optional but useful)

```kotlin
@Entity(tableName = "profile_import_history")
data class ProfileImportHistoryEntity(
    @PrimaryKey
    val id: String, // UUID
    val sourceDisplayName: String,
    val sourceUri: String?,
    val importMode: ImportMode,
    val importedAtUtc: String,
    val resultJson: String
)
```

---

## 8. Type-specific aircraft details: best approach

Do **not** create one giant table with dozens of nullable columns for every aircraft type.

That gets ugly fast.

### Recommended approach
Use:

- common fields as Room columns
- type-specific fields as sealed JSON in `aircraftSpecificsJson`

Example sealed hierarchy:

```kotlin
@Serializable
sealed interface AircraftSpecifics {
    val schemaVersion: Int
}

@Serializable
data class SailplaneSpecifics(
    override val schemaVersion: Int = 1,
    val wingSpanMeters: Double?,
    val dryMassKg: Double?,
    val maxMassKg: Double?,
    val waterBallastLiters: Double?,
    val polarJson: String?,
    val bugFactor: Double?,
    val safetySpeedsJson: String?
) : AircraftSpecifics

@Serializable
data class HangGliderSpecifics(
    override val schemaVersion: Int = 1,
    val certification: String?,
    val wingSizeSqM: Double?,
    val hookInWeightKg: Double?,
    val trimSpeedMps: Double?,
    val maxSpeedMps: Double?
) : AircraftSpecifics

@Serializable
data class ParagliderSpecifics(
    override val schemaVersion: Int = 1,
    val certification: String?,
    val wingSizeSqM: Double?,
    val takeoffWeightKg: Double?,
    val trimSpeedMps: Double?,
    val acceleratedSpeedMps: Double?
) : AircraftSpecifics
```

### Why this is the right tradeoff

This gives you:

- clean schema
- easy export/import
- fewer future migrations when you add type-specific fields
- simple Kotlin modeling with sealed types
- queryable common metadata without Room pain

---

## 9. Layout system recommendation

## 9.1 Separate layout presets from aircraft profiles

This is critical.

A layout preset should be its own object because:

- multiple aircraft may share one layout
- one aircraft may have multiple layouts
- users will want to duplicate and tweak layouts
- import/export should be able to include layouts independently

### Good model
- aircraft chooses a **default layout**
- aircraft can also expose a list of compatible layouts
- active session can temporarily override the default layout

## 9.2 Layout storage

If XCPro already has a dynamic card/page model, store it as `layoutSpecJson`.

If XCPro currently has mostly code-defined screens, implement in two stages:

### Stage A — fast implementation
- create built-in layout preset IDs
- each aircraft stores the default preset ID
- switching aircraft changes the chosen preset
- export/import includes preset references and any user-custom presets

### Stage B — full implementation
- make layouts fully data-driven
- store widget/page/card arrangements in JSON
- allow clone/edit/export/import of user layouts

### Strong recommendation
Even if the layout renderer is not fully dynamic yet, still create the **LayoutPreset** domain now.  
That prevents profile architecture from turning into a dead end.

---

## 10. Recommended effective-settings overlay model

When XCPro resolves “what settings should be active right now?”, use this order:

1. **system defaults**
2. **aircraft-type defaults**
3. **pilot/app defaults**
4. **aircraft profile overrides**
5. **layout preset**
6. **temporary session overrides**

This gives you a predictable merge model.

### Example
A sailplane can inherit generic sailplane defaults, then override:

- polar
- ballast
- STF behavior
- logger tags
- default layout

Then the selected layout can override display-only behavior like:

- which cards appear
- order of pages
- font size choice
- map overlays visible by default

This keeps physics/config separate from display config.

---

## 11. Export/import architecture

## 11.1 Use a portable bundle, not a DB dump

### Export format recommendation
Use a **ZIP-based profile bundle** with a custom extension:

- extension: `.xcproprofile`
- MIME used when saving: `application/zip`

### Why ZIP
Because a real export may need to contain:

- pilot profile JSON
- multiple aircraft JSON files
- multiple layout JSON files
- thumbnails/previews later
- custom audio/layout assets later
- manifest and checksums

This is much better than one giant JSON file and far better than a raw DB export.

---

## 11.2 Recommended bundle structure

```text
my_backup_2026-03-11_153000.xcproprofile
├── manifest.json
├── pilot.json
├── aircraft/
│   ├── 2d2a7b2d-....json
│   ├── 69fbce8a-....json
├── layouts/
│   ├── 8e2f9f20-....json
│   ├── 9cbd7dd5-....json
├── metadata/
│   ├── export_summary.json
│   └── checksums.json
└── assets/
    └── (reserved for future)
```

## 11.3 Manifest format

```json
{
  "packageType": "xcpro-profile-bundle",
  "formatVersion": 1,
  "exportedAtUtc": "2026-03-11T04:30:00Z",
  "appVersion": "1.0.0",
  "installationId": "0f1d....",
  "pilotProfileId": "pilot",
  "objectCounts": {
    "aircraft": 3,
    "layouts": 4
  },
  "checksumsFile": "metadata/checksums.json"
}
```

## 11.4 Export scopes to support

Implement these three export modes:

### A. Export all profiles
Exports:

- pilot profile
- all aircraft profiles
- all referenced layouts
- optional app-wide defaults

### B. Export one aircraft profile
Exports:

- the selected aircraft profile
- any layouts directly linked to it
- enough pilot/app defaults to make the import usable elsewhere

### C. Export layouts only
Useful if the pilot wants to move card/UI setups between devices or aircraft.

---

## 11.5 Import modes to support

Implement these import modes:

### A. Merge
- preserve existing profiles
- update by stable UUID when incoming object IDs match
- create new objects when IDs do not match
- handle name collisions by suffixing copied names

### B. Replace imported IDs
- if same UUID exists, overwrite that object
- if object not found, insert new one

### C. Import as copies
- regenerate new UUIDs for incoming aircraft/layouts
- keep existing objects untouched

### D. Replace all profile data
Dangerous, but useful as a full restore mode.  
This should only wipe profile tables, **not** flight logs.

---

## 11.6 Import flow

### Required flow

1. User picks bundle with `ACTION_OPEN_DOCUMENT`
2. App reads the ZIP stream from `ContentResolver`
3. Parse `manifest.json`
4. Validate:
   - package type
   - format version
   - checksums
   - required files
5. Build an **ImportPreview**
6. Show user:
   - aircraft count
   - layout count
   - collisions
   - unsupported objects if any
7. If confirmed:
   - run import inside a Room transaction
   - write import history
   - refresh active profile state if needed

### Required behavior
If import fails halfway, no partial broken state should remain.

So the actual import must be transactional.

---

## 11.7 Stable IDs are mandatory

Every exportable object must use a **stable UUID**.

Never rely on database row order or auto-increment IDs for portable import/export.

Use UUIDs for:

- aircraft profile IDs
- layout preset IDs
- import history IDs
- installation ID
- export job ID if desired

---

## 11.8 Forward compatibility rules

### Must do
- include `formatVersion` in bundle manifest
- include `schemaVersion` in layout and type-specific JSON
- parse JSON with `ignoreUnknownKeys = true`
- fail clearly on unsupported major versions

### Best-effort behavior
If a future layout contains unknown optional fields:

- keep import working
- preserve known data
- report unsupported parts in preview/result

---

## 12. External drives, USB, cloud, and proper Android file handling

## 12.1 Correct Android mechanism

Use **Storage Access Framework**.

### For export
Use `ActivityResultContracts.CreateDocument("application/zip")`

### For import
Use `ActivityResultContracts.OpenDocument()`

### For reusable backup folder
Use `ActivityResultContracts.OpenDocumentTree()`

This is the proper way to support:

- device local storage
- SD cards
- cloud providers like Drive
- transient roots such as USB storage providers

## 12.2 Persisting folder access

If the user chooses a backup folder tree, persist URI access with:

- `takePersistableUriPermission()`

Store the chosen tree URI in Proto DataStore.

That lets XCPro write backup bundles there again later without asking every time, unless the provider revokes access or the document disappears.

## 12.3 What not to do

Do **not** design this around:

- manual file-path strings
- writing into arbitrary folders directly
- legacy broad storage access as the primary path
- assuming “external drive” means only a physical SD card

On modern Android, SAF is the right answer.

---

## 13. Backup strategy recommendation

## 13.1 Manual export/import is the primary backup mechanism
Because the user explicitly wants:

- save to external drives
- import/export between devices
- portable profile backups

So manual export/import is mandatory.

## 13.2 Android Auto Backup is optional secondary protection
Enable Android backup intentionally for profile data only.

### Recommended use
Back up:

- profile database (if small)
- DataStore settings for active profile state
- maybe lightweight user defaults

Exclude:

- caches
- terrain/map downloads
- weather caches
- replay caches
- giant logs
- temporary files

### Important quota note
Android Auto Backup has a 25 MB per-app-user cloud backup limit.  
So Auto Backup is useful for profile/config state, not for huge aviation data blobs.

## 13.3 Strong recommendation
If possible, keep the profile subsystem small enough that Android backup can safely include it.

That gives the user:

- manual portable backups
- plus normal device migration restore

---

## 14. Recommended domain model

```kotlin
data class PilotProfile(
    val id: String,
    val displayName: String,
    val defaultUnits: UnitsConfig,
    val defaults: AppDefaults
)

data class AircraftProfile(
    val id: String,
    val name: String,
    val type: AircraftType,
    val manufacturer: String?,
    val model: String?,
    val registration: String?,
    val competitionId: String?,
    val flarmHex: String?,
    val ognDeviceId: String?,
    val specifics: AircraftSpecifics,
    val calcOverrides: CalcOverrides?,
    val sensorBindings: SensorBindings?,
    val connectivity: ConnectivityProfile?,
    val defaultLayoutPresetId: String?,
    val archived: Boolean,
    val lastUsedAtUtc: Instant?
)

data class LayoutPreset(
    val id: String,
    val name: String,
    val targetAircraftType: AircraftType?,
    val source: LayoutPresetSource,
    val schemaVersion: Int,
    val immutable: Boolean,
    val spec: LayoutSpec
)

data class ActiveProfileSelection(
    val aircraftId: String?,
    val layoutId: String?,
    val sessionOverrides: SessionOverrides
)
```

---

## 15. Required repositories and use cases

## 15.1 Repositories

```text
ProfileRepository
LayoutPresetRepository
ProfileImportExportRepository
ActiveProfileRepository
```

### Suggested responsibilities

#### ProfileRepository
- create aircraft profile
- update aircraft profile
- duplicate aircraft profile
- archive/unarchive
- delete profile
- list profiles
- set last used
- resolve effective profile

#### LayoutPresetRepository
- list layouts
- clone layout
- attach layout to aircraft
- detach layout
- set aircraft default layout

#### ProfileImportExportRepository
- export bundle
- preview import
- import bundle
- validate checksums
- record import history

#### ActiveProfileRepository
- observe active aircraft ID
- observe active layout ID
- set active aircraft/layout
- store last selected profile state in Proto DataStore

## 15.2 Use cases

```text
CreateAircraftProfileUseCase
DuplicateAircraftProfileUseCase
ArchiveAircraftProfileUseCase
DeleteAircraftProfileUseCase
SetActiveAircraftUseCase
SetActiveLayoutUseCase
ResolveEffectiveProfileUseCase
ExportProfilesUseCase
PreviewImportProfilesUseCase
ImportProfilesUseCase
```

---

## 16. UI / UX recommendations

## 16.1 Main Profile Manager screen

Create a dedicated screen with tabs or segmented sections:

- **Aircraft**
- **Layouts**
- **Import / Export**
- **Pilot Defaults**

### Aircraft list row should show
- aircraft name
- aircraft type
- manufacturer/model
- registration or wing size summary
- linked default layout
- “Active” badge if selected

### Primary actions
- Add aircraft
- Duplicate aircraft
- Archive
- Delete
- Export selected
- Activate

## 16.2 New aircraft flow

When creating an aircraft:

1. choose type
2. choose template or blank
3. enter common identity info
4. enter type-specific info
5. choose default layout
6. save and optionally activate

### Templates to provide
At minimum:

- sailplane template
- hang glider template
- paraglider template
- club / trainer template
- blank custom template

## 16.3 Layout flow

Allow:

- use built-in layout
- clone built-in layout
- rename clone
- assign clone as aircraft default
- export selected layouts
- import layouts

### Important rule
Built-in layouts should be immutable.  
If the user wants to edit one, XCPro should clone it first.

## 16.4 Quick switching

Add a fast pre-flight switcher:

- current aircraft chip/button
- current layout chip/button
- recent aircraft list
- “use last active profile” option

This matters because pilots often need to switch aircraft quickly.

---

## 17. Deletion and archive behavior

## 17.1 Archive instead of delete by default
Because pilots often want to keep old aircraft around without seeing them in the main list.

### Default list behavior
- show active + non-archived
- optional “show archived”

## 17.2 Hard delete rules
Hard delete should only remove:

- aircraft profile rows
- aircraft-layout links
- maybe imported cloned layouts if unreferenced and chosen by user

Hard delete should **not** remove:

- flights
- IGC files
- flight summaries
- historical profile snapshots

---

## 18. Import/export conflict rules

Codex should implement deterministic conflict handling.

## 18.1 Collision rules

### Same UUID
Treat as the same object.

### Different UUID but same visible name
Treat as a name collision, not the same object.

### Recommended default
For “Import as copies”:
- append ` (Imported)` or ` (Imported 2)` as needed

## 18.2 Updated-at handling
Each object should have `updatedAtUtc`.

For merge mode:
- if incoming UUID matches existing UUID, incoming can replace existing if user chose merge/replace
- preview should show “will update existing object”

---

## 19. Validation rules

## 19.1 Aircraft validation
At save time:

### Required
- non-blank name
- valid aircraft type

### Conditional rules
#### Sailplane
- warn if no polar / performance config
- warn if no registration/comp ID if logger integration expects it

#### Hang glider / paraglider
- warn if wing size/certification missing where relevant

### Connectivity checks
- validate FLARM / OGN hex formats if provided

## 19.2 Layout validation
- valid schema version
- valid page/card IDs
- valid references to known widgets
- reject duplicate card IDs if your renderer requires uniqueness

## 19.3 Import validation
- ZIP readable
- manifest present
- package type correct
- format version supported
- required JSON files parseable
- required referenced layouts present
- checksum validation pass

---

## 20. Recommended implementation phases for Codex

## Phase 1 — Domain and persistence
Build:

- Room entities
- DAOs
- mappers
- domain models
- repositories
- Proto DataStore for active selection

### Deliverables
- profile schema
- repository tests
- migration tests

## Phase 2 — Aircraft profile UI
Build:

- profile list screen
- create/edit aircraft flow
- duplicate/archive/delete
- activate aircraft

### Deliverables
- Compose screens
- ViewModels
- UDF state/events/effects

## Phase 3 — Layout preset linking
Build:

- layout preset domain
- attach/detach layouts to aircraft
- set default layout
- quick layout switching

### Deliverables
- layout list UI
- default-layout selection
- active-session switching

## Phase 4 — Export/import bundle
Build:

- exporter
- importer
- preview flow
- merge/replace/copy modes
- checksum validation
- ZIP packaging

### Deliverables
- CreateDocument export
- OpenDocument import
- transaction-safe restore

## Phase 5 — Backup folder support
Build:

- OpenDocumentTree folder picker
- persistable URI storage
- “Export latest backup to this folder” action
- optional “auto-backup on profile change”

### Deliverables
- reusable backup destination
- safe error handling if folder disappears

## Phase 6 — History correctness
Build:

- flight-time aircraft snapshot capture
- no-cascade-delete rules
- migration if current flight rows need snapshot field

### Deliverables
- historical accuracy preserved
- deletion safe

## Phase 7 — Polish and hardening
Build:

- import history screen
- validation messages
- profile templates
- clone layout flow
- preview text and error reporting

---

## 21. Suggested package/module structure

Adjust to XCPro’s existing modules, but the shape should look like this:

```text
core/model/profile/
core/model/layout/
core/persistence/profile/
data/profile/
domain/profile/
feature/profilemanager/
feature/profileeditor/
feature/layoutpresets/
feature/profileimportexport/
```

Suggested responsibilities:

```text
core/model/profile      -> domain enums, sealed models, DTOs
core/persistence/profile -> Room DB, entities, DAOs, converters
data/profile            -> repositories, mappers, import/export code
domain/profile          -> use cases
feature/profilemanager  -> list + activate
feature/profileeditor   -> create/edit aircraft
feature/layoutpresets   -> layout assignment + clone
feature/profileimportexport -> export/import UI
```

---

## 22. Suggested DAO operations

```kotlin
@Dao
interface AircraftProfileDao {
    @Query("SELECT * FROM aircraft_profile WHERE archived = 0 ORDER BY sortOrder, name")
    fun observeActiveProfiles(): Flow<List<AircraftProfileEntity>>

    @Query("SELECT * FROM aircraft_profile WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): AircraftProfileEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: AircraftProfileEntity)

    @Query("UPDATE aircraft_profile SET archived = :archived, updatedAtUtc = :updatedAt WHERE id = :id")
    suspend fun setArchived(id: String, archived: Boolean, updatedAt: String)

    @Query("DELETE FROM aircraft_profile WHERE id = :id")
    suspend fun deleteById(id: String)
}
```

---

## 23. Import/export service interfaces

```kotlin
interface ProfileBundleExporter {
    suspend fun export(
        scope: ExportScope,
        destination: Uri
    ): ExportResult
}

interface ProfileBundleImporter {
    suspend fun preview(source: Uri): ImportPreview
    suspend fun import(
        source: Uri,
        mode: ImportMode
    ): ImportResult
}
```

### Important implementation note
Both should use `ContentResolver` streams, not file paths.

---

## 24. Compose integration notes

### Export
Use `rememberLauncherForActivityResult(CreateDocument("application/zip"))`

### Import
Use `rememberLauncherForActivityResult(OpenDocument())`

### Backup folder
Use `rememberLauncherForActivityResult(OpenDocumentTree())`

When a tree URI is returned:

- request persistable permission
- store the URI string in Proto DataStore
- later recreate `DocumentFile.fromTreeUri(...)` or use `DocumentsContract` APIs to create timestamped backup files

---

## 25. Error handling requirements

Codex should implement user-visible error classes such as:

```text
UnsupportedBundleVersion
InvalidBundleStructure
ChecksumMismatch
MissingReferencedLayout
ImportConflict
StoragePermissionLost
DestinationUnavailable
ExportWriteFailed
ImportReadFailed
```

### UI behavior
Errors should be shown as plain-English messages, not raw stack traces.

---

## 26. Migration requirements

Room migration quality matters here.

### Required
- migration test for every schema version step
- import/export round-trip tests
- active selection restore tests
- hard-delete safety tests
- no flight data loss tests

### If using JSON in Room
Also test:

- old JSON payload with missing optional fields
- new JSON payload with unknown fields
- import with higher minor schema version
- graceful failure with unsupported major version

---

## 27. Acceptance criteria

The implementation is done only when all of this is true:

### Core behavior
- one pilot can create multiple aircraft profiles
- aircraft can be sailplane / hang glider / paraglider
- each aircraft can have its own default layout
- user can switch active aircraft quickly
- user can duplicate/archive/delete aircraft safely

### Data safety
- deleting a profile does not delete old flights
- flights preserve an aircraft snapshot
- profile import is transactional
- import handles conflicts deterministically

### Android correctness
- import/export uses SAF
- export works to local, cloud, and USB-capable document providers
- reusable backup folder uses persisted URI permission
- no broad legacy storage permission required for the main workflow

### Backup portability
- user can export all profiles to one bundle
- user can export one aircraft profile
- user can import the bundle on another device
- layouts linked to imported aircraft come across correctly

---

## 28. Recommended “done right” vs “done fast” path

## Done right
- Room for structured profile data
- Proto DataStore for active selection
- reusable LayoutPreset domain
- ZIP bundle export/import
- flight snapshot preservation
- merge/replace/copy import modes

## Done fast
If Codex needs a simpler first pass:

1. implement PilotProfile + AircraftProfile + ActiveSelection
2. store layout as a preset ID, not full editable JSON yet
3. export/import only aircraft + preset IDs first
4. add custom editable layouts second

### Important
Even in the fast version, still:
- use stable UUIDs
- use SAF
- preserve flight snapshots
- avoid raw DB export

---

## 29. Direct instructions for Codex

Implement the feature with these non-negotiable rules:

1. **One pilot profile only** per app install.
2. **Many aircraft profiles** under that pilot.
3. Aircraft profile data must support **sailplane**, **hang glider**, and **paraglider** specifics.
4. **Layout presets are separate objects**, not hard-baked into aircraft rows.
5. Use **Room** as the single source of truth for structured profile data.
6. Use **Proto DataStore** for active selection and small persistent UI/session state.
7. Use **Storage Access Framework** for export/import and external drive/folder handling.
8. Export/import must use a **portable ZIP bundle**, not a database dump.
9. Every exportable object must use a **stable UUID**.
10. Deleting an aircraft profile must **not** delete past flight logs.
11. At flight start, write an **immutable aircraft/profile snapshot** into the flight record.
12. Import must support **preview + transactional apply**.
13. Add **Room migration tests** and **round-trip export/import tests**.
14. Keep the feature modular and aligned with XCPro’s MVVM/UDF/SSOT architecture.

---

## 30. My final recommendation

For XCPro, the proper profile system is:

- **single pilot**
- **many aircraft profiles**
- **many reusable layout presets**
- **one active session selection**
- **portable ZIP profile bundles via SAF**
- **history-safe flight snapshots**

That is the right long-term design.

Anything simpler than that usually turns into a mess when the user starts switching between gliders/wings/layouts and then wants backup/restore across devices.

---

## 31. References

### Android Developers
- Room database guide: https://developer.android.com/training/data-storage/room
- Room migration guide: https://developer.android.com/training/data-storage/room/migrating-db-versions
- DataStore guide: https://developer.android.com/topic/libraries/architecture/datastore
- Shared storage / documents-files guide: https://developer.android.com/training/data-storage/shared/documents-files
- Storage Access Framework guide: https://developer.android.com/guide/topics/providers/document-provider
- Auto Backup guide: https://developer.android.com/identity/data/autobackup

### Comparable product references
- Naviter / SeeYou Navigator aircraft hangar: https://kb.naviter.com/en/kb/wing-glider-hangar/
- XCTrack change log mentioning export/import configuration: https://xctrack.org/Change_Log.html
- AIR³ / XCTrack profile-based layouts: https://www.fly-air3.com/en/support/air3-xctrack-manual/air3-manager-manual/
- AIR³ / XCTrack interface by chosen profile: https://www.fly-air3.com/en/support/air3-xctrack-manual/air3-manager-manual/air3-manager-xctrack-interface/
- Example XCTrack config export notes: https://henrikbengtsson.github.io/my-paragliding-setup/xctrack/README.html

---

# Appendix A — Google Drive cloud backup + automatic IGC upload addendum

This appendix **extends and partially refines** the earlier recommendations.

## A1. Product decision

### Verdict

Yes, **optional** cloud backup and automatic upload are good ideas.

No, asking the user to type their **Gmail password** into XCPro is **not** a good idea and should not be built.

The correct industry-standard design is:

1. keep XCPro **offline-first** with local Room/DataStore as the source of truth
2. keep **manual export/import via SAF** as the provider-agnostic baseline
3. add **optional Google-backed automation**
4. never store a Google password
5. avoid storing long-lived Google refresh tokens on-device

### Recommended shipping plan

Implement this in **two layers**:

#### Layer 1 — provider-agnostic file automation (ship first)
Use the existing SAF export/import design and let the user choose a folder, including a **Google Drive folder**, as the backup/upload destination.

This gives you:

- Google Drive support
- USB / SD / local / cloud provider support
- no password handling
- no custom Google OAuth token handling
- the same code path as manual export/import

#### Layer 2 — native Google Drive backup (optional advanced mode)
Add a Google-specific backup mode for **profile snapshots only**, using the Google Drive `appDataFolder`.

This gives you:

- hidden app-specific cloud backup
- no user-visible Drive clutter for configuration data
- per-change automatic profile backup
- easier restore of settings on another XCPro install after sign-in

### Final recommendation

For XCPro, do **both** eventually:

- **SAF folder mode** for visible files and user-controlled destinations
- **Google Drive appData mode** for hidden automatic profile backup

But if you want the best cost/benefit ratio first, ship **SAF folder mode** before native Google Drive API work.

---

## A2. What must NOT be built

Do **not** implement:

- an email + password form for Google accounts
- storage of the user's Gmail password in XCPro
- local storage of Google refresh tokens on-device
- Google Drive as the only backup path
- cloud sync as a replacement for local persistence

### Non-negotiable rule

XCPro should never know the user's Google password.

If XCPro supports Google-backed automation, the user should either:

- choose a Google Drive folder through Android's system document picker, or
- sign in with Google and authorize Drive access through Google's identity flow

---

## A3. Correct Google auth model for Android

## A3.1 Authentication

Use **Credential Manager + Sign in with Google** if XCPro offers a Google account connection UI.

Use this only to let the user choose the Google account cleanly and securely.

### Practical UI
In Settings > Cloud Backup & Sync:

- **Connect Google account**
- show connected email after success
- **Disconnect Google account**

### Important
The Google account is selected through the Google / Android identity UI.  
It is **not typed manually** into XCPro.

## A3.2 Authorization

For Google data access, use **AuthorizationClient** and request Drive scopes **only when the user enables the relevant feature**.

### Scope recommendation

#### For hidden automatic profile backups
Request:

- `https://www.googleapis.com/auth/drive.appdata`

Use this for:

- current profile snapshot
- optional versioned profile backups
- small restore metadata

#### For native visible Drive folder/file access (only if you later add it)
Prefer the narrow scope:

- `https://www.googleapis.com/auth/drive.file`

Use this for:

- files created by XCPro in Drive
- app-managed visible folders/files

### Important design choice
If IGC upload is implemented via **SAF folder mode**, you do **not** need to request Drive API file scope for that path.  
The system document provider handles account access.

---

## A4. Security and encryption recommendation

## A4.1 Passwords

Do not store them because XCPro should never receive them.

## A4.2 Access tokens

Do not design a custom token vault.

Let Google's auth stack provide short-lived access tokens when needed.  
If a token becomes invalid, clear cached token state and request authorization again as needed.

## A4.3 Refresh tokens

Do **not** store refresh tokens on-device.

If XCPro ever adds a backend that needs offline server-side Drive access, store refresh tokens **only on your backend**, not in the Android app.

## A4.4 What XCPro may store locally

XCPro may persist:

- connected Google email address
- a stable local flag that Google sync is enabled
- chosen backup/upload mode
- chosen SAF tree URI
- app-managed remote file IDs or relative paths
- last successful sync time
- last error state
- upload policy settings

These are not the user's Google credentials.

## A4.5 If you must encrypt any app secret

If XCPro stores any app-generated secret or sensitive local credential in the future, protect it with **Android Keystore-backed encryption** and do not include it in exported bundles.

### Practical stance
For the design in this appendix, the cleanest path is to avoid storing sensitive Google secrets at all.

---

## A5. Recommended XCPro cloud architecture

## A5.1 Keep the original storage architecture

Do not change the earlier core storage recommendation:

- **Room** remains the source of truth for structured profile data
- **Proto DataStore** remains the source of truth for active small state
- **SAF** remains the baseline for manual export/import and provider-agnostic folder automation

## A5.2 Add a sync layer, not a second source of truth

Cloud storage is **replication**, not the canonical model.

### Rule
The local app state remains authoritative.  
Cloud copies are derived artifacts and recovery material.

That means:

- all edits write locally first
- background work uploads derived backups/files later
- network failure never blocks the local user action

## A5.3 Use a queue-based sync design

Add a small sync subsystem.

### Recommended entities

```kotlin
enum class CloudSyncMode {
    NONE,
    SAF_FOLDER,
    GOOGLE_DRIVE_APPDATA
}

enum class SyncItemType {
    PROFILE_BUNDLE,
    PROFILE_SNAPSHOT,
    IGC_FILE
}

enum class SyncState {
    PENDING,
    RUNNING,
    SUCCEEDED,
    FAILED_RETRYABLE,
    FAILED_AUTH_REQUIRED,
    FAILED_PERMANENT
}

@Entity(tableName = "cloud_sync_config")
data class CloudSyncConfigEntity(
    @PrimaryKey
    val id: String = "primary",
    val mode: CloudSyncMode,
    val enabled: Boolean,
    val googleEmail: String?,
    val safTreeUri: String?,
    val autoBackupProfiles: Boolean,
    val autoUploadIgc: Boolean,
    val unmeteredOnly: Boolean,
    val chargingRequired: Boolean,
    val keepVersionedProfileBackups: Boolean,
    val lastSuccessfulSyncAtUtc: String?,
    val lastErrorCode: String?,
    val updatedAtUtc: String
)

@Entity(
    tableName = "sync_queue",
    indices = [
        Index("state"),
        Index("itemType"),
        Index("updatedAtUtc")
    ]
)
data class SyncQueueEntity(
    @PrimaryKey
    val id: String, // UUID
    val itemType: SyncItemType,
    val localObjectId: String?,          // aircraft/profile batch/flight row id
    val localUriOrPath: String?,         // local file if applicable
    val remoteKey: String?,              // remote file id / relative path / appData key
    val checksumSha256: String?,
    val state: SyncState,
    val attemptCount: Int,
    val nextAttemptAtUtc: String?,
    val errorCode: String?,
    val createdAtUtc: String,
    val updatedAtUtc: String
)
```

### Why this matters
Without a queue, auto-backup and auto-upload quickly turn into fragile ad hoc code.

---

## A6. Best storage target per data type

## A6.1 Profile backups

### Best target
Use **Google Drive `appDataFolder`** for hidden automatic profile backup.

### Why
Profile/config backups are app-internal recovery artifacts.  
They do **not** need to be user-browsable in normal use.

### What to store there
Keep it small and deterministic:

```text
appDataFolder/
├── profile_snapshot_current.json
├── profile_snapshot_manifest.json
├── profile_backup_2026-03-11T15-30-00Z.xcproprofile   (optional versioned copy)
└── profile_backup_2026-03-12T08-10-12Z.xcproprofile   (optional versioned copy)
```

### Recommended content
Use one of these two strategies:

#### Strategy A — simplest
Store the same `.xcproprofile` ZIP bundle you already use for export/import.

Pros:
- one format everywhere
- easier restore testing
- fewer code paths

Cons:
- slightly heavier than a small JSON snapshot

#### Strategy B — optimized
Store:

- one compact JSON snapshot for frequent backups
- plus optional versioned `.xcproprofile` bundles daily / weekly / manual

### Final recommendation
Ship **Strategy A** first. Reuse the bundle exporter.

---

## A6.2 IGC files

### Best target
Use a **user-visible folder**, not `appDataFolder`.

### Why
Pilots often want to:

- browse the file
- share it
- copy it to another app
- upload it to contest/logbook services
- access it from desktop later

### Recommended implementation path
Use **SAF tree URI folder mode** for IGC auto-upload.

Let the user pick a folder that can be:

- Google Drive
- local storage
- SD card
- USB-capable provider
- another cloud-backed document provider

### Recommended folder structure

```text
<chosen folder>/
└── XCPro/
    ├── Flights/
    │   ├── 2026/
    │   │   ├── 03/
    │   │   │   ├── 2026-03-11_ABC123_MtBorah.igc
    │   │   │   └── 2026-03-11_ABC123_MtBorah.json   (optional sidecar)
    └── Profiles/
        ├── latest.xcproprofile
        └── history/
            └── 2026-03-11_153000.xcproprofile
```

### Optional sidecar file
A sidecar JSON can hold:

- flight UUID
- aircraft snapshot id
- checksum
- uploadedAtUtc
- launch name
- site name
- app version

This helps duplicate detection and later migration.

---

## A7. Recommended hybrid product design

## A7.1 Default user experience

Expose this as one feature area:

### Settings > Cloud Backup & Sync

Sections:

#### Connection
- Google account: **Connect / Disconnect**
- connected email
- status: connected / auth required / disconnected

#### Backup destination
- **Use chosen folder** (recommended first shipping mode)
- **Use hidden Google backup** (advanced)
- **Choose flight upload folder**
- **Choose profile backup folder** (optional, if using folder mode only)

#### Automation
- Auto-backup profiles when they change
- Auto-upload IGC when a flight is finalized
- Upload on: any network / unmetered only
- Require charging: on/off
- Keep versioned profile backups: on/off

#### Actions
- Back up now
- Upload pending flights now
- Export bundle now
- Restore latest backup
- View last sync result

## A7.2 Recommended defaults

### Suggested defaults
- cloud sync off by default
- local-only app works fully without cloud
- auto profile backup on after user enables cloud sync
- auto IGC upload on after user chooses a folder
- unmetered only default = true
- charging required default = false
- keep local IGC after upload = always true

### Important
Do not delete the local IGC after successful upload.  
The cloud copy is a backup/share target, not the only copy.

---

## A8. Change detection and job scheduling

## A8.1 Profile backup trigger

Do **not** upload on every keystroke.

### Recommended trigger model
When a profile/layout/pilot-default change is committed:

1. mark profile data as dirty
2. debounce
3. enqueue one unique backup job

### Recommended debounce
Use a short debounce window such as:

- 15 seconds after save/apply
- or 30–60 seconds if edits are frequent

### Recommended unique work name
```text
profile-cloud-backup
```

Use `ExistingWorkPolicy.REPLACE` or the equivalent behavior so repeated changes collapse into one pending upload.

## A8.2 IGC upload trigger

Upload only after the flight log is complete and the file is finalized.

### Recommended trigger
When flight state changes to finalized / landed / recording closed:

1. compute checksum
2. create sync queue entry
3. enqueue upload work

### Recommended unique work name
```text
igc-upload-<flightId>
```

## A8.3 WorkManager usage

Use **WorkManager** for all deferrable upload jobs.

### Constraints
Respect user settings:

- network connected
- optionally unmetered
- optionally charging

### Retry
Use exponential backoff for transient failures.

### Auth failures
If authorization is needed and user interaction is required:

- mark queue item `FAILED_AUTH_REQUIRED`
- surface a notification / in-app banner
- do not loop forever in the background

---

## A9. Google Drive API vs SAF responsibilities

## A9.1 SAF folder mode responsibilities

Use SAF folder mode for:

- manual export/import
- reusable provider-agnostic backup folders
- user-visible IGC destinations
- user-visible profile bundle history

### Why
This keeps XCPro aligned with Android storage best practice and avoids provider lock-in.

## A9.2 Native Google Drive responsibilities

Use native Google Drive API only for:

- hidden profile backup in `appDataFolder`
- account-linked restore of app profile data
- future multi-device profile sync if you decide to add it

### Why
The hidden Drive app-data area is good for configuration but poor for user-visible operational files.

---

## A10. Duplicate detection and overwrite policy

## A10.1 Profile backups

For hidden Google profile backup:

- keep a stable remote object key for the latest snapshot
- overwrite that latest snapshot on each successful backup
- optionally create periodic versioned copies

### Suggested latest keys
```text
profile_snapshot_current.json
latest_bundle.xcproprofile
manifest.json
```

## A10.2 IGC files

Never detect duplicates by filename alone.

Use:

- flight UUID
- takeoff time
- checksum SHA-256
- optional file size

### Duplicate rule
If a remote destination already has the same logical flight and the same checksum:

- mark as already synced
- do not upload again

If the same logical flight exists with a different checksum:

- keep the original
- upload the new one with a deterministic suffix such as `_v2`

---

## A11. Conflict resolution rules

## A11.1 Profiles

For restore/import, the earlier import rules still apply.  
Cloud backup is **not** a collaborative real-time sync system.

### Recommended restore model
Treat cloud restore as:

- explicit restore latest
- explicit restore chosen backup
- or explicit import-as-copy

### Do not ship first version as “live bidirectional sync”
That adds:

- conflict resolution complexity
- multi-device race conditions
- more support burden

### Safer first version
Ship:

- automatic backup
- manual restore
- optional “restore newest remote backup on clean install” flow

## A11.2 Future multi-device sync
If XCPro later supports true cross-device live sync, add:

- object revision numbers
- sync journal / tombstones
- per-object merge policy
- clearer conflict UI

Do not fake this with last-write-wins on every object unless you accept silent data loss.

---

## A12. Flight-history safety rules

These are unchanged and remain mandatory.

### Mandatory
When a flight starts or becomes the active recorded flight, store an immutable aircraft/profile snapshot with the flight.

That means:

- if the aircraft name changes later, the old flight keeps the old snapshot
- if the profile is deleted later, the old flight still has the original metadata
- if a backup restore imports newer aircraft settings, old flights remain historically correct

### Additional cloud rule
Auto-uploaded IGC files should be generated from the finalized local flight data, not from mutable current profile rows.

---

## A13. Suggested user flows

## A13.1 Enable Google-backed backup

1. User opens Settings > Cloud Backup & Sync
2. taps **Connect Google account**
3. XCPro launches Sign in with Google
4. user selects account
5. XCPro stores account display info only
6. if user enables hidden profile backup, XCPro requests Drive app-data authorization
7. XCPro runs an initial backup
8. UI shows last backup time

## A13.2 Enable folder-based Google Drive upload

1. User opens Settings > Cloud Backup & Sync
2. taps **Choose flight upload folder**
3. Android picker opens
4. user picks a folder in Google Drive
5. XCPro takes persistable URI permission
6. XCPro stores the tree URI
7. next finalized IGC is written to that folder automatically via WorkManager

## A13.3 Restore on a new device

### Folder-based restore
1. user installs XCPro
2. chooses Import
3. opens `.xcproprofile` from Google Drive/local/USB via SAF
4. imports with preview

### Hidden Google backup restore
1. user installs XCPro
2. connects same Google account
3. XCPro checks for latest hidden backup
4. if found, shows restore prompt
5. user previews and confirms restore
6. restore runs transactionally

---

## A14. Package and module additions

Add modules/packages along these lines:

```text
core/model/cloudsync/
core/persistence/cloudsync/
data/cloudsync/
domain/cloudsync/
feature/cloudsync/
```

Suggested responsibilities:

```text
core/model/cloudsync        -> enums, DTOs, sync result models
core/persistence/cloudsync  -> Room entities, queue DAO, config DAO
data/cloudsync              -> auth wrappers, Drive/appData client, SAF folder writer, workers
domain/cloudsync            -> use cases
feature/cloudsync           -> settings UI, connect/disconnect, status, restore
```

---

## A15. Suggested use cases

```text
ConnectGoogleAccountUseCase
DisconnectGoogleAccountUseCase
EnableGoogleProfileBackupUseCase
ChooseFlightUploadFolderUseCase
QueueProfileBackupUseCase
RunProfileBackupNowUseCase
QueueIgcUploadUseCase
RunPendingSyncUseCase
RestoreLatestGoogleBackupUseCase
ObserveCloudSyncStatusUseCase
```

---

## A16. Suggested interfaces

```kotlin
interface GoogleAccountGateway {
    suspend fun connect(): GoogleAccountConnectionResult
    suspend fun disconnect()
    suspend fun ensureDriveAppDataAccess(): DriveAccessResult
}

interface ProfileCloudBackupGateway {
    suspend fun backupLatestBundle(): CloudBackupResult
    suspend fun restoreLatestBundlePreview(): ImportPreview?
}

interface FolderUploadGateway {
    suspend fun uploadIgcToChosenFolder(
        flightId: String,
        sourceUri: Uri,
        destinationName: String
    ): UploadResult
}
```

---

## A17. Codex implementation rules for Google support

Implement these additional non-negotiable rules:

1. **Do not build manual Gmail credential entry.**
2. If Google account linking exists, use **Credential Manager** for sign-in.
3. Use **AuthorizationClient** for Google Drive authorization, separate from sign-in.
4. Request Google scopes **only when the user enables the relevant feature**.
5. Use **`drive.appdata`** for hidden automatic profile backups.
6. Use **SAF folder mode** for auto-uploading IGC files to a user-visible destination, including Google Drive.
7. Keep the app **offline-first**; local Room data is the source of truth.
8. Use **WorkManager** for deferred profile backup and IGC upload jobs.
9. Add a **sync queue** and do not rely on ad hoc fire-and-forget uploads.
10. Never store Google passwords.
11. Never store long-lived Google refresh tokens on-device.
12. Do not export tokens, auth codes, or cloud metadata secrets inside `.xcproprofile` bundles.
13. If Google auth is lost, fail safely and ask the user to reconnect.
14. Preserve the earlier rules on **flight snapshots**, **transactional import**, and **stable UUIDs**.

---

## A18. Acceptance criteria for the cloud add-on

The Google/cloud add-on is done only when all of this is true:

### Security
- no Gmail password field exists
- no refresh token is stored on-device
- any sensitive local secret uses Keystore-backed protection or is not stored at all

### Product behavior
- user can connect a Google account without typing credentials into XCPro
- user can choose a Google Drive folder via Android picker
- profile changes can trigger automatic backup
- finalized IGC files can upload automatically
- cloud failures never block local flight recording or profile editing

### Reliability
- uploads are queued
- retries use backoff
- auth-required state is surfaced cleanly
- repeated profile edits collapse into one backup job
- duplicate IGC uploads are avoided deterministically

### Recovery
- user can restore from a manual `.xcproprofile` bundle
- user can restore from latest hidden Google backup if that mode is enabled
- flight logs remain valid after profile restore/import
- local app remains usable with no network

---

## A19. My updated final recommendation

Yes: **optional Google-backed backup/upload is worth adding**.

No: **storing Gmail account details or passwords in XCPro is the wrong design**.

The proper XCPro solution is:

- keep the current **Room + Proto DataStore + SAF** profile architecture
- keep **manual ZIP export/import** as the portable baseline
- let the user choose a **Google Drive folder** through SAF for visible files like IGC
- optionally add **hidden Google Drive appData backup** for profile snapshots
- use **Credential Manager** for sign-in if account linking is exposed
- use **AuthorizationClient** for Google Drive access
- use **WorkManager + a sync queue** for automatic background work
- keep XCPro **offline-first** and never let cloud availability control core flying workflows

That is the clean, Android-correct, user-safe way to build it.

---

## Appendix B — Additional references for the Google/cloud add-on

### Google / Android identity and auth
- About Sign in with Google (Credential Manager): https://developer.android.com/identity/sign-in/credential-manager-siwg
- Implement Sign in with Google: https://developer.android.com/identity/sign-in/credential-manager-siwg-implementation
- Authorize access to Google user data: https://developer.android.com/identity/authorization
- Migration from legacy Google Sign-In: https://developer.android.com/identity/sign-in/legacy-gsi-migration

### Google Drive
- Store application-specific data (`appDataFolder`): https://developers.google.com/workspace/drive/api/guides/appdata
- Choose Google Drive API scopes: https://developers.google.com/workspace/drive/api/guides/api-specific-auth
- Upload file data: https://developers.google.com/workspace/drive/api/guides/manage-uploads
- Create and manage files: https://developers.google.com/workspace/drive/api/guides/create-file
- Create and populate folders: https://developers.google.com/workspace/drive/api/guides/folder

### Android security and background work
- Android Keystore system: https://developer.android.com/privacy-and-security/keystore
- WorkManager guide: https://developer.android.com/develop/background-work/background-tasks/persistent/getting-started/define-work
- Offline-first architecture guidance: https://developer.android.com/topic/architecture/data-layer/offline-first

### Comparable product patterns
- Naviter SeeYou Navigator backup and sync: https://naviter.com/2023/07/backup-and-sync/
- Naviter profiles concept: https://legacy-kb.naviter.com/en/kb/profiles/
- XCTrack export/import examples: https://henrikbengtsson.github.io/my-paragliding-setup/xctrack/README.html
- AvPlan sync aircraft across devices: https://help.avplan-efb.com/en/articles/6621386-how-do-i-sync-my-aircraft-profiles-flight-plans-and-user-waypoints-across-devices
