# XCPro / OGN / FLARM integration handoff note

**Date:** 2026-03-19
**Audience:** implementation-planning agent
**Purpose:** clarify how Glider A should be sourced, identified, deduplicated, and displayed when XCPro, relay, FLARM, and OGN can all contribute data.

---

## 1) Bottom line

- Use **both transmission paths**, but only **one aircraft identity**.
- **XCPro should always send enriched task-aware data to the relay.**
- **FLARM should continue broadcasting over RF normally.**
- **XCPro may also publish to OGN**, but only under the **same airborne radio identity** as the glider's onboard FLARM / ICAO identity.
- When a follower is viewing **Glider A**:
  - **relay = primary / canonical source** for Glider A
  - **OGN = background traffic overlay** for everyone else
  - **OGN = fallback / backfill** for Glider A only if relay data goes stale
- The app/backend must **suppress the duplicate OGN representation of Glider A**.

This means the answer to **"XCPro or FLARM or both?"** is:

> **Both can exist, but they must collapse into one aircraft identity and one displayed Glider A object.**

---

## 2) What is confirmed by external docs

### 2.1 App + OGN behavior

Naviter states that SeeYou Navigator / Oudie relays its own position to OGN and receives positions of others back through OGN. It also warns that when flying with another FLARM device, the default setup can report the aircraft **twice** â€” once with the app/device ID and once independently through the FLARM. Naviter's mitigation is to enter the glider registration so the system can resolve / use the corresponding FLARM ID.
**Implication:** the duplicate-aircraft problem is real and already seen in shipping OGN-integrated products.

### 2.2 Phone / internet path can preserve FLARM identity

Naviter also states that combining phone location with OGN location lets Navigator transmit the glider's **FLARM ID** to OGN when the glider is out of reach of the OGN ground-station network.
**Implication:** an app can keep the same aircraft identity when using the internet path as a coverage extension.

### 2.3 FLARM identity is not arbitrary

FLARM's configuration spec says the radio-broadcast ID can be:
- the official **ICAO 24-bit aircraft address**
- `FFFFFF` = a **constant unique FLARM-ID**
- `0` = **random ID mode**

The same spec warns that random ID changes at startup / during transmission and diminishes or disables tracking and SAR capability.
**Implication:** do **not** build canonical tracking around random IDs.

### 2.4 FLARM interface exposes multiple identity types

FLARM's data-port ICD says the target ID field can represent either:
- an **ICAO 24-bit address**, or
- a **FLARM-generated ID**

depending on target type.
**Implication:** the XCPro data model should be able to store **ID type + ID value**, not just a single undifferentiated string.

### 2.5 Hybrid multi-source designs are already used in practice

Naviter also describes a hybrid model where internet-connected devices can combine FLARM / FANET / OGN data and, where there is no OGN ground coverage, act as a "portable ground station" relaying traffic to OGN servers.
**Implication:** a relay + RF + OGN hybrid architecture is sensible; the key issue is identity fusion and source priority.

---

## 3) Interpretation for XCPro

From the above, the safest architecture is:

1. **Do not let XCPro create an independent second aircraft identity in OGN** when the glider already has onboard FLARM.
2. **Bind XCPro to the same glider radio identity** used by the onboard FLARM / ICAO identity.
3. In follower mode:
   - **task state, task progress, and canonical snail trail** should come from the **relay**
   - **OGN** should be used for:
     - other aircraft overlay
     - fallback / backfill for Glider A if relay becomes stale
4. A glider should be labeled **"same task"** only if that comes from XCPro / relay competition data or another verified task source.
   **Do not infer same-task participation from OGN alone.**

---

## 4) Recommended architecture

### 4.1 Transmission paths

