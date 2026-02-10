# TASK.md — XCPro OGN Live Traffic (50 km)

## Objective
Implement live Open Glider Network (OGN) traffic display within a 50 km radius
around ownship, rendered on the map, with connection status, filtering,
and privacy-safe target details.

This implementation must be complete, deterministic, and architecture-compliant.

---

## Authoritative Plan (MUST EXECUTE)
- docs/ogn/OGN_PROTOCOL_NOTES.md

---

## Constraints (NON-NEGOTIABLE)
- docs/RULES/ARCHITECTURE.md
- docs/RULES/CODING_RULES.md
- docs/RULES/PIPELINE.md
- docs/RULES/KNOWN_DEVIATIONS.md

OGN traffic is:
- informational only
- NOT used for navigation, task logic, collision avoidance, or scoring

---

## Scope
IN SCOPE:
- Live OGN traffic ingestion
- 50 km radius filtering
- Stale eviction
- Map overlay rendering
- Toggle + connection status
- Tap details (callsign/id, distance, age, altitude, climb)

OUT OF SCOPE:
- Replay integration (default OFF in replay)
- Persistence of raw traffic history
- Any influence on XCPro flight logic

---

## Execution Rules
- Follow AGENT_RELEASE.md without exception.
- Do NOT ask questions unless execution is impossible.
- If protocol ambiguity exists:
  - Implement conservative behavior
  - Document assumptions in OGN_PROTOCOL_NOTES.md
- Preserve performance and battery characteristics.
- Respect OGN privacy expectations.

---

## Required Checks (MUST PASS)
- ./gradlew testDebugUnitTest
- ./gradlew lintDebug
- ./gradlew assembleDebug

---

## Stop Condition
STOP ONLY when ALL are true:
- OGN traffic is visible on map within 50 km
- Connection state is exposed and rendered
- Targets update, expire, and evict correctly
- All required checks pass
- No undocumented deviations remain
