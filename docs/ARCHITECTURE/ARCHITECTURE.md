## Dependency Direction

Allowed dependency flow (canonical default):
UI -> domain -> data

Rules:
- UI must not depend on low-level data sources or infrastructure adapters.
- UI/ViewModels may depend on stable domain-facing seams only.
- Domain must not depend on Android or UI types.
- Domain defines repository/data-source interfaces (ports) for external I/O used by business logic.
- Data layer implements those ports via adapters.
- Engines/use-cases depend on ports, not concrete Android/data implementations.
- Core pipeline components are provided by DI, never constructed inside managers.

Clarification:
- The canonical flow is UI -> UseCase -> Repository.
- However, ViewModels may directly depend on focused, stable owner/port seams when:
  - the seam is authoritative,
  - the API is narrow and domain-facing,
  - a UseCase wrapper would be rename-only boilerplate.

Forbidden:
- Direct dependency on low-level data sources (DAOs, network clients, sensors).
- Dependency on broad dependency bags or service locators.

---

### Allowed Flow (Canonical)

Sensors / Data Sources
  -> Repository (SSOT)
    -> UseCase (derive / filter)
      -> ViewModel (UI State)
        -> UI (render only)

Clarification:
- This is the default and preferred flow.
- Direct ViewModel -> focused domain seam access is allowed when it avoids rename-only UseCase wrappers and preserves clean boundaries.
