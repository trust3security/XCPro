# 🚀 Parallel Development Guide for Racing, AAT, and DHT Task Types

## Executive Summary

This guide explains how to set up and run **three parallel Claude Code instances** to simultaneously develop and test Racing, AAT, and DHT task types independently. The architecture is already perfectly separated - this document shows how to leverage that separation for maximum development efficiency.

## 🎯 Goal

Enable three developers (or Claude agents) to work simultaneously on:
- **Window 1**: Racing Task Optimization
- **Window 2**: AAT Task Enhancement
- **Window 3**: DHT Task Implementation

Each window operates independently with:
- Separate Git branches/worktrees
- Independent builds
- Isolated testing
- Zero cross-contamination

## 📋 Prerequisites

### Required Tools
- Git 2.5+ (for worktree support)
- Android Studio or command line build tools
- 3 terminal windows or Claude Code instances
- ADB for device deployment
- Sufficient disk space (~3GB per worktree)

### Current Architecture Strengths
✅ **Perfect Task Separation**: Racing, AAT, and DHT modules are completely independent
✅ **Coordinator Pattern**: TaskManagerCoordinator.kt routes without shared logic
✅ **Independent Models**: Each task type has its own data models
✅ **Zero Cross-Imports**: No shared code between task types

## 🏗️ Setup Instructions

### Step 1: Create Parallel Development Environment

#### Option A: Git Worktrees (Recommended)
```bash
# From main project directory
# Create directory for parallel development trees
mkdir -p trees

# Create worktree for Racing development
git worktree add ./trees/racing-dev -b feature/racing-optimization

# Create worktree for AAT development
git worktree add ./trees/aat-dev -b feature/aat-enhancement

# Create worktree for DHT development
git worktree add ./trees/dht-dev -b feature/dht-implementation
```

#### Option B: Separate Clones
```bash
# Clone repository three times
git clone <repo> racing-dev
cd racing-dev && git checkout -b feature/racing-optimization

git clone <repo> aat-dev
cd aat-dev && git checkout -b feature/aat-enhancement

git clone <repo> dht-dev
cd dht-dev && git checkout -b feature/dht-implementation
```

### Step 2: Configure Each Development Instance

#### Window 1: Racing Task Development
```bash
cd trees/racing-dev

# Configure Git identity for clarity
git config user.name "Racing Developer"
git config user.email "racing@example.com"

# Verify you're on correct branch
git status
# On branch feature/racing-optimization
```

#### Window 2: AAT Task Development
```bash
cd trees/aat-dev

git config user.name "AAT Developer"
git config user.email "aat@example.com"

git status
# On branch feature/aat-enhancement
```

#### Window 3: DHT Task Development
```bash
cd trees/dht-dev

git config user.name "DHT Developer"
git config user.email "dht@example.com"

git status
# On branch feature/dht-implementation
```

## 🛠️ Development Workflow

### Parallel Development Rules

#### 1. **Strict Directory Boundaries**
Each developer/agent works ONLY in their assigned directories:

**Racing Developer (Window 1)**:
```
✅ ALLOWED to modify:
app/src/main/java/com/example/baseui1/tasks/racing/
├── RacingTaskManager.kt
├── RacingTaskCalculator.kt
├── RacingTaskDisplay.kt
├── RacingTaskMapOverlay.kt
├── models/
│   ├── RacingTask.kt
│   └── RacingWaypoint.kt
└── turnpoints/
    ├── CylinderCalculator.kt
    ├── FAIQuadrantCalculator.kt
    └── FinishLineDisplay.kt

❌ FORBIDDEN to modify:
- Any files in tasks/aat/
- Any files in tasks/dht/
- TaskManagerCoordinator.kt (unless coordinated)
```

**AAT Developer (Window 2)**:
```
✅ ALLOWED to modify:
app/src/main/java/com/example/baseui1/tasks/aat/
├── AATTaskManager.kt
├── AATTaskCalculator.kt
├── AATTaskDisplay.kt
├── AATTaskMapOverlay.kt
├── models/
│   └── AATWaypoint.kt
├── areas/
└── calculations/

❌ FORBIDDEN to modify:
- Any files in tasks/racing/
- Any files in tasks/dht/
```

**DHT Developer (Window 3)**:
```
✅ ALLOWED to modify:
app/src/main/java/com/example/baseui1/tasks/dht/
├── DHTTaskManager.kt
├── DHTTaskCalculator.kt
├── DHTTaskDisplay.kt
├── DHTTaskValidator.kt
└── models/
    └── DHTWaypoint.kt

❌ FORBIDDEN to modify:
- Any files in tasks/racing/
- Any files in tasks/aat/
```

#### 2. **Independent Building**
Each window can build independently:

```bash
# Window 1 (Racing)
cd trees/racing-dev
./gradlew assembleDebug
# Creates: app/build/outputs/apk/debug/app-debug.apk

# Window 2 (AAT)
cd trees/aat-dev
./gradlew assembleDebug

# Window 3 (DHT)
cd trees/dht-dev
./gradlew assembleDebug
```

