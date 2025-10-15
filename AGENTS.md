# Repository Guidelines

## Project Structure & Module Organization
XCPro is a multi-module Android project built with Jetpack Compose. The primary UI lives in `app/src/main/java/com/example/xcpro`, with feature areas grouped by package (e.g., `tasks/`, `skysight/`, `vario/`). Shared UI and flight-data logic sit in the `dfcards-library` module. Android resources are under `app/src/main/res`. Unit and instrumentation tests mirror the source tree in each module’s `src/test` and `src/androidTest`. Reference documentation and design notes are collected in `docs/` and root-level `.md` files.

## Build, Test, and Development Commands
- `./gradlew :app:assembleDebug` – compile the mobile app and produce a debug build for local testing.
- `./gradlew test` – execute JVM unit tests across all modules.
- `./gradlew connectedAndroidTest` – run instrumentation and Compose UI tests on an attached emulator or device.
- `./gradlew :app:lint` – run Android lint; use before submitting to catch style and resource issues.
Run commands from the repository root. Clean stale outputs with `./gradlew clean` if Gradle caching causes unexpected failures.

## Coding Style & Naming Conventions
Write Kotlin using the standard JetBrains style: four-space indentation, trailing commas where helpful, and descriptive `camelCase` identifiers. Compose `@Composable` functions should use UpperCamelCase verbs or nouns that reflect the rendered UI (e.g., `TaskMapOverlay`). Keep files under ~500 lines as enforced in recent refactors; extract helpers into dedicated packages when behavior grows. Prefer immutable data classes, sealed hierarchies for state, and extension functions for formatting utilities.

## Testing Guidelines
JUnit4 powers JVM tests, while instrumentation suites use Espresso and Compose test APIs. Place unit tests alongside the feature under `src/test` with class names ending in `Test`. Instrumentation specs live under `src/androidTest` and should use descriptive method names following `shouldDoX_whenY`. Target the vario, task-planning, and SkySight integrations with regression cases—these areas have the tightest constraints. Ensure new features include at least one assertion around business logic or UI state.

## Commit & Pull Request Guidelines
Commits follow short, imperative summaries (`Refactor AGL calculation`, `Update roadmap`) without trailing punctuation. Group related changes into a single commit; leave drive-by cleanups for separate commits. Pull requests should include: a concise description of the change, testing notes (`test`, `connectedAndroidTest`, manual validation), links to relevant docs or issues, and screenshots/GIFs for visible UI updates. Highlight any schema, permission, or credential impacts so reviewers can double-check release readiness.

## Configuration & Secrets
Map credentials, SkySight tokens, and other API keys belong in `local.properties` or encrypted Gradle properties—never commit secrets. Use the provided `*.md` integration guides for setup checklists, and document new keys or feature flags in the `docs/` index before merging.
