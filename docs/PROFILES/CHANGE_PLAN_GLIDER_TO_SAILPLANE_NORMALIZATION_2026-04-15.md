# GLIDER To SAILPLANE Normalization

Date: 2026-04-15
Status: Complete
Owner: `feature:profile`

## Summary

- XCPro standardizes user-facing aircraft types to:
  - `SAILPLANE`
  - `PARAGLIDER`
  - `HANG_GLIDER`
- Legacy `GLIDER` remains accepted for compatibility when reading old stored state and importing old JSON payloads.
- Legacy `GLIDER` is normalized eagerly to canonical `SAILPLANE` during hydration, import, and repository mutation persistence.

## Implementation

- Canonical aircraft-type selection is shared across first-launch, create-profile, and edit-profile UI.
- `default-profile` aircraft type is now editable through the same profile settings seam used by other profiles.
- Repository mutation and hydration paths normalize legacy `GLIDER` before persisting state so new exports and managed backups do not emit legacy aircraft types.
- Bundle parsing also normalizes legacy `GLIDER` before preview/import consumers read the parsed profiles.

## Tests

- Hydration tests cover normalization and snapshot repair for legacy stored `GLIDER` values.
- Import tests cover legacy `GLIDER` to `SAILPLANE` normalization.
- Export/parse tests cover canonicalized profile types for legacy bundle inputs.
- UI tests cover:
  - only 3 canonical aircraft types shown in create dialog
  - default profile aircraft type can be edited

## Notes

- `GLIDER` was not hard-deleted from the enum in this slice because old stored JSON and imported files still need to parse safely.
- A future cleanup slice may remove the compatibility enum value only after legacy read-path requirements are retired.
