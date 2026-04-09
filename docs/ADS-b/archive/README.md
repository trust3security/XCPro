# ADS-B Archive

Archived on 2026-03-28 to keep `docs/ADS-b/` focused on active runtime and
change-plan docs.

## Archived files

1. `AGENT_AUTOMATION_CONTRACT_ADSB_POSITION_FRESHNESS_PHASED_EXECUTION_2026-03-10.md`
2. `AGENT_CONTRACT_ADSB_DEFAULT_ICON_PHASE_EXECUTION_2026-03-08.md`
3. `EXECUTION_LOG_ADSB_POSITION_FRESHNESS_PHASED_EXECUTION_2026-03-10.md`
4. `XCPro_ADSB_Blunt_Codex_Keep_Refactor_DoNotRewrite_2026-03-16.md`
5. `XCPro_ADSB_Codex_Execution_Order_2026-03-16.md`
6. `ADSB_Improvement_Plan.md`

## Why these moved

- They are one-off agent execution or prompt-steering documents, not durable
  ADS-B runtime/reference contracts.
- `ADSB_Improvement_Plan.md` is a completed historical plan; active ADS-B work
  now routes through the dated `CHANGE_PLAN_*` docs in the parent folder.
- Repo-level `AGENTS.md` and `docs/ARCHITECTURE/AGENT.md` now own the active
  agent contract.
- Keeping them out of the active folder reduces drift and makes the current
  ADS-B path easier to scan.

These files are retained as history. Their internal links may still point to
the old active-folder paths because they describe historical runs.
