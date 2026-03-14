# 05 — Test Plan

## 1. Test strategy

Use a layered test strategy:

1. unit tests for geometry + scoring primitives
2. integration tests for pilot state transitions
3. simulation tests for multi-pilot leaderboard behavior
4. UI/viewmodel tests for setup and leaderboard screens
5. reconciliation tests for live vs official log paths

## 2. Critical test matrix

| Area | What must be proven |
|---|---|
| Rules profile | FAI mode blocks invalid geometry; custom mode allows it with warnings |
| Start/finish | Crossings are detected correctly and interpolated where needed |
| Area achievement | In-zone fix and segment intersection both work |
| Credited fix optimization | Best credited-fix set is chosen across candidate windows |
| Marking distance | Finished, outlanded-last-leg, outlanded-earlier-leg each score correctly |
| Marking time/speed | Finishers use `max(elapsed, minimum)`; non-finishers have no speed |
| Handicap transforms | `Dh` and `Vh` are applied consistently |
| Day parameters | `Do`, `Vo`, `To`, `n1`, `n2`, `N`, `Pm`, `Pdm`, `Pvm`, `F`, `FCR` recompute correctly |
| Leaderboard | Official / provisional / projected ordering works |
| Finish closure | Airborne pilots convert to outlanded-at-closure correctly |
| Official reconciliation | Accepted FR logs replace live estimates and can change ranking |
| Setup page | Blocking warnings and publish rules behave correctly |

## 3. Unit test cases

## 3.1 Rules profile validation

### Case RP-01
- FAI profile
- start geometry = line
- assigned areas = circle + sector
- finish geometry = ring
- expected: valid

### Case RP-02
- FAI profile
- assigned area type = keyhole
- expected: invalid or blocked with explicit custom-profile requirement

### Case RP-03
- FAI profile
- start geometry = cylinder
- waiver ref missing
- expected: invalid / publish blocked

### Case RP-04
- custom profile
- keyhole or start sector selected
- expected: valid with warning badge

## 3.2 Area achievement

### Case AA-01
- a raw fix lies inside a circular area
- expected: area achieved

### Case AA-02
- no raw fix inside a sector, but the segment between two fixes intersects the sector
- expected: area achieved

### Case AA-03
- nearest approach misses area boundary
- expected: not achieved

## 3.3 Credited-fix optimizer

### Case CF-01
- one candidate per area
- expected: trivial selection

### Case CF-02
- multiple candidates in two adjacent areas
- expected: optimizer chooses combination with largest total credited distance, not necessarily area centers

### Case CF-03
- current area still active with live candidate updates
- expected: provisional credited fix set can change as better later-leg candidates appear

## 3.4 Marking distance

### Case MD-01 Finished
- valid start
- all assigned areas achieved
- valid finish
- expected: distance = start -> credited fixes -> finish, with start/finish geometry adjustments as applicable

### Case MD-02 Outlanded on last leg
- all areas achieved
- no finish
- expected: last-leg deduction from outlanding position to finish

### Case MD-03 Outlanded on earlier leg
- next required area not yet achieved
- expected: use nearest point on next assigned area for the unfinished leg treatment

## 3.5 Marking time and speed

### Case MT-01
- elapsed < minimum task time
- finisher
- expected: `T = minimum`

### Case MT-02
- elapsed > minimum task time
- finisher
- expected: `T = elapsed`

### Case MT-03
- outlanded
- expected: no marking time and no speed

## 3.6 Handicap transforms

### Case HC-01
- handicap disabled
- expected: `H = 1`

### Case HC-02
- handicap enabled
- expected: `Dh = D / H`, `Vh = V / H`

## 4. Integration tests

## 4.1 Single-pilot live progression

- pilot receives fixes
- start detected
- area 1 achieved
- area 2 achieved
- finish detected
- status evolves correctly
- provisional score exists before FR-log officialization

## 4.2 Pilot outlanding flow

- pilot starts and achieves part of task
- no finish
- landing/outlanding inferred
- provisional non-finisher distance score computed

## 4.3 Finish closure flow

- pilot still airborne at configured finish closure
- last fix before closure exists
- expected:
  - status becomes finish-closed-unfinished
  - pilot leaves projected bucket
  - non-finisher distance recomputed from closure outlanding point

## 5. Multi-pilot simulation tests

## 5.1 Mixed field

Simulate at least 5 pilots:

- Pilot A official finisher
- Pilot B provisional finisher
- Pilot C airborne under min time
- Pilot D airborne over min time
- Pilot E pending accounting

Expected:

- visible ranking excludes Pilot E
- Pilot A appears above provisional/projected rows if ranking metric ties
- projected rows use configured projection mode

## 5.2 Live day-parameter recomputation

Scenario:

- one pilot finishes fast
- one pilot finishes slower
- one pilot outlands with long distance
- one pilot still airborne

Expected:

- `Do`, `Vo`, `To`, `n1`, `n2`, `N` update after each major event
- projected classic scores move accordingly

## 5.3 Official reconciliation changes winner

Scenario:

- live tracker sparse for Pilot X, making live credited distance slightly low
- FR log later shows a better credited-fix set
- expected:
  - Pilot X official score increases
  - ranking changes
  - old live values preserved for audit/debug if supported

## 6. UI tests

## 6.1 Setup page

Verify:

- Alternative scoring unsupported -> publish disabled
- FAI profile + keyhole -> blocking issue shown
- handicaps missing when required -> publish disabled
- config hash displayed after save

## 6.2 Leaderboard UI

Verify:

- badges/labels render for official, provisional, projected
- pending-accounting rows hidden when configured
- sort order follows ranking rules
- confidence or low-data hint shows for sparse tracker projections if implemented

## 7. Suggested fixture set

Create reusable fixtures:

### Fixture F1 — Baseline 3-hour AAT
- line start
- 3 assigned areas:
  - circle
  - sector
  - circle
- ring finish
- minimum task time = 3h

### Fixture F2 — Sparse tracking
- fixes every 15 seconds
- area crossed by line segment but no sampled point inside

### Fixture F3 — Handicap-enabled class
- at least 3 pilots with different handicaps

### Fixture F4 — Finish closure
- closure time before all pilots are home

### Fixture F5 — Custom profile
- keyhole/start sector or other repo-specific custom geometry

## 8. Non-functional tests

### NFT-01 Performance
- 50 pilots
- 5 assigned areas
- leaderboard recompute stays within performance target

### NFT-02 Determinism
- same fix stream twice
- identical outputs and ranking order

### NFT-03 Main-thread safety
- no heavy scoring work runs on UI thread

## 9. Regression tests against existing docs

Add tests specifically to guard these corrections:

- current target points are not treated as final credited fixes
- marking speed formula is not mistaken for full classic day score
- non-standard geometry is blocked in FAI profile mode
- start cylinder without waiver is blocked in FAI profile mode

## 10. Final acceptance checklist

Before merge, verify:

- [ ] rules profile tests pass
- [ ] credited-fix optimizer tests pass
- [ ] single-pilot finished/outlanded tests pass
- [ ] multi-pilot leaderboard simulation passes
- [ ] finish closure test passes
- [ ] official reconciliation test passes
- [ ] setup-page validation test passes
- [ ] performance test is acceptable
