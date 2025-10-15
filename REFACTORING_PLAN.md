# XCPro Refactoring Plan - Distance Calculation Consolidation

**Version:** 1.0
**Date:** 2025-10-13
**Status:** Ready for Implementation
**Estimated Effort:** 8-12 hours across 3 phases

---

## 🎯 Executive Summary

**Problem:** 20+ duplicate implementations of Haversine distance calculation scattered across the codebase, creating maintenance burden and violating SSOT principle.

**Solution:** Consolidate to 3 domain-specific implementations while maintaining absolute Racing/AAT task separation.

**Impact:** 85% code reduction (20+ → 3 implementations), zero cross-contamination, improved maintainability.

---

## 📐 Architectural Principles (NON-NEGOTIABLE)

### 1. **SSOT (Single Source of Truth)**
- Each domain has exactly ONE distance calculation implementation
- No duplicate Haversine formulas within a domain
- Bug fixes propagate automatically (not manually across 20 files)

### 2. **KISS (Keep It Simple, Stupid)**
- Don't reinvent the wheel in every file
- Reuse proven implementations
- Simple, clear import patterns

### 3. **Complete Task Type Separation**
- ❌ Racing code NEVER imports AAT utilities
- ❌ AAT code NEVER imports Racing utilities
- ❌ NO shared task-specific utilities between Racing/AAT
- ✅ Each task type is self-contained and independently testable

### 4. **Domain Boundaries**

```
┌─────────────────────────────────────────────────────────────┐
│  NON-TASK CODE (UI, Widgets, Map)                         │
│  Uses: utils/DistanceUtils.kt                              │
└─────────────────────────────────────────────────────────────┘
         │
         │ (NO cross-domain imports)
         │
    ┌────┴────┬────────────────────────────────┐
    │         │                                │
    ▼         ▼                                ▼
┌─────────┐ ┌──────────────────────────────┐ ┌──────────────┐
│ Racing  │ │ AAT                          │ │ Coordinator  │
│ Domain  │ │ Domain                       │ │ (Routes only)│
│         │ │                              │ │              │
│ Uses:   │ │ Uses:                        │ │ No distance  │
│ Racing  │ │ AATMathUtils.kt              │ │ calculations │
│ Geometry│ │                              │ │              │
│ Utils.kt│ │                              │ │              │
└─────────┘ └──────────────────────────────┘ └──────────────┘
```

---

## 🔍 Current State Analysis

### **Problem Inventory (20+ Duplicates Found)**

| File | Function | Unit | Status | Action |
|------|----------|------|--------|--------|
| **Racing Domain (8 files)** |
| `RacingGeometryUtils.kt` | `haversineDistance()` | KM | ✅ **KEEP (canonical)** | None |
| `CylinderCalculator.kt` | `calculateDistance()` override | KM | ❌ Duplicate | Delete, use RacingGeometryUtils |
| `KeyholeCalculator.kt` | `calculateDistance()` override | KM | ❌ Duplicate | Delete, use RacingGeometryUtils |
| `FAIQuadrantCalculator.kt` | `calculateDistance()` override | KM | ❌ Duplicate | Delete, use RacingGeometryUtils |
| `FinishLineDisplay.kt` | `calculateDistance()` private | KM | ❌ Duplicate | Delete, use RacingGeometryUtils |
| `StartLineDisplay.kt` | (likely has one) | KM | ❌ Duplicate | Delete, use RacingGeometryUtils |
| `RacingTask.kt` | `calculateDistance()` private | KM | ❌ Duplicate | Delete, use RacingGeometryUtils |
| `RacingTaskCalculator.kt` | (uses utils) | KM | ✅ Correct | Verify imports |
| **AAT Domain (7 files)** |
| `AATMathUtils.kt` | `calculateDistance()` | KM | ✅ **KEEP (canonical)** | None |
| `AATMathUtils.kt` | `calculateDistanceKm()` wrapper | KM | ✅ **KEEP (convenience)** | None |
| `AATTaskManager.kt` | `calculateDistance()` private | KM | ✅ Delegates correctly | Verify |
| `AATMapInteractionHandler.kt` | `calculateDistance()` private | KM | ❌ Duplicate | Delete, use AATMathUtils |
| `FAIComplianceRules.kt` | `calculateDistance()` private | **METERS** | ❌ Unit mismatch! | Delete, use AATMathUtils |
| `AreaBoundaryCalculator.kt` | `calculateDistanceInArea()` | KM | ⚠️ Check impl | May be specialized |
| `AATDistanceCalculator.kt` | Multiple methods | KM | ⚠️ Check impl | May use AATMathUtils |
| **Non-Task Code (5+ files)** |
| `DistanceCirclesOverlay.kt` | `calculateDistance()` private | KM | ❌ Duplicate | Move to DistanceUtils |
| Various map/UI files | (various) | KM | ❌ Duplicates | Move to DistanceUtils |

