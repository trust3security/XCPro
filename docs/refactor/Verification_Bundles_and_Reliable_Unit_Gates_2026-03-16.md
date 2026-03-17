# Verification Bundles And Reliable Unit Gates

## 0) Metadata

- Title: Repo-owned verification bundles and root unit-test reliability hardening
- Owner: XCPro Team
- Date: 2026-03-16
- Issue/PR: local-dev-qa-20260316
- Status: In progress

## 1) Scope

- Problem statement:
  - `testDebugUnitTest` is the correct PR-ready repo gate, but it is too heavy for routine slice validation.
  - Windows file-lock churn on `output.bin` can make the root unit-test gate fail for local-environment reasons rather than code regressions.
  - `xcpro-build` can choose a lighter tier, but the repo itself still needs explicit reusable verification bundles.
- Why now:
  - Recent terrain work showed that the repo had focused proof available, but the default local path still pushed directly into the heaviest gate.
  - The skill should be able to discover repo-owned commands instead of inventing raw Gradle task sets each time.
- In scope:
  - Add repo-owned verification bundles for common change lanes.
  - Add a reliable wrapper for root `testDebugUnitTest` that performs lock cleanup and one retry.
  - Document when to use bundles versus the canonical full gate.
- Out of scope:
  - Weakening or narrowing the meaning of root `testDebugUnitTest`.
  - Changing CI/release verification policy.
  - Automatic changed-file to test inference.
- User-visible impact:
  - None in app behavior.
  - Faster and more consistent local verification workflow.
- Rule class touched: Default

## 2) Architecture Contract

### 2.1 SSOT Ownership

| Data | Owner | Exposed As | Forbidden Duplicates |
|---|---|---|---|
| Verification bundle definitions | `scripts/qa/run_change_verification.ps1` | explicit named profiles | ad hoc duplicated Gradle command recipes across docs |
| Root unit-test lock recovery policy | `scripts/qa/run_root_unit_tests_reliable.ps1` | one retrying wrapper | multiple inconsistent cleanup command sets |

### 2.2 Dependency Direction

Expected flow:

`developer or agent -> repo-owned QA script -> gradlew / existing helper scripts`

- Modules/files touched:
  - `scripts/qa/`
  - `docs/ARCHITECTURE/CONTRIBUTING.md`
  - `scripts/dev/README.md`
- Boundary risk:
  - Do not create hidden policy drift where repo scripts claim a lighter proof can replace PR-ready gates.

### 2.2A Reference Pattern Check

| Reference File | Why It Is Similar | Pattern To Reuse | Planned Deviation |
|---|---|---|---|
| `scripts/qa/run_openweathermap_phase_gates.ps1` | repo-owned PowerShell QA runner | structured parameter parsing, serial command execution, repo-root location | simpler profile runner instead of long-lived phase state |
| `repair-build.bat` | existing lock/generated-state recovery helper | explicit local-state repair before rerun | narrower `output.bin` recovery for root unit tests |

### 2.2B Boundary Moves

| Responsibility | Old Owner | New Owner | Why | Validation |
|---|---|---|---|---|
| common slice verification command recipes | assistant memory / scattered docs | `scripts/qa/run_change_verification.ps1` | stable repo-owned entry point | dry-run profile checks |
| root unit-test lock cleanup recipe | manual commands in docs | `scripts/qa/run_root_unit_tests_reliable.ps1` | one reliable local recovery path | dry-run wrapper check |

### 2.2D File Ownership Plan

