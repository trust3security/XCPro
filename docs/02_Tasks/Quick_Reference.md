# Task Quick Reference

**Last Updated:** 2026-03-29
**Purpose:** Fast links and checks for Racing/AAT task work.

## Key Docs

- [Task_Type_Separation.md](./Task_Type_Separation.md)
- [Racing_Tasks.md](./Racing_Tasks.md)
- [AAT_Tasks.md](./AAT_Tasks.md)
- [Default_Values.md](./Default_Values.md)
- [../RACING_TASK/README.md](../RACING_TASK/README.md)
- [ARCHITECTURE.md](../ARCHITECTURE/ARCHITECTURE.md)
- [CODING_RULES.md](../ARCHITECTURE/CODING_RULES.md)
- [PIPELINE.md](../ARCHITECTURE/PIPELINE.md)

## Verification Commands

```bash
./gradlew enforceRules
./gradlew testDebugUnitTest
./gradlew assembleDebug
```

## Task Separation Checks

```bash
rg -n "import.*racing" feature/tasks/src/main/java/com/trust3/xcpro/tasks/aat
rg -n "import.*aat" feature/tasks/src/main/java/com/trust3/xcpro/tasks/racing
```
