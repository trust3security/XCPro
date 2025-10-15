# App Review Checklist

- [x] Analyze project structure and main directories
- [x] Examine core app components and functionality
- [x] Review task management system architecture
- [x] Document key features and technologies used

---

# Android Gliding App - Deep Architecture Analysis

## Project Metadata
- **Package Name**: com.example.xcpro
- **Target SDK**: 36 (Android 14+)
- **Min SDK**: 30 (Android 11)
- **Build System**: Gradle 8.13 with Kotlin DSL
- **Kotlin Version**: 2.0.21
- **Total Files**: ~156 Kotlin files in main app module
- **Module Structure**: Multi-module with main app + dfcards library

---

# Android Gliding App - Code Structure & Functionality

## What the App Does

This is a professional gliding/soaring flight navigation app for Android that provides pilots with comprehensive flight management tools including:

- Real-time mapping with custom gesture controls
- Task planning for competitive flying (Racing, AAT, DHT)
- Weather integration via SkySight
- Flight data monitoring with customizable cards
- Profile management for different pilot configurations

## Code Structure

### Main Project Architecture

```
baseui1/                         # Root project (Gradle multi-module)
├── .gradle/                     # Gradle cache and configuration
├── .idea/                       # Android Studio project configuration
├── .claude/                     # Claude Code configuration & documentation
│   ├── agents/                  # Task planning documents
│   ├── commands/                # Custom commands
│   └── docs/                    # Project documentation
├── app/                         # Main Android application module
│   ├── build.gradle.kts        # App module build configuration
│   └── src/main/java/com/example/baseui1/
│       ├── MainActivity.kt      # Entry point (637 lines - navigation, status bar)
│       ├── MapScreen.kt         # Core map UI (637 lines - extracted to managers)
│       ├── NavigationDrawer.kt  # Navigation drawer implementation
│       ├── audio/               # Variometer audio feedback
│       ├── components/          # Reusable UI components
│       ├── gestures/            # Custom map gesture handling
│       ├── icons/               # Custom vector icons (Compose)
│       ├── location/            # Real-time GPS services
│       ├── map/                 # Map architecture managers
│       │   ├── components/      # Map UI components
│       │   └── layers/          # Map overlay layers
│       ├── profiles/            # User profile management
│       ├── screens/             # UI screens by feature
│       │   ├── airspace/        # Airspace settings
│       │   ├── flightdata/      # Flight data management
│       │   ├── navdrawer/       # Navigation drawer screens
│       │   ├── overlays/        # Map overlay controls
│       │   ├── skysight/        # Weather integration UI
│       │   ├── task/            # Task creation UI
│       │   └── utils/           # Screen utilities
│       ├── skysight/            # SkySight weather API client
│       ├── tasks/               # Task management (strict separation)
│       │   ├── aat/             # AAT task module
│       │   │   ├── areas/       # Area geometry calculations
│       │   │   ├── calculations/# AAT-specific calculations
│       │   │   └── models/      # AAT data models
│       │   ├── dht/             # DHT task module
│       │   │   └── models/      # DHT data models
│       │   └── racing/          # Racing task module
│       │       ├── models/      # Racing data models
│       │       └── turnpoints/  # Turn point calculators
│       ├── ui/                  # UI theme and styling
│       ├── useraccounts/        # User account management
│       └── utils/               # Utility classes
├── dfcards-library/             # Flight data cards module
│   ├── build.gradle.kts        # Library build configuration
│   └── src/main/java/com/example/dfcards/
│       ├── dfcards/             # Card system implementation
│       │   └── calculations/    # Flight calculations
│       └── filters/             # Data filtering
└── gradle/
    └── libs.versions.toml       # Centralized dependency management
```

## Key Technologies

- **UI Framework:** Jetpack Compose with Material Design 3
- **Mapping:** MapLibre Android SDK with custom overlays
- **Architecture:** Clean Architecture with ViewModels and Compose State
- **Build System:** Gradle with Kotlin DSL, Android SDK 36
- **Navigation:** Jetpack Navigation Compose

## Core Components

