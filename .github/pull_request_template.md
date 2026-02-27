# PR Checklist (XCPro)

## Summary
- What changed:
- Why:
- Risk areas:

## Architecture Drift Checklist (MANDATORY)
- [ ] MVVM + UDF + SSOT respected (no state duplication).
- [ ] UI does not import `data` layer.
- [ ] Domain/use-cases do not import Android/UI types.
- [ ] ViewModels depend on use-cases only (no platform APIs, no persistence).
- [ ] No raw manager/controller escape hatches exposed through use-cases or ViewModels.
- [ ] No Compose runtime state primitives used in non-UI managers/domain.
- [ ] No MapLibre types in domain/task managers.
- [ ] Timebase rules respected (no monotonic vs wall/replay mixing).
- [ ] Replay remains deterministic for identical inputs.
- [ ] If any rule is knowingly violated, it is recorded in `docs/ARCHITECTURE/KNOWN_DEVIATIONS.md` with issue ID, owner, expiry.

## Verification
Commands run (paste output or summarize):
- [ ] `./gradlew enforceRules`
- [ ] `./gradlew testDebugUnitTest`
- [ ] `./gradlew assembleDebug`
- [ ] (If relevant) `./gradlew connectedDebugAndroidTest`

## Rollback Plan (MANDATORY for non-trivial changes)
- [ ] One-step rollback command documented: `git revert <merge_or_commit_sha>`
- [ ] Post-rollback checks listed:
  - [ ] `./gradlew enforceRules`
  - [ ] `./gradlew testDebugUnitTest` (or focused test scope if justified)
  - [ ] `./gradlew assembleDebug`

## Docs
- [ ] `docs/ARCHITECTURE/PIPELINE.md` updated if wiring changed.
- [ ] `docs/ARCHITECTURE/ARCHITECTURE.md` / `docs/ARCHITECTURE/CODING_RULES.md` updated if rules changed.

## Quality Rescore (MANDATORY)
(From `docs/ARCHITECTURE/AGENT.md`)

- Architecture cleanliness: __ / 5  
- Maintainability / change safety: __ / 5  
- Test confidence on risky paths: __ / 5  
- Overall map/task slice quality: __ / 5  
- Release readiness (map/task slice): __ / 5  

Evidence:
- Files/areas improved:
- Tests added/updated:
- Remaining risks:
