# Project Context - AI Reading Guide

**Last Updated:** 2025-01-08
**Purpose:** Tells Claude Code which documentation to read for each task type

> **For Claude:** This file maps tasks AND file paths to required reading. ALWAYS check this first to know what to read!

---

## 🗂️ File Path Detection - "When Editing X, Read Y"

### When Editing Files in `tasks/aat/`
**Pattern:** `app/.../tasks/aat/**/*.kt`

**MUST READ FIRST:**
1. `docs/01_Core/Task_Type_Separation.md` - Separation rules
2. `docs/02_Tasks/AAT_Tasks.md` - AAT specifications
3. `app/.../tasks/aat/ARCHITECTURE.md` - AAT module structure

**CRITICAL CHECKS:**
- ❌ NO Racing imports (`import.*racing`)
- ✅ Use AATMathUtils only
- ✅ Green task lines (#4CAF50)

---

### When Editing Files in `tasks/racing/`
**Pattern:** `app/.../tasks/racing/**/*.kt`

**MUST READ FIRST:**
1. `docs/01_Core/Task_Type_Separation.md` - Separation rules
2. `docs/02_Tasks/Racing_Tasks.md` - Racing specifications
3. `app/.../tasks/racing/ARCHITECTURE.md` - Racing module structure

**CRITICAL CHECKS:**
- ❌ NO AAT imports (`import.*aat`)
- ✅ Use RacingMathUtils only
- ✅ Blue task lines (#0066FF)
- ✅ Test finish radius changes

---

### When Editing Files in `dfcards-library/`
**Pattern:** `dfcards-library/**/*.kt`

**MUST READ FIRST:**
1. `docs/03_Features/Flight_Data_Cards.md` - DFCards system
2. `dfcards-library/REALTIME_DATA_IMPLEMENTATION.md` - Real-time data

**CRITICAL CHECKS:**
- ✅ Z-index: Cards at 2f, edit mode at 10f
- ✅ Independent card state

---

### When Editing `MapScreen.kt` or Map UI
**Pattern:** `MapScreen.kt` or `*Orientation*.kt` or `Compass*.kt`

**MUST READ FIRST:**
1. `CLAUDE.md § Custom Gesture Requirements`
2. `CLAUDE.md § Z-Index Layering Reference`

**CRITICAL CHECKS:**
- ❌ NO standard MapLibre gestures
- ✅ Two-finger pan only

---

## 📖 AI Reading Map - "When Doing X, Read Y"

### 🏁 When Working on Racing Tasks

**ALWAYS READ FIRST:**
1. `docs/01_Core/Task_Type_Separation.md` - **CRITICAL** - Separation rules
2. `docs/02_Tasks/Racing_Tasks.md` - Racing specifications and patterns

**THEN READ AS NEEDED:**
- `racing_task_spec.md` - Detailed Racing specification (reference)
- `PRDRacingTask.md` - Product requirements
- `app/.../tasks/racing/ARCHITECTURE.md` - Racing module structure
- `keyhole_task_spec.md` - If working with keyhole geometry

**CRITICAL REMINDERS:**
- ❌ NEVER import AAT code
- ✅ Use RacingMathUtils (not shared utilities)
- ✅ ALWAYS test finish radius changes
- ✅ Blue task lines only (#0066FF)

---

### 🎯 When Working on AAT Tasks

**ALWAYS READ FIRST:**
1. `docs/01_Core/Task_Type_Separation.md` - **CRITICAL** - Separation rules
2. `docs/02_Tasks/AAT_Tasks.md` - AAT specifications and patterns

**THEN READ AS NEEDED:**
- `aat_task_spec.md` - Detailed AAT specification (reference)
- `app/.../tasks/aat/ARCHITECTURE.md` - AAT module structure
- `app/.../tasks/aat/validation/README.md` - If working on validation

**CRITICAL REMINDERS:**
- ❌ NEVER import Racing code
- ✅ Use AATMathUtils (not shared utilities)
- ✅ Validate minimum time ≥ 30 minutes
- ✅ Green task lines only (#4CAF50)

---

### 🎴 When Working on Flight Data Cards (DFCards)

**ALWAYS READ FIRST:**
1. `docs/03_Features/Flight_Data_Cards.md` - DFCards system overview

**THEN READ AS NEEDED:**
- `dfcards-library/REALTIME_DATA_IMPLEMENTATION.md` - Real-time data
- `docs/df-cards-analysis-and-prd.md` - Product requirements

**CRITICAL REMINDERS:**
- ✅ Z-index: Normal cards at 2f, edit mode at 10f
- ✅ Profile-based templates
- ✅ Independent card state

---

### 🌦️ When Working on SkySight/Weather

**ALWAYS READ FIRST:**
1. `docs/03_Features/SkySight_Weather.md` - SkySight integration guide

**THEN READ AS NEEDED:**
- `SKYSIGHT_UX_DESIGN.md` - UX design guide

**CRITICAL REMINDERS:**
- ✅ Store credentials securely (encrypted preferences)
- ✅ Z-index: Weather overlay at 3.5f

---

### 🗺️ When Working on Map Features

**ALWAYS READ FIRST:**
1. `CLAUDE.md § Custom Gesture Requirements` - Gesture system
2. `CLAUDE.md § Z-Index Layering Reference` - Layer ordering

**THEN READ AS NEEDED:**
- `docs/TRACK_UP_MODE.md` - Track-up mode implementation

**CRITICAL REMINDERS:**
- ❌ NEVER enable standard MapLibre gestures
- ✅ Two-finger pan only
- ✅ Single-finger: vertical = zoom, horizontal = mode switch

---

### 🛠️ When Doing General Development

**ALWAYS READ FIRST:**
1. `CLAUDE.md` - Master development guide (use TOC for navigation)
2. `docs/04_Reference/Quick_Reference.md` - Command cheat sheet

**CRITICAL REMINDERS:**
- ❌ NEVER use `./gradlew clean` (wipes user data)
- ✅ Use `./gradlew assembleDebug` for builds
- ✅ Test through Android Studio when possible

---

### 🧪 When Writing Tests

**ALWAYS READ:**
- `CLAUDE.md § Testing Strategy` - TDD approach
- Task-specific docs (Racing/AAT) for test patterns

---

### 🔧 When Refactoring

**ALWAYS READ:**
- `CLAUDE.md § Code Structure & Modularity` - File/function limits
- `REFACTORING_ROADMAP.md` - Planned refactoring

---

### 🚨 When Fixing Bugs

**ALWAYS CHECK:**
- `CLAUDE.md § Critical Bug Prevention Rules` - Known bug patterns
- `CLAUDE.md § Critical Bug Fixes - Learned Lessons` - Historical fixes

---

## 🎯 Decision Tree for Claude

```
What task are you doing?
│
├─ Racing task changes?
│  └─> READ: Task_Type_Separation.md + Racing_Tasks.md
│
├─ AAT task changes?
│  └─> READ: Task_Type_Separation.md + AAT_Tasks.md
│
├─ DFCards changes?
│  └─> READ: Flight_Data_Cards.md
│
├─ Map/gestures changes?
│  └─> READ: CLAUDE.md § Gestures + § Z-Index
│
├─ Build/deploy?
│  └─> READ: Quick_Reference.md
│
└─ Not sure?
   └─> READ: CLAUDE.md (master guide), then follow links
```

---

## 📚 Document Hierarchy (Most Important First)

### Tier 1: ALWAYS Read (Critical Rules)
1. `CLAUDE.md` - Master guide
2. `docs/01_Core/Task_Type_Separation.md` - If touching tasks
3. `docs/04_Reference/Quick_Reference.md` - Common commands

### Tier 2: Read for Specific Work
4. `docs/02_Tasks/Racing_Tasks.md` - Racing work
5. `docs/02_Tasks/AAT_Tasks.md` - AAT work
6. `docs/03_Features/*.md` - Feature-specific work

### Tier 3: Reference (As Needed)
7. Original specs (racing_task_spec.md, aat_task_spec.md)
8. PRDs (PRDRacingTask.md, etc.)
9. Historical docs (AAT_FIXES_PATCH.md, etc.)

---

## 🔍 Quick Checks Before Coding

### Task Type Work (Racing/AAT)
```
✅ Read Task_Type_Separation.md?
✅ Read task-specific guide (Racing_Tasks.md or AAT_Tasks.md)?
✅ Verified no cross-imports?
✅ Using task-specific math utils?
```

### Map/UI Work
```
✅ Read CLAUDE.md § Gestures?
✅ Checked Z-Index Layering Reference?
✅ Verified gesture requirements?
```

### Build/Deploy
```
✅ Read Quick_Reference.md?
✅ Using ./gradlew assembleDebug (NOT clean)?
✅ Checked safe commands?
```

---

## 📝 Notes for Claude

**Context Window Optimization:**
- Read ONLY what's needed for current task
- Use this file as index, don't read everything
- Follow decision tree to minimize reading

**When Uncertain:**
1. Check this file first
2. Read CLAUDE.md TOC to find relevant section
3. Ask user if still unclear

**Duplication Handling:**
- If same info in multiple files, read the consolidated version
- Consolidated docs are in `docs/` subdirectories
- Original specs are reference only

---

## 🚨 Critical Project Rules (Quick Reference)

### Data Protection
- ❌ **NEVER** use `./gradlew clean` - wipes user data
- ✅ Use `./gradlew assembleDebug` for builds

### Task Type Separation
- ❌ **NEVER** import Racing in AAT (or vice versa)
- ✅ Each task type is 100% autonomous
- ✅ Use task-specific math utilities (RacingMathUtils, AATMathUtils)

### Map Gestures
- ❌ **NEVER** enable standard MapLibre gestures
- ✅ Two-finger pan, single-finger zoom/mode-switch only

### Testing Requirements
- ✅ **ALWAYS** test Racing finish radius changes affect distance
- ✅ Verify task distance calculations after geometry changes

---

**Last Updated:** 2025-01-08
**Version:** 2.0 (Reorganized as AI reading map)
