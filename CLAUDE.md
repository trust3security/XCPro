# CLAUDE.md

**Last Updated:** 2025-10-13
**Status:** ✅ Current (Compacted)

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

---

## 📖 Table of Contents

- [Development Philosophy](#-development-philosophy)
- [Single Source of Truth (SSOT)](#-absolute-critical---single-source-of-truth-ssot)
- [Critical Data Protection](#-critical---data-protection)
- [Custom Gesture Requirements](#-critical---custom-gesture-requirements)
- [Common Development Commands](#-common-development-commands)
- [Project Architecture](#-project-architecture)
- [Development Patterns](#-development-patterns)
- [Code Structure & Modularity](#-code-structure--modularity)
- [Gliding App Requirements](#-gliding-app-requirements)
- [Z-Index Layering Reference](#-z-index-layering-reference)
- [Critical Testing Requirements](#-critical-testing-requirements)
- [Task Type Separation](#-absolute-critical---task-type-separation)
- [Enforcement Checklist](#-enforcement-checklist)

> **Quick Start:** See [QUICK_REFERENCE.md](./QUICK_REFERENCE.md) for essential commands and [DOCS_INDEX.md](./DOCS_INDEX.md) for complete documentation.

---

# Android Gliding App - Development Guide

## 🚨 SUB-AGENT NOTIFICATION REQUIREMENT

**CRITICAL**: When calling ANY sub-agent, notify user FIRST with: `I AM CALLING [AGENT-NAME] SUB-AGENT`

Examples: `I AM CALLING CODE-REVIEWER SUB-AGENT`, `I AM CALLING AAT-TASK-VALIDATOR SUB-AGENT`

## 🎯 DEVELOPMENT PHILOSOPHY

### Core Principles
- **KISS**: Simple solutions over complex architectures
- **YAGNI**: Don't add features until needed
- **DRY**: Reuse components, eliminate duplication
- **Fail Fast**: Check errors early, provide clear feedback
- **Readable Code**: Self-documenting, safety-critical aviation code

### Design Principles
- **Dependency Inversion**: High-level logic independent of low-level implementations
- **Open/Closed**: Open for extension, closed for modification
- **Single Responsibility**: One clear purpose per function/class/module
- **Fail Fast**: Catch sensor/data errors immediately

## 🚨 ABSOLUTE CRITICAL - SINGLE SOURCE OF TRUTH (SSOT)

### ⚠️ COMPETITION SAFETY REQUIREMENT
**THIS APP IS USED IN GLIDING COMPETITIONS - DATA INCONSISTENCIES CAN HAVE SERIOUS CONSEQUENCES**

#### 🎯 PRIORITY #1 - VISUAL-CALCULATION CONSISTENCY (MOST CRITICAL)
**Map geometry MUST exactly match calculated distances:**

- ❌ **Map shows 45.2km, calculator uses 47.8km** → Pilot disqualified
- ❌ **Course line displayed ≠ scored** → Invalid results
- ✅ **Map visuals = Calculations = Scoring** → Competition valid

**ENFORCEMENT**: Every geometry type MUST use single algorithm for BOTH visual display AND distance calculation.

### MANDATORY SSOT PRINCIPLE
**EVERY piece of data MUST have exactly ONE authoritative source.**

**DEFINITION**:
1. ✅ **One canonical data store** - All reads from this source
2. ✅ **One update path** - All writes to this source
3. ✅ **Zero duplication** - No cached/copied values
4. ✅ **Reactive updates** - UI auto-refreshes when source changes
5. ✅ **No manual sync** - Never copy data between sources

### ✅ CORRECT SSOT PATTERN

```kotlin
// ✅ CORRECT: AATRadiusAuthority is SSOT for all AAT radii
object AATRadiusAuthority {
    fun getRadiusForWaypoint(waypoint: AATWaypoint): Double {
        return waypoint.assignedArea.radiusMeters / 1000.0 // SINGLE ALGORITHM
    }
}

// ✅ UI reads from authority
val displayRadius = AATRadiusAuthority.getRadiusForWaypoint(waypoint)

// ✅ Calculations read from authority
val taskDistance = calculator.calculate(
    radius = AATRadiusAuthority.getRadiusForWaypoint(waypoint)
)
```

### 🚫 FORBIDDEN SSOT VIOLATIONS

1. **❌ Dual Data Stores** - Two sources for same data (will desync!)
2. **❌ Manual Synchronization** - Copying data between sources (brittle!)
3. **❌ Cached Values Without Reactivity** - Stale data displayed
4. **❌ Duplicate Logic** - Same calculation in multiple places
5. **❌ Non-Reactive Capture** - Data frozen in time

### ✅ SSOT IMPLEMENTATION CHECKLIST

**BEFORE WRITING ANY DATA HANDLING CODE:**
- [ ] **Identify SSOT**: What is THE canonical source?
- [ ] **Single read path**: All consumers read from SSOT
- [ ] **Single write path**: All updates go to SSOT
- [ ] **Reactive binding**: UI/calculations auto-update
- [ ] **Zero duplication**: No cached/derived values
- [ ] **Single algorithm**: Same code for display + calculation

### 📚 SSOT EXAMPLES IN CODEBASE

1. ✅ `AATRadiusAuthority` - All AAT radii
2. ✅ `currentAATTask` - AAT task state
3. ✅ `currentRacingTask` - Racing task state
4. ✅ Turn point calculators - Geometry per type
5. ✅ `MapOrientationManager` - Map orientation state

### 🚨 MANDATORY SSOT FOR TASKS

**ABSOLUTE REQUIREMENTS:**
1. **NO duplicate properties** - One data store only
2. **NO manual sync** - Data updates automatically
3. **NO dual algorithms** - Visual + calculations use SAME code
4. **NO cached values** - UI reads from SSOT reactively

**BEFORE WRITING AAT/RACING/TURNPOINT CODE:**
- [ ] ONE clear source of truth?
- [ ] UI reactively reads from that source?
- [ ] Calculations use SAME algorithm as visual?
- [ ] ZERO manual synchronization?

**IF YOU VIOLATE SSOT IN TASK CODE → YOU BREAK COMPETITION SAFETY**

## ⚠️ CRITICAL - Data Protection

### NEVER USE `./gradlew clean`
- **FORBIDDEN**: `./gradlew clean` wipes ALL user data
- **USE INSTEAD**: `./gradlew assembleDebug` for regular builds
- **EXCEPTION**: Only if user explicitly requests and understands data loss

## 🖐️ CRITICAL - CUSTOM GESTURE REQUIREMENTS

### ✅ IMPLEMENTED Custom Gestures
1. ✅ **TWO-FINGER PAN**: Only two fingers pan map
2. ✅ **ONE-FINGER VERTICAL ZOOM**: Up/down zooms
3. ✅ **ONE-FINGER HORIZONTAL**: Left/right changes flight mode
4. ✅ **ONE-FINGER NO PANNING**: Single finger blocked from panning

### ❌ NEVER Enable Standard MapLibre Gestures
- **FORBIDDEN**: `map.uiSettings.isScrollGesturesEnabled = true`
- **FORBIDDEN**: `map.uiSettings.isZoomGesturesEnabled = true`
- **REQUIRED**: Use custom Compose gesture handlers

## 📚 DOCUMENTATION STRUCTURE

**All technical reference .md files are at project root** for easy access and AI assistant visibility.

### Available Documentation

```
XCPro/
├── AGL.md                          ← AGL technical reference (sensors, accuracy, formulas)
├── AGL_IMPLEMENTATION.md           ← AGL implementation log (KISS approach)
├── CLAUDE.md                       ← This file (Claude Code instructions)
├── REFACTORING_ROADMAP.md          ← Refactoring progress tracker
├── BAROMETRIC_ALTITUDE.md          ← (future) Barometric altitude deep dive
├── SENSORS.md                      ← (future) Phone sensors reference
└── TASK_SYSTEM.md                  ← (future) Racing/AAT architecture
```

### When to Check Documentation

**BEFORE working on a subsystem, check if a .md file exists for it:**

| If working on... | Read this file first... |
|------------------|-------------------------|
| AGL calculation, terrain, or altitude | [AGL.md](./AGL.md) |
| QNH calibration, barometric sensors | [AGL.md](./AGL.md) |
| Phone GPS, sensors, accuracy | [AGL.md](./AGL.md) |
| Code refactoring, file structure | [REFACTORING_ROADMAP.md](./REFACTORING_ROADMAP.md) |
| General development guidelines | [CLAUDE.md](./CLAUDE.md) (this file) |

**Example workflow:**
```
User: "Can you improve the AGL calculation accuracy?"
Claude: 1. Read AGL.md for technical background
        2. Understand sensor limitations (GPS vertical ±10-30m)
        3. Review current implementation approach
        4. Propose improvements with context
```

### Creating New Documentation

**When to create a new .md file:**
- ✅ Complex subsystem with >500 lines of code
- ✅ Technical background needed (physics, algorithms)
- ✅ Multiple approaches considered (document decisions)
- ✅ Future maintainer needs context
- ✅ Reference data (sensor specs, formulas, limits)

**Format:**
- Place at project root
- Use descriptive name (e.g., `BAROMETRIC_ALTITUDE.md`)
- Include: Overview, Technical Details, Architecture, Known Issues
- Link from CLAUDE.md in documentation table

## 🔧 COMMON DEVELOPMENT COMMANDS

### Building and Testing
```bash
# Build debug APK (preserves user data)
./gradlew assembleDebug

# Install with replacement (keeps data)
adb install -r app/build/outputs/apk/debug/app-debug.apk

# Deploy scripts (Windows)
./deploy.bat          # Builds and installs
./build-only.bat      # Builds only
./install-only.bat    # Installs only

# Launch app
adb shell monkey -p com.example.xcpro.debug -c android.intent.category.LAUNCHER 1
```

### Debugging
```bash
# Monitor logs
adb logcat -s "MapScreen" "TaskManager" "MapOrientationManager" -v time
adb logcat -s "GPS" "Location" -v time
adb logcat -s "SkysightClient" -v time

# Check permissions
adb shell dumpsys package com.example.xcpro.debug | grep -A 20 "permissions:"
```

## 🏗️ PROJECT ARCHITECTURE

### Core Components
Professional gliding app with MapLibre mapping, task management, flight data visualization.

**Key Modules:**
- **Main App**: MapScreen, Navigation, MainActivity
- **DFCards**: Flight data cards system
- **Task System**: Racing and AAT (completely separated)
- **SkySight**: Weather data API integration
- **Profiles**: User profile management

### Technology Stack
- **UI**: Jetpack Compose + Material Design
- **Mapping**: MapLibre Android SDK
- **Architecture**: Clean Architecture, ViewModels, Compose State
- **Build**: Gradle with Kotlin DSL, Version Catalog (`libs.versions.toml`)
- **Sensors**: GPS, magnetometer, accelerometer

### File Organization
```
app/src/main/java/com/example/baseui1/
├── MainActivity.kt, MapScreen.kt
├── MapOrientationManager.kt, CompassWidget.kt
├── profiles/
├── screens/ (flightdata/, task/, skysight/)
├── skysight/
├── tasks/
│   ├── racing/ (Racing tasks only - ZERO AAT imports)
│   └── aat/ (AAT tasks only - ZERO Racing imports)
└── utils/
```

## 📋 DEVELOPMENT PATTERNS

### Code Standards
- **UI**: Jetpack Compose + Kotlin DSL exclusively
- **Build**: Gradle with Kotlin DSL, Version Catalog only
- **Dependencies**: Use `libs.xxx` references
- **Architecture**: Compose State, ViewModels, clean separation

### File Limits
- **Max 500 lines per file** - Refactor if approaching limit
- **Functions under 50 lines** - Single responsibility
- **Classes under 100 lines** - Single concept
- **Module organization** - Group by feature

### Testing Strategy
- **TDD** for critical flight calculations
- **Test pyramid**: Unit > Integration > UI tests
- **Mock externals**: Network, sensors, file system
- **Test edge cases**: Failures, invalid data, boundaries

### Security Best Practices
- **Never log sensitive data**: Credentials, API keys, personal info
- **Validate all inputs**: User input, API responses, files
- **Secure defaults**: Fail securely, minimum permissions
- **Encrypted storage**: Use for sensitive data (SkySight tokens)

## 🧱 CODE STRUCTURE & MODULARITY

### Kotlin Best Practices
- **Immutable data classes**: `data class` with `val` properties
- **Null safety**: Avoid `!!` operator in safety-critical code
- **Extension functions**: For utility functions
- **Coroutines**: Structured concurrency for GPS/sensors
- **Sealed classes**: For restricted hierarchies (task types, modes)
- **Type-safe builders**: DSL for complex configurations

## 🎯 GLIDING APP REQUIREMENTS

### Fixed Aircraft Icon Positioning - CRITICAL
**Icon must be FIXED on screen, NOT a map element:**
1. **Icon represents pilot** - "You are here" at fixed position
2. **Map moves, icon doesn't** - World slides beneath icon
3. **Screen overlay** - NOT geographic map element
4. **Position**: Center or 65% down for forward visibility
5. **Only rotation changes** - Based on orientation mode

### Real-Time Movement Tracking
1. **Continuous Updates**: Every 1-2 seconds (100ms ideal)
2. **Stable Zoom**: User controls zoom - NEVER auto-zoom
3. **Smooth Panning**: Map slides smoothly beneath icon
4. **No Surprises**: No jumps, flashing, or unexpected changes

### Critical Bug Prevention

**❌ NEVER REINTRODUCE AUTO-RETURN BUG:**
```kotlin
// ❌ FORBIDDEN: Automatic camera tracking after user pan
if (isTrackingLocation) { map.animateCamera(...) } // NO!

// ✅ REQUIRED: Only manual return
if (false) { // Permanently disabled auto-tracking
```

**SOLUTION**: Keep auto-tracking disabled, manual return button only.

## 📊 Z-INDEX LAYERING REFERENCE

### Current Z-Index Hierarchy
```
┌───────────────────────────────────────────┐
│  1.0f   Map Base Layers                  │
│  2.0f   CardContainer (flight data)      │
│  3.0f   Task Overlay (turnpoints/areas)  │
│  3.0f   Variometer Widget                │
│  3.5f   Skysight Weather                 │
│  4.0f   Hamburger Menu                   │
│  5.0f   Flight Mode Indicator            │
│  5.0f   Compass Widget                   │
│  10.0f  Cards in Edit Mode               │
│  11.0f  AAT FAB                          │
│  50.0f  Distance Circles FAB             │
└───────────────────────────────────────────┘
```

### MapLibre Layer Order
```kotlin
style.addLayerAbove(layer, "road-label")  // ✅ CORRECT
style.addLayer(layer)                     // ❌ WRONG - adds at bottom
```

**Key Lessons:**
- Cards at 2f normally, 10f in edit mode
- Task overlay at 3f (above cards, below FABs)
- UI widgets at 4f-5f (always accessible)
- `addLayerAbove()` for waypoints (not `addLayer()`)

## ⚠️ CRITICAL TESTING REQUIREMENTS

### Racing Task Distance Calculations
**ALWAYS TEST when changing racing calculations:**
1. **Finish Cylinder Radius**: Change radius, verify distance updates
2. **Turnpoint Radii**: Modify radii, ensure optimal touch points used
3. **Start Line vs Cylinder**: Switch types, verify geometry used

**Display Testing:**
- Course line matches calculated distance
- Course touches cylinder edges at optimal points
- Visual represents actual FAI racing line

### Aircraft Icon Testing
**Current Status:**
- Icon at camera center (not GPS position)
- Camera follows GPS in all orientation modes
- 10Hz updates for smooth tracking

**Known Issue:** Icon flashes every 100ms (GeoJSON redraws)

**Proper Solutions:**
1. Canvas overlay at fixed screen coordinates (recommended)
2. Static marker + camera movement illusion
3. LocationComponent with custom rendering

## 🚨 ABSOLUTE CRITICAL - TASK TYPE SEPARATION

### ZERO CROSS-CONTAMINATION RULE - MANDATORY
**Racing and AAT MUST BE COMPLETELY ISOLATED - ZERO shared code, models, or logic.**

### 🚫 FORBIDDEN PATTERNS
1. ❌ Using Racing calculators for AAT tasks
2. ❌ Using AAT models in Racing code
3. ❌ Sharing geometry calculations
4. ❌ Using `when (taskType)` in calculations
5. ❌ Importing Racing classes in AAT (or vice versa)
6. ❌ Shared base classes between task types
7. ❌ Cross-referencing task type constants

### ✅ MANDATORY REQUIREMENTS

**EACH TASK TYPE MUST HAVE:**
- ✅ Own calculator, model, display, validator classes
- ✅ Own geometry classes (Racing ≠ AAT)
- ✅ Own directory - NO imports between types

**Required Structure:**
```
tasks/
├── TaskManager.kt (coordinator only)
├── racing/
│   ├── RacingTaskCalculator.kt
│   ├── RacingTaskDisplay.kt
│   └── turnpoints/ (FAI, Cylinder, Keyhole calculators)
└── aat/
    ├── AATTaskCalculator.kt
    └── AATTaskDisplay.kt
```

### 🔒 ENFORCED SEPARATION RULES

**Import Audit:**
```kotlin
// ❌ FORBIDDEN in AAT: import com.example.xcpro.tasks.racing.*
// ❌ FORBIDDEN in Racing: import com.example.xcpro.tasks.aat.*
// ✅ ALLOWED: import com.example.xcpro.tasks.aat.models.*
```

**TaskManager Pattern:**
```kotlin
// ✅ REQUIRED: Delegation only, NO shared calculation logic
fun updateRadius(taskType: TaskType, radius: Double) {
    when (taskType) {
        TaskType.RACING -> racingCalculator.updateRadius(radius)
        TaskType.AAT -> aatCalculator.updateAreaRadius(radius)
    }
}
```

### Turn Point Type Separation - ✅ IMPLEMENTED

**STATUS**: ✅ COMPLETED (Oct 2025) - Single-algorithm principle enforced

**Architecture**: Dedicated calculator + display pairs per turn point type
- `FAIQuadrantCalculator.kt` + `FAIQuadrantDisplay.kt`
- `CylinderCalculator.kt` + `CylinderDisplay.kt`
- `KeyholeCalculator.kt` + `KeyholeDisplay.kt`
- `TurnPointInterfaces.kt` (defines contracts)

**Benefits:**
- ✅ Visual rendering + math use SAME geometry engine
- ✅ No dual algorithms
- ✅ Bug fixes isolated per geometry type

## 🚨 ENFORCEMENT CHECKLIST

**BEFORE MERGING ANY CODE:**

### ❌ Cross-Contamination Detection
- [ ] No Racing imports in AAT modules
- [ ] No AAT imports in Racing modules
- [ ] No shared calculation functions
- [ ] No `when (taskType)` in calculations
- [ ] No shared geometry classes

### ✅ Proper Separation Validation
- [ ] Each task type has own calculator/model/display
- [ ] TaskManager only routes, doesn't calculate
- [ ] All imports stay within task-specific directories
- [ ] Each module can be tested independently

### 🔍 Code Review Questions
1. **"Does this change affect other task types?"** → If YES, it's wrong
2. **"Could I delete Racing without breaking AAT?"** → Should be YES
3. **"Are there shared enums/constants?"** → Should be NO
4. **"Does calculation depend on task type detection?"** → Should be NO

### 🚫 IMMEDIATE RED FLAGS
- `import racing.*` in AAT code
- `when (taskType)` in calculation functions
- Cross-task-type function parameters
- Shared base calculation classes

**REMEMBER: One contaminated function breaks the entire separation architecture!**
