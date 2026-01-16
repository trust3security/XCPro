# Racing Tasks - Complete Guide

**Last Updated:** 2026-01-15
**Status:** ✅ Current - Consolidated from multiple sources
**Module:** `app/src/main/java/.../tasks/racing/`

> **Quick Links:** [Task Separation Rules](../01_Core/Task_Type_Separation.md) | [Default Values](./Default_Values.md) | [Racing ARCHITECTURE](../../app/src/main/java/com/example/xcpro/tasks/racing/ARCHITECTURE.md) | [Racing Task Summary](./racingtask.md) | [XCSoar-Style Navigation](./Racing_XCSoar_Navigation.md)

---

## 📖 What is a Racing Task?

Racing tasks are the **traditional** competitive gliding format:
- Fixed turnpoints at specific coordinates
- Speed-based scoring (fastest wins)
- Precise navigation to exact locations
- NO minimum time requirement
- Typical distances: 300-600km in European conditions

**Key Difference from AAT:** Pilots must fly through exact points, not flexible areas.

---

## 🎯 Racing Task Specifications

### Turnpoint Types

Racing tasks support multiple turnpoint geometries, each with specific FAI rules:

#### 1. Cylinder (Most Common)
- **Default radius:** 0.5km
- **Rule:** Must enter the cylinder airspace
- **Use case:** Standard racing turnpoints
- **FAI reference:** SC3 Section 6.3.1

#### 2. FAI Quadrant
- **Radius:** Infinite (90° sector)
- **Orientation:** Based on turn direction (left/right)
- **Rule:** Bisector is perpendicular to task line bisector
- **Use case:** FAI Triangle races, long-distance racing
- **FAI reference:** SC3 Annex A Section 6.3.1a

#### 3. Keyhole
- **Inner cylinder:** 500m radius (fixed)
- **Outer sector:** 90° infinite sector
- **Orientation:** Sector opens away from previous turnpoint
- **Rule:** Must enter either cylinder OR sector
- **Use case:** Flexible racing with safety margin
- **FAI reference:** SC3 Annex A Section 6.3.1b

#### 4. Symmetric Quadrant
- **Radius:** Infinite (90° sector)
- **Orientation:** Perpendicular to task line bisector (symmetric)
- **Rule:** Bisector points directly away from task line
- **Use case:** Specialized competitions
- **FAI reference:** Custom/regional variations

### Start Types

| Type | Geometry | Default Size | Use Case |
|------|----------|--------------|----------|
| **Start Line** | Perpendicular line | 10km | Standard (most common) |
| **Start Cylinder** | Circle around point | 10km radius | Alternative start |
| **FAI Start Quadrant** | 90° sector | Infinite radius | FAI competitions |

### Finish Types

| Type | Geometry | Default Size | Use Case |
|------|----------|--------------|----------|
| **Finish Cylinder** | Circle around point | 3km radius | Standard (most common) |
| **Finish Line** | Perpendicular line | 3km | Alternative finish |

---

## 📏 Default Values

See [Default_Values.md](./Default_Values.md) for complete reference.

**Quick Summary:**
- Start lines: **10km** (universal default)
- Finish cylinders: **3km** (universal default)
- Racing turnpoints: **0.5km** cylinder (task-specific default)

**Preservation Rule:** User customizations are preserved when switching task types.

---

## 🧮 Distance Calculations

### Critical Testing Requirement

**⚠️ MUST TEST:** When changing Racing task calculations or display:

1. **Finish Cylinder Radius Test**:
   - Change finish cylinder radius (e.g., 5km → 0.5km)
   - Verify total task distance changes accordingly
   - **Historical Bug:** Finish cylinder radius was ignored in FAI calculations

2. **Turnpoint Radius Test**:
   - Modify turnpoint cylinder radii
   - Ensure distance reflects optimal touch points (not center-to-center)

3. **Start Line vs Cylinder**:
   - Switch between START_LINE and START_CYLINDER
   - Verify distance calculation uses appropriate geometry

### Optimal Path Calculation

Racing tasks use **FAI-compliant optimal path** for distance:
- Start → Optimal entry to first turnpoint
- Between turnpoints → Optimal exit to optimal entry
- Last turnpoint → Optimal exit to finish

**Source Code:** `racing/turnpoints/FAIQuadrantCalculator.kt`, `CylinderCalculator.kt`

**Display Rule:** Visual course line must match calculated optimal distance.

---

## 🗂️ File Organization

### Racing Module Structure

```
app/src/main/java/.../tasks/racing/
│
├── RacingTaskManager.kt          ← Main coordinator
├── RacingTaskCalculator.kt       ← Distance/scoring calculations
├── RacingTaskDisplay.kt          ← Map visualization
├── RacingTaskValidator.kt        ← FAI compliance validation
│
├── models/
│   ├── RacingWaypoint.kt        ← Racing-specific waypoint model
│   ├── RacingCylinder.kt        ← Cylinder geometry
│   └── RacingTask.kt            ← Task definition
│
├── calculations/
│   └── RacingMathUtils.kt       ← Math utilities (autonomous)
│
├── turnpoints/
│   ├── CylinderCalculator.kt
│   ├── CylinderDisplay.kt
│   ├── FAIQuadrantCalculator.kt
│   ├── FAIQuadrantDisplay.kt
│   ├── KeyholeCalculator.kt
│   ├── KeyholeDisplay.kt
│   └── SymmetricQuadrantCalculator.kt
│
└── ui/
    └── RacingTaskPointTypeSelector.kt  ← Turnpoint type UI
```

