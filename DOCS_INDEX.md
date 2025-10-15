# XCPro Gliding App - Documentation Index

**Last Updated:** 2025-01-08
**Status:** ✅ Current - **Reorganized!** See [Migration Guide](#-migration-guide) for old → new paths

> **New to the project?** Start with the [Quick Start Guide](#-quick-start-read-in-order)

---

## 🚀 Quick Start (Read in Order)

### For New Developers
1. **[Quick_Reference.md](./docs/04_Reference/Quick_Reference.md)** - Essential commands and rules (5 min read)
2. **[CLAUDE.md](./CLAUDE.md)** - Master development guide (30 min read)
3. **[Default_Values.md](./docs/02_Tasks/Default_Values.md)** - Default values & task switching (10 min)

### For Code Changes
Before making any changes, check:
- ✅ [CLAUDE.md](#critical-rules) - "NEVER" rules and critical requirements
- ✅ [Quick_Reference.md](./docs/04_Reference/Quick_Reference.md) - Common gotchas
- ✅ [Task_Type_Separation.md](./docs/01_Core/Task_Type_Separation.md) - **MANDATORY** if touching tasks
- ✅ Relevant task type spec below

---

## 📚 Documentation by Topic

### 🎯 Core Development

| Document | Description | Size | Priority |
|----------|-------------|------|----------|
| [CLAUDE.md](./CLAUDE.md) | **Master development guide** - Philosophy, architecture, critical rules | 37KB | ⭐⭐⭐⭐⭐ |
| **[Task_Type_Separation.md](./docs/01_Core/Task_Type_Separation.md)** | **CRITICAL** - Task type isolation rules (consolidated) | 18KB | ⭐⭐⭐⭐⭐ |
| [Quick_Reference.md](./docs/04_Reference/Quick_Reference.md) | Quick command reference and common patterns | 12KB | ⭐⭐⭐⭐⭐ |
| [Default_Values.md](./docs/02_Tasks/Default_Values.md) | Default values, task switching behavior | 6KB | ⭐⭐⭐⭐ |

---

### 🏁 Racing Tasks

| Document | Description | When to Use |
|----------|-------------|-------------|
| **[Racing_Tasks.md](./docs/02_Tasks/Racing_Tasks.md)** | **Consolidated Racing guide** - Specs, defaults, examples | Working with Racing tasks |
| [racing_task_spec.md](./racing_task_spec.md) | Original Racing specification (reference) | Deep dive into Racing rules |
| [PRDRacingTask.md](./PRDRacingTask.md) | Product requirements for racing tasks | Understanding business requirements |
| [keyhole_task_spec.md](./keyhole_task_spec.md) | Keyhole turnpoint specification | Working with keyhole geometry |

**Key Files in Code:**
- `app/src/main/java/.../tasks/racing/` - Racing task implementation
- [racing/ARCHITECTURE.md](./app/src/main/java/com/example/xcpro/tasks/racing/ARCHITECTURE.md) - Module structure
- See [Task_Type_Separation.md](./docs/01_Core/Task_Type_Separation.md) for critical rules

---

### 🎯 AAT (Assigned Area Tasks)

| Document | Description | When to Use |
|----------|-------------|-------------|
| **[AAT_Tasks.md](./docs/02_Tasks/AAT_Tasks.md)** | **Consolidated AAT guide** - Specs, strategy, examples | ⭐ **Start here for AAT work** |
| [aat/ARCHITECTURE.md](./app/src/main/java/com/example/xcpro/tasks/aat/ARCHITECTURE.md) | AAT module architecture and refactoring status | Understanding AAT code organization |
| [aat_task_spec.md](./aat_task_spec.md) | Original AAT specification (reference) | Deep dive into FAI rules |
| [aat/validation/README.md](./app/src/main/java/com/example/xcpro/tasks/aat/validation/README.md) | AAT validation system documentation | Competition compliance validation |

**Historical/Reference Docs:**
- [AAT_IMPLEMENTATION_SUMMARY.md](./AAT_IMPLEMENTATION_SUMMARY.md) - Implementation summary
- [AAT_PIN_DRAGGING_IMPLEMENTATION.md](./AAT_PIN_DRAGGING_IMPLEMENTATION.md) - Movable pin details
- [aat-task-compliance-tester.md](./aat-task-compliance-tester.md) - Compliance testing

**Key Files in Code:**
- `app/src/main/java/.../tasks/aat/` - AAT task implementation
- See [Task_Type_Separation.md](./docs/01_Core/Task_Type_Separation.md) for critical rules

---

### 🛠️ System Features

#### Flight Data Cards (DFCards)
- **[Flight_Data_Cards.md](./docs/03_Features/Flight_Data_Cards.md)** - Complete DFCards system documentation
- [dfcards-library/REALTIME_DATA_IMPLEMENTATION.md](./dfcards-library/REALTIME_DATA_IMPLEMENTATION.md) - Real-time data integration
- [docs/df-cards-analysis-and-prd.md](./docs/df-cards-analysis-and-prd.md) - Product requirements

#### Weather Integration
- **[SkySight_Weather.md](./docs/03_Features/SkySight_Weather.md)** - SkySight weather integration guide
- [SKYSIGHT_UX_DESIGN.md](./SKYSIGHT_UX_DESIGN.md) - SkySight UX design guide

#### Map & Navigation
- **[Map_Orientation_System.md](./docs/03_Features/Map_Orientation_System.md)** - **Complete orientation reference** - NORTH_UP, TRACK_UP, HEADING_UP modes
- [docs/TRACK_UP_MODE.md](./docs/TRACK_UP_MODE.md) - Track-up map mode implementation (legacy)
- See [CLAUDE.md § Map Orientation System](./CLAUDE.md#map-orientation-system)
- See [CLAUDE.md § Custom Gesture Requirements](./CLAUDE.md#-critical---custom-gesture-requirements)

---

### ✈️ FAI Compliance & Competition Rules

| Document | Description |
|----------|-------------|
| [BGA_Start_Sector_Guide.md](./BGA_Start_Sector_Guide.md) | BGA start sector rules and implementation |
| [FAI_VERIFICATION_TEST.md](./FAI_VERIFICATION_TEST.md) | FAI compliance testing procedures |
| [aat/validation/README.md](./app/src/main/java/com/example/xcpro/tasks/aat/validation/README.md) | AAT validation against FAI Section 3 |

---

### 🔧 Development Workflow

| Document | Description |
|----------|-------------|
| [REFACTORING_ROADMAP.md](./REFACTORING_ROADMAP.md) | Future refactoring plans |
| [docs/Parallel_Development_Guide.md](./docs/Parallel_Development_Guide.md) | Parallel development best practices |
| [SYNC_INSTRUCTIONS.md](./SYNC_INSTRUCTIONS.md) | Project synchronization instructions |
| [docs/INITIAL.md](./docs/INITIAL.md) | Initial project setup |

---

## 🔍 Quick Reference Sections

### Critical Rules (Must Know)
See [CLAUDE.md](./CLAUDE.md) for complete details:

- 🚫 **NEVER** use `./gradlew clean` (wipes user data)
- 🚫 **NEVER** import Racing code in AAT modules (or vice versa)
- 🚫 **NEVER** enable standard MapLibre gestures (conflicts with custom gestures)
- 🚫 **NEVER** auto-return camera after user panning (causes navigation bugs)

### Default Values (Memorize)
See [TASK_TYPE_RULES.md](./TASK_TYPE_RULES.md):

| Item | Value | Applies to |
|------|-------|------------|
| Start line | 10km | All task types |
| Finish cylinder | 3km | All task types |
| Racing turnpoint | 0.5km | Racing only |
| AAT area | 10km | AAT only |

### Common Commands
See [QUICK_REFERENCE.md](./QUICK_REFERENCE.md) for full list:

```bash
# Build (safe - preserves data)
./gradlew assembleDebug
./deploy.bat

# NEVER use (wipes data)
./gradlew clean  # ⚠️ FORBIDDEN
```

---

## 🆘 Troubleshooting Guide

| Problem | Check This Document | Section |
|---------|---------------------|---------|
| Build fails | [CLAUDE.md](./CLAUDE.md) | § Common Development Commands |
| Task separation error | [CLAUDE.md](./CLAUDE.md) | § Task Type Separation |
| Default values wrong | [TASK_TYPE_RULES.md](./TASK_TYPE_RULES.md) | § Current Issues to Fix |
| AAT validation fails | [aat/validation/README.md](./app/src/main/java/com/example/xcpro/tasks/aat/validation/README.md) | § Validation Categories |
| Map gestures broken | [CLAUDE.md](./CLAUDE.md) | § Custom Gesture Requirements |
| Racing distance wrong | [CLAUDE.md](./CLAUDE.md) | § Critical Testing Requirements |
| Z-index layering issue | [CLAUDE.md](./CLAUDE.md) | § Z-Index Layering Reference |
| SkySight not working | [SkySightIntegrationGuide.md](./SkySightIntegrationGuide.md) | Full guide |

---

## 📝 Recent Changes

### 2025-01-08
- ✅ Created DOCS_INDEX.md (this file)
- ✅ Created QUICK_REFERENCE.md
- ✅ Added TOC to CLAUDE.md

### 2025-01-02
- ✅ AAT code cleanup - removed 1,220 lines dead code
- ✅ Updated [aat/ARCHITECTURE.md](./app/src/main/java/com/example/xcpro/tasks/aat/ARCHITECTURE.md)

---

## 📖 Reading Paths

### "I want to add a Racing task feature"
1. [racing_task_spec.md](./racing_task_spec.md) - Understand Racing requirements
2. [CLAUDE.md § Task Separation](./CLAUDE.md#-absolute-critical---task-type-separation) - Architecture rules
3. [CLAUDE.md § Critical Testing Requirements](./CLAUDE.md#-critical-testing-requirements) - Test finish radius!

### "I want to add an AAT task feature"
1. [AAT_Tasks.md](./docs/02_Tasks/AAT_Tasks.md) - Complete AAT guide
2. [aat/ARCHITECTURE.md](./app/src/main/java/com/example/xcpro/tasks/aat/ARCHITECTURE.md) - Module structure
3. [Task_Type_Separation.md](./docs/01_Core/Task_Type_Separation.md) - No cross-contamination!
4. [aat_task_spec.md](./aat_task_spec.md) - FAI rules reference

### "I want to modify DFCards"
1. [DFCARDS_SYSTEM.md](./DFCARDS_SYSTEM.md) - Complete system guide
2. [dfcards-library/REALTIME_DATA_IMPLEMENTATION.md](./dfcards-library/REALTIME_DATA_IMPLEMENTATION.md) - Real-time data

### "I just want to build and test"
1. [QUICK_REFERENCE.md](./QUICK_REFERENCE.md) - Commands cheat sheet
2. [CLAUDE.md § Common Development Commands](./CLAUDE.md#-common-development-commands) - Full command list

---

## 🎯 Document Health Status

| Category | Files | Status |
|----------|-------|--------|
| Core Guides | 3 | ✅ Current |
| Task Specs | 8 | ✅ Current |
| Feature Docs | 7 | ✅ Current |
| Compliance | 3 | ✅ Current |
| **Total** | **21** | **✅ Well Documented** |

**Last Audit:** 2025-01-08

---

**Need help?** Check [QUICK_REFERENCE.md](./QUICK_REFERENCE.md) first, then search this index for the relevant document.

---

## 🔄 Migration Guide

**Documentation was reorganized on 2025-01-08.** Here's where old files moved:

### Moved to New Locations

| Old Location | New Location | Status |
|--------------|--------------|--------|
| `QUICK_REFERENCE.md` | `docs/04_Reference/Quick_Reference.md` | ✅ Moved |
| `TASK_TYPE_RULES.md` | `docs/02_Tasks/Default_Values.md` | ✅ Moved |
| `DFCARDS_SYSTEM.md` | `docs/03_Features/Flight_Data_Cards.md` | ✅ Moved |
| `SkySightIntegrationGuide.md` | `docs/03_Features/SkySight_Weather.md` | ✅ Moved |

### New Consolidated Documents

| New File | Consolidates | Description |
|----------|--------------|-------------|
| **`docs/01_Core/Task_Type_Separation.md`** | CLAUDE.md + racing_task_spec.md + task separation rules from multiple files | **Single source of truth** for task separation rules |
| **`docs/02_Tasks/Racing_Tasks.md`** | racing_task_spec.md + PRDRacingTask.md + keyhole_task_spec.md | Complete Racing guide with all specs |
| **`docs/02_Tasks/AAT_Tasks.md`** | aat_task_spec.md + AAT implementation plans + multiple AAT docs | Complete AAT guide with strategy |

### Old Files (Still Available)

Original files remain in root for backward compatibility:
- `CLAUDE.md` - Still the master guide (unchanged except TOC added)
- `racing_task_spec.md` - Reference (now supplemented by Racing_Tasks.md)
- `aat_task_spec.md` - Reference (now supplemented by AAT_Tasks.md)
- Historical AAT docs (AAT_FIXES_PATCH.md, etc.) - Kept for reference

### Breaking Changes

**None!** All old paths still work. New paths provide:
- ✅ Better organization
- ✅ Consolidated information
- ✅ Reduced duplication
- ✅ Easier navigation

