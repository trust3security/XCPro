#!/usr/bin/env python3
from __future__ import annotations

import re
from dataclasses import dataclass
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
MAX_REPORT_LINES = 200
APP_BUILD_GRADLE = ROOT / "app" / "build.gradle.kts"
EXPECTED_APPLICATION_ID = "com.example.openxcpro"
EXPECTED_DEBUG_APPLICATION_ID_SUFFIX = ".debug"

FORBIDDEN_PATTERNS = [
    r"\bSystem\.currentTimeMillis\(\)",
    r"\bSystem\.nanoTime\(\)",
    r"\bSystemClock\.",
    r"\bInstant\.now\(",
    r"\bLocalDateTime\.now\(",
    r"\bZonedDateTime\.now\(",
    r"\bOffsetDateTime\.now\(",
    r"\bCalendar\.getInstance\(",
    r"\bDate\(",
    r"\bkotlin\.system\.getTimeMillis\(",
]

ALLOW_DIR_PARTS = {
    "src/test",
    "src/androidTest",
    "buildSrc",
    "scripts",
    ".github",
    "gradle",
}

# Adapter files that are allowed to bridge platform time APIs to Clock-like abstractions.
ALLOW_PRODUCTION_FILES = {
    "core/time/src/main/java/com/example/xcpro/core/time/Clock.kt",
    "feature/map/src/main/java/com/example/xcpro/orientation/OrientationClock.kt",
}

KOTLIN_FILE_EXTS = {".kt", ".kts"}


@dataclass(frozen=True)
class Violation:
    path: Path
    line_number: int
    pattern: str
    line_text: str


def as_repo_relative(path: Path) -> str:
    return str(path.relative_to(ROOT)).replace("\\", "/")


def is_allowed_path(path: Path) -> bool:
    rel = as_repo_relative(path)
    if any(part in rel for part in ALLOW_DIR_PARTS):
        return True
    return rel in ALLOW_PRODUCTION_FILES


def find_violations(path: Path) -> list[Violation]:
    violations: list[Violation] = []
    text = path.read_text(encoding="utf-8", errors="replace")
    for idx, line in enumerate(text.splitlines(), start=1):
        for pattern in FORBIDDEN_PATTERNS:
            if re.search(pattern, line):
                violations.append(
                    Violation(
                        path=path,
                        line_number=idx,
                        pattern=pattern,
                        line_text=line.strip(),
                    )
                )
    return violations


def main() -> int:
    violations: list[Violation] = []
    for path in ROOT.rglob("*"):
        if not path.is_file() or path.suffix not in KOTLIN_FILE_EXTS:
            continue
        if is_allowed_path(path):
            continue
        violations.extend(find_violations(path))

    if violations:
        print("\nARCH GATE FAILED: forbidden time API usage found in production code\n")
        for violation in violations[:MAX_REPORT_LINES]:
            rel = as_repo_relative(violation.path)
            print(
                f"- {rel}:{violation.line_number} matched /{violation.pattern}/ -> {violation.line_text}"
            )
        if len(violations) > MAX_REPORT_LINES:
            remaining = len(violations) - MAX_REPORT_LINES
            print(f"... and {remaining} more")
        print(f"\nTotal violations: {len(violations)}")
        print(
            "Fix: route time through core/time Clock (or approved adapter abstraction) instead of direct APIs."
        )
        return 1

    app_id_errors = find_application_id_contract_errors()
    if app_id_errors:
        print("\nARCH GATE FAILED: app identity contract drift detected\n")
        for error in app_id_errors:
            print(f"- {error}")
        print(
            "\nFix: keep application identity stable, or document and execute a profile migration plan in-repo."
        )
        return 1

    print("ARCH GATE PASSED")
    return 0


def find_application_id_contract_errors() -> list[str]:
    if not APP_BUILD_GRADLE.exists():
        return [f"Missing file: {as_repo_relative(APP_BUILD_GRADLE)}"]

    text = APP_BUILD_GRADLE.read_text(encoding="utf-8", errors="replace")
    errors: list[str] = []

    app_id_match = re.search(r'applicationId\s*=\s*"([^"]+)"', text)
    if app_id_match is None:
        errors.append(
            f"{as_repo_relative(APP_BUILD_GRADLE)}: missing defaultConfig applicationId"
        )
    else:
        actual_app_id = app_id_match.group(1)
        if actual_app_id != EXPECTED_APPLICATION_ID:
            errors.append(
                f"{as_repo_relative(APP_BUILD_GRADLE)}: applicationId is '{actual_app_id}' "
                f"but must be '{EXPECTED_APPLICATION_ID}'"
            )

    debug_block_match = re.search(
        r"debug\s*\{(?P<body>.*?)\n\s*}",
        text,
        flags=re.DOTALL,
    )
    if debug_block_match is None:
        errors.append(
            f"{as_repo_relative(APP_BUILD_GRADLE)}: missing debug buildTypes block"
        )
        return errors

    debug_body = debug_block_match.group("body")
    debug_suffix_match = re.search(r'applicationIdSuffix\s*=\s*"([^"]+)"', debug_body)
    if debug_suffix_match is None:
        errors.append(
            f"{as_repo_relative(APP_BUILD_GRADLE)}: debug applicationIdSuffix missing "
            f"(expected '{EXPECTED_DEBUG_APPLICATION_ID_SUFFIX}')"
        )
    else:
        actual_suffix = debug_suffix_match.group(1)
        if actual_suffix != EXPECTED_DEBUG_APPLICATION_ID_SUFFIX:
            errors.append(
                f"{as_repo_relative(APP_BUILD_GRADLE)}: debug applicationIdSuffix is "
                f"'{actual_suffix}' but must be '{EXPECTED_DEBUG_APPLICATION_ID_SUFFIX}'"
            )

    return errors


if __name__ == "__main__":
    raise SystemExit(main())
