# LiveFollow Phase 3 â€” Codex Prompts

Use a **new Codex task/agent** for Phase 3.
Do Phase 3 in **two passes**:
1. seam / audit pass
2. implementation pass

---

## Pass 1 â€” seam / audit prompt

```text
Review the current LiveFollow state after Phase 2 and propose the repo-native Phase 3 UI/route wiring plan only.

Read and follow:
- docs/LIVEFOLLOW/XCPro_LiveFollow_Change_Plan_v8.md
- docs/LIVEFOLLOW/livefollow_v2.md
- docs/LIVEFOLLOW/LiveFollow_Next_Steps_v4.md
- docs/LIVEFOLLOW/PHASE3_REVIEW_CHECKLIST.md
- docs/ARCHITECTURE/archive/2026-04-11-root-cleanup/PIPELINE_LiveFollow_Addendum.md
- AGENTS.md
- AGENT.md
- ARCHITECTURE.md
- CODING_RULES.md
- PIPELINE.md
- CODEBASE_CONTEXT_AND_INTENT.md
- CONTRIBUTING.md
- KNOWN_DEVIATIONS.md

Goal:
Design Phase 3 only: ViewModel/UI/route wiring for LiveFollow.

Do not implement yet.

I want a short audit that answers:
1. What existing Phase 1 and Phase 2 code should Phase 3 build on?
2. What exact ViewModel/UI state holders/screens/routes should be added?
3. What exact pilot controls should be wired now?
4. What exact follower route / entry handling should be wired now?
5. What is the thinnest allowed map/task render-consumer path?
6. Which module(s) should own the new UI/route pieces?
7. What minimal app/navigation/DI wiring, if any, is needed?
8. What should remain explicitly out of scope for Phase 3?

Constraints:
- No backend/network implementation
- No Retrofit/WebSocket implementation
- No FCM delivery implementation
- No background notification handlers
- No task ownership changes
- No second ownship pipeline
- No direct coordinator bypasses
- No UI-local source arbitration or freshness math

Important:
- Use repo-native package/module conventions
- Keep the plan minimal and buildable
- Do not ask me to create files manually
- Do not create duplicate abstractions if suitable seams already exist
- Keep map/runtime render-only

Deliver:
- Proposed Phase 3 file/module ownership plan
- Exact ViewModel/UI/route pieces to add
- Risks or architecture concerns
- A recommended implementation order
```

---

## Pass 2 â€” implementation prompt

```text
Implement Phase 3 for LiveFollow exactly according to the approved seam/contract plan.

Read and follow:
- docs/LIVEFOLLOW/XCPro_LiveFollow_Change_Plan_v8.md
- docs/LIVEFOLLOW/livefollow_v2.md
- docs/LIVEFOLLOW/LiveFollow_Next_Steps_v4.md
- docs/LIVEFOLLOW/PHASE3_REVIEW_CHECKLIST.md
- docs/ARCHITECTURE/archive/2026-04-11-root-cleanup/PIPELINE_LiveFollow_Addendum.md
- AGENTS.md
- AGENT.md
- ARCHITECTURE.md
- CODING_RULES.md
- PIPELINE.md
- CODEBASE_CONTEXT_AND_INTENT.md
- CONTRIBUTING.md
- KNOWN_DEVIATIONS.md

Goal:
Implement ONLY Phase 3 viewer/pilot route wiring for LiveFollow.

Allowed scope:
- ViewModel/state holders for pilot and follower flows
- pilot start/stop controls wired to the existing session seam
- follower route / entry handling using existing session/watch state
- render-ready UI state mapping
- thin map-render consumption only
- exported task render snapshot/use-case seam consumption only
- deterministic VM/UI tests
- Compose/instrumentation tests only where they prove real Phase 3 behavior
- minimal app/navigation/DI wiring required to build and test

Forbidden:
- No backend/network implementation
- No Retrofit/WebSocket implementation
- No FCM delivery implementation
- No background notification handler work
- No task ownership changes
- No second ownship pipeline
- No direct task manager/coordinator calls from UI/VM/Composables
- No map/runtime-owned session truth
- No ordinary OGN overlay preference state as watch truth
- No UI-local identity matching, source arbitration, or freshness math

Requirements:
- Keep package/module structure repo-native
- Keep files <= 500 lines where practical
- Reuse Phase 1 and Phase 2 seams instead of duplicating them
- Preserve monotonic ms usage and replay-side-effect blocking
- Keep map/runtime render-only
- Keep task truth external via exported task snapshot/use-case seam

Verification:
- ./gradlew enforceRules
- ./gradlew testDebugUnitTest
- ./gradlew assembleDebug
- ./gradlew connectedDebugAndroidTest (if Phase 3 introduces meaningful UI/route behavior that requires it; if skipped, explain why)

Deliver:
1. files created/changed
2. ViewModel/UI/route pieces added
3. tests added
4. any architecture risks found
5. whether any docs should be updated after Phase 3
```