### **Critical Bugs Found:**

1. **Unit Mismatch:** `FAIComplianceRules.kt` uses METERS (6371000.0) while all others use KM (6371.0)
2. **Algorithm Variance:** `AATMathUtils` uses `asin()` while others use `atan2()` (mathematically equivalent but different precision)
3. **Maintenance Hell:** Bug fix requires updating 20+ files manually

---

## 🎯 Refactoring Goals

### **Success Criteria:**

- [ ] **SSOT:** Racing domain has 1 implementation (RacingGeometryUtils)
- [ ] **SSOT:** AAT domain has 1 implementation (AATMathUtils)
- [ ] **SSOT:** Non-task code has 1 implementation (DistanceUtils)
- [ ] **Separation:** Zero Racing imports in AAT code
- [ ] **Separation:** Zero AAT imports in Racing code
- [ ] **Reduction:** 85% reduction in duplicate code (20+ → 3)
- [ ] **Testing:** All existing tests still pass
- [ ] **No Regressions:** Distance calculations produce identical results

### **Validation Commands:**

```bash
# Verify no cross-contamination after refactoring
grep -r "import.*tasks\.racing" app/src/main/java/com/example/xcpro/tasks/aat --include="*.kt"
# Expected: No results (empty output)

grep -r "import.*tasks\.aat" app/src/main/java/com/example/xcpro/tasks/racing --include="*.kt"
# Expected: No results (empty output)

# Verify Racing uses only RacingGeometryUtils
grep -r "calculateDistance\|haversineDistance" app/src/main/java/com/example/xcpro/tasks/racing --include="*.kt" | grep -v "RacingGeometryUtils"
# Expected: No private implementations (only imports of RacingGeometryUtils)

# Verify AAT uses only AATMathUtils
grep -r "calculateDistance" app/src/main/java/com/example/xcpro/tasks/aat --include="*.kt" | grep -v "AATMathUtils"
# Expected: No private implementations (only imports of AATMathUtils)
```

---

## 🚀 Implementation Plan

### **Phase 1: Create Non-Task Utility (NEW FILE)**

#### **Step 1.1: Create utils/DistanceUtils.kt**

**File:** `app/src/main/java/com/example/xcpro/utils/DistanceUtils.kt`

```kotlin
package com.example.xcpro.utils

import kotlin.math.*
import org.maplibre.android.geometry.LatLng

/**
 * Centralized distance calculation utilities for NON-TASK code
 *
 * USAGE RULES:
 * - Use this ONLY for UI, widgets, map overlays, profiles
 * - DO NOT use in Racing tasks (use RacingGeometryUtils instead)
 * - DO NOT use in AAT tasks (use AATMathUtils instead)
 *
 * SSOT: Single implementation for all non-task distance calculations
 */
object DistanceUtils {

    /** Earth's radius in kilometers (WGS84 mean radius) */
    private const val EARTH_RADIUS_KM = 6371.0

    /**
     * Calculate great-circle distance using Haversine formula
     *
     * @param lat1 Latitude of first point (degrees)
     * @param lon1 Longitude of first point (degrees)
     * @param lat2 Latitude of second point (degrees)
     * @param lon2 Longitude of second point (degrees)
     * @return Distance in kilometers
     */
    fun calculateDistanceKm(
        lat1: Double,
        lon1: Double,
        lat2: Double,
        lon2: Double
    ): Double {
        val lat1Rad = Math.toRadians(lat1)
        val lat2Rad = Math.toRadians(lat2)
        val deltaLat = Math.toRadians(lat2 - lat1)
        val deltaLon = Math.toRadians(lon2 - lon1)

        val a = sin(deltaLat / 2).pow(2) +
                cos(lat1Rad) * cos(lat2Rad) *
                sin(deltaLon / 2).pow(2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))

        return EARTH_RADIUS_KM * c
    }

    /**
     * Calculate distance between MapLibre LatLng objects
     * Convenience overload for map operations
     */
    fun calculateDistanceKm(from: LatLng, to: LatLng): Double {
        return calculateDistanceKm(
            from.latitude, from.longitude,
            to.latitude, to.longitude
        )
    }

    /**
     * Calculate distance in meters
     * Convenience method for UI that needs meter precision
     */
    fun calculateDistanceMeters(
        lat1: Double,
        lon1: Double,
        lat2: Double,
        lon2: Double
    ): Double {
        return calculateDistanceKm(lat1, lon1, lat2, lon2) * 1000.0
    }
}
```

