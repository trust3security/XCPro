# Profiles Modernization Plan

This document describes how XCPro will evolve profile management so every user has a reliable default configuration, a single source of truth (SSOT) for all settings, and an intuitive import/export workflow.

---

## 1. Goals

1. **Always-available Welcome profile** - ship with a non-removable baseline so new installs and corrupted stores recover automatically.
2. **Profile = SSOT** - each profile owns references to every configurable subsystem (flight data cards, polar data, look & feel, etc.), and switching profiles rehydrates all repositories coherently.
3. **Versioned bundles** - exporting/importing a profile round-trips everything the user customized, enabling easy device migration.

---

## 2. Architecture Overview

### 2.1 ProfileManager (new domain layer)

- Responsibilities:
  - Owns the list of `UserProfile`s, the active profile ID, and exposes `StateFlow<ProfileState>`.
  - Provides typed accessors/mutators such as `profileManager.flightDataConfig`, `profileManager.polarSettings`, etc.
  - Delegates persistence to a `ProfileStore` (JSON/DataStore/ProtoBuf) and coordinates with subsystem repositories (e.g., `CardPreferences`, `PolarRepository`).
  - Emits hydration events when the active profile changes so each subsystem can update.

### 2.2 Profile Schema (v2)

```json
{
  "id": "uuid",
  "name": "Welcome",
  "aircraftType": "SAILPLANE",
  "flags": {
    "isDefault": true,
    "version": 2
  },
  "settings": {
    "flightData": {
      "templates": {...},
      "cardsByMode": {...},
      "visibilities": {...}
    },
    "polar": {...},
    "general": {...},
    "lookAndFeel": {...}
  },
  "checksum": "sha256..."
}
```

- **Version** enables migrations.
- **Checksum** detects corruption before loading.
- `settings.flightData` mirrors the structures currently owned by `CardPreferences` (template mappings, card lists per mode, visibilities).

---

## 3. Implementation Plan

### Phase 1 - Default Profile & Guards
1. Add `/assets/profiles/welcome_profile_v2.json`.
2. On app launch:
   - If `ProfileRepository` has no entries, clone the welcome profile, set `isDefault = true`, activate it.
   - If the active profile ID is missing/invalid, fall back to this welcome profile.
   - Prevent deletion of any profile with `flags.isDefault`.
3. Provide `ProfileManager.resetDefaultProfile()` that re-applies the baseline state.

### Phase 2 - ProfileManager & SSOT Wiring
1. Introduce `ProfileManager` (domain module):
   - `val profiles: StateFlow<List<UserProfile>>`
   - `val activeProfile: StateFlow<UserProfile>`
   - Methods: `setActiveProfile(id)`, `updateFlightDataConfig(...)`, etc.
2. Refactor `FlightDataViewModel` to depend on `ProfileManager` instead of reading directly from `CardPreferences`. Internally it still uses `CardPreferences`, but writes flow through the manager so other consumers can see updates.
3. Repeat for other subsystems (polar, general settings) - expose read/write APIs through the manager.

### Phase 3 - Unified Persistence
1. Extend `ProfileStore` so each profile JSON persists:
   - Flight data template mappings (`templates`, `cardsByMode`, `visibilities`).
   - Polar parameters.
   - General settings (units, audio, gestures).
2. During save:
   - Gather current state from each repository via the manager.
   - Serialize to the active profile's JSON and write to storage atomically.
3. During load/switch:
   - Read the selected profile JSON, validate checksum/version.
   - Hydrate each subsystem repository.
   - Notify UIs via the existing flows (`activeProfile`, `flightDataConfig`, etc.).
4. Migration:
   - Provide a converter from legacy `CardPreferences`/`ProfileRepository` data into the new schema.
   - Store a `schemaVersion` to support future migrations.

### Phase 4 - Export / Import UX
1. Add Export profile" action:
   - Writes the JSON + checksum to `/XCPro/Profiles/<profileName>-<date>.json`.
2. Add Import profile" action:
   - Lets user pick a file, validates checksum & schema version.
   - If valid, adds to repository and optionally activates it.
3. Document the directory so users can copy profiles between devices.

---

## 4. UI Updates

1. **Manage Profiles Screen**
   - Display the Welcome profile with a lock badge and Reset" button.
   - Show accurate card counts by reading `ProfileManager.flightDataConfig`.
   - Surface export/import actions.
2. **Profile Indicator / Switcher**
   - Subscribe to `ProfileManager.activeProfile`.
   - Offer quick switch while ensuring the default cannot be removed.
3. **Flight Data Modal & Map Screen**
   - Already consume `FlightDataViewModel`; ensure the VM listens to ProfileManager so toggling cards writes back to the profile bundle.

---

## 5. Testing Strategy

1. **Unit Tests**
   - ProfileManager: profile creation, switching, reset, guard against deleting default.
   - Serialization/deserialization round-trips for the new schema.
2. **Migration Tests**
   - Feed legacy DataStore contents, ensure migration creates the welcome profile + converts existing ones.
3. **Integration Tests (Robolectric)**
   - Launch app with no profiles: verify welcome profile seeded, cards available.
   - Toggle cards, switch profiles, confirm SSOT across modals and map.
4. **Export/Import Tests**
   - Export a customized profile, delete local store, import file, ensure all settings restored.

---

## 6. Rollout Considerations

1. **Backups**
   - Before migrating, copy legacy data files to `/Profiles/backup-<timestamp>.zip`.
2. **Telemetry (optional)**
   - Count how many users rely on the default vs custom profiles to prioritize future UX work.
3. **Documentation**
   - Update user guides to explain the Welcome profile, exports, and how to reset.
4. **Staged Release**
   - Enable the new profile system behind a feature flag for internal testing before rolling to all users.

---

By following this plan we ensure every user starts from a known-good configuration, reduce SSOT violations, and make multi-device workflows predictable. The ProfileManager becomes the gateway for every setting, so future features (e.g., per-aircraft polar libraries) plug into the same infrastructure without inventing new persistence paths.

