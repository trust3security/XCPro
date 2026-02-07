> NOTICE (2026-02-06): Task refactor plan is documented in $plan. Review before implementing task-related changes.

# How to Run Codex in This Repo

This repo uses a strict two-file execution model.

## Required Files
- docs/AGENT/AGENT_RELEASE.md
- docs/AGENT/TASK.md

These are the ONLY files passed to Codex.

## CLI Usage

codex agent \
  --instructions docs/AGENT/AGENT_RELEASE.md \
  --input docs/AGENT/TASK.md

## Interactive Usage

Execute TASK.md under AGENT_RELEASE.md.
Do not ask questions.
Run until completion.

## Golden Rules
- One agent law
- One task spec
- Plans referenced, not duplicated

