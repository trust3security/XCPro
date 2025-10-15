# Quick Reference - XCPro Gliding App

**Last Updated:** 2025-01-08
**For:** Developers working on XCPro gliding app

> **💡 TIP:** Bookmark this page - you'll use it daily!

---

## 🚫 NEVER DO THIS (Will Break Things)

```bash
# ❌ DON'T - Wipes ALL user data (settings, profiles, SkySight credentials)
./gradlew clean

# ❌ DON'T - Conflicts with custom gesture system
map.uiSettings.isScrollGesturesEnabled = true
map.uiSettings.isZoomGesturesEnabled = true

# ❌ DON'T - Causes auto-return navigation bug
if (isTrackingLocation) {
    map.animateCamera(...)  // Users lose position reference!
}
```

```kotlin
// ❌ DON'T - Cross-contamination between task types
// In AATTaskManager.kt
import com.example.xcpro.tasks.racing.RacingCalculator  // FORBIDDEN!

// In RacingTaskManager.kt
import com.example.xcpro.tasks.aat.AATCalculator  // FORBIDDEN!
```

**Why these break things:** See [CLAUDE.md § Critical Rules](./CLAUDE.md)

---

## ✅ ALWAYS DO THIS (Safe Patterns)

```bash
# ✅ DO - Build without destroying data
./gradlew assembleDebug

# ✅ DO - Quick deploy
./deploy.bat

# ✅ DO - Install with data preservation
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

```kotlin
// ✅ DO - Use task-specific utilities
// In AATTaskManager.kt
import com.example.xcpro.tasks.aat.calculations.AATMathUtils
val distance = AATMathUtils.calculateDistanceKm(...)

// In RacingTaskManager.kt
import com.example.xcpro.tasks.racing.calculations.RacingMathUtils
val distance = RacingMathUtils.calculateDistance(...)
```

```kotlin
// ✅ DO - Manual camera return only
if (showReturnButton && userClickedReturn) {
    map.animateCamera(...)  // User-initiated only
}
```

---

## 📏 Default Values (Memorize These)

| Waypoint Type | Default Value | Applies To | Source |
|---------------|---------------|------------|--------|
| **Start Line** | 10km | All task types | Universal |
| **Finish Cylinder** | 3km | All task types | Universal |
| **Racing Turnpoint** | 0.5km cylinder | Racing only | Task-specific |
| **AAT Area** | 10km circle | AAT only | Task-specific |
| **DHT Turnpoint** | 1km cylinder | DHT only | Task-specific |

**Full spec:** [TASK_TYPE_RULES.md](./TASK_TYPE_RULES.md)

---

## 🔧 Common Commands

### Build & Deploy
```bash
# Build debug APK (safe - preserves user data)
./gradlew assembleDebug

# Build + Install + Launch (Windows)
./deploy.bat

# Build only
./build-only.bat

# Install existing APK
adb install -r app/build/outputs/apk/debug/app-debug.apk

# Launch app for testing
adb shell monkey -p com.example.xcpro.debug -c android.intent.category.LAUNCHER 1
```

### Debugging
```bash
# Monitor all app logs
adb logcat -s "MapScreen" "TaskManager" "MapOrientationManager" -v time

# Monitor task-specific logs
adb logcat -s "AATTaskManager" "RacingTaskManager" -v time

# Monitor SkySight integration
adb logcat -s "SkysightClient" "SkysightApi" -v time

# Check app permissions
adb shell dumpsys package com.example.xcpro.debug | grep -A 20 "permissions:"
```

### Git Workflow
```bash
# Check task separation (should return ZERO)
grep -r "import.*racing" app/src/main/java/.../tasks/aat

# Check for Racing imports in AAT (should be empty)
grep -r "import.*aat" app/src/main/java/.../tasks/racing

# Commit with proper format
git commit -m "feat: Add AAT sector validation

- Implemented FAI-compliant sector validation
- Added unit tests for edge cases
- Updated documentation

🤖 Generated with Claude Code
Co-Authored-By: Claude <noreply@anthropic.com>"
```

---

## 🧪 Before You Code - Checklist

### For ANY code change:
- [ ] Read [CLAUDE.md § Critical Rules](./CLAUDE.md) for your area
- [ ] Check default values in [TASK_TYPE_RULES.md](./TASK_TYPE_RULES.md)
- [ ] Verify no `./gradlew clean` in your scripts
- [ ] Run existing tests to ensure baseline works

### For Racing task changes:
- [ ] Read [racing_task_spec.md](./racing_task_spec.md)
- [ ] Check [CLAUDE.md § Task Separation](./CLAUDE.md#-absolute-critical---task-type-separation)
- [ ] **MUST TEST:** Change finish cylinder radius, verify distance updates
- [ ] No AAT imports: `grep "import.*aat" YourFile.kt` → should be empty

### For AAT task changes:
- [ ] Read [aat/ARCHITECTURE.md](./app/src/main/java/com/example/xcpro/tasks/aat/ARCHITECTURE.md)
- [ ] Read [aat_task_spec.md](./aat_task_spec.md) for FAI rules
- [ ] Check [CLAUDE.md § Task Separation](./CLAUDE.md#-absolute-critical---task-type-separation)
- [ ] No Racing imports: `grep "import.*racing" YourFile.kt` → should be empty

### For map/UI changes:
- [ ] Check [CLAUDE.md § Custom Gesture Requirements](./CLAUDE.md#-critical---custom-gesture-requirements)
- [ ] Check [CLAUDE.md § Z-Index Layering](./CLAUDE.md#-z-index-layering-reference)
- [ ] Verify two-finger pan still works
- [ ] Verify single-finger zoom/mode-switch still works

---

## 🐛 Common Issues & Quick Fixes

### Issue: "Build failed - clean required"
```bash
# ❌ DON'T do this first
./gradlew clean  # Wipes user data!