**Validation:**
```bash
# Verify file created
ls -la app/src/main/java/com/example/xcpro/utils/DistanceUtils.kt

# Verify it compiles
./gradlew :app:compileDebugKotlin
```

---

### **Phase 2: Racing Domain Consolidation**

#### **Step 2.1: Verify RacingGeometryUtils is Canonical**

**File:** `app/src/main/java/com/example/xcpro/tasks/racing/RacingGeometryUtils.kt`

**Check:** Does it have `haversineDistance()` function?

```bash
grep -A 10 "fun haversineDistance" app/src/main/java/com/example/xcpro/tasks/racing/RacingGeometryUtils.kt
```

**Expected:** Function exists and is public. If not, STOP and report.

---

#### **Step 2.2: Refactor CylinderCalculator.kt**

**File:** `app/src/main/java/com/example/xcpro/tasks/racing/turnpoints/CylinderCalculator.kt`

**BEFORE:**
```kotlin
override fun calculateDistance(from: RacingWaypoint, to: RacingWaypoint): Double {
    return RacingGeometryUtils.haversineDistance(from.lat, from.lon, to.lat, to.lon)
}
```

**AFTER:**
```kotlin
// ✅ NO CHANGE NEEDED - Already uses RacingGeometryUtils correctly
override fun calculateDistance(from: RacingWaypoint, to: RacingWaypoint): Double {
    return RacingGeometryUtils.haversineDistance(from.lat, from.lon, to.lat, to.lon)
}
```

**Action:** Verify it's already correct. If it has a private implementation instead, delete it and replace with above.

**Validation:**
```bash
# Verify CylinderCalculator imports RacingGeometryUtils
grep "import.*RacingGeometryUtils" app/src/main/java/com/example/xcpro/tasks/racing/turnpoints/CylinderCalculator.kt

# Verify no private calculateDistance implementation
grep -A 5 "private fun calculateDistance" app/src/main/java/com/example/xcpro/tasks/racing/turnpoints/CylinderCalculator.kt
# Expected: No results (should use RacingGeometryUtils)
```

---

#### **Step 2.3: Refactor KeyholeCalculator.kt**

**File:** `app/src/main/java/com/example/xcpro/tasks/racing/turnpoints/KeyholeCalculator.kt`

**Find and DELETE any private `calculateDistance()` function**

**Replace all distance calculations with:**
```kotlin
import com.example.xcpro.tasks.racing.RacingGeometryUtils

// Use this pattern everywhere:
RacingGeometryUtils.haversineDistance(lat1, lon1, lat2, lon2)
```

**Validation:**
```bash
# Verify import exists
grep "import.*RacingGeometryUtils" app/src/main/java/com/example/xcpro/tasks/racing/turnpoints/KeyholeCalculator.kt

# Verify no private implementation
grep "private fun calculateDistance\|private fun haversineDistance" app/src/main/java/com/example/xcpro/tasks/racing/turnpoints/KeyholeCalculator.kt
# Expected: No results

# Verify it compiles
./gradlew :app:compileDebugKotlin
```

---

#### **Step 2.4: Refactor FAIQuadrantCalculator.kt**

**File:** `app/src/main/java/com/example/xcpro/tasks/racing/turnpoints/FAIQuadrantCalculator.kt`

