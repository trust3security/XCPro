
# Task Type Separation - Complete Guide

**Last Updated:** 2025-01-08
**Status:** ... Current - Single Source of Truth
**Priority:** "' CRITICAL - Read before ANY task-related development

> **  ABSOLUTE REQUIREMENT:** Racing and AAT task types MUST maintain ZERO cross-contamination. This is a **safety-critical** architectural requirement.

---

## "- Table of Contents

- [The Ironclad Rule](#the-ironclad-rule)
- [Why This Matters](#why-this-matters)
- [Forbidden Patterns](#-forbidden-patterns)
- [Required Separation](#-required-separation)
- [Directory Structure](#-directory-structure)
- [Enforcement Checklist](#-enforcement-checklist)
- [Common Violations & Fixes](#-common-violations--fixes)
- [Testing Separation](#-testing-separation)

---

## The Ironclad Rule

### ZERO CROSS-CONTAMINATION - MANDATORY

**Definition:** Racing and AAT task types must be **completely isolated** with ZERO shared code, models, or calculation logic.

```
... ALLOWED:
- Each task type has its own complete implementation
- Each task type imports only from its own directory
- TaskManagerCoordinator routes between task types (routing ONLY, no logic)

oe FORBIDDEN:
- Racing code importing AAT classes
- AAT code importing Racing classes
- Shared calculation functions between task types
- Shared geometry classes
- Shared models or enums
- when (taskType) switches in calculation code
```

---

## Why This Matters

### Historical Bug Examples

**Bug 1: Racing Distance Breaks AAT Validation**
- **What happened:** Racing finish radius calculation changed
- **Side effect:** AAT area validation started failing
- **Root cause:** Shared geometry calculator used by both
- **Impact:** 2 days debugging + competition delay

**Bug 2: AAT Optimization Breaks Racing**
- **What happened:** AAT path optimizer refactored
- **Side effect:** Racing FAI quadrant angles calculated wrong
- **Root cause:** Shared bearing calculation function
- **Impact:** Invalid race results, had to re-score event

**Bug 3: New Racing Feature Crashes AAT**
- **What happened:** Added symmetric quadrant to Racing
- **Side effect:** AAT task loading threw exceptions
- **Root cause:** Shared turnpoint type enum
- **Impact:** App crash on AAT task load

### Prevention Through Separation

With **ZERO cross-contamination**:
- ... Racing bug fix = Racing-only impact
- ... AAT feature = AAT-only impact
- ... No cascading failures
- ... Independent testing
- ... Parallel development possible
- ... Clear debugging (bug is in one place)

---

## << Forbidden Patterns

### oe Pattern 1: Cross-Task Imports

```kotlin
// oe FORBIDDEN - In AAT code
package com.example.xcpro.tasks.aat

import com.example.xcpro.tasks.racing.RacingCalculator  // NO!
import com.example.xcpro.tasks.racing.models.*  // NO!

fun calculateAAT() {
    val distance = RacingCalculator.calculateDistance(...)  // NO!
}
```

```kotlin
// oe FORBIDDEN - In Racing code
package com.example.xcpro.tasks.racing

import com.example.xcpro.tasks.aat.AATCalculator  // NO!
import com.example.xcpro.tasks.aat.models.*  // NO!

fun calculateRacing() {
    val area = AATArea(...)  // NO!
}
```

### oe Pattern 2: Shared Calculation Functions

```kotlin
// oe FORBIDDEN - Shared utilities
package com.example.xcpro.tasks.shared  // NO "shared" directory!

object SharedMathUtils {  // NO!
    fun calculateDistance(...) { ... }
}

// Used by both Racing and AAT = VIOLATION
```

### oe Pattern 3: Task Type Switching in Logic

```kotlin
// oe FORBIDDEN - Type switching in calculations
fun updateRadius(taskType: TaskType, radius: Double) {
    when (taskType) {
        TaskType.RACING -> {
            // Racing calculation logic here  // NO!
        }
        TaskType.AAT -> {
            // AAT calculation logic here  // NO!
        }
    }
    // This should be in TaskManagerCoordinator for ROUTING only!
}
```

### oe Pattern 4: Shared Models

```kotlin
// oe FORBIDDEN - Shared task models
data class TaskArea(...)  // Used by both Racing and AAT = VIOLATION

// Racing uses RacingCylinder
// AAT uses AATArea
// Completely different models!
```

### oe Pattern 5: Mixed Geometry Classes

```kotlin
// oe FORBIDDEN - Shared geometry
class CylinderGeometry {  // Used by both = VIOLATION
    fun calculateIntersection(...) { ... }
}

// Racing has racing/turnpoints/CylinderCalculator.kt
// AAT has aat/areas/CircleAreaCalculator.kt
// Separate implementations!
```

---

## ... Required Separation

### Proper Architecture

```kotlin
// ... CORRECT - AAT code uses AAT utilities
package com.example.xcpro.tasks.aat

import com.example.xcpro.tasks.aat.calculations.AATMathUtils
import com.example.xcpro.tasks.aat.models.AATWaypoint
import com.example.xcpro.tasks.aat.areas.CircleAreaCalculator

class AATTaskCalculator {
    fun calculateDistance() {
        val distance = AATMathUtils.calculateDistanceKm(...)  // ... AAT's own
    }
}
```

```kotlin
// ... CORRECT - Racing code uses Racing utilities
package com.example.xcpro.tasks.racing

import com.example.xcpro.tasks.racing.calculations.RacingMathUtils
import com.example.xcpro.tasks.racing.models.RacingWaypoint
import com.example.xcpro.tasks.racing.turnpoints.CylinderCalculator

class RacingTaskCalculator {
    fun calculateDistance() {
        val distance = RacingMathUtils.calculateDistance(...)  // ... Racing's own
    }
}
```

```kotlin
// ... CORRECT - TaskManagerCoordinator routes ONLY
package com.example.xcpro.tasks

class TaskManagerCoordinator {
    private val racingManager = RacingTaskManager()
    private val aatManager = AATTaskManager()

    fun updateRadius(taskType: TaskType, radius: Double) {
        // ROUTING only - no calculation logic!
        when (taskType) {
            TaskType.RACING -> racingManager.updateCylinderRadius(radius)
            TaskType.AAT -> aatManager.updateAreaRadius(radius)
        }
    }
}
```

### Each Task Type Must Have

| Component | Racing | AAT |
|-----------|--------|-----|
| **Calculator** | RacingTaskCalculator | AATTaskCalculator |
| **Models** | RacingWaypoint, RacingCylinder | AATWaypoint, AATArea |
| **Display** | RacingTaskDisplay | AATTaskDisplay |
| **Validator** | RacingTaskValidator | AATTaskValidator |
| **Geometry** | racing/turnpoints/* | aat/areas/* |
| **Math Utils** | RacingMathUtils | AATMathUtils |

**No sharing between columns!**

---

## " Directory Structure

### Enforced File Organization

```
app/src/main/java/.../tasks/
"
""" TaskManagerCoordinator.kt    * ROUTER ONLY (no calculation logic)
"
""" racing/                      * RACING MODULE (100% autonomous)
"   """ RacingTaskManager.kt
"   """ RacingTaskCalculator.kt
"   """ RacingTaskDisplay.kt
"   """ RacingTaskValidator.kt
"   """ models/
"   "   """ RacingWaypoint.kt
"   "   """ RacingCylinder.kt
"   "   """" RacingTask.kt
"   """ calculations/
"   "   """" RacingMathUtils.kt
"   """" turnpoints/
"       """ CylinderCalculator.kt
"       """ FAIQuadrantCalculator.kt
"       """" KeyholeCalculator.kt
"
"""" aat/                         * AAT MODULE (100% autonomous)
    """ AATTaskManager.kt
    """ AATTaskCalculator.kt
    """ AATTaskDisplay.kt
    """ AATTaskValidator.kt
    """ models/
    "   """ AATWaypoint.kt
    "   """ AATArea.kt
    "   """" AATTask.kt
    """ calculations/
    "   """" AATMathUtils.kt
    """" areas/
        """ CircleAreaCalculator.kt
        """" SectorAreaCalculator.kt
```

**Critical Rules:**
- oe NO files in `tasks/shared/`
- oe NO imports from `racing/` in `aat/` files
- oe NO imports from `aat/` in `racing/` files
- ... Each module is completely self-contained

---

## " Enforcement Checklist

### Before Every Commit - Run These Checks

```bash
# 1. Check for Racing imports in AAT code (should return ZERO)
grep -r "import.*racing" app/src/main/java/.../tasks/aat --include="*.kt"

# 2. Check for AAT imports in Racing code (should return ZERO)
grep -r "import.*aat" app/src/main/java/.../tasks/racing --include="*.kt"

# 3. Check for shared directories (should return ZERO)
find app/src/main/java/.../tasks -name "shared" -type d

# 4. Verify no task type switching in calculation files
grep -r "when.*taskType" app/src/main/java/.../tasks/racing app/src/main/java/.../tasks/aat --include="*Calculator.kt"
```

### Automated CI Check

```yaml
# .github/workflows/task-separation.yml
name: Task Separation Check

on: [push, pull_request]

jobs:
  check-separation:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v2
      - name: Check Racing imports in AAT
        run: |
          if grep -r "import.*racing" app/src/main/java/.../tasks/aat --include="*.kt"; then
            echo "ERROR: Found Racing imports in AAT code!"
            exit 1
          fi
      - name: Check AAT imports in Racing
        run: |
          if grep -r "import.*aat" app/src/main/java/.../tasks/racing --include="*.kt"; then
            echo "ERROR: Found AAT imports in Racing code!"
            exit 1
          fi
```

---

## "section Common Violations & Fixes

### Violation 1: Duplicate Math Functions

**Problem:**
```kotlin
// Both files have identical code
// racing/calculations/RacingMathUtils.kt
fun calculateDistance(...) { haversine formula }

// aat/calculations/AATMathUtils.kt
fun calculateDistance(...) { haversine formula }
```

**Why Allowed:**
- ... Each module is autonomous
- ... Can be tested independently
- ... Changes to Racing won't break AAT
- ... Slightly different formulas may be needed (atan2 vs asin)

**DO NOT consolidate into shared utility!**

### Violation 2: Cross-Type Function Parameters

**Problem:**
```kotlin
// oe FORBIDDEN
fun calculateAAT(racingWaypoint: RacingWaypoint) { ... }  // NO!
```

**Fix:**
```kotlin
// ... CORRECT
fun calculateAAT(aatWaypoint: AATWaypoint) { ... }  // YES!
```

### Violation 3: Using Wrong Task Type's Models

**Problem:**
```kotlin
// In AATTaskManager.kt
val cylinder = RacingCylinder(...)  // oe Using Racing model in AAT!
```

**Fix:**
```kotlin
// In AATTaskManager.kt
val area = AATArea(...)  // ... Using AAT's own model
```

---

## sectiona Testing Separation

### Unit Test Independence

```kotlin
// ... CORRECT - Racing tests don't import AAT
class RacingTaskCalculatorTest {
    @Test
    fun `racing distance calculation`() {
        val calculator = RacingTaskCalculator()
        // No AAT imports needed
    }
}
```

```kotlin
// ... CORRECT - AAT tests don't import Racing
class AATTaskCalculatorTest {
    @Test
    fun `aat distance calculation`() {
        val calculator = AATTaskCalculator()
        // No Racing imports needed
    }
}
```

### Integration Test Pattern

```kotlin
// ... CORRECT - Integration tests can test routing
class TaskManagerCoordinatorTest {
    @Test
    fun `coordinator routes to correct task type`() {
        val coordinator = TaskManagerCoordinator()

        // Test Racing routing
        coordinator.setTaskType(TaskType.RACING)
        coordinator.updateRadius(5.0)
        // Verify Racing manager was called

        // Test AAT routing
        coordinator.setTaskType(TaskType.AAT)
        coordinator.updateRadius(10.0)
        // Verify AAT manager was called
    }
}
```

---

## "s Separation Success Metrics

### Healthy Separation Indicators

- ... Each task type builds independently
- ... Zero imports between Racing and AAT
- ... Bug fixes are isolated to one task type
- ... New features don't require changes to other task types
- ... Testing focuses on one task type at a time
- ... Grep checks return ZERO cross-contamination

### Red Flags

- << `grep "import.*racing"` in AAT files returns results
- << `grep "import.*aat"` in Racing files returns results
- << Shared calculation functions between task types
- << `when (taskType)` in calculation logic
- << Bug fix in Racing breaks AAT tests
- << New Racing feature causes AAT compilation errors

---

## * Emergency: Separation Violation Found

### If You Discover a Violation

1. **STOP** - Do not merge code
2. **Identify** - Which files are contaminated?
3. **Isolate** - Move shared code into task-specific copies
4. **Test** - Verify both task types still work
5. **Document** - Add comment explaining why duplication is intentional

### Example Fix

**Before (VIOLATION):**
```kotlin
// shared/GeometryUtils.kt
fun calculateBearing(...) { ... }  // Used by both Racing and AAT
```

**After (FIXED):**
```kotlin
// racing/calculations/RacingMathUtils.kt
fun calculateBearing(...) { ... }  // Racing's own copy

// aat/calculations/AATMathUtils.kt
fun calculateBearing(...) { ... }  // AAT's own copy

// Note: Duplication is intentional for task type separation
```

---

## " Related Documentation

- [Quick_Reference.md](./Quick_Reference.md) - Daily cheat sheet
- [Racing_Tasks.md](../02_Tasks/Racing_Tasks.md) - Racing implementation
- [AAT_Tasks.md](../02_Tasks/AAT_Tasks.md) - AAT implementation
- [aat/ARCHITECTURE.md](../../feature/map/src/main/java/com/example/xcpro/tasks/aat/ARCHITECTURE.md) - AAT module structure
- [racing/ARCHITECTURE.md](../../feature/map/src/main/java/com/example/xcpro/tasks/racing/ARCHITECTURE.md) - Racing module structure

---

## ... Summary

**The Golden Rule:** Racing and AAT are **completely separate universes** that communicate only through TaskManagerCoordinator routing.

**Remember:**
- << ZERO imports between task types
- ... Duplication is GOOD when it maintains separation
- sectiona Test independently
- " Check before every commit

**One contaminated function can break the entire separation architecture!**

---

**Questions?** See [ARCHITECTURE README](../ARCHITECTURE/README.md) or [Quick_Reference.md](./Quick_Reference.md)


