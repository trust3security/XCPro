### ViewModel Dependency Rule (Corrected)

- Profile/session-scoped owners must not be grouped into dependency bags when injected into ViewModels.
- ViewModels must depend on individual, focused domain-facing seams instead of aggregated *Dependencies-style containers.
- Any compatibility grouping must be treated as a temporary shim and must not be normalized as a stable pattern in pipeline documentation.
