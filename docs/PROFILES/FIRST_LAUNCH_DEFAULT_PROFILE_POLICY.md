# First-Launch Default Profile Policy

Date: 2026-04-03
Status: Active
Owner: `feature:profile`

## Purpose

Define the startup policy for profile creation when no valid profile state exists after hydration.

## Policy

### Clean install

- If profile storage is valid and contains zero profiles, XCPro must not silently create a profile.
- The app must block normal entry until first-launch setup is completed.
- First-launch setup must ask which aircraft type the default profile should represent.
- First-launch choices are limited to:
  - `SAILPLANE`
  - `PARAGLIDER`
  - `HANG_GLIDER`
- Legacy `GLIDER` is not a valid new selection. Stored or imported legacy `GLIDER` profiles are normalized to `SAILPLANE`.
- First-launch setup must also expose `Load Profile File`.
- There is no skip path on clean install.

### Reinstall and restored state

- First-launch setup is not tied to APK install count.
- If Android backup/device transfer restores valid profile state, XCPro must treat that as existing user data.
- Valid restored or existing profile state must skip the first-launch picker and enter the normal app flow.
- Restored-state startup remains silent under the current policy; no extra confirmation screen is shown.

### Canonical default profile

- Completing first-launch setup creates the canonical default profile immediately.
- Canonical defaults created by first-launch setup use:
  - `id = default-profile`
  - `name = Default`
  - selected canonical aircraft type from the picker
  - `aircraftModel = null`
  - `description = null`
- The created profile becomes the active profile in the same repository mutation.

### Recovery and degraded startup

- Parse/read/bootstrap errors do not reuse the clean-install flow.
- Recovery stays explicit through the existing recovery card and import actions.
- `Recover Default` remains a separate fallback path for damaged storage and is not the first-launch policy owner.

### Backup mirroring

- Managed profile backup sync must not write public backup files while no profile exists.
- Backup/index mirroring resumes only after a real profile exists and an active profile ID is present.
- The managed Downloads mirror is not startup truth and must not be auto-imported during app launch.

### Clean-start testing

- Use `adb shell pm clear <package>` to verify the true first-launch picker path.
- Uninstall/reinstall is not a reliable clean-start test while Android backup restore is enabled.
- Deleting files in `Download/XCPro/profiles/` alone is insufficient because private DataStore profile state remains authoritative.

## Rationale

- Clean installs should reflect the aircraft the pilot actually flies.
- Restored profiles should behave like normal user data instead of being discarded just because the APK was reinstalled.
- Recovery should stay explicit so corrupted storage is visible and auditable instead of being hidden by silent reprovisioning.
- Suppressing backup output before first-launch completion avoids empty or misleading starter backup artifacts.
