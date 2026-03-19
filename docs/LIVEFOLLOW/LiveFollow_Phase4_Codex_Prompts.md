# LiveFollow Phase 4 — Codex Prompts

Use a **new Codex task/agent** for Phase 4.
Do Phase 4 in **two passes**:
1. seam / audit pass
2. implementation pass

---

## Pass 1 — seam / audit prompt

```text
Review the current LiveFollow state after Phase 3 and propose the repo-native Phase 4 hardening plan only.

Read and follow:
- docs/LIVEFOLLOW/XCPro_LiveFollow_Change_Plan_v9.md
- docs/LIVEFOLLOW/livefollow_v2.md
- docs/LIVEFOLLOW/LiveFollow_Next_Steps_v5.md
- docs/LIVEFOLLOW/PHASE4_REVIEW_CHECKLIST.md
- docs/ARCHITECTURE/PIPELINE.md
- AGENTS.md
- AGENT.md
- ARCHITECTURE.md
- CODING_RULES.md
- PIPELINE.md
- CODEBASE_CONTEXT_AND_INTENT.md
- CONTRIBUTING.md
- KNOWN_DEVIATIONS.md

Goal:
Design Phase 4 only: hardening and final app-side doc/runtime sync for LiveFollow.

Do not implement yet.

I want a short audit that answers:
1. What merged Phase 1/2/3 code should Phase 4 build on?
2. What exact unavailable-adapter, replay/privacy, lifecycle, and route hardening work is still needed?
3. Which issues can be proven with deterministic unit tests vs which truly need connected/instrumentation checks?
4. What exact docs must be updated in the same PR?
5. What should remain explicitly out of scope for Phase 4?

Constraints:
- No backend/network implementation
- No Retrofit/WebSocket implementation
- No FCM delivery implementation
- No background notification handlers
- No task ownership changes
- No second ownship pipeline
- No direct coordinator bypasses
- No UI-local source arbitration or freshness math
- No product expansion (share links, leaderboard/history, etc.)

Important:
- Use repo-native package/module conventions
- Keep the plan minimal and buildable
- Do not ask me to create files manually
- Do not create duplicate abstractions if suitable seams already exist
- Keep map/runtime render-only
- Keep unavailable transports explicit; no silent NoOps

Deliver:
- Proposed Phase 4 file/module ownership plan
- Exact hardening fixes/tests/docs to add
- Risks or architecture concerns
- A recommended implementation order
```

---

## Pass 2 — implementation prompt

```text
Implement Phase 4 for LiveFollow exactly according to the approved hardening plan.

Read and follow:
- docs/LIVEFOLLOW/XCPro_LiveFollow_Change_Plan_v9.md
- docs/LIVEFOLLOW/livefollow_v2.md
- docs/LIVEFOLLOW/LiveFollow_Next_Steps_v5.md
- docs/LIVEFOLLOW/PHASE4_REVIEW_CHECKLIST.md
- docs/ARCHITECTURE/PIPELINE.md
- AGENTS.md
- AGENT.md
- ARCHITECTURE.md
- CODING_RULES.md
- PIPELINE.md
- CODEBASE_CONTEXT_AND_INTENT.md
- CONTRIBUTING.md
- KNOWN_DEVIATIONS.md

Goal:
Implement ONLY Phase 4 hardening and final app-side doc/runtime sync for LiveFollow.

Allowed scope:
- explicit unavailable-adapter UX hardening
- route/lifecycle edge-case fixes
- replay/privacy hardening fixes if needed
- deterministic tests for risky runtime/UI behavior
- connected/instrumentation tests only where they prove behavior unit tests cannot prove well enough
- final doc/status sync that matches the merged runtime path

Forbidden:
- No backend/network implementation
- No Retrofit/WebSocket implementation
- No FCM delivery implementation
- No background notification handler work
- No share links
- No leaderboard/history
- No task ownership changes
- No second ownship pipeline
- No direct task manager/coordinator calls from UI/VM/Composables
- No map/runtime-owned session truth
- No ordinary OGN overlay/facade state as watch truth
- No UI-local identity matching, source arbitration, or freshness math
- No camera/gesture/animation policy changes

Requirements:
- Keep package/module structure repo-native
- Keep files <= 500 lines where practical
- Reuse merged Phase 1/2/3 seams instead of duplicating them
- Preserve monotonic ms usage and replay-side-effect blocking
- Keep map/runtime render-only
- Keep task truth external via exported task snapshot/use-case seam
- Keep unavailable transports explicit; no fake production behavior and no silent NoOps

Verification:
- ./gradlew enforceRules
- ./gradlew testDebugUnitTest
- ./gradlew assembleDebug
- ./gradlew connectedDebugAndroidTest only if Phase 4 changes behavior that truly requires device/runtime proof; if skipped, explain why

Deliver:
1. files created/changed
2. exact hardening fixes made
3. tests added
4. any architecture risks found
5. whether Phase 4 now passes the review checklist
6. exact docs updated in the same PR
```
