# Phase 6 Recovery Kill Matrix

## Purpose

Concrete proof map for the release-grade recovery kill-point matrix.

## Matrix

| Kill Point | Crash Timing | Expected Recovery Outcome | Proof |
| --- | --- | --- | --- |
| `K1` | snapshot persisted before staged recovery bytes exist | terminal failure `STAGING_MISSING`; persisted finalizing snapshot is cleared; app starts fresh | `feature/map/src/test/java/com/trust3/xcpro/igc/usecase/IgcRecoveryBootstrapUseCaseTest.kt` `k1_finalizingSnapshotWithoutStagingMaterial_returnsTerminalFailure`; `feature/map/src/test/java/com/trust3/xcpro/igc/usecase/IgcRecordingUseCaseTest.kt` `k1_restartAfterSnapshotPersistBeforeStagedWrite_clearsFinalizingSnapshotAndStartsFresh` |
| `K2` | staged file written before MediaStore insert | recover by publishing from staging | `feature/igc/src/test/java/com/trust3/xcpro/igc/data/IgcFlightLogRepositoryRecoveryKillPointTest.kt` `k2_crashAfterStagedWriteBeforeMediaStoreInsert_recoversByPublishingFromStaging` |
| `K3` | pending MediaStore row inserted before copy | delete orphan pending row; republish once | `feature/igc/src/test/java/com/trust3/xcpro/igc/data/IgcFlightLogRepositoryRecoveryKillPointTest.kt` `k3_crashAfterPendingRowInsert_deletesPendingRowAndRepublishes` |
| `K4` | crash during byte copy into pending row | delete orphan pending row; republish once | `feature/igc/src/test/java/com/trust3/xcpro/igc/data/IgcFlightLogRepositoryRecoveryKillPointTest.kt` `k4_crashMidByteCopy_deletesPendingRowAndRepublishes` |
| `K5` | copy completed before pending row finalize | delete orphan pending row; republish once | `feature/igc/src/test/java/com/trust3/xcpro/igc/data/IgcFlightLogRepositoryRecoveryKillPointTest.kt` `k5_crashAfterByteCopyBeforePendingFinalize_deletesPendingRowAndRepublishes` |
| `K6` | publish finalized before snapshot clear | detect existing finalized entry and return `Recovered` without duplicate publish | `feature/igc/src/test/java/com/trust3/xcpro/igc/data/IgcFlightLogRepositoryRecoveryKillPointTest.kt` `k6_crashAfterPublishBeforeSnapshotClear_recoversExistingFinalizedEntry` |
| `K7` | snapshot already cleared before restart | no recovery work is attempted; no repository recovery call | `feature/map/src/test/java/com/trust3/xcpro/igc/usecase/IgcRecordingUseCaseTest.kt` `k7_restartAfterSnapshotClear_doesNotInvokeRepositoryRecovery` |

## Active-Flight Continuity

`Recording` is not a terminal recovery path. Restart continuity is proven by:

- `feature/map/src/test/java/com/trust3/xcpro/igc/usecase/IgcRecoveryBootstrapUseCaseTest.kt`
  - `recordingSnapshot_returnsResumeExisting_withoutCallingRepository`
- `feature/map/src/test/java/com/trust3/xcpro/igc/usecase/IgcRecordingUseCaseTest.kt`
  - `recordingSnapshot_resumesOnStartup_withoutRepositoryRecovery`

## Device Proof

Real MediaStore restart behavior is proven by:

- `feature/igc/src/androidTest/java/com/trust3/xcpro/igc/IgcRecoveryRestartInstrumentedTest.kt`
  - `recoveryRestart_deletesPendingRow_andPublishesSingleFinalFile_onRealMediaStore`

This covers:

- staged file present on restart
- orphan pending row cleanup on device
- single finalized file after recovery
- rerun does not duplicate the finalized artifact