**Same process as KeyholeCalculator:**
1. Delete private `calculateDistance()` if exists
2. Add import: `import com.example.xcpro.tasks.racing.RacingGeometryUtils`
3. Replace all calls with `RacingGeometryUtils.haversineDistance(...)`

**Validation:** Same commands as Step 2.3

---

#### **Step 2.5: Refactor FinishLineDisplay.kt & StartLineDisplay.kt**

**Files:**
- `app/src/main/java/com/example/xcpro/tasks/racing/turnpoints/FinishLineDisplay.kt`
- `app/src/main/java/com/example/xcpro/tasks/racing/turnpoints/StartLineDisplay.kt`

**Same process:**
1. Search for private `calculateDistance()` implementations
2. Delete them
3. Add import: `import com.example.xcpro.tasks.racing.RacingGeometryUtils`
4. Replace all calls with `RacingGeometryUtils.haversineDistance(...)`

**Validation:**
```bash
# Check both files
for file in FinishLineDisplay.kt StartLineDisplay.kt; do
    echo "Checking $file..."
    grep "private fun calculateDistance" app/src/main/java/com/example/xcpro/tasks/racing/turnpoints/$file
    grep "import.*RacingGeometryUtils" app/src/main/java/com/example/xcpro/tasks/racing/turnpoints/$file
done

# Expected: No private implementations, RacingGeometryUtils import present
```

---

#### **Step 2.6: Refactor RacingTask.kt**

**File:** `app/src/main/java/com/example/xcpro/tasks/racing/models/RacingTask.kt`

**Find this:**
```kotlin
private fun calculateDistance(from: RacingLatLng, to: RacingLatLng): Double {
    // Haversine implementation...
}
```

**Replace with:**
```kotlin
// DELETE the private function entirely

// At top of file, add import:
import com.example.xcpro.tasks.racing.RacingGeometryUtils

// Replace all calls like:
// calculateDistance(from, to)
// with:
RacingGeometryUtils.haversineDistance(from.latitude, from.longitude, to.latitude, to.longitude)
```

**Validation:**
```bash
grep "private fun calculateDistance" app/src/main/java/com/example/xcpro/tasks/racing/models/RacingTask.kt
# Expected: No results

grep "import.*RacingGeometryUtils" app/src/main/java/com/example/xcpro/tasks/racing/models/RacingTask.kt
# Expected: Import found

./gradlew :app:compileDebugKotlin
```

---

#### **Step 2.7: Verify Racing Domain Consolidation**

**Final Racing Domain Check:**
```bash
# Find all distance calculations in Racing domain
grep -r "fun calculateDistance\|fun haversineDistance" app/src/main/java/com/example/xcpro/tasks/racing --include="*.kt"

# Expected output: ONLY RacingGeometryUtils.kt should have implementation
# All other files should just be calling RacingGeometryUtils

# Verify no Racing code imports AAT utilities
grep -r "import.*tasks\.aat" app/src/main/java/com/example/xcpro/tasks/racing --include="*.kt"
# Expected: No results (empty output)
```

---

### **Phase 3: AAT Domain Consolidation**

#### **Step 3.1: Verify AATMathUtils is Canonical**

**File:** `app/src/main/java/com/example/xcpro/tasks/aat/calculations/AATMathUtils.kt`

**Check:** Does it have both `calculateDistance()` and `calculateDistanceKm()` functions?

```bash
grep -A 15 "fun calculateDistance" app/src/main/java/com/example/xcpro/tasks/aat/calculations/AATMathUtils.kt | head -35
```

**Expected:** Two functions exist:
1. `calculateDistance(from: AATLatLng, to: AATLatLng): Double`
2. `calculateDistanceKm(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double`

If not present, STOP and report.

---

#### **Step 3.2: Refactor AATMapInteractionHandler.kt**

**File:** `app/src/main/java/com/example/xcpro/tasks/aat/interaction/AATMapInteractionHandler.kt`

**Find and DELETE:**
```kotlin
private fun calculateDistance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
    // Haversine implementation...
}
```

**Add import:**
```kotlin
import com.example.xcpro.tasks.aat.calculations.AATMathUtils
```

**Replace all calls:**
```kotlin
// OLD: calculateDistance(lat1, lon1, lat2, lon2)
// NEW:
AATMathUtils.calculateDistanceKm(lat1, lon1, lat2, lon2)
```

