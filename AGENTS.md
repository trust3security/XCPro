# AGENTS.md

## Purpose

This is the canonical instruction entrypoint for coding agents in this repo.
It defines what must be read first, what must never be violated, and what
checks are required before considering work complete.

## Mandatory Read Order

Read these files in order before making changes:

1. `docs/ARCHITECTURE/ARCHITECTURE.md`
2. `docs/ARCHITECTURE/CODING_RULES.md`
3. `docs/ARCHITECTURE/PIPELINE.md`
4. `docs/ARCHITECTURE/CODEBASE_CONTEXT_AND_INTENT.md`
5. `docs/ARCHITECTURE/CONTRIBUTING.md`
6. `docs/ARCHITECTURE/KNOWN_DEVIATIONS.md`

If touching variometer/replay pipeline behavior, also read:

1. `docs/LEVO/levo.md`
2. `docs/LEVO/levo-replay.md`

## Non-Negotiables

- Preserve MVVM + UDF + SSOT layering.
- Respect dependency direction and module boundaries.
- Keep business/domain logic out of UI.
- Use injected clocks/time sources in domain/fusion paths.
- Do not introduce hidden global mutable state.
- Keep replay deterministic.
- If architecture rules are violated, fix the code or record a time-boxed
  exception in `docs/ARCHITECTURE/KNOWN_DEVIATIONS.md` (issue ID, owner, expiry).

## Feature Implementation Defaults

When implementing a feature, agents must keep ownership explicit and file
responsibilities narrow.

- Target `<= 450` lines per file when practical. The repo-wide enforced default
  remains `<= 500`; do not treat `450` as permission to ignore the hard cap.
- Split code by responsibility before a file becomes large. Prefer several
  focused files over one mixed-responsibility file.
- Reuse existing feature structure and naming before introducing new patterns.
- Identify the authoritative owner for each new piece of state before editing.

Ownership defaults:
- UI owns rendering, user input forwarding, and display-only logic.
- ViewModels own screen state, intent handling, and orchestration.
- Use cases/domain classes own business rules, calculations, and policy.
- Repositories own authoritative data coordination and persistence-facing state.
- Data sources/adapters own API, database, device, and file I/O.
- Mappers own model transformations between layers.

Implementation expectations:
- Do not put business logic in Composables, Activities, Fragments, adapters, or
  other UI classes.
- Do not bypass layers for convenience.
- Before touching runtime-heavy code, inspect existing use of `CoroutineScope(`,
  direct `Log.*`, `UUID.randomUUID()`, `TimeBridge.nowWallMs()`, `NoOp`, and
  compatibility shims so new code follows the established or corrected policy.
- When adding a new Kotlin `object`, singleton-like holder, or convenience
  constructor, state why it will not become a hidden state owner, service
  locator, or silent production fallback path.
- Before editing, state which files will be created or changed and what each
  file will own.
- After editing, summarize file ownership so review can confirm boundaries.

## Task Execution Template (Per-Change)

For autonomous feature/refactor work, start from:

- `docs/ARCHITECTURE/AGENT.md`

This ensures phased execution, acceptance criteria, required checks, and a mandatory quality rescore.

## Documentation Sync Rules

- If pipeline wiring changes, update `docs/ARCHITECTURE/PIPELINE.md`.
- If rules/policies change, update `docs/ARCHITECTURE/ARCHITECTURE.md` and/or
  `docs/ARCHITECTURE/CODING_RULES.md`.
- If ownership boundaries, module APIs, or long-lived architecture tradeoffs
  change, add or update an ADR using `docs/ARCHITECTURE/ADR_TEMPLATE.md`.
- For non-trivial feature/refactor work, start from
  `docs/ARCHITECTURE/CHANGE_PLAN_TEMPLATE.md` before implementation.
- Do not rely on unstated assumptions; document intent in-repo.

## Required Verification

Run these checks locally for non-trivial changes:

```bash
./gradlew enforceRules
./gradlew testDebugUnitTest
./gradlew assembleDebug
```

Run when relevant (device/emulator available):

```bash
./gradlew :app:connectedDebugAndroidTest --no-parallel "-Pandroid.injected.androidTest.leaveApksInstalledAfterRun=true"
```

Run for release/CI verification (full multi-module instrumentation):

```bash
./gradlew connectedDebugAndroidTest --no-parallel
```

Windows convenience:

```bat
preflight.bat
```

PR hygiene:
- Use `.github/pull_request_template.md` and complete the architecture drift checklist.

## Legacy Note

`docs/ARCHITECTURE/README_FOR_CODEX.md` is kept as a pointer for older flows.
This `AGENTS.md` file is the primary agent contract.
