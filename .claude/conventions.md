# Coding Conventions

## Android Development Standards

### Kotlin Code Style
- Follow [Android Kotlin Style Guide](https://developer.android.com/kotlin/style-guide)
- Use camelCase for functions and variables
- Use PascalCase for classes and interfaces
- Use UPPER_SNAKE_CASE for constants

### File Structure
```
app/src/main/java/com/example/baseui1/
├── activities/          # Activity classes
├── fragments/          # Fragment classes
├── adapters/           # RecyclerView adapters
├── models/             # Data models
├── tasks/              # Task-related modules
│   ├── racing/         # Racing task calculations
│   ├── aat/            # AAT task calculations
│   └── dht/            # DHT task calculations
├── utils/              # Utility classes
└── services/           # Background services
```

### Task Architecture Principles
- **Separation by Task Type**: Racing, AAT, and DHT tasks must have completely separate calculation modules
- **No Shared Calculation Logic**: Each task type implements its own calculator interface
- **Turn Point Type Separation**: Each turn point geometry (FAI Quadrant, Cylinder, Keyhole) has dedicated calculator/display modules
- **Single Algorithm Principle**: Visual display and mathematical calculation use the same geometry engine

### Interface Design
```kotlin
interface TaskCalculator {
    fun calculateDistance(waypoints: List<TaskWaypoint>): Double
    fun findOptimalPath(waypoints: List<TaskWaypoint>): List<Pair<Double, Double>>
}

interface TurnPointCalculator {
    fun calculateOptimalTouchPoint(waypoint: TaskWaypoint, prev: TaskWaypoint?, next: TaskWaypoint): Pair<Double, Double>
    fun calculateDistance(from: TaskWaypoint, to: TaskWaypoint): Double
    fun isWithinObservationZone(position: Pair<Double, Double>, waypoint: TaskWaypoint): Boolean
}
```

### Error Handling
- Use sealed classes for result types
- Implement proper exception handling for GPS and network operations
- Log errors with appropriate context

### Testing Requirements
- **Critical**: Test finish cylinder radius changes affect total task distance
- **Critical**: Test turnpoint cylinder radii modifications
- **Critical**: Test start line vs cylinder geometry switches
- Verify course display matches calculated optimal distance

### Naming Conventions
- Activities: `*Activity.kt` (e.g., `MainActivity.kt`)
- Fragments: `*Fragment.kt` (e.g., `TaskFragment.kt`)
- Calculators: `*Calculator.kt` (e.g., `RacingTaskCalculator.kt`)
- Display modules: `*Display.kt` (e.g., `RacingTaskDisplay.kt`)
- Validators: `*Validator.kt` (e.g., `RacingTaskValidator.kt`)

### Performance Guidelines
- Use ViewBinding for layout access
- Implement proper RecyclerView view recycling
- Minimize object allocations in calculation loops
- Cache expensive calculations where appropriate

### Security Considerations
- Never log authentication tokens
- Store sensitive data in encrypted preferences
- Validate all external input (SkySight API responses, user input)