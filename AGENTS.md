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

1. `docs/LevoVario/levo.md`
2. `docs/LevoVario/levo-replay.md`

## Non-Negotiables

- Preserve MVVM + UDF + SSOT layering.
- Respect dependency direction and module boundaries.
- Keep business/domain logic out of UI.
- Use injected clocks/time sources in domain/fusion paths.
- Do not introduce hidden global mutable state.
- Keep replay deterministic.
- If architecture rules are violated, fix the code or record a time-boxed
  exception in `docs/ARCHITECTURE/KNOWN_DEVIATIONS.md` (issue ID, owner, expiry).

## Documentation Sync Rules

- If pipeline wiring changes, update `docs/ARCHITECTURE/PIPELINE.md`.
- If rules/policies change, update `docs/ARCHITECTURE/ARCHITECTURE.md` and/or
  `docs/ARCHITECTURE/CODING_RULES.md`.
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
./gradlew connectedDebugAndroidTest
```

Windows convenience:

```bat
preflight.bat
```

## Legacy Note

`docs/ARCHITECTURE/README_FOR_CODEX.md` is kept as a pointer for older flows.
This `AGENTS.md` file is the primary agent contract.
