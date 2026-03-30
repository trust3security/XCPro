# XCPro close-proximity aircraft declutter brief

## Problem seen on screen
When several aircraft are physically close together, the current Phase 1 behavior is not enough. Zoom-band icon shrinking helps at medium and wide zoom, but at close zoom the map still allows too many labels and icons to sit on top of each other.

The screenshot shows the exact failure mode:
- many aircraft are within a very small pixel radius
- labels are all rendered at once
- icons and text overlap into one unreadable stack
- zooming in further will only partially help because the aircraft are genuinely close in map space

## Bottom line
Yes, this can be improved.

But this is not a Phase 1 tweak.
This needs a dedicated close-proximity declutter phase.

## What to change

### 1) Keep true coordinates, but fan out the visuals
Do **not** move the real aircraft positions in the data source.
Instead, when aircraft are within a small on-screen radius, compute temporary **display positions** for rendering only.

That means:
- hit-testing and details still map to the real aircraft
- traffic logic still uses real coordinates
- only the icon/label drawing is offset for readability

This is the safest way to improve readability without corrupting navigation meaning.

### 2) Create collision groups in screen space
At each refresh or camera change:
- project each visible aircraft to screen pixels
- group aircraft that are inside a configurable radius, for example `36dp` to `56dp`
- only apply fan-out to groups with `count >= 2`

This must be based on **screen pixels**, not meters.

### 3) Use spiderfy / fan-out for small groups
For a small collision group, place aircraft around the real center using a ring or arc.

Suggested defaults:
- 2 aircraft: opposite sides
- 3 to 5 aircraft: small circle
- 6 to 10 aircraft: larger circle
- 11+ aircraft: do not fully fan out all labels; combine with label suppression

Important:
- show a subtle leader line from display position back to the real position
- keep offsets small enough that the map still feels honest

### 4) Stop showing every label in a collision group
This is the real fix for the screenshot.

Inside a collision group:
- show a full label only for the highest-priority aircraft
- show compact markers for the others
- reveal the full label for a non-primary aircraft only when selected

Priority order should be something like:
1. selected target
2. alert / threat / conflict aircraft
3. nearest aircraft
4. aircraft with strongest climb/sink significance if that matters in XCPro
5. everything else

### 5) Introduce compact labels for non-primary aircraft
Instead of full text blocks for every aircraft, render a compact fallback, for example:
- callsign only, or
- distance only, or
- a numbered badge

Then on tap:
- expand the tapped aircraft to full label
- collapse the others back to compact mode

### 6) Add selection-driven expansion
When the user taps one aircraft in a packed group:
- pin that aircraft as primary
- give it the full label
- optionally increase its icon scale slightly
- optionally widen the fan-out radius for that group

That gives the user control without cluttering the whole stack.

### 7) Keep wide/medium zoom rules from Phase 1
Do not replace the zoom-band work.
Stack this new close-proximity logic on top of it.

Use this behavior:
- wide/medium zoom: current restyle-only zoom policy
- close zoom + no collision group: current normal rendering
- close zoom + collision group: fan-out + label admission control

### 8) Separate icons from labels in the policy
You need two independent decisions:
- icon placement strategy
- label admission strategy

That means a policy model like:
- `iconMode = NORMAL | FAN_OUT`
- `labelMode = HIDE_ALL | PRIMARY_ONLY | COMPACT_OTHERS | FULL_ALL`

For packed groups, `PRIMARY_ONLY` or `COMPACT_OTHERS` is what you want.

## Recommended implementation order

### Phase 2A: packed-group label control
Lowest risk and biggest visual gain.
- detect collision groups in screen space
- keep all icons at real coordinates for now
- show full label only for the primary aircraft in each group
- hide or compact the rest

This alone will make the screenshot much better.

### Phase 2B: icon fan-out / spiderfy
- add display-only offsets for packed groups
- add leader lines
- keep tap selection stable

### Phase 2C: selection-driven expansion
- tap one aircraft in a packed group
- make it primary
- expand full details for the selected aircraft

## What not to do
- do not move real aircraft coordinates in the underlying traffic model
- do not solve this by shrinking text forever
- do not show every label just because the user is zoomed in
- do not force source rebuilds on every tiny zoom change if a style update or display-layout recompute will do
- do not mix this with ADS-B reconnect policy work

## Success criteria
A packed group should become readable enough that:
- individual aircraft can be distinguished visually
- at least one label in the group is readable
- the user can tap the intended aircraft
- selected aircraft expands cleanly
- the map no longer turns into a black text knot like the screenshot

## Recommended next brief name
`xcpro-phase-2a-close-proximity-declutter-brief.md`

## Codex direction in one sentence
Implement screen-space collision grouping and primary-only label admission first; then add display-only spiderfy for packed groups without altering real aircraft coordinates.