**Validation:**
```bash
grep "private fun calculateDistance" app/src/main/java/com/example/xcpro/tasks/aat/interaction/AATMapInteractionHandler.kt
# Expected: No results

grep "import.*AATMathUtils" app/src/main/java/com/example/xcpro/tasks/aat/interaction/AATMapInteractionHandler.kt
# Expected: Import found

./gradlew :app:compileDebugKotlin
```

---

#### **Step 3.3: CRITICAL - Fix FAIComplianceRules.kt Unit Mismatch**

**File:** `app/src/main/java/com/example/xcpro/tasks/aat/validation/FAIComplianceRules.kt`

**BEFORE (BUGGY - uses METERS):**
```kotlin
private fun calculateDistance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
    val earthRadius = 6371000.0 // ⚠️ METERS - BUG!
    // ... Haversine ...
    return earthRadius * c
}
```

**AFTER:**
```kotlin
// DELETE the entire private function

// Add import at top:
import com.example.xcpro.tasks.aat.calculations.AATMathUtils

// Replace all distance calculations:
// OLD: val distance = calculateDistance(lat1, lon1, lat2, lon2)
// NEW:
val distanceKm = AATMathUtils.calculateDistanceKm(lat1, lon1, lat2, lon2)
val distanceMeters = distanceKm * 1000.0 // If meters needed
```

**⚠️ CRITICAL:** Check if FAI rules actually need meters or kilometers. If FAI specs use meters, keep the conversion. If they use kilometers, remove the `* 1000.0`.

**Validation:**
```bash
# Verify no private implementation
grep "private fun calculateDistance" app/src/main/java/com/example/xcpro/tasks/aat/validation/FAIComplianceRules.kt
# Expected: No results

# Verify uses AATMathUtils
grep "import.*AATMathUtils" app/src/main/java/com/example/xcpro/tasks/aat/validation/FAIComplianceRules.kt
# Expected: Import found

# Verify no hardcoded 6371000.0 (meters)
grep "6371000" app/src/main/java/com/example/xcpro/tasks/aat/validation/FAIComplianceRules.kt
# Expected: No results

./gradlew :app:compileDebugKotlin
```

---

#### **Step 3.4: Check AATTaskManager.kt**

**File:** `app/src/main/java/com/example/xcpro/tasks/aat/AATTaskManager.kt`

**Current code ALREADY delegates correctly:**
```kotlin
private fun calculateDistance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
    // Use AAT's own math utilities for consistency
    return AATMathUtils.calculateDistanceKm(lat1, lon1, lat2, lon2)
}
```

**Action:** ✅ NO CHANGE NEEDED - This is the correct pattern (wrapper for internal use)

**Validation:**
```bash
# Verify it delegates to AATMathUtils
grep -A 3 "private fun calculateDistance" app/src/main/java/com/example/xcpro/tasks/aat/AATTaskManager.kt | grep "AATMathUtils"
# Expected: Should contain "AATMathUtils.calculateDistanceKm"
```

---

#### **Step 3.5: Check Specialized AAT Calculators**

**Files to inspect:**
- `app/src/main/java/com/example/xcpro/tasks/aat/areas/AreaBoundaryCalculator.kt`
- `app/src/main/java/com/example/xcpro/tasks/aat/calculations/AATDistanceCalculator.kt`
- `app/src/main/java/com/example/xcpro/tasks/aat/calculations/AATSpeedCalculator.kt`

**For each file:**
```bash
# Check if it has private calculateDistance implementation
grep "private fun calculateDistance" <file>

# If YES: Delete it and replace with AATMathUtils calls
# If NO: Verify it already uses AATMathUtils or doesn't need distance calculations
```

**General rule:** Any private Haversine implementation should be deleted and replaced with `AATMathUtils.calculateDistanceKm()`.

---

#### **Step 3.6: Verify AAT Domain Consolidation**

**Final AAT Domain Check:**
```bash
# Find all distance calculations in AAT domain
grep -r "fun calculateDistance" app/src/main/java/com/example/xcpro/tasks/aat --include="*.kt"

# Expected: ONLY AATMathUtils.kt should have implementation
# AATTaskManager.kt may have a wrapper (acceptable)
# All other files should just be calling AATMathUtils

# Verify no AAT code imports Racing utilities
grep -r "import.*tasks\.racing" app/src/main/java/com/example/xcpro/tasks/aat --include="*.kt"
# Expected: No results (empty output)
```

