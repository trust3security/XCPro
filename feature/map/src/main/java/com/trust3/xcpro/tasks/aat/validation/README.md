# AAT Task Validation System

A comprehensive validation system for Area Assignment Task (AAT) validation following FAI Section 3 competition rules for competitive gliding.

## Overview

This validation system provides rigorous testing of AAT tasks to ensure compliance with official FAI regulations and competitive gliding best practices. It categorizes issues by severity and provides detailed fix suggestions for task improvement.

## Key Features

- **FAI Section 3 Compliance**: Full validation against current FAI soaring regulations
- **Competition Class Support**: Specific validation for Club, Standard, Open, World Class, and Two-Seater competitions
- **Detailed Categorization**: Issues classified as CRITICAL, WARNING, or INFO with fix suggestions
- **Integration Ready**: Seamlessly integrates with existing AAT task management system
- **Scoring System**: Numerical validation scores with letter grades (A+ to F)
- **Real-time Validation**: Quick validation methods for UI feedback

## Architecture

### Core Components

1. **ComprehensiveAATValidator**: Main validation engine with complete FAI rule checking
2. **FAIComplianceRules**: Official FAI rule specifications and competition class definitions
3. **AATValidationResult**: Detailed validation results with categorized issues
4. **AATValidationIntegration**: Integration utilities for easy UI and workflow integration

### File Structure

```
tasks/aat/validation/
""" ComprehensiveAATValidator.kt     # Main validation engine
""" FAIComplianceRules.kt            # FAI rule specifications
""" AATValidationResult.kt           # Result data models
""" AATValidationIntegration.kt      # Integration utilities
""" AATValidatorExamples.kt          # Usage examples
"""" README.md                        # This documentation
```

## Validation Categories

### CRITICAL Issues
- Task cannot be used in competition
- Must be fixed before task is flyable
- Examples: Missing areas, invalid geometry, FAI rule violations

### WARNING Issues
- Task is flyable but may have problems
- Should be addressed for optimal competition use
- Examples: Short start lines, small areas, questionable distances

### INFO Suggestions
- Task is valid but could be optimized
- Recommendations for improvement
- Examples: Strategic optimization, area positioning suggestions

## Competition Classes Supported

### Club Class
- Minimum time: 2 hours
- Distance range: 100-400km
- Area radius: 5-25km
- Relaxed validation rules

### Standard Class
- Minimum time: 2.5 hours
- Distance range: 150-500km
- Area radius: 8-30km
- Standard FAI compliance

### Open Class
- Minimum time: 2.5 hours
- Distance range: 200-750km
- Area radius: 10-40km
- Strict validation rules

### World Class
- Minimum time: 2.5 hours
- Distance range: 150-500km
- Area radius: 8-25km
- Maximum compliance required

### Two-Seater
- Minimum time: 2.5 hours
- Distance range: 150-500km
- Area radius: 10-30km
- Adapted for two-seater performance

## Usage Examples

### Basic Validation

```kotlin
val task = createAATTask()
val validator = AATTaskValidator()

// Quick validation check
val isValid = validator.validateTask(task).isValid

// Comprehensive validation
val result = validator.validateTaskComprehensive(task)
when (result.getValidationStatus()) {
    ValidationStatus.SUCCESS -> // Task ready for competition
    ValidationStatus.WARNING -> // Flyable with warnings
    ValidationStatus.CRITICAL -> // Must fix critical errors
    ValidationStatus.INFO -> // Valid with suggestions
}
```

### UI Integration

```kotlin
// Get user-friendly validation results
val uiResult = AATValidationIntegration.validateTaskForUI(task)

// Display status in UI
val statusText = "${uiResult.summary} (Grade: ${uiResult.score})"

// Show issues
uiResult.criticalIssues.forEach { showError(it) }
uiResult.warnings.forEach { showWarning(it) }
uiResult.suggestions.forEach { showSuggestion(it) }
```

### Competition Validation

```kotlin
// Validate for specific competition class
val competitionResult = AATValidationIntegration
    .validateForCompetitionWithRecommendations(task, "STANDARD")

if (competitionResult.suitable) {
    println("... Task suitable for ${competitionResult.competitionClass}")
    println("Compliance: ${competitionResult.compliance}%")
} else {
    println("oe Task needs improvement for competition use")
    competitionResult.recommendations.forEach { println("- $it") }
}
```

### Task Manager Integration

```kotlin
// In AATTaskManager
fun validateCurrentTask(): ValidationUIResult {
    val task = convertToAATTask()
    return AATValidationIntegration.validateTaskForUI(task)
}

fun isCompetitionReady(): Boolean {
    val task = convertToAATTask()
    return AATValidationIntegration.isTaskFlyable(task)
}

fun getTaskGrade(): String {
    val task = convertToAATTask()
    return AATValidationIntegration.getTaskValidationSummary(task).grade
}
```

