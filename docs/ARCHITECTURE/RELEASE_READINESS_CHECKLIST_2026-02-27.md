# RELEASE_READINESS_CHECKLIST_2026-02-27.md

## Purpose

Execution checklist and evidence log for
`CHANGE_PLAN_RELEASE_GRADE_COMPLIANCE_2026-02-27.md`.

## Metadata

- Owner: XCPro Team
- Date: 2026-02-27
- Status: In progress
- Primary plan:
  - `docs/ARCHITECTURE/CHANGE_PLAN_RELEASE_GRADE_COMPLIANCE_2026-02-27.md`

## Phase Status

| Phase | Status | Notes |
|---|---|---|
| Phase 0 - Baseline and Freeze | In progress | Initial baseline captured; full gate evidence pending |
| Phase 1 - Secrets and Credential Hygiene | In progress | Tracked secret placeholders applied to known files |
| Phase 2 - Build/Config Hardening | Pending | |
| Phase 3 - Architecture Compliance Re-pass | In progress | ADS-B map test stabilization patch applied; rerun evidence pending |
| Phase 4 - Verification Matrix Execution | In progress | `enforceRules` and `assembleDebug` pass captured; full matrix pending |
| Phase 5 - RC Decision and Sign-off | Pending | |

## Security Hygiene Checklist

- [x] Replace real values in tracked `docs/OPENSKY/credentials.json`.
- [x] Remove API key material from tracked `.vscode/settings.json`.
- [ ] Rotate previously exposed credentials/tokens (external operation).
- [ ] Run repo-wide secret scan on tracked files and attach output.
- [ ] Confirm no secret-bearing values remain in tracked docs/settings.

## Required Verification Matrix

### Core Gates

- [x] `./gradlew enforceRules`
- [ ] `./gradlew testDebugUnitTest`
- [x] `./gradlew assembleDebug`

### Instrumentation Gates

- [ ] `./gradlew :app:connectedDebugAndroidTest --no-parallel "-Pandroid.injected.androidTest.leaveApksInstalledAfterRun=true"` (when device/emulator is available)
- [ ] `./gradlew connectedDebugAndroidTest --no-parallel` (release/CI verification)

## Command Evidence Log

| Command | Date | Result | Notes |
|---|---|---|---|
| `./gradlew.bat enforceRules` | 2026-02-27 | Pass | "Rule enforcement passed." |
| `./gradlew.bat :feature:map:testDebugUnitTest` | 2026-02-27 | Interrupted | Prior run interrupted by daemon/lock contention and user abort |
| `C:\Users\Asus\.gradle\wrapper\dists\gradle-8.13-bin\5xuhj0ry160q40clulazy9h7d\gradle-8.13\bin\gradle.bat --no-daemon assembleDebug` | 2026-02-27 | Pass | Basic build-only gate completed successfully |
| `:feature:map:testDebugUnitTest --tests "com.example.xcpro.map.MapScreenViewModelTest"` | 2026-02-27 | Interrupted | User-aborted rerun after applying ADS-B preference-reset test patch |

## Open Risks (Current)

| Risk | Severity | Mitigation Owner | Status |
|---|---|---|---|
| Exposed credentials were previously committed | High | XCPro Team | Rotate keys and verify revocation |
| Full verification matrix not yet green | High | XCPro Team | Execute sequential gates via safe runner where needed |
| Targeted map ADS-B suite rerun not yet completed after patch | Medium | XCPro Team | Complete `MapScreenViewModelTest` rerun and then full unit suite |
| Potential secret material in other tracked artifacts | Medium | XCPro Team | Complete tracked-file secret scan and cleanup |

## Final Sign-off

- [ ] No real secrets in tracked files
- [ ] Required verification matrix green
- [ ] Architecture/rules compliance validated
- [ ] Residual risks accepted with owners and ETAs
- [ ] RC go/no-go decision recorded