#### 3. **Parallel Testing**

**Option 1: Sequential Device Testing**
```bash
# Test Racing changes
cd trees/racing-dev
adb install -r app/build/outputs/apk/debug/app-debug.apk
# Test Racing features...

# Test AAT changes
cd trees/aat-dev
adb install -r app/build/outputs/apk/debug/app-debug.apk
# Test AAT features...

# Test DHT changes
cd trees/dht-dev
adb install -r app/build/outputs/apk/debug/app-debug.apk
# Test DHT features...
```

**Option 2: Multiple Device/Emulator Testing**
```bash
# Deploy to different devices simultaneously
# Device 1 (Racing)
adb -s device1 install -r trees/racing-dev/app/build/outputs/apk/debug/app-debug.apk

# Device 2 (AAT)
adb -s device2 install -r trees/aat-dev/app/build/outputs/apk/debug/app-debug.apk

# Device 3 (DHT)
adb -s device3 install -r trees/dht-dev/app/build/outputs/apk/debug/app-debug.apk
```

## 🤖 Claude Code Agent Configuration

### Setting Up Three Claude Instances

#### Instance 1: Racing Task Agent
```bash
# Terminal 1
cd trees/racing-dev
claude-code

# Agent Instructions:
"You are the Racing Task specialist. Focus exclusively on:
- Racing task calculations and optimization
- FAI quadrant and cylinder geometry
- Racing task display and validation
- Work ONLY in app/src/main/java/com/example/baseui1/tasks/racing/
- DO NOT modify AAT or DHT code"
```

#### Instance 2: AAT Task Agent
```bash
# Terminal 2
cd trees/aat-dev
claude-code

# Agent Instructions:
"You are the AAT Task specialist. Focus exclusively on:
- AAT area calculations and optimization
- AAT waypoint management
- AAT task display and validation
- Work ONLY in app/src/main/java/com/example/baseui1/tasks/aat/
- DO NOT modify Racing or DHT code"
```

#### Instance 3: DHT Task Agent
```bash
# Terminal 3
cd trees/dht-dev
claude-code

# Agent Instructions:
"You are the DHT Task specialist. Focus exclusively on:
- DHT zone calculations
- DHT waypoint management
- DHT task display and validation
- Work ONLY in app/src/main/java/com/example/baseui1/tasks/dht/
- DO NOT modify Racing or AAT code"
```

### Agent Coordination Protocol

#### 1. **TaskManagerCoordinator Changes**
If changes to TaskManagerCoordinator.kt are needed:
```kotlin
// MUST be coordinated between all three agents
// One agent makes change, others pull update:

// Agent 1 makes coordinator change
git add app/src/main/java/com/example/baseui1/tasks/TaskManagerCoordinator.kt
git commit -m "Update coordinator for Racing enhancement"
git push

// Agents 2 & 3 pull the update
git fetch origin
git merge origin/feature/racing-optimization
```

#### 2. **Integration Points**
The ONLY allowed integration point is TaskManagerCoordinator.kt:
```kotlin
class TaskManagerCoordinator {
    fun routeToTaskManager(taskType: TaskType): TaskManager {
        return when (taskType) {
            TaskType.RACING -> racingTaskManager  // Racing agent domain
            TaskType.AAT -> aatTaskManager        // AAT agent domain
            TaskType.DHT -> dhtTaskManager        // DHT agent domain
        }
    }
}
```

## 🔍 Monitoring and Debugging

### Parallel Log Monitoring
Open three terminal windows for log monitoring:

```bash
# Window 1: Racing Logs
adb logcat -s "RacingTask" "RacingCalculator" "RacingDisplay" -v time

# Window 2: AAT Logs
adb logcat -s "AATTask" "AATCalculator" "AATDisplay" -v time

# Window 3: DHT Logs
adb logcat -s "DHTTask" "DHTCalculator" "DHTValidator" -v time
```

### Build Status Dashboard
```bash
# Check all builds simultaneously
echo "Racing Build:" && cd trees/racing-dev && ./gradlew assembleDebug --quiet && echo "✅ SUCCESS"
echo "AAT Build:" && cd trees/aat-dev && ./gradlew assembleDebug --quiet && echo "✅ SUCCESS"
echo "DHT Build:" && cd trees/dht-dev && ./gradlew assembleDebug --quiet && echo "✅ SUCCESS"
```

## 📊 Progress Tracking

### Git Branch Status
```bash
# View all worktrees and their status
git worktree list

# Output:
/path/to/baseui1              abcd123 [main]
/path/to/trees/racing-dev     efgh456 [feature/racing-optimization]
/path/to/trees/aat-dev        ijkl789 [feature/aat-enhancement]
/path/to/trees/dht-dev        mnop012 [feature/dht-implementation]
```