### Architecture Rules

See [Task_Type_Separation.md](../01_Core/Task_Type_Separation.md) for complete rules.

**Key Requirements:**
- ✅ 100% autonomous (no AAT imports)
- ✅ Own models, calculations, and display
- ✅ Separate math utilities
- ❌ NO sharing with AAT module

---

## 🎨 UI Components

### TaskPointTypeSelector

Dropdown interface for selecting turnpoint types:
- Start types: Line, Cylinder, FAI Quadrant
- Finish types: Cylinder, Line
- Turnpoint types: Cylinder, FAI Quadrant, Keyhole, Symmetric Quadrant

**Location:** `racing/ui/RacingTaskPointTypeSelector.kt`

### Map Display

Racing tasks display:
- Blue task line (#0066FF)
- Turnpoint cylinders/sectors
- Start/finish zones
- Optimal path visualization

**Location:** `racing/RacingTaskDisplay.kt`

---

## Navigation (XCSoar Alignment)

XCPro will follow XCSoar's transition-driven task logic:
- Start is detected on exit from the start observation zone (line/cylinder/sector).
- Turnpoints advance on entry into the observation zone.
- Finish is detected on entry, and only after previous points are achieved.
- Transition checks use last GPS vs current GPS positions (not single-point proximity).

Implementation details and the long-term strategy live in:
- `docs/02_Tasks/Racing_XCSoar_Navigation.md`

---
## ⚖️ FAI Compliance

### Racing Task Validation

**Rules Enforced:**
1. Minimum 3 turnpoints for triangle tasks
2. Turnpoint separation ≥ 500m
3. Valid start/finish geometry
4. Turnpoint cylinders within reasonable bounds (< 50km)

**Validator:** `RacingTaskValidator.kt`

### Competition Classes

Racing tasks work with standard FAI competition classes:
- Club Class
- Standard Class
- Open Class
- 15m Class
- 18m Class

**No minimum time requirement** (unlike AAT)

---

## 🧪 Testing

### Unit Tests

Each turnpoint geometry has dedicated tests:
```bash
# Run Racing tests only
./gradlew test --tests "*Racing*"

# Specific geometry tests
./gradlew test --tests "CylinderCalculatorTest"
./gradlew test --tests "FAIQuadrantCalculatorTest"
./gradlew test --tests "KeyholeCalculatorTest"
```

### Integration Tests

Test complete Racing task workflows:
- Task creation with multiple turnpoint types
- Distance calculation end-to-end
- Map display rendering
- Type switching (cylinder → keyhole)

---

## 🔧 Common Operations

### Creating a Racing Task

```kotlin
val racingManager = RacingTaskManager()

// Add start
racingManager.addWaypoint(startWaypoint, role = START, type = START_LINE)

// Add turnpoints
racingManager.addWaypoint(tp1, role = TURNPOINT, type = CYLINDER)
racingManager.addWaypoint(tp2, role = TURNPOINT, type = FAI_QUADRANT)
racingManager.addWaypoint(tp3, role = TURNPOINT, type = KEYHOLE)

// Add finish
racingManager.addWaypoint(finishWaypoint, role = FINISH, type = FINISH_CYLINDER)

// Calculate distance
val distance = racingManager.calculateTaskDistance()
```

### Changing Turnpoint Type

```kotlin
// Change turnpoint 2 from cylinder to keyhole
racingManager.updateTurnpointType(
    index = 2,
    newType = TurnPointType.KEYHOLE,
    keyholeInnerRadius = 0.5,  // km
    keyholeAngle = 90.0        // degrees
)
```

### Validating Task

```kotlin
val validator = RacingTaskValidator()
val result = validator.validateTask(racingTask)

if (result.isValid) {
    println("Task is FAI compliant")
} else {
    println("Errors: ${result.errors}")
}
```

---

## 📚 Related Documentation

### Core Docs
- [Task_Type_Separation.md](../01_Core/Task_Type_Separation.md) - **MUST READ** separation rules
- [Default_Values.md](./Default_Values.md) - Default sizes and preservation
- [AAT_Tasks.md](./AAT_Tasks.md) - Comparison with AAT

### Racing-Specific
- [racing_task_spec.md](../../racing_task_spec.md) - Original specification
- [PRDRacingTask.md](../../PRDRacingTask.md) - Product requirements
- [keyhole_task_spec.md](../../keyhole_task_spec.md) - Keyhole geometry details
- [racing/ARCHITECTURE.md](../../app/src/main/java/.../tasks/racing/ARCHITECTURE.md) - Module architecture

### Reference
- [Quick_Reference.md](../04_Reference/Quick_Reference.md) - Command cheat sheet
- [CLAUDE.md](../../CLAUDE.md) - Master development guide

---

## ⚠️ Critical Reminders

1. **NEVER import AAT code** - See [Task_Type_Separation.md](../01_Core/Task_Type_Separation.md)
2. **ALWAYS test finish radius changes** - Distance must update
3. **Use RacingMathUtils** - Not shared utilities
4. **Blue task lines only** - Racing = #0066FF, AAT = #4CAF50

---

**Questions?** See [DOCS_INDEX.md](../../DOCS_INDEX.md) for complete documentation map.