### 1. Map System (`MapScreen.kt`)
- MapLibre integration with custom tile servers
- Custom gesture controls:
  - Two-finger pan (not single finger)
  - Single finger vertical zoom
  - Single finger horizontal flight mode switching
- Real-time GPS tracking with blue location overlay
- Distance circles with screen-adaptive intervals
- 3-mode orientation: North-up, Track-up, Heading-up

### 2. Task Management System (`tasks/`)
Strictly separated by task type to prevent cross-contamination:

```
tasks/
├── TaskManager.kt              # Coordinator only - routes to task types
├── racing/                     # Racing task calculations & display
│   ├── RacingTaskCalculator.kt
│   ├── RacingTaskDisplay.kt
│   └── turnpoints/            # FAI sectors, cylinders, keyholes
├── aat/                        # Assigned Area Task calculations
│   ├── AATTaskCalculator.kt
│   ├── AATTaskDisplay.kt
│   └── areas/                 # Circle/sector area calculations
└── dht/                        # DHT task calculations
    ├── DHTTaskCalculator.kt
    └── DHTTaskDisplay.kt
```

**Critical Rule:** Zero shared code between Racing, AAT, and DHT modules to prevent bugs.

### 3. SkySight Weather Integration (`skysight/`)
- API client for weather data fetching
- Map overlay system for weather visualization
- Auto-loading and credential management
- Layer management for different weather types

### 4. Flight Data Cards (`dfcards-library/`)
- Modular card system for displaying flight metrics
- Profile-based templates with card libraries
- Real-time data sources and calculations
- Flight mode switching (Cruise, Thermal, Final Glide)

### 5. Profile Management (`profiles/`)
- User profile system with import/export
- Settings persistence per profile
- Profile indicator and selection UI

## Gesture System (Critical Feature)

Custom gesture requirements different from standard maps:

```kotlin
// ✅ WORKING Implementation in MapScreen.kt
when (fingerCount) {
    1 -> {
        if (abs(totalDragX) > abs(totalDragY)) {
            // Horizontal: Flight mode switching
        } else {
            // Vertical: Zoom in/out
        }
        // ❌ NO PANNING with single finger
    }
    2 -> {
        // ✅ Two-finger: Map panning
    }
}
```

## Architecture Patterns & Code Organization

### Package Organization Strategy
The codebase follows a **feature-based package structure** rather than layer-based:
- Each feature (e.g., `tasks`, `skysight`, `profiles`) has its own package
- Features contain all related components (UI, logic, models)
- Shared utilities and components are extracted to common packages

### Naming Conventions
- **Files**: PascalCase for classes (e.g., `MapScreen.kt`, `TaskManager.kt`)
- **Packages**: lowercase with feature grouping (e.g., `tasks.racing`, `screens.airspace`)
- **Composables**: PascalCase functions (e.g., `@Composable fun MapActionButtons()`)
- **State holders**: `*State` suffix (e.g., `MapScreenState`, `BottomSheetState`)
- **Managers**: `*Manager` suffix for coordinator classes (e.g., `TaskManagerCoordinator`)
- **ViewModels**: `*ViewModel` suffix (e.g., `FlightDataViewModel`, `ProfileViewModel`)

### Architectural Patterns

#### 1. Manager Pattern (Extracted from MapScreen)
The large MapScreen.kt (637 lines) has been refactored into specialized managers:
```kotlin
map/
├── MapCameraManager.kt      # Camera control logic
├── MapComposeEffects.kt     # Side effects management
├── MapInitializer.kt        # Map setup and configuration
├── MapLifecycleManager.kt   # Lifecycle handling
├── MapModalManager.kt       # Modal dialogs coordination
├── MapOverlayManager.kt     # Overlay rendering
├── MapScreenState.kt        # State management
├── MapTaskScreenManager.kt  # Task UI coordination
└── MapUIWidgetManager.kt    # UI widget management
```