### Commit History Visualization
```bash
# See parallel development progress
git log --all --graph --oneline --decorate

# Output shows parallel branches:
* commit3 (feature/dht-implementation) DHT: Add zone validation
| * commit2 (feature/aat-enhancement) AAT: Optimize area calculations
|/
| * commit1 (feature/racing-optimization) Racing: Fix FAI quadrant
|/
* base (main) Initial separation complete
```

## 🔒 Safety Measures

### 1. **Cross-Contamination Prevention**
Each build automatically checks for forbidden imports:
```gradle
// In each task module's build.gradle
tasks.register('checkTaskSeparation') {
    doLast {
        // Racing module check
        if (file contains "import.*tasks.aat" || file contains "import.*tasks.dht") {
            throw GradleException("Cross-contamination detected!")
        }
    }
}
```

### 2. **Data Protection**
```bash
# NEVER use clean - it wipes user data
❌ FORBIDDEN: ./gradlew clean

# ALWAYS use assembleDebug
✅ SAFE: ./gradlew assembleDebug
```

### 3. **Merge Conflict Resolution**
Since each agent works in separate directories, conflicts are rare. If they occur:
```bash
# In TaskManagerCoordinator.kt conflicts:
git checkout --ours tasks/TaskManagerCoordinator.kt  # Keep your version
# OR
git checkout --theirs tasks/TaskManagerCoordinator.kt  # Take their version
# Then manually reconcile the routing logic
```

## 🚀 Advanced Techniques

### 1. **Automated Parallel Testing**
Create `parallel-test.sh`:
```bash
#!/bin/bash
echo "🏁 Starting parallel builds..."

(cd trees/racing-dev && ./gradlew assembleDebug) &
PID1=$!

(cd trees/aat-dev && ./gradlew assembleDebug) &
PID2=$!

(cd trees/dht-dev && ./gradlew assembleDebug) &
PID3=$!

wait $PID1 $PID2 $PID3
echo "✅ All builds complete!"
```

### 2. **Live Synchronization**
Use file watchers to sync common files:
```bash
# Watch for TaskManagerCoordinator changes
fswatch -o tasks/TaskManagerCoordinator.kt | xargs -n1 -I{} git pull
```

### 3. **Performance Comparison**
```bash
# Benchmark each task type independently
cd trees/racing-dev && ./gradlew connectedAndroidTest --tests="*Racing*"
cd trees/aat-dev && ./gradlew connectedAndroidTest --tests="*AAT*"
cd trees/dht-dev && ./gradlew connectedAndroidTest --tests="*DHT*"
```

## 📈 Success Metrics

### Development Velocity
- ✅ 3x faster development with parallel agents
- ✅ Zero merge conflicts in task-specific code
- ✅ Independent testing cycles
- ✅ No cross-contamination bugs

### Code Quality
- ✅ Each task type maintains its own standards
- ✅ Specialized optimization per task type
- ✅ Clear separation of concerns
- ✅ Easy to onboard new developers

### Architecture Benefits
- ✅ Racing bugs don't affect AAT/DHT
- ✅ AAT optimizations don't break Racing
- ✅ DHT can evolve independently
- ✅ Future task types easily added

## 🎯 Quick Start Checklist

- [ ] Create three worktrees or clones
- [ ] Configure Git identity for each
- [ ] Open three terminal/Claude instances
- [ ] Assign each agent to their task type
- [ ] Set up log monitoring terminals
- [ ] Create build status dashboard
- [ ] Test independent builds
- [ ] Verify no cross-contamination
- [ ] Start parallel development!

## 💡 Pro Tips

1. **Use Visual Indicators**: Color-code terminals (red=Racing, green=AAT, orange=DHT)
2. **Automated Builds**: Set up file watchers to auto-build on save
3. **Branch Protection**: Use Git hooks to prevent accidental cross-contamination
4. **Regular Sync**: Pull coordinator changes frequently to stay aligned
5. **Test Early**: Run tests after every significant change

## 🆘 Troubleshooting

### Issue: Build Conflicts
**Solution**: Ensure each worktree has clean build directories
```bash
cd trees/racing-dev && rm -rf build app/build
cd trees/aat-dev && rm -rf build app/build
cd trees/dht-dev && rm -rf build app/build
```

### Issue: Import Cross-Contamination
**Solution**: Use IDE search to find and remove forbidden imports
```bash
# Find Racing imports in AAT code (should return nothing)
grep -r "import.*tasks.racing" trees/aat-dev/app/src/main/java/com/example/baseui1/tasks/aat/
```

### Issue: Coordinator Conflicts
**Solution**: Designate one agent as coordinator owner for the session

## 📚 References

- [Git Worktree Documentation](https://git-scm.com/docs/git-worktree)
- [Android Gradle Build Guide](https://developer.android.com/studio/build)
- [Task Separation Architecture](CLAUDE.md#absolute-critical---task-type-separation)

---

**Remember**: The architecture is already perfectly separated. This guide simply shows how to leverage that separation for maximum parallel development efficiency. Each task type is an independent kingdom - respect the boundaries!