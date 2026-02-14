
# Agent-Execution-Contract.md -- Generic XCPro Autonomous Execution Skeleton

Use this file as a reusable execution contract for future XCPro work.
The human fills Section 1. The agent executes Sections 2-7.
Use together with `docs/ARCHITECTURE/AGENT.md` and `AGENTS.md`.

---

# 0) Agent Execution Contract (Read First)

This document is the task-level execution contract for autonomous implementation.

## 0.1 Authority
- Execute end-to-end without checkpoint confirmations.
- Ask questions only when blocked by missing information that cannot be inferred from the repo.
- If ambiguity exists, choose the most architecture-consistent option and document assumptions.

## 0.2 Responsibilities
- Implement Section 1 completely.
- Preserve MVVM + UDF + SSOT and dependency direction (`UI -> domain -> data`).
- Keep business logic testable and free of Android/UI framework dependencies unless explicitly required.
- Use injected clocks/time sources in domain/fusion paths.
- Preserve replay determinism and avoid hidden global mutable state.
- Fix build/test/lint failures introduced by the change.

## 0.3 Workflow Rules
- Execute phases in order (Section 2).
- Do not leave partial implementations or TODO placeholders in production paths.
- If tests are modified or removed, justify why this is required by Section 1.
- Update architecture docs when wiring/policy changes (see Section 2.5).

## 0.4 Definition of Done
Work is complete only when:
- All phases are complete and gates are satisfied.
- Acceptance criteria in Section 3 are satisfied.
- Required verification in Section 4 passes.
- Verification evidence table in Section 4.1 is complete.
- Any local limitations are documented per Section 4.2.
- Architecture drift self-audit (Section 6) is complete.
- Quality rescore (Section 7) is complete with evidence.

## 0.5 Mandatory Read Order
Read these before implementation:
- `AGENTS.md`
- `docs/ARCHITECTURE/ARCHITECTURE.md`
- `docs/ARCHITECTURE/CODING_RULES.md`
- `docs/ARCHITECTURE/PIPELINE.md`
- `docs/ARCHITECTURE/CODEBASE_CONTEXT_AND_INTENT.md`
- `docs/ARCHITECTURE/CONTRIBUTING.md`
- `docs/ARCHITECTURE/KNOWN_DEVIATIONS.md`

Read when relevant:
- `docs/ARCHITECTURE/AGENT.md`
- `docs/LevoVario/levo.md`
- `docs/LevoVario/levo-replay.md`

---

# 1) Change Request (Human Fills This In)

## 1.1 Feature Summary (1-3 sentences)
- [ ] Describe what should be built or changed.

## 1.2 User Stories / Use Cases
- [ ] As a ___, I want ___, so that ___.
- [ ] As a ___, I want ___, so that ___.

## 1.3 Scope and Non-Goals
- In scope:
  - [ ] ___
- Out of scope:
  - [ ] ___

## 1.4 Constraints
- Modules/layers affected:
  - [ ] ___
- Performance/battery limits:
  - [ ] ___
- Compatibility/migrations:
  - [ ] ___
- Safety/compliance constraints:
  - [ ] ___

## 1.5 Inputs / Outputs
- Inputs (events/sensors/data sources):
  - [ ] ___
- Outputs (UI/data/logs/metrics):
  - [ ] ___

## 1.6 Behavior Parity (for refactors/replacements)
- [ ] List behaviors that must remain identical.

## 1.7 Time Base Declaration (required if time-dependent)
| Value | Time Base (Monotonic / Replay / Wall) | Why |
|---|---|---|
| [ ] | [ ] | [ ] |

## 1.8 SSOT Ownership Declaration
- Data item:
  - Authoritative owner:
  - Exposed as:
  - Forbidden duplicates:

---

# 2) Execution Plan (Agent Owns, May Refine for Reality)

## Phase 0 -- Baseline and Safety Net
- Identify affected flow(s) and entry points.
- Confirm current behavior and defaults.
- Add/confirm regression tests that lock expected behavior.

Gate: no functional changes; repo builds.

## Phase 1 -- Pure Logic Implementation
- Implement/extract business logic into domain/use-case components.
- Keep logic deterministic and framework-agnostic.
- Add unit tests for happy path and edge cases.

Gate: deterministic unit tests pass.

## Phase 2 -- Repository and SSOT Wiring
- Wire authoritative state ownership.
- Expose `Flow`/`StateFlow` APIs only.
- Keep ports/adapters boundaries clean when persistence/network/device/file I/O is touched.

Gate: no duplicate state ownership; dependency direction preserved.

## Phase 3 -- ViewModel and UI Wiring
- ViewModels consume use-cases/repositories via DI (no raw manager/controller escape hatches).
- UI renders state and emits intents only.
- Ensure lifecycle-aware collection in UI.

Gate: end-to-end feature behavior works in debug.

