# Phase 6 Manual Checklist

## Purpose

Operator-facing manual signoff checklist for the recovery release-grade slice.

## Checklist

- [x] `Recording` snapshot restart resumes existing live session and does not invoke terminal repository recovery.
- [x] `Finalizing` snapshot restart routes through `IgcRecoveryBootstrapUseCase`.
- [x] Structured recovery metadata is persisted at recording start.
- [x] First valid B-record updates recovery metadata with authoritative fix time.
- [x] Recovery uses structured metadata as primary identity authority.
- [x] Short-form `HFDTE` is accepted during staged metadata parse fallback.
- [x] Multiple finalized matches for one `sessionId` surface `DUPLICATE_SESSION_GUARD`.
- [x] Orphan pending MediaStore rows are deleted before republish.
- [x] Recovery rerun does not create a duplicate finalized file.
- [x] Startup recovery outcomes emit typed diagnostics.
- [x] Diagnostics distinguish repository-classified terminal failure from bootstrap exception fallback.
- [x] Pipeline documentation records startup bootstrap and diagnostics publication.
- [x] Full multi-module `connectedDebugAndroidTest --no-parallel` release verification.
- [ ] Branch hygiene cleanup of unrelated pre-existing Phase 6/profile worktree changes.

## Remaining Manual Release Actions

1. Run `./gradlew connectedDebugAndroidTest --no-parallel` on a stable attached device or emulator pool.
2. Isolate or separately track unrelated dirty worktree paths before recovery release signoff.
