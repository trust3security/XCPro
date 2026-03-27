# README_GLIDE_COMPUTER_RELEASE_GRADE_PACK_2026-03-27

This pack contains the phased implementation plan and Codex prompts for making XCPro release-grade for core glide-computer metrics.

## Files

- `CHANGE_PLAN_GLIDE_COMPUTER_RELEASE_GRADE_2026-03-27.md`
- `GLIDE_COMPUTER_METRIC_CONTRACT_MATRIX_2026-03-27.md`
- `CODEX_IMPLEMENTATION_BRIEFS_GLIDE_COMPUTER_RELEASE_GRADE_2026-03-27.md`
- `CODEX_REVIEW_BRIEFS_GLIDE_COMPUTER_RELEASE_GRADE_2026-03-27.md`

## Intended workflow

1. Create a clean working branch.
2. Add the change-plan and metric-contract docs to the repo before coding.
3. Run Phase 0 implementation brief.
4. Run Phase 0 PASS/FAIL review.
5. If PASS, commit/tag a checkpoint.
6. Repeat for each later phase.
7. Do not move to the next phase from a dirty tree.
8. Only push/open a PR after Phase 5 PASS.

## Branching

If the work depends on `final-glide-route-runtime-migration`, use a stacked branch from that branch until it merges.
If it does not, or after it merges, use a fresh branch from `origin/main`.
