# Production Compatibility Profile: XCS / WeGlide

## Purpose

Record the actual production IGC export behavior implemented in app code on
2026-03-10 so future agents do not confuse:

- fixture-generator compatibility work, with
- the real XCPro write/finalize/recovery path.

## Why This Note Exists

The first WeGlide upload investigation improved only
`docs/IGC/phase7_evidence/generate_igc_fixture.py`.

That was not sufficient for production because XCPro itself was still writing
the old app export shape. This note is the canonical correction: the production
writer path now carries the compatibility changes too.

## Current Production Contract

### Metadata and header shape

Production recorder metadata now comes from:

- `feature/map/src/main/java/com/example/xcpro/igc/data/IgcMetadataSources.kt`

Current behavior:

- manufacturer id: `XCS`
- recorder type: `XCPro,SignedMobile`
- security status: `SIGNED`
- signature profile: `XCS`
- GPS altitude datum: `ELL`

That metadata flows into the actual writer path through:

- `feature/igc/src/main/java/com/example/xcpro/igc/data/IgcRecordingRuntimeActionSink.kt`
- `feature/igc/src/main/java/com/example/xcpro/igc/domain/IgcHeaderMapper.kt`

Resulting production export shape:

- `A` record starts with `AXCS...`
- `HFFTYFRTYPE` uses `XCPro,SignedMobile`
- declaration diagnostics use `LXCS...` so they are included in the signer
  digest

### Finalize and recovery signing

Production signing is now applied in:

- `feature/igc/src/main/java/com/example/xcpro/igc/domain/IgcGRecordSigner.kt`
- `feature/igc/src/main/java/com/example/xcpro/igc/data/IgcFlightLogRepository.kt`

Current behavior:

- finalize appends `G` records when `signatureProfile = XCS`
- recovery reparses staged files, strips any existing `G` lines, and re-signs
  idempotently before republish
- staged metadata parsing infers `XCS` signing from the `A` record manufacturer
- merged recovery metadata now preserves the signature profile so crash recovery
  does not silently downgrade a signed export to unsigned output

## Important Separation

The Python fixture generator remains useful for upload experiments and evidence:

- `docs/IGC/phase7_evidence/generate_igc_fixture.py`

But it is not the production export path.

Future agents must not treat generator-only fixes as application fixes.

## Guardrails For Future Agents

1. If WeGlide or another validator rejects app-written files, inspect the
   production path first:
   - metadata source
   - runtime action sink
   - finalize repository
   - recovery repository
   - signer
2. If you change manufacturer id, recorder type, or signer profile, update the
   corresponding unit tests in `feature/igc/src/test`.
3. Recovery is part of the contract. Do not patch finalize-only behavior and
   leave crash recovery on the old profile.
4. The repo architecture rules prohibit the literal string `xcsoar` in
   production Kotlin. Keep production code vendor-neutral even when matching the
   `XCS` compatibility profile.
5. Historical docs that describe `G` signing as future-only are superseded by
   this note and the current code.

## Focused Verification Snapshot

Focused verification run completed after the production patch:

```bash
./gradlew :feature:igc:testDebugUnitTest --tests "com.example.xcpro.igc.domain.IgcGRecordSignerTest" --tests "com.example.xcpro.igc.data.IgcFlightLogRepositoryTest" --tests "com.example.xcpro.igc.data.IgcFlightLogRepositoryRecoveryTest" --tests "com.example.xcpro.igc.data.IgcFlightLogRepositoryRecoveryKillPointTest" --tests "com.example.xcpro.igc.data.IgcRecoveryMetadataStoreTest" :feature:igc:assembleDebug :feature:map:assembleDebug
```

Result:

- PASS on 2026-03-10

This was a fast targeted verification pass only, not the full repo verification
order from `AGENTS.md`.
