# refactor index

This folder is a large ledger of refactor plans, phased implementation packs,
and agent-execution contracts.

Important boundary:
- do not treat this folder as the authoritative architecture rulebook
- authoritative invariants remain in `../ARCHITECTURE/ARCHITECTURE.md`,
  `../ARCHITECTURE/CODING_RULES.md`, and `../../AGENTS.md`

Use this folder for:
- in-progress or historical refactor plans
- phased implementation packs
- targeted execution contracts for specific slices

Use topic folders first when a current domain-specific owner exists:
- `../ARCHITECTURE/`
- `../OGN/`
- `../ADS-b/`
- `../LIVEFOLLOW/`
- `../MAPSCREEN/`
- `../RACING_TASK/`

Archive:
- `archive/2026-04-doc-pass/README.md`
  - completed execution contracts and completed parent plans moved out of the
    active top level
