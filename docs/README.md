# XCPro Documentation

**Last Updated:** 2025-01-08
**Status:** ✅ Organized Structure

This directory contains **organized, consolidated documentation** for the XCPro Gliding App.

---

## 📂 Directory Structure

```
docs/
├── README.md (this file)
├── 01_Core/              ← Core development principles & rules
│   └── Task_Type_Separation.md
├── 02_Tasks/             ← Task type specifications
│   ├── Default_Values.md
│   ├── Racing_Tasks.md
│   └── AAT_Tasks.md
├── 03_Features/          ← System features
│   ├── Flight_Data_Cards.md
│   └── SkySight_Weather.md
└── 04_Reference/         ← Quick references & troubleshooting
    └── Quick_Reference.md
```

---

## 🚀 Quick Start

**New to the project?** Read in this order:
1. [Quick_Reference.md](./04_Reference/Quick_Reference.md) - Essential commands (5 min)
2. [../CLAUDE.md](../CLAUDE.md) - Master guide (30 min)
3. [Task_Type_Separation.md](./01_Core/Task_Type_Separation.md) - If working with tasks (15 min)

**Working on specific features?**
- Racing tasks → [Racing_Tasks.md](./02_Tasks/Racing_Tasks.md)
- AAT tasks → [AAT_Tasks.md](./02_Tasks/AAT_Tasks.md)
- Flight data cards → [Flight_Data_Cards.md](./03_Features/Flight_Data_Cards.md)
- Flight data cards SSOT refactor → [Flight_Data_Cards_SSOT_Refactor.md](./03_Features/Flight_Data_Cards_SSOT_Refactor.md)
- Weather → [SkySight_Weather.md](./03_Features/SkySight_Weather.md)

---

## 📋 What's What

### 01_Core/ - Development Fundamentals

**Must-read before coding:**
- **Task_Type_Separation.md** - CRITICAL rules for Racing/AAT isolation

### 02_Tasks/ - Task Type Specifications

**Complete guides for each task type:**
- **Racing_Tasks.md** - Racing task implementation (consolidated)
- **AAT_Tasks.md** - AAT task implementation (consolidated)
- **Default_Values.md** - Universal defaults (10km start, 3km finish, etc.)

### 03_Features/ - System Features

**Feature-specific documentation:**
- **Flight_Data_Cards.md** - DFCards system (current implementation)
- **Flight_Data_Cards_SSOT_Refactor.md** - SSOT refactoring plan (future implementation)
- **SkySight_Weather.md** - Weather integration

### 04_Reference/ - Quick Help

**Daily reference materials:**
- **Quick_Reference.md** - Command cheat sheet, common patterns, troubleshooting

---

## 🔄 Reorganization Notes

**Documentation was reorganized on 2025-01-08** to:
- ✅ Consolidate overlapping content
- ✅ Reduce duplication
- ✅ Improve discoverability
- ✅ Create single sources of truth

**Migration:** See [DOCS_INDEX.md](../DOCS_INDEX.md#-migration-guide) for old → new paths

**Backward Compatibility:** All old documentation paths still work!

---

## 📚 Complete Index

For the **full documentation map**, see [DOCS_INDEX.md](../DOCS_INDEX.md)

---

**Questions?** Start with [Quick_Reference.md](./04_Reference/Quick_Reference.md) or check [DOCS_INDEX.md](../DOCS_INDEX.md)