# ✅ DO try these instead:
./gradlew --stop
./gradlew assembleDebug --rerun-tasks

# Only if above fails AND you understand data loss:
./gradlew clean assembleDebug
```

### Issue: "Task separation error - AAT imports Racing"
```bash
# Find the problem
grep -rn "import.*racing" app/src/main/java/.../tasks/aat

# Fix: Replace Racing import with AAT equivalent
# Before:
import com.example.xcpro.tasks.racing.calculations.haversineDistance

# After:
import com.example.xcpro.tasks.aat.calculations.AATMathUtils
val distance = AATMathUtils.calculateDistanceKm(...)
```

### Issue: "Map gestures not working"
```kotlin
// ❌ Problem: Enabled MapLibre gestures
map.uiSettings.isScrollGesturesEnabled = true  // Conflicts!

// ✅ Solution: Use custom gesture handlers
// See CLAUDE.md § Custom Gesture Requirements for full implementation
```

### Issue: "Racing task distance doesn't change when I modify finish radius"
```kotlin
// ❌ Problem: Using waypoint centers instead of cylinder edges
val distance = calculateDistance(waypoint1.lat, waypoint2.lat)

// ✅ Solution: Use optimal entry/exit points
val optimalPoint = cylinderCalculator.calculateOptimalEntryPoint(...)
val distance = calculateDistance(prevPoint, optimalPoint)

// See: CLAUDE.md § Critical Testing Requirements
```

---

## 📂 File Organization Quick Guide

### Adding Racing feature:
```
app/src/main/java/.../tasks/racing/
├── RacingTaskManager.kt       ← Main coordinator
├── calculations/              ← Math utilities
├── turnpoints/               ← Turnpoint geometries
│   ├── CylinderCalculator.kt
│   ├── KeyholeCalculator.kt
│   └── FAIQuadrantCalculator.kt
└── ui/                       ← UI components
```

### Adding AAT feature:
```
app/src/main/java/.../tasks/aat/
├── AATTaskManager.kt         ← Main coordinator
├── calculations/             ← Math utilities (separate from Racing!)
├── areas/                    ← Area geometries
│   ├── CircleAreaCalculator.kt
│   └── SectorAreaCalculator.kt
├── validation/               ← FAI compliance
└── ui/                       ← UI components
```

**Rule:** Racing and AAT directories must NEVER import each other!

---

## 🎯 Code Patterns

### Creating a new waypoint (use defaults):
```kotlin
// ✅ CORRECT - Uses standardized defaults
val waypoint = TaskWaypoint(
    lat = lat,
    lon = lon,
    title = title,
    role = WaypointRole.START,
    gateWidth = TaskWaypoint.getEffectiveRadius(role, taskType)  // 10km for starts
)
```

### Calculating distance (task-specific):
```kotlin
// ✅ AAT code - use AAT utilities
import com.example.xcpro.tasks.aat.calculations.AATMathUtils
val dist = AATMathUtils.calculateDistanceKm(lat1, lon1, lat2, lon2)

// ✅ Racing code - use Racing utilities
import com.example.xcpro.tasks.racing.calculations.RacingMathUtils
val dist = RacingMathUtils.calculateDistance(point1, point2)
```

### Custom gestures (map interaction):
```kotlin
// ✅ CORRECT - Custom gesture handler
.pointerInput(Unit) {
    awaitEachGesture {
        val event = awaitPointerEvent()
        val fingerCount = event.changes.filter { !it.changedToUp() }.size

        when (fingerCount) {
            1 -> handleZoomOrModeSwitch()  // Vertical = zoom, Horizontal = mode
            2 -> handleMapPan()             // Two-finger pan only
        }
    }
}
```

---

## 📚 Where to Find More Details

| Topic | Document | Section |
|-------|----------|---------|
| **Master guide** | [CLAUDE.md](./CLAUDE.md) | All sections |
| **All docs index** | [DOCS_INDEX.md](./DOCS_INDEX.md) | Full documentation map |
| **Task defaults** | [TASK_TYPE_RULES.md](./TASK_TYPE_RULES.md) | Default values |
| **Racing tasks** | [racing_task_spec.md](./racing_task_spec.md) | Implementation spec |
| **AAT architecture** | [aat/ARCHITECTURE.md](./app/src/main/java/com/example/xcpro/tasks/aat/ARCHITECTURE.md) | Module organization |
| **AAT implementation** | [AAT_Tasks.md](../02_Tasks/AAT_Tasks.md) | Complete AAT guide |
| **Flight data cards** | [DFCARDS_SYSTEM.md](./DFCARDS_SYSTEM.md) | Complete guide |
| **SkySight weather** | [SkySightIntegrationGuide.md](./SkySightIntegrationGuide.md) | Integration guide |

---

## ⚡ Emergency Reference

### "I accidentally ran ./gradlew clean"
- User data is gone (settings, profiles, SkySight credentials)
- Restore from backup if available
- Document in commit message what was lost
- **Prevention:** Add to git hooks to prevent future accidents

### "Tests are failing"
```bash
# Run specific test
./gradlew test --tests "AATTaskValidatorTest"

# Run all AAT tests
./gradlew test --tests "*AAT*"

# Clean test cache
./gradlew cleanTest test
```

### "Task separation violation in CI"
```bash
# Find all violations
find app/src/main/java -name "*.kt" -exec grep -l "import.*racing.*aat\|import.*aat.*racing" {} \;

# This should return ZERO files
```

---

**💡 Pro Tip:** Print this page and keep it near your desk. 90% of questions are answered here!

**Need more?** See [DOCS_INDEX.md](./DOCS_INDEX.md) for complete documentation map.