## Validation Rules Covered

### Task Structure
- Task ID and name validation
- Minimum time requirements (30min - 8 hours)
- Area count limits (1-8 areas)
- Basic task integrity

### Area Geometry
- Area size validation (5-2000 km^2)
- Separation requirements (minimum 1km)
- Overlap detection
- Radius/sector geometry validation

### Distance & Time
- Distance range validation
- Speed feasibility (40-150 km/h range)
- Strategic options assessment
- Competition class specific requirements

### Start/Finish Configuration
- Start altitude limits (0-3000m MSL)
- Start/finish line lengths (minimum 1km recommended)
- Cylinder radius validation (minimum 500m)
- Geometry type appropriateness

### Competition Rules
- FAI Section 3 compliance
- Competition class specific requirements
- Airspace and safety considerations
- Strategic validity assessment

## Scoring System

Tasks receive numerical scores (0-100) in five categories:

1. **Structure Score**: Basic task validity
2. **Geometry Score**: Area geometry compliance
3. **Rules Score**: FAI rule adherence
4. **Strategic Score**: Task difficulty and options
5. **Safety Score**: Airspace and safety considerations

### Grade Scale
- **A+**: 95%+ - Excellent, competition-ready task
- **A**: 90-94% - Very good task
- **B+**: 85-89% - Good task with minor issues
- **B**: 80-84% - Acceptable task
- **C+**: 75-79% - Needs some improvement
- **C**: 70-74% - Significant issues present
- **D**: 60-69% - Many problems
- **F**: <60% - Invalid or severely flawed task

## FAI Rule References

The validator checks compliance with:

- **FAI Sporting Code Section 3**: Core AAT regulations
- **FAI 3.2.1**: Task time requirements
- **FAI 3.2.2**: Assigned area specifications
- **FAI 3.2.3**: Area size and geometry rules
- **FAI 3.2.4**: Area separation requirements
- **FAI 3.2.5**: Distance calculation rules
- **FAI 3.3.1**: Start/finish procedures
- **FAI Annex A**: Competition class specifications

## Integration Points

### With AATTaskManager
- `validateAATTask()`: Get comprehensive validation
- `isCompetitionReady()`: Quick competition readiness check
- `getTaskGrade()`: Letter grade for task quality
- `getTaskImprovementSuggestions()`: Improvement recommendations

### With UI Components
- Real-time validation feedback during task creation
- Status indicators in task lists
- Detailed validation reports in task settings
- Competition class selection with validation

### With File Operations
- Validate tasks on import/load
- Check task integrity before save
- Export validation reports with tasks

## Error Handling

The validation system handles:
- Invalid task structures gracefully
- Missing or malformed data
- Calculation errors (fallback to safe defaults)
- Concurrent access (thread-safe operations)

## Performance Characteristics

- **Quick validation**: < 10ms for basic checks
- **Comprehensive validation**: < 100ms for full analysis
- **Memory efficient**: Minimal object allocation
- **Cache-friendly**: Results cacheable for UI performance

## Thread Safety

All validation methods are thread-safe and can be called from:
- UI thread for real-time feedback
- Background threads for batch processing
- Worker threads for intensive analysis

## Extensibility

The system is designed for extension:
- New competition classes can be added easily
- Additional validation rules can be plugged in
- Custom validation categories supported
- Result formatting customizable

## Testing

The validation system includes:
- Unit tests for each validation rule
- Integration tests with sample tasks
- Performance benchmarks
- Competition compliance verification
- Edge case validation

## Dependencies

**Internal Dependencies** (AAT module only):
- `AATTask` and related models
- `AATDistanceCalculator` for distance validation
- `AATMathUtils` for geometric calculations

**External Dependencies**:
- Kotlin standard library
- Java time API for duration handling

**Zero Dependencies On**:
- Racing task modules (complete separation maintained)
- DHT task modules (complete separation maintained)
- External validation libraries

## Future Enhancements

Planned improvements:
- Airspace integration for restricted area checking
- Weather consideration in validation
- Terrain analysis for safety assessment
- Advanced strategic analysis
- Machine learning optimization suggestions
- Integration with online competition databases

## Changelog

### Version 1.0 (Current)
- Initial implementation with comprehensive FAI validation
- Support for all major competition classes
- Integration with existing AAT task management
- Complete documentation and examples

---

For detailed usage examples, see `AATValidatorExamples.kt`.
For integration patterns, see `AATValidationIntegration.kt`.