---

### **Phase 4: Non-Task Code Consolidation**

#### **Step 4.1: Refactor DistanceCirclesOverlay.kt**

**File:** `app/src/main/java/com/example/xcpro/map/DistanceCirclesOverlay.kt`

**BEFORE:**
```kotlin
private fun calculateDistance(point1: LatLng, point2: LatLng): Double {
    val earthRadius = 6371.0
    val lat1Rad = Math.toRadians(point1.latitude)
    // ... full Haversine implementation ...
    return earthRadius * c
}
```

**AFTER:**
```kotlin
// DELETE the entire private function

// Add import at top:
import com.example.xcpro.utils.DistanceUtils

// Replace all calls:
// OLD: val distance = calculateDistance(point1, point2)
// NEW:
val distance = DistanceUtils.calculateDistanceKm(point1, point2)
```

**Validation:**
```bash
grep "private fun calculateDistance" app/src/main/java/com/example/xcpro/map/DistanceCirclesOverlay.kt
# Expected: No results

grep "import.*DistanceUtils" app/src/main/java/com/example/xcpro/map/DistanceCirclesOverlay.kt
# Expected: Import found

./gradlew :app:compileDebugKotlin
```

---

#### **Step 4.2: Search and Refactor Other Non-Task Files**

**Search for other duplicates:**
```bash
# Find all distance calculations in non-task code
find app/src/main/java/com/example/xcpro -name "*.kt" \
  -not -path "*/tasks/*" \
  -exec grep -l "private fun calculateDistance\|private fun haversineDistance" {} \;
```

**For each file found:**
1. Read the file and identify the private implementation
2. Delete the private function
3. Add `import com.example.xcpro.utils.DistanceUtils`
4. Replace all calls with `DistanceUtils.calculateDistanceKm(...)`
5. Compile and test

---

### **Phase 5: Final Validation & Testing**

#### **Step 5.1: Compile Check**
```bash
./gradlew clean
./gradlew :app:compileDebugKotlin

# Expected: No compilation errors
```

#### **Step 5.2: Task Separation Verification**
```bash
# ✅ Verify NO Racing imports in AAT
grep -r "import.*tasks\.racing" app/src/main/java/com/example/xcpro/tasks/aat --include="*.kt"
# Expected: Empty (no results)

# ✅ Verify NO AAT imports in Racing
grep -r "import.*tasks\.aat" app/src/main/java/com/example/xcpro/tasks/racing --include="*.kt"
# Expected: Empty (no results)

# ✅ Verify Racing uses only RacingGeometryUtils
grep -r "calculateDistance\|haversineDistance" app/src/main/java/com/example/xcpro/tasks/racing --include="*.kt" \
  | grep -v "RacingGeometryUtils" | grep "private fun"
# Expected: Empty (no private implementations)

# ✅ Verify AAT uses only AATMathUtils
grep -r "calculateDistance" app/src/main/java/com/example/xcpro/tasks/aat --include="*.kt" \
  | grep -v "AATMathUtils" | grep "private fun"
# Expected: Empty (no private implementations except AATTaskManager wrapper)
```

#### **Step 5.3: Duplicate Count Verification**
```bash
# Count remaining implementations
echo "Racing domain implementations:"
grep -r "fun haversineDistance" app/src/main/java/com/example/xcpro/tasks/racing --include="*.kt" | wc -l
# Expected: 1 (only RacingGeometryUtils)

echo "AAT domain implementations:"
grep -r "fun calculateDistance" app/src/main/java/com/example/xcpro/tasks/aat --include="*.kt" | wc -l
# Expected: 2 (AATMathUtils.calculateDistance + AATMathUtils.calculateDistanceKm)

echo "Non-task implementations:"
grep -r "fun calculateDistance" app/src/main/java/com/example/xcpro/utils --include="*.kt" | wc -l
# Expected: 2 (DistanceUtils.calculateDistanceKm + overload)

echo "Private implementations (should be 0):"
grep -r "private fun calculateDistance\|private fun haversineDistance" app/src/main/java --include="*.kt" | wc -l
# Expected: 0 (maybe 1 if AATTaskManager wrapper is private - that's OK)
```