| File | New / Existing | Owner / Responsibility | Why Here | Why Not Another Layer/File | Split Needed? |
|---|---|---|---|---|---|
| `docs/refactor/Verification_Bundles_and_Reliable_Unit_Gates_2026-03-16.md` | New | change plan and intent record | required repo plan for non-trivial tooling change | not durable enough for ADR | No |
| `scripts/qa/run_change_verification.ps1` | New | named verification bundles and serial execution | `scripts/qa` is the repo QA entrypoint | not a build-script concern | No |
| `scripts/qa/run_change_verification.bat` | New | Windows wrapper for the bundle runner | matches existing QA wrapper pattern | PowerShell-only is less discoverable for Windows users | No |
| `scripts/qa/run_root_unit_tests_reliable.ps1` | New | root `testDebugUnitTest` retry and lock cleanup wrapper | keeps lock recovery explicit and local | not a Gradle build-logic policy change | No |
| `scripts/qa/run_root_unit_tests_reliable.bat` | New | Windows wrapper for the reliable root gate | matches existing helper style | same reason as above | No |
| `docs/ARCHITECTURE/CONTRIBUTING.md` | Existing | developer verification workflow doc | canonical contributor instructions | avoiding doc drift | No |
| `scripts/dev/README.md` | Existing | local helper discovery | closest script-local documentation | complements, not replaces, CONTRIBUTING | No |

### 2.2E Module and API Surface

| Contract / API | Owner | Consumers | Visibility | Why Needed | Compatibility / Removal Plan |
|---|---|---|---|---|---|
| named QA profiles (`slice-terrain`, `slice-profile`, `slice-replay`, `fast-loop`, `pr-ready`, `release-ready`) | `scripts/qa/run_change_verification.ps1` | developers, agents, docs | repo-local script CLI | keeps light verification explicit | evolve by editing the script and docs together |
| reliable root unit-test wrapper CLI | `scripts/qa/run_root_unit_tests_reliable.ps1` | developers, agents | repo-local script CLI | stabilizes local root test reruns | keep while Windows lock churn remains a practical issue |

### 2.6 Enforcement Coverage

| Risk | Rule Reference | Guard Type | File/Test |
|---|---|---|---|
| local workflow drifts back to ad hoc heavy reruns | `AGENTS.md` execution defaults, `CONTRIBUTING.md` verification guidance | docs + script entrypoint | new QA scripts plus docs |
| root unit-test retries hide real failures | verification honesty rules in `AGENTS.md` | wrapper logic + docs | wrapper retries only lock-signature failures once |

## 3) Data Flow

Before:

`developer or agent -> hand-written Gradle commands -> optional manual lock cleanup`

After:

`developer or agent -> named QA bundle or reliable root wrapper -> gradlew / recovery -> result`

## 4) Implementation Phases

### Phase 1

- Goal:
  - add repo-owned bundle runner and reliable root wrapper
- Files to change:
  - `scripts/qa/run_change_verification.ps1`
  - `scripts/qa/run_change_verification.bat`
  - `scripts/qa/run_root_unit_tests_reliable.ps1`
  - `scripts/qa/run_root_unit_tests_reliable.bat`
- Tests to add/update:
  - dry-run validation for the new scripts
- Exit criteria:
  - bundles are explicit and runnable
  - root wrapper retries only lock-style failures

### Phase 2

- Goal:
  - document the new workflow where developers and `xcpro-build` can discover it
- Files to change:
  - `docs/ARCHITECTURE/CONTRIBUTING.md`
  - `scripts/dev/README.md`
- Exit criteria:
  - repo docs distinguish fast loop, slice bundle, PR-ready, and release-ready paths

## 5) Test Plan

- Unit tests:
  - none; script dry-run validation is sufficient for this tooling slice
- Change-type coverage matrix:

| Change Type | Required Proof | Planned Evidence |
|---|---|---|
| Workflow / QA tooling | dry-run script validation + doc sync | bundle runner dry-run, reliable wrapper dry-run, docs updated |
| Rule / gate preservation | enforceRules | `./gradlew enforceRules` |

Required checks:

```bash
./gradlew enforceRules
```

## 6) Risks and Mitigations

| Risk | Impact | Mitigation | Owner |
|---|---|---|---|
| bundles become stale and misleading | Medium | keep bundles small, explicit, and documented in one script | XCPro Team |
| root wrapper masks real failures | High | retry only once and only on lock-signature errors | XCPro Team |

## 6A) ADR / Durable Decision Record

- ADR required: No
- Decision summary:
  - this is workflow tooling, not a long-lived architecture boundary