## Phase 4 -- Hardening and Cleanup
- Verify threading, cancellation, lifecycle behavior.
- Remove dead code and update docs/tests.
- Run required checks.

Gate: Section 4 passes.

## 2.5 Documentation Sync Rules
- If pipeline wiring changes, update `docs/ARCHITECTURE/PIPELINE.md`.
- If policies/rules change, update `docs/ARCHITECTURE/ARCHITECTURE.md` and/or `docs/ARCHITECTURE/CODING_RULES.md`.
- If any rule is intentionally violated, add a time-boxed entry in `docs/ARCHITECTURE/KNOWN_DEVIATIONS.md` (issue, owner, expiry).

---

# 3) Acceptance Criteria (Human Defines, Agent Must Satisfy)

## 3.1 Functional Criteria
- [ ] Given ___ when ___ then ___.
- [ ] Given ___ when ___ then ___.

## 3.2 Edge Cases
- [ ] No data / degraded data / unavailable dependencies.
- [ ] Lifecycle transitions (background/foreground, rotation, process restore when applicable).
- [ ] Error handling and recovery behavior.

## 3.3 Required Test Coverage
- [ ] Unit tests for business logic.
- [ ] ViewModel tests for state transitions.
- [ ] Integration/instrumentation tests when relevant.

---

# 4) Required Verification (Agent Must Run and Report)

Minimum:
- `./gradlew enforceRules`
- `./gradlew testDebugUnitTest`
- `./gradlew assembleDebug`

Run when relevant (device/emulator available):
- `./gradlew :app:connectedDebugAndroidTest --no-parallel "-Pandroid.injected.androidTest.leaveApksInstalledAfterRun=true"`

Release/CI verification:
- `./gradlew connectedDebugAndroidTest --no-parallel`

Optional quality checks:
- `./gradlew detekt`
- `./gradlew ktlintCheck`

Agent report must include:
- Commands run
- Pass/fail result per command
- Fixes applied if failures occurred

## 4.1 Verification Evidence Table (Required in Final Report)
| Command | Purpose | Result (PASS/FAIL) | Duration | Failures fixed | Notes |
|---|---|---|---|---|---|
| `./gradlew enforceRules` | Architecture/coding rule enforcement | [ ] | [ ] | [ ] | [ ] |
| `./gradlew testDebugUnitTest` | Unit/regression test coverage | [ ] | [ ] | [ ] | [ ] |
| `./gradlew assembleDebug` | Build integrity | [ ] | [ ] | [ ] | [ ] |
| `./gradlew :app:connectedDebugAndroidTest --no-parallel "-Pandroid.injected.androidTest.leaveApksInstalledAfterRun=true"` | App-module instrumentation (when relevant) | [ ] | [ ] | [ ] | [ ] |
| `./gradlew connectedDebugAndroidTest --no-parallel` | Full multi-module instrumentation (release/CI parity) | [ ] | [ ] | [ ] | [ ] |

## 4.2 Local Limitations Rule
If any required verification command cannot be run locally, the agent must record:
- The exact command not run.
- The exact blocker (for example: no emulator, no device, sandbox/permission issue).
- What was run instead as partial evidence.
- Required follow-up verification step for CI or device, with explicit owner.
- Risk of shipping without that check.

---

# 5) Notes / ADR

Record non-trivial decisions:
- Decision:
- Alternatives considered:
- Why chosen:
- Impact/risks:
- Follow-ups:

## 5.1 Architecture Exception Template (Use Only If Necessary)
If a rule must be temporarily violated, add this entry to
`docs/ARCHITECTURE/KNOWN_DEVIATIONS.md` before merge:

- Issue ID:
- Rule violated:
- Owner:
- Expiry date (YYYY-MM-DD):
- Scope (files/modules):
- Rationale:
- Risk:
- Removal plan:
- Tracking link:

---

# 6) Architecture Drift Self-Audit (Mandatory Before Completion)

Verify:
- [ ] No business logic moved into UI.
- [ ] No UI/data dependency direction violations.
- [ ] No direct system time calls in domain/fusion logic.
- [ ] No new hidden global mutable state.
- [ ] No manager/controller escape hatches exposed through ViewModels/use-cases.
- [ ] Replay remains deterministic for identical inputs.
- [ ] No new rule violations, or deviation recorded in `KNOWN_DEVIATIONS.md`.

---

# 7) Agent Output Format (Mandatory)

At end of each phase:

## Phase N Summary
- What changed:
- Files touched:
- Tests added/updated:
- Verification results:
- Risks/notes:

At final completion:
- Done checklist (Sections 0.4 and 6)
- Quality rescore:
  - Architecture cleanliness: __ / 5
  - Maintainability/change safety: __ / 5
  - Test confidence on risky paths: __ / 5
  - Release readiness: __ / 5
- PR-ready summary (what/why/how)
- Manual verification steps (2-5 steps)