#### **Step 5.4: Run Tests**
```bash
# Run unit tests
./gradlew :app:testDebugUnitTest

# Expected: All tests pass (or same failure rate as before refactoring)
```

#### **Step 5.5: Build APK**
```bash
./gradlew assembleDebug

# Expected: APK builds successfully
```

---

## 🚫 Anti-Patterns to AVOID

### **❌ DO NOT:**

1. **Create a shared utils package used by both Racing and AAT**
   ```kotlin
   // ❌ WRONG - Creates cross-contamination
   import com.example.xcpro.tasks.shared.DistanceUtils  // NO!
   ```

2. **Import Racing utils in AAT code**
   ```kotlin
   // ❌ WRONG - Violates separation
   import com.example.xcpro.tasks.racing.RacingGeometryUtils
   ```

3. **Import AAT utils in Racing code**
   ```kotlin
   // ❌ WRONG - Violates separation
   import com.example.xcpro.tasks.aat.calculations.AATMathUtils
   ```

4. **Leave private implementations "just in case"**
   ```kotlin
   // ❌ WRONG - Defeats SSOT
   private fun calculateDistance(...) { /* backup implementation */ }
   ```

5. **Mix units without clear documentation**
   ```kotlin
   // ❌ WRONG - Confusion and bugs
   val distance = calculateDistance(...) // KM or meters???
   ```

### **✅ DO:**

1. **Use domain-specific utilities within each domain**
   ```kotlin
   // ✅ CORRECT - Racing uses Racing utils
   import com.example.xcpro.tasks.racing.RacingGeometryUtils

   // ✅ CORRECT - AAT uses AAT utils
   import com.example.xcpro.tasks.aat.calculations.AATMathUtils

   // ✅ CORRECT - UI uses neutral utils
   import com.example.xcpro.utils.DistanceUtils
   ```

2. **Document units clearly**
   ```kotlin
   // ✅ CORRECT - Clear naming
   val distanceKm = DistanceUtils.calculateDistanceKm(...)
   val distanceMeters = DistanceUtils.calculateDistanceMeters(...)
   ```

3. **Delete redundant code completely**
   ```kotlin
   // ✅ CORRECT - No private implementations
   // Use domain utilities instead
   ```

---

## 🔄 Rollback Strategy

If something goes wrong during refactoring:

### **Option 1: Git Revert**
```bash
# Before starting, create a backup branch
git checkout -b refactor-distance-backup
git checkout flight-data-cards-v3

# If refactoring fails, revert:
git checkout refactor-distance-backup
```

### **Option 2: Stash Changes**
```bash
# If mid-refactor and need to undo:
git stash save "Distance refactoring - incomplete"

# To restore later:
git stash pop
```

### **Option 3: File-by-File Revert**
```bash
# If only specific files are problematic:
git checkout HEAD -- path/to/problematic/file.kt
```

---

## 📊 Progress Tracking Checklist

### **Phase 1: Create Utils**
- [ ] Created `utils/DistanceUtils.kt`
- [ ] File compiles without errors
- [ ] Contains both `calculateDistanceKm()` overloads

### **Phase 2: Racing Domain**
- [ ] Verified `RacingGeometryUtils.kt` exists and is canonical
- [ ] Refactored `CylinderCalculator.kt`
- [ ] Refactored `KeyholeCalculator.kt`
- [ ] Refactored `FAIQuadrantCalculator.kt`
- [ ] Refactored `FinishLineDisplay.kt`
- [ ] Refactored `StartLineDisplay.kt`
- [ ] Refactored `RacingTask.kt`
- [ ] Verified no Racing → AAT imports
- [ ] Verified only 1 implementation (RacingGeometryUtils)

### **Phase 3: AAT Domain**
- [ ] Verified `AATMathUtils.kt` exists and is canonical
- [ ] Refactored `AATMapInteractionHandler.kt`
- [ ] Fixed `FAIComplianceRules.kt` (CRITICAL unit mismatch)
- [ ] Verified `AATTaskManager.kt` delegates correctly
- [ ] Checked specialized calculators
- [ ] Verified no AAT → Racing imports
- [ ] Verified only 1-2 implementations (AATMathUtils + optional wrapper)

