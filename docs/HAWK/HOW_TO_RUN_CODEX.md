# How to Run Codex in This Repo (HAWK)

This repo uses a strict two-file execution model.

## Required Files
- docs/HAWK/AGENT_RELEASE.md
- docs/HAWK/TASK.md

These are the ONLY files passed to Codex.

## Single Plan
- docs/HAWK/Agent-Execution-Contract-HAWK.md

TASK.md must reference this plan. Do not duplicate it.

## CLI Usage
codex agent \
  --instructions docs/HAWK/AGENT_RELEASE.md \
  --input docs/HAWK/TASK.md

## Interactive Usage
Execute TASK.md under AGENT_RELEASE.md.
Do not ask questions.
Run until completion.

## Golden Rules
- One agent law
- One task spec
- One plan referenced, not duplicated