1. **FLARM -> RF -> nearby aircraft + OGN ground stations**
2. **XCPro -> internet -> relay** (**always**)
3. **XCPro -> internet -> OGN** (**optional**, but only using the **same glider identity** as #1)

### 4.2 Source-of-truth by use case

#### Followed glider (Glider A)
- **Primary:** relay
- **Secondary fallback:** OGN

#### Other traffic
- **Primary:** OGN
- **Optional enrichment:** relay only if those aircraft are also XCPro participants

### 4.3 Where dedupe should happen

Do **not** rely on OGN alone to solve the UX duplicate problem.

Perform deterministic dedupe in the **fusion layer**:
- **server-side preferred**
- **client-side acceptable as backup**

---

## 5) Identity model

Store one canonical aircraft identity with aliases.

```yaml
glider_uuid: internal stable UUID

radio_identity:
  type: icao24 | flarm_id
  value: "6-hex-chars"

known_aliases:
  - type: icao24
    value: "optional"
  - type: flarm_id
    value: "optional"
  - type: registration
    value: "optional"
  - type: comp_no
    value: "optional"

source_bindings:
  relay_pilot_id: "optional"
  relay_device_id: "optional"
  ogn_address: "optional"
```

### Notes

- `radio_identity.type + radio_identity.value` is the **primary dedupe key**.
- Store **both ICAO and FLARM ID** when known.
- Registration and competition number are useful aliases, but should **not** be the sole primary key.
- If XCPro cannot automatically read the FLARM / ICAO identity, support:
  1. **manual entry**
  2. **registration-based lookup**
  3. **explicit admin override**
- If available, an OGN DDB registration can help map registration -> FLARM ID, but the app should still keep its own canonical mapping.

---

## 6) Deduplication rules

### Core rule

If an OGN target matches Glider A's canonical radio identity (or a trusted alias that resolves to that identity), then:

- **do not render a second aircraft icon**
- treat the OGN target as **the same aircraft**
- use it only as **redundancy / backfill / fallback**

### Practical clustering rule

Cluster tracks into one aircraft object when they match on any trusted identity path:

1. exact `icao24`
2. exact `flarm_id`
3. explicit alias mapping maintained in backend
4. registration + confirmed alias mapping
   (use with caution, never as the only trust path)

### Important guardrail

Do **not** use loose spatial proximity alone to dedupe the followed glider.
Two gliders can be very close on course or in the same thermal.

---

## 7) Source-priority / freshness state machine

A reasonable starting proposal:

### Relay source states for Glider A

- **Fresh:** `age <= 12s`
  - relay fully authoritative
  - OGN duplicate hidden
- **Degraded:** `12s < age <= 30s`
  - keep relay object
  - may optionally blend in newer OGN positions for smoothing, but do **not** split into a second object
- **Stale:** `age > 30s`
  - if OGN has a fresher matching track, switch the **same internal object** to OGN-backed positioning
- **Lost:** both relay and OGN stale
  - keep last known position briefly, then mark lost / unavailable

### Why one internal object matters

When switching source:
- keep the **same Glider A object id**
- keep the **same task context**
- keep the **same snail trail**

The user should never perceive a "relay aircraft" disappearing and an "OGN aircraft" appearing as a different glider.

> These thresholds are a **starting point**, not a final requirement. They should be tuned after field testing.

---

## 8) Follower-mode display rules

When a follower user is viewing **Glider A**:

### Must do
- Show **one** Glider A icon only
- Use **relay data first** while fresh
- Show **other OGN traffic** normally, including aircraft that do **not** run XCPro
- Suppress the duplicate OGN representation of Glider A
- Preserve a continuous trail and task view if source switches from relay to OGN

### Should do
- Expose a subtle source status internally or in debug tools:
  - `relay`
  - `relay_degraded`
  - `ogn_fallback`

### Should not do
- Do not show **two Glider A icons**
- Do not let OGN overwrite relay task-state semantics while relay is healthy
- Do not mark another OGN aircraft as "same task" unless there is a verified task / competition link

---

## 9) Snail trail strategy

The snail trail for Glider A should be keyed by **canonical glider identity**, not by source.

### Recommended behavior

- Store trail against `glider_uuid`
- Append **relay points** normally
- If relay goes stale and matching OGN data exists:
  - append OGN points as fallback / backfill
  - keep them attached to the **same trail**
- When relay resumes:
  - continue appending to the **same trail**
- Do not split the trail into:
  - "relay trail"
  - "OGN trail"

### Internal metadata

It may still be useful to tag trail points internally:

```yaml
trail_point:
  timestamp: ...
  lat: ...
  lon: ...
  altitude: ...
  source: relay | ogn
  confidence: normal | degraded
```

This gives debugging visibility without fragmenting the user-facing trail.

---

## 10) Edge cases

### 10.1 Random FLARM ID
If the onboard FLARM is in random-ID mode:
- do **not** treat that ID as a stable canonical identity
- warn / flag configuration issue
- prefer a stable ICAO or stable FLARM-ID mapping if available

### 10.2 Glider with no onboard FLARM
If XCPro is the only source:
- relay can still be canonical
- XCPro may publish to OGN with its own aircraft identity
- but the same "one aircraft object" rule still applies

### 10.3 Identity mismatch
If relay says one identity and OGN says another for what appears to be the same glider:
- do **not** auto-merge unless there is a trusted alias mapping
- surface as a configuration issue

### 10.4 Two devices in the cockpit
If multiple internet devices are present:
- only one should act as the aircraft's OGN publisher, or
- all must share the same aircraft identity and dedupe token

### 10.5 Same registration, different data quality
Registration alone is not strong enough to merge live tracks without a validated mapping.

---

## 11) Concrete implementation tasks

### Data model
- add `radio_id_type`
- add `radio_id_value`
- add `icao24`
- add `flarm_id`
- add `registration`
- add `comp_no`
- add backend alias table

### Fusion logic
- add track clustering by canonical identity
- add relay-vs-OGN source-priority rules
- add stale / degraded state machine
- add duplicate suppression for followed glider

### UI / UX
- render only one Glider A object
- maintain one continuous trail
- preserve task context when source switches
- optionally expose source health in debug UI

### Config / onboarding
- allow manual entry of ICAO / FLARM ID
- support registration-based resolution when available
- validate that XCPro is not publishing as a second independent aircraft identity

### Testing
- same glider visible via:
  - relay only
  - OGN only
  - both relay + OGN
- no duplicate icon for Glider A
- source switch does not break trail
- source switch does not reset task progress context

---

## 12) Checklist for comparing against the other agent's plan

The other plan should ideally say **YES** to all of these:

- Does it treat **relay as primary** for the followed glider?
- Does it treat **OGN as overlay + fallback**, not as the main source for Glider A?
- Does it require **one canonical aircraft identity** across relay / FLARM / OGN?
- Does it store **ID type + ID value**, not just a single ambiguous ID?
- Does it prevent **two Glider A icons** when both FLARM and app uploads exist?
- Does it preserve **one continuous snail trail** across source switches?
- Does it avoid inferring **same task** from OGN alone?
- Does it support **manual / verified mapping** of ICAO and FLARM ID into XCPro?

If any of those answers is **NO**, the implementation plan probably needs updating.

---

## 13) Recommended decision wording for the implementation plan

Use wording close to this:

> XCPro shall always send enriched task-aware tracking to the relay.
> FLARM shall continue its normal RF broadcast.
> XCPro may also publish to OGN, but only using the same canonical aircraft radio identity as the onboard FLARM / ICAO identity.
> In follower mode, the relay shall be the primary source for the followed glider, while OGN shall provide background traffic overlay and fallback/backfill if relay data becomes stale.
> The system shall fuse relay and OGN tracks into a single aircraft object and shall never display the followed glider twice.

---

## 14) Sources

1. **Naviter Knowledge Base â€” "Open Glider Network (OGN) & Live Tracking"**
   Confirms:
   - Navigator uploads its position to OGN and receives other traffic from OGN
   - duplicate reporting can happen when another FLARM device is present
   - entering glider registration can resolve / use the FLARM ID
   - phone location can continue transmitting the glider's FLARM ID when outside OGN ground-station range
   URL: https://kb.naviter.com/en/kb/open-glider-network-ogn/

2. **FLARM Configuration Specification (FTD-014)**
   Confirms:
   - ID can be official ICAO 24-bit address
   - `FFFFFF` is a constant unique FLARM-ID
   - `0` is random ID mode
   - random ID hurts tracking / SAR
   URL: https://www.flarm.com/wp-content/uploads/2024/04/FTD-014-FLARM-Configuration-Specification-1.16.pdf

3. **FLARM Data Port Interface Control Document (FTD-012)**
   Confirms:
   - target ID field may represent ICAO 24-bit or FLARM-generated ID depending on target type
   URL: https://www.flarm.com/wp-content/uploads/2024/04/FTD-012-Data-Port-Interface-Control-Document-ICD-7.19.pdf

4. **Naviter â€” "Hybrid Live-tracking"**
   Useful context for multi-source / hybrid tracking and portable ground-station behavior
   URL: https://naviter.com/2023/03/hybrid-live-tracking/

---

## 15) Confidence statement

I am confident in the **identity / dedupe / source-priority direction** above.

The parts that remain design choices rather than externally proven facts are:
- the exact freshness thresholds (`12s`, `30s`, etc.)
- whether OGN publication should happen directly from XCPro or be relay-mediated
- the final UI treatment of degraded / fallback state

Those should be treated as **implementation proposals**, not protocol facts.