### **Phase 4: Non-Task Code**
- [ ] Refactored `DistanceCirclesOverlay.kt`
- [ ] Searched for other non-task duplicates
- [ ] Refactored all found files
- [ ] Verified non-task code uses DistanceUtils only

### **Phase 5: Validation**
- [ ] Compiled successfully (`./gradlew compileDebugKotlin`)
- [ ] Zero Racing → AAT imports
- [ ] Zero AAT → Racing imports
- [ ] Duplicate count: 85% reduction achieved
- [ ] All tests pass
- [ ] APK builds successfully

---

## 📝 Post-Refactoring Documentation

After completing all phases, update the following files:

### **1. Update CLAUDE.md**
Add to the "Code Structure & Modularity" section:
```markdown
### Distance Calculations - SSOT Compliance ✅

**Domain-Specific Utilities (Maintained Separation):**
- Racing tasks: `tasks/racing/RacingGeometryUtils.kt`
- AAT tasks: `tasks/aat/calculations/AATMathUtils.kt`
- UI/Widgets: `utils/DistanceUtils.kt`

**Rules:**
- ❌ Racing NEVER imports AAT utilities
- ❌ AAT NEVER imports Racing utilities
- ❌ No private Haversine implementations allowed
- ✅ Each domain uses only its own utility
```

### **2. Create DISTANCE_CALCULATIONS.md** (NEW)
Document the refactoring for future reference:
```markdown
# Distance Calculations - Architecture

## SSOT Implementation

This codebase follows SSOT principle for distance calculations while maintaining
complete task type separation.

### Domains

1. **Racing Domain**: Uses `RacingGeometryUtils.haversineDistance()`
2. **AAT Domain**: Uses `AATMathUtils.calculateDistanceKm()`
3. **Non-Task Code**: Uses `DistanceUtils.calculateDistanceKm()`

### History

- **2025-10-13**: Consolidated 20+ duplicate implementations → 3 domain-specific utilities
- **Reduction**: 85% code reduction
- **Benefit**: Single point of maintenance, zero cross-contamination

### Adding New Distance Calculations

**Wrong:**
```kotlin
private fun calculateDistance(...) {
    // Don't create new implementations!
}
```

**Right:**
```kotlin
// Racing code:
import com.example.xcpro.tasks.racing.RacingGeometryUtils
val distance = RacingGeometryUtils.haversineDistance(...)

// AAT code:
import com.example.xcpro.tasks.aat.calculations.AATMathUtils
val distance = AATMathUtils.calculateDistanceKm(...)

// UI code:
import com.example.xcpro.utils.DistanceUtils
val distance = DistanceUtils.calculateDistanceKm(...)
```
```

---

## 🎓 Learning & Context for Future Claude Instances

**When reading this document in a new context:**

1. **Understand the principles FIRST** (Section: Architectural Principles)
2. **Read Current State Analysis** to understand what's broken
3. **Follow implementation steps IN ORDER** (Phases 1-5)
4. **Use validation commands** after EVERY step
5. **Don't skip anti-patterns section** - these are real mistakes that can happen
6. **If unsure, STOP and ask** - don't guess

**Key Insight:**
This refactoring is NOT about creating one shared utility. It's about:
- Consolidating within each domain (SSOT within domain)
- Maintaining absolute separation between domains (Zero cross-contamination)
- Reducing maintenance burden (20+ → 3 implementations)

**The architecture trades "maximum code reuse" for "maximum domain isolation" because:**
- Competition safety requires zero risk of Racing/AAT calculation mixing
- Domain separation is more valuable than saving a few lines of code
- Each domain can evolve independently without affecting the other

---

## 📞 Support & Questions

**If you encounter issues while following this plan:**

1. **Compilation errors:** Check imports and function signatures match exactly
2. **Test failures:** Compare distance calculation results before/after (should be identical)
3. **Import violations:** Run validation commands to detect cross-contamination
4. **Unclear instructions:** STOP and ask for clarification - don't guess

**This document is living:** Update it if you discover better approaches or encounter edge cases not covered here.

---

**Version History:**
- v1.0 (2025-10-13): Initial comprehensive refactoring plan