#### 2. Task Type Separation (Critical Pattern)
Strict separation between Racing, AAT, and DHT tasks:
```kotlin
tasks/
├── TaskManagerCoordinator.kt  # Router only - no logic
├── racing/
│   ├── RacingTaskManager.kt   # Racing-specific logic
│   └── turnpoints/            # Turn point modules
│       ├── CylinderCalculator.kt
│       └── FAIQuadrantCalculator.kt
├── aat/
│   ├── AATTaskManager.kt      # AAT-specific logic
│   └── areas/                 # Area geometry modules
└── dht/
    └── DHTTaskManager.kt       # DHT-specific logic
```

#### 3. Compose State Management
- Uses `mutableStateOf()` for reactive UI state
- `rememberSaveable` for configuration changes
- ViewModels for business logic and data persistence
- State hoisting to parent composables

#### 4. Dependency Injection Strategy
- Manual dependency injection through constructors
- Context passed as parameter where needed
- SharedPreferences for persistence
- No DI framework (Dagger/Hilt) - keeps it simple

### Code Quality Patterns

#### SOLID Principles Implementation
1. **Single Responsibility**: Each class has one clear purpose
   - `RacingTaskCalculator` only calculates racing tasks
   - `SkysightClient` only handles API communication

2. **Open/Closed**: Extension through composition
   - New turn point types added without modifying existing ones
   - Task types extended through new modules

3. **Dependency Inversion**: Abstractions over implementations
   - `TurnPointCalculator` interface for all turn point types
   - `FlightDataProvider` interface for data sources

#### Safety Patterns
- **Fail-Fast**: Early validation and error detection
- **Null Safety**: Kotlin's null safety features used extensively
- **Immutable Data**: `data class` with `val` properties
- **Sealed Classes**: For restricted type hierarchies (task types, states)

### Testing Considerations
- Module separation enables isolated testing
- Calculator/Display separation allows independent verification
- Clear boundaries between features facilitate unit testing

## Development Patterns

- SOLID principles with dependency inversion
- Clean separation of concerns
- Fail-fast error handling for safety
- Version catalog dependency management
- Compose-first UI with Material Design
- Feature-based package organization
- Manager pattern for complex UI components
- Strict task type separation architecture

## Safety & Aviation Focus

- Screen wake lock for variometer operation
- Status bar transparency for maximum map visibility
- Critical bug prevention (e.g., auto-return disabled)
- Data protection (no `./gradlew clean` to preserve user data)
- Testing protocols for task distance calculations

---

# Claude Code IDE Environment & Available Tools

## Overview
This project is developed using **Claude Code** - Anthropic's official CLI for Claude that provides a comprehensive IDE-like environment with powerful development tools and integrations.

## Available Development Tools

### 🔧 Core File Operations
- **Read**: Read any file in the codebase with line numbers and syntax highlighting
- **Write**: Create new files with content
- **Edit**: Make precise edits to existing files using find-and-replace
- **MultiEdit**: Perform multiple edits to a single file in one operation
- **Glob**: Fast file pattern matching (e.g., `**/*.kt`, `src/**/*.java`)
- **Grep**: Powerful search across codebase using ripgrep with regex support

### 🏗️ Build & Development Tools
- **Bash**: Execute shell commands with proper Windows/Unix support
  - Gradle builds: `./gradlew assembleDebug`, `./gradlew build`
  - ADB operations: `adb install`, `adb logcat`, `adb shell`
  - Git operations: `git status`, `git add`, `git commit`
- **BashOutput**: Monitor output from long-running background processes
- **KillShell**: Terminate background processes when needed

### 📱 Android Development Integration
- **ADB Logcat Monitoring**: Real-time app debugging with filtered log streams
- **APK Installation**: Automated build and install workflows
- **Device Management**: Connect to Android devices and emulators
- **Background Process Management**: Monitor builds and tests

### 🌐 Web & Research Tools
- **WebFetch**: Fetch and analyze web content (especially useful for API docs)
- **WebSearch**: Search the web for current information
- **mcp__http-fetch__**: Advanced HTTP operations (raw text, rendered HTML, markdown)

