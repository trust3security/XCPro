# LIVEFOLLOW — Codex Implementation Brief
## Startup Chooser Slice

**Status:** Approved implementation brief for the next slice  
**Scope:** App entry UX only. Reuse existing live-follow flows.  
**Non-goal:** No new backend, transport, auth, or multi-pilot product work.

## Goal
Make the app open to a simple chooser with two clear paths:

- **Flying**
- **Friends Flying**

This slice is about turning already-proven pieces into a coherent product entry flow.

## Known-good existing pieces
Treat these as existing capability to reuse, not new scope:

- pilot sharing works
- stop sharing works
- single-pilot watch by session id works
- single-pilot watch by share code works
- server exposes an active-pilots list
- first Friends Flying bottom-sheet list slice already exists locally

## Baseline user journey to implement
1. App launches to a startup chooser.
2. User sees:
   - logo
   - **Flying**
   - **Friends Flying**
3. If user taps **Flying**:
   - enter the existing pilot-mode flow
   - auto-start sharing using the existing mechanism
   - open the existing map in flying mode
4. If user taps **Friends Flying**:
   - open the existing active public pilots list
   - keep the current bottom-sheet list implementation if it already exists
   - user selects one pilot
   - hand off into the existing single-pilot watch flow for that pilot

## Meaning of “Friends Flying” in this slice
For this slice, **Friends Flying** means only:

- show currently active public pilots
- show them in the current bottom-sheet list flow
- allow selection of one pilot
- open the existing single-pilot live watch screen/route

It does **not** mean:

- multi-glider map
- private friend graph
- invite-only visibility
- social features
- auth redesign
- server identity redesign
- task metadata work
- notifications / FCM
- new viewer state architecture

## Core implementation instructions
- Make the chooser the default app entry/start destination.
- Reuse the current navigation architecture and current working screens wherever possible.
- Keep the diff small and local.
- Do not change backend contracts or transport logic.
- Do not invent a new watch flow.
- Do not rename existing screens/routes unless that is required for integration.
- If the app currently launches straight into a map or prior home screen, replace that start behavior with the chooser and route into the existing downstream flows from there.

## Path-specific rules
### Flying
- Tapping **Flying** should immediately enter the existing pilot/flying flow.
- Sharing must **not** start on app launch.
- Sharing starts only after the user explicitly taps **Flying**.
- Use the current start-sharing path and current flying map screen.
- Do not add an extra preflight chooser screen.

### Friends Flying
- Tapping **Friends Flying** must not trigger sharing or pilot-mode side effects.
- Reuse the current active-pilots source and current bottom-sheet list if already implemented.
- Tapping a pilot must reuse the current single-pilot watch screen/route.
- Do not add multi-pilot map behavior.

## UX defaults for this slice
Keep these simple. Do not over-design.

- **Chooser UI:** logo + two primary actions only
- **No active pilots:** show a simple empty state like “No pilots flying right now”
- **Loading:** lightweight loading state only
- **Errors:** reuse existing app conventions if present
- **Stale pilots:** keep existing behavior; if stale state is already surfaced in the list/watch flow, preserve it
- **Permissions:** request pilot-only permissions only when entering the Flying path, not on app launch and not on the Friends Flying path

## Navigation / back-stack guidance
- The chooser becomes the root/start screen.
- Do not redesign downstream navigation beyond what is required to make the new start screen work cleanly.
- Preserve current behavior in the flying map and single-pilot watch flows unless a small integration change is required.

## Manual test cases
1. Cold launch opens the chooser, not directly the map/watch screen.
2. Chooser shows logo, **Flying**, **Friends Flying**.
3. Tapping **Flying**:
   - opens the existing flying flow
   - starts sharing automatically using the current mechanism
   - shows the existing map in flying mode
4. Tapping **Friends Flying**:
   - opens the active-pilots list/bottom sheet
   - does not start sharing
5. Selecting a pilot from the list opens the existing single-pilot watch flow.
6. If no pilots are active, the user sees a clean empty state.
7. Existing sharing/watch functionality continues to work without regression.

## Acceptance criteria
This slice is complete when:

- app launch goes to the startup chooser
- chooser shows logo + **Flying** + **Friends Flying**
- **Flying** opens the current pilot flow and auto-starts sharing
- **Friends Flying** opens the active pilot list
- selecting one pilot opens that pilot’s existing live watch flow
- no new backend endpoints are introduced
- no new transport abstractions are introduced
- no multi-pilot map behavior is introduced
- no sharing occurs until the user taps **Flying**

## Constraints / guardrails
- Prefer reuse over refactor.
- Prefer integration over invention.
- Keep this slice narrowly product-facing.
- Leave list polish and watched-pilot detail polish for later slices.

## Report back expected from Codex
When done, report back with:

- files touched
- a short summary of the integration approach chosen
- any assumptions made because of current code structure
- any unrelated issues noticed but intentionally left untouched
