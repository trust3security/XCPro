# Track-Up Mode Documentation

## Overview
Track-Up mode is a map orientation mode designed for intuitive navigation where the map rotates to keep your direction of travel pointing toward the top of the screen. This is the preferred mode for active navigation during flight.

## Fixed Icon Positioning - The Fundamental Principle

### "You Are Here" Concept
In a professional gliding/aviation app, the aircraft icon represents **YOU** at a **fixed position on the screen**. This fundamental principle is critical for proper navigation:

1. **You're sitting in the cockpit** - you don't move around the screen
2. **The world moves beneath you** - the map slides under your fixed position
3. **Your icon is like a pin stuck through the screen** - it never moves from its spot

### Why Fixed Icon Positioning Matters

#### Spatial Awareness
- You always know where you are (center of screen or configured position)
- No need to search for your position on the map
- Instant orientation and situational awareness

#### Forward Visibility
- Can see what's ahead by looking up from icon
- Icon typically positioned at 65% down from top for optimal forward view
- Matches natural pilot scanning pattern

#### Intuitive Navigation
- Matches the real experience of flying over terrain
- You stay still, the world moves past you
- Natural for following routes and navigating

### What Should Happen vs Common Implementation Errors

#### ❌ WRONG Implementation (Icon Moves on Map):
```
Screen View:
┌─────────────────┐
│  [Map stays]    │
│                 │
│    ✈️ →         │  Icon moves across map
│                 │
│                 │
└─────────────────┘
```

#### ✅ CORRECT Implementation (Icon Fixed, Map Moves):
```
Screen View:
┌─────────────────┐
│  [Map slides ←] │
│                 │
│       ✈️        │  Icon stays at fixed position
│                 │
│                 │
└─────────────────┘
```

### Technical Implementation

The aircraft icon should:
- **NOT** have geographic coordinates (lat/lng)
- **NOT** be a map layer element
- **BE** a screen overlay at fixed pixel coordinates
- **BE** drawn at the camera's center position

The map camera should:
- **Follow** the GPS position continuously
- **Offset** slightly for forward visibility
- **Move** smoothly under the fixed icon

## How Track-Up Mode Works

### Core Behavior

1. **Map Rotation**
   - The map continuously rotates so your direction of travel points UP
   - As you turn, the map rotates underneath you
   - Terrain, waypoints, and features rotate around your position

2. **Glider Icon Behavior**
   - **Position**: Glider icon stays centered on screen (with slight offset toward bottom for better forward visibility)
   - **Rotation**: Icon ALWAYS points straight up (does not rotate)
   - The glider represents YOU moving forward into the screen

3. **Camera Behavior**
   - Camera continuously follows your GPS position
   - Map center is locked to your location
   - Zoom level can be adjusted but position stays centered

### Visual Experience

#### While Flying Straight
```
    ↑ [Glider Icon]
    |
    | (Your direction)
    |
[Waypoints and terrain scroll down]
```

#### While Turning Right
```
    ↑ [Glider Icon - stays pointing up]

[Map rotates left, waypoints slide left]
```

#### While Turning Left
```
    ↑ [Glider Icon - stays pointing up]

[Map rotates right, waypoints slide right]
```

## Comparison with Other Modes

### Fixed Icon Principle Applies to ALL Modes
The fundamental principle of fixed icon positioning applies to all three orientation modes. The icon should ALWAYS stay at a fixed screen position - only its rotation and the map behavior change:

### North-Up Mode
- **Map**: Fixed with north always at top, slides beneath fixed icon
- **Glider Icon Position**: FIXED at screen center/offset
- **Glider Icon Rotation**: Rotates to show your heading/track
- **Use Case**: Planning, overview, traditional navigation
- **Mental Model**: You rotate relative to the world

### Track-Up Mode
- **Map**: Rotates with your track always at top, slides beneath fixed icon
- **Glider Icon Position**: FIXED at screen center/offset
- **Glider Icon Rotation**: Fixed pointing up (0°)
- **Use Case**: Active navigation, following routes
- **Mental Model**: The world rotates around you

### Heading-Up Mode
- **Map**: Rotates with device magnetic heading at top, slides beneath fixed icon
- **Glider Icon Position**: FIXED at screen center/offset
- **Glider Icon Rotation**: Shows difference between track and heading (drift angle)
- **Use Case**: Understanding wind drift, compass navigation
- **Mental Model**: Where the nose points vs where you're going

### Key Point: Icon Never Moves on Screen
Regardless of mode, the aircraft icon should:
- Stay at the same screen position (e.g., center X, 65% down Y)
- Only change its rotation based on mode
- Never move across the map as coordinates change
- Act as a fixed reference point for the pilot

## Why Track-Up for Navigation

### Intuitive Direction
- **Left on screen = Turn left in reality**
- **Right on screen = Turn right in reality**
- No mental rotation needed

### Natural Path Following
- The route ahead naturally unfolds at the top of the screen
- Upcoming waypoints appear in the direction you need to turn
- Easy to see if you're left or right of course

### Reduced Cognitive Load
- Critical during turbulence or high workload
- Instant spatial awareness
- Natural for following roads, valleys, or task routes

## Technical Implementation

### GPS Track Calculation
- Primary: Uses Android Location bearing when available
- Fallback: Calculates bearing between consecutive GPS points
- Smoothing: Applied to prevent jumpy rotation

### Speed Threshold
- Minimum speed: 2 knots (3.7 km/h)
- Below threshold: Maintains last valid bearing
- Prevents erratic behavior when stationary

### Camera Management
- Continuous position updates from GPS
- Smooth animation transitions
- User panning temporarily disables tracking (return button appears)