### 🗃️ Version Control Integration (MCP Git Server)
- **mcp__git-server__git_status**: Show working tree status
- **mcp__git-server__git_add**: Stage files for commit
- **mcp__git-server__git_commit**: Create commits with messages
- **mcp__git-server__git_diff**: Show changes (staged, unstaged, or between branches)
- **mcp__git-server__git_log**: View commit history
- **mcp__git-server__git_branch**: List and manage branches
- **mcp__git-server__git_checkout**: Switch branches
- **mcp__git-server__git_show**: View commit details

### 🔍 IDE-Like Features
- **mcp__ide__getDiagnostics**: Get real-time code diagnostics and error detection
- **Syntax Highlighting**: Automatic syntax highlighting when reading code files
- **Error Detection**: Identify compilation and runtime issues
- **Code Intelligence**: Understanding of Kotlin, Java, XML, and Gradle files

### 📝 Project Management Tools
- **TodoWrite**: Create and manage structured task lists for development workflows
- **Task**: Launch specialized AI agents for complex, multi-step tasks
- **ExitPlanMode**: Transition from planning to implementation phases

### 📚 Notebook & Documentation Support
- **NotebookEdit**: Edit Jupyter notebooks (.ipynb files)
- **PDF Reading**: Read and analyze PDF documentation
- **Image Analysis**: View and analyze screenshots, diagrams, and UI mockups

## MCP (Model Context Protocol) Servers

### Git Server Integration
Provides native Git operations without shell commands:
- Full repository management
- Branch operations and history
- Staging and commit workflows
- Diff and status reporting

### HTTP Fetch Server
Advanced web content retrieval:
- Raw text fetching for APIs and data
- Rendered HTML for dynamic web content
- Markdown conversion for documentation
- Content summarization

### IDE Server
Development environment integration:
- Real-time error detection
- Code diagnostics and warnings
- Syntax validation
- Integration with language servers

## Development Workflow Support

### Automated Background Monitoring
Claude Code can run multiple concurrent adb logcat sessions to monitor:
- Map rendering and gesture handling
- Task management operations
- SkySight weather data integration
- Flight data card updates
- Location and sensor data

### Intelligent File Management
- **Pattern Matching**: Find files across large codebases instantly
- **Content Search**: Locate specific code patterns or text across all files
- **Batch Operations**: Perform multiple file operations efficiently
- **Safe Editing**: Precise find-replace with validation

### Build & Deploy Automation
- **Gradle Integration**: Native support for Android build system
- **APK Management**: Automated build, install, and deployment
- **Testing Support**: Run unit tests, integration tests, and UI tests
- **Continuous Monitoring**: Track build progress and deployment status

## Benefits for Development

### Context Awareness
Claude Code automatically understands:
- Project structure and dependencies
- Build system configuration (Gradle, version catalogs)
- Code architecture and patterns
- Testing frameworks and conventions
- Git repository state and history

### Safety & Data Protection
- **No Data Loss**: Tools designed to preserve user data and settings
- **Safe Operations**: Validation before destructive operations
- **Backup Awareness**: Automatic backup file detection
- **Error Recovery**: Robust error handling and recovery options

### Aviation-Specific Considerations
- **Safety-Critical Code**: Enhanced validation for flight-related calculations
- **Real-Time Monitoring**: Live debugging of location and sensor data
- **Performance Tracking**: Monitor app performance during flight operations
- **Data Integrity**: Ensure flight data and profiles remain intact

---

## Claude Code Auto-Discovery

**Answer to your question**: Claude Code automatically knows about all available tools through its built-in tool discovery system. Each new context window automatically has access to the complete tool suite without requiring manual configuration.

However, documenting the tools serves several important purposes:
1. **Human Reference**: Developers can understand what capabilities are available
2. **Context Optimization**: Helps users request the right type of assistance
3. **Workflow Planning**: Enables better planning of complex development tasks
4. **Tool Selection**: Guides choice of appropriate tools for specific tasks

The tools are always available - this documentation helps both humans and Claude use them more effectively.

---

This is a production-ready aviation app with professional-grade flight planning and navigation capabilities, built with modern Android development practices and strict safety considerations.