### Glider Icon Rotation
```kotlin
// Track-Up Mode
iconRotation = 0  // Always points up

// North-Up Mode
iconRotation = gpsTrack  // Rotates with heading

// Heading-Up Mode
iconRotation = gpsTrack - magneticHeading  // Shows drift
```

## Glider Icon Orientation Details

### Understanding Icon Behavior

The glider icon serves as a visual indicator of your aircraft's orientation and movement. Its behavior changes based on the selected map orientation mode to provide the most useful information for navigation.

#### NORTH-UP Mode
**Map Behavior**: Fixed with north always at the top
**Icon Behavior**: Rotates freely (0-360°) to show actual GPS track
**What It Shows**: Your actual direction of travel relative to north
**Visual Example**:
```
        N
        ↑
    W ← + → E      [If traveling northeast]
        ↓              ↗ (icon points ~45°)
        S
```
**Use Case**: Traditional navigation, planning, understanding geographic orientation

#### TRACK-UP Mode
**Map Behavior**: Rotates to keep your direction of travel pointing up
**Icon Behavior**: LOCKED at 0° (always points straight up)
**What It Shows**: You're always moving "forward" into the screen
**Visual Example**:
```
    [Your Direction]
          ↑
      [Aircraft]     (icon always points up)
          ↑
    [Map rotates]    (world turns beneath you)
```
**Use Case**: Active navigation, following routes, intuitive left/right guidance

#### HEADING-UP Mode
**Map Behavior**: Rotates based on magnetic compass heading
**Icon Behavior**: Shows drift angle (track - heading)
**What It Shows**: Wind drift visualization
- **Icon straight up (0°)**: No drift, track matches heading
- **Icon tilted right**: Drifting right of heading (wind from left)
- **Icon tilted left**: Drifting left of heading (wind from right)

**Visual Examples**:
```
No Wind:              West Wind:           East Wind:
    ↑                     ↑                    ↑
[Aircraft]            [Aircraft]↗          [Aircraft]↖
    ↑                  (drift right)       (drift left)
Track = Heading       Track > Heading      Track < Heading
```

### Why Different Icon Behaviors?

1. **NORTH-UP**: Icon rotation shows your heading - essential for geographic awareness
2. **TRACK-UP**: Fixed icon reduces visual complexity - you focus on the route ahead
3. **HEADING-UP**: Drift angle visualization helps understand wind effects

### Technical Implementation

The icon uses `iconRotationAlignment("viewport")` which means:
- Icon rotates relative to the screen/viewport, not the map
- Prevents double rotation in TRACK-UP mode
- Ensures consistent visual reference

### Practical Applications

#### For Cross-Country Soaring
- **NORTH-UP**: Use at turnpoints to understand geographic position
- **TRACK-UP**: Use between turnpoints for route following
- **HEADING-UP**: Use to assess wind drift and optimize speed-to-fly

#### For Thermal Flying
- **NORTH-UP**: Preferred - stable reference while circling
- **TRACK-UP**: Can be disorienting in thermals due to constant rotation
- **HEADING-UP**: Useful for drift assessment while thermalling

#### For Final Glide
- **TRACK-UP**: Ideal - destination stays at top of screen
- **HEADING-UP**: Shows if you're crabbing due to crosswind
- **NORTH-UP**: Good for awareness of terrain and airspace

## User Controls

### Switching Modes
- Tap compass widget to cycle: North → Track → Heading → North
- Current mode shown on compass widget
- Smooth transition between modes

### Manual Override
- Pan the map to temporarily disable tracking
- "Return" button appears to re-center
- Zoom adjustments don't affect tracking

## Troubleshooting

### Track-Up Not Working?

1. **Check GPS Signal**
   - Need GPS fix for track calculation
   - Poor signal = no bearing data

2. **Check Speed**
   - Must be moving > 2 knots
   - Stationary = no track updates

3. **Check Mode Selection**
   - Verify "TRACK UP" shown on compass
   - Tap compass to cycle modes

### Jumpy Rotation?
- Poor GPS signal causing bearing jumps
- Solution: Wait for better GPS fix
- Smoothing algorithm reduces but can't eliminate

### Map Not Rotating?
- GPS bearing not being provided
- App calculates from position changes
- Need movement for calculation

## Best Practices

### For Cross-Country Flying
1. Use Track-Up during active navigation
2. Switch to North-Up for planning at turnpoints
3. Return to Track-Up for leg navigation

### For Thermal Flying
1. North-Up often preferred (stable reference)
2. Track-Up useful when drifting with wind
3. Personal preference varies

### For Final Glide
1. Track-Up ideal for staying on course
2. Easy to see if drifting left/right
3. Waypoint stays at top of screen

## Configuration

### Settings Available
- Default orientation mode on startup
- Glider icon size adjustment
- Camera offset (how far from bottom)
- Rotation smoothing level

### Performance Considerations
- Track-Up uses more CPU (continuous rotation)
- Battery impact minimal on modern devices
- Smooth at 60fps on most hardware

## Aviation Safety Note

Track-Up mode is designed to reduce pilot workload during navigation. However:
- Always maintain visual lookout
- Don't fixate on the screen
- Use mode that feels most natural to you
- Practice mode switching on ground first

## Implementation Status

### Working Features ✅
- GPS track calculation with fallback
- Map rotation based on bearing
- Glider icon positioning
- Mode switching via compass widget

### Recent Fixes
- Calculate bearing when GPS doesn't provide it
- Proper icon rotation per mode
- Camera follows location in Track-Up only
- Reduced speed threshold to 2 knots

### Known Limitations
- Requires GPS movement for track
- Initial bearing takes 2+ position updates
- Magnetic interference affects Heading-Up mode