#!/usr/bin/env python3
from __future__ import annotations

import os
import re
from dataclasses import dataclass
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
MAX_REPORT_LINES = 200
APP_BUILD_GRADLE = ROOT / "app" / "build.gradle.kts"
EXPECTED_APPLICATION_ID = "com.trust3.xcpro"
EXPECTED_DEBUG_APPLICATION_ID_SUFFIX = ".debug"
RAW_LOG_ALLOWLIST = ROOT / "config" / "quality" / "raw_log_allowlist.txt"
EXCLUDED_DIR_NAMES = {
    ".git",
    ".gradle",
    ".idea",
    "artifacts",
    "build",
}

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
FORBIDDEN_REGEXES = tuple(re.compile(pattern) for pattern in FORBIDDEN_PATTERNS)

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
    "core/time/src/main/java/com/trust3/xcpro/core/time/Clock.kt",
    "feature/map/src/main/java/com/trust3/xcpro/orientation/OrientationClock.kt",
}

KOTLIN_FILE_EXTS = {".kt", ".kts"}
PRODUCTION_KOTLIN_FILE_EXTS = {".kt"}
RAW_LOG_PATTERN = re.compile(r"\bLog\.(d|i|w|e|v)\b")


@dataclass(frozen=True)
class Violation:
    path: Path
    line_number: int
    pattern: str
    line_text: str


@dataclass(frozen=True)
class RawLogDrift:
    path: str
    actual_count: int
    allowed_count: int | None


def as_repo_relative(path: Path) -> str:
    return str(path.relative_to(ROOT)).replace("\\", "/")


def is_allowed_relative_path(rel: str) -> bool:
    if any(part in rel for part in ALLOW_DIR_PARTS):
        return True
    return rel in ALLOW_PRODUCTION_FILES


def is_production_kotlin_relative_path(rel: str) -> bool:
    return rel.endswith(".kt") and "/src/main/" in rel


def iter_repo_kotlin_files() -> list[Path]:
    kotlin_files: list[Path] = []
    for current_root, dirnames, filenames in os.walk(ROOT):
        dirnames[:] = [
            dirname for dirname in dirnames if dirname not in EXCLUDED_DIR_NAMES
        ]
        current_path = Path(current_root)
        for filename in filenames:
            if Path(filename).suffix not in KOTLIN_FILE_EXTS:
                continue
            kotlin_files.append(current_path / filename)
    kotlin_files.sort(key=as_repo_relative)
    return kotlin_files


def find_violations(path: Path, text: str) -> list[Violation]:
    violations: list[Violation] = []
    for idx, line in enumerate(text.splitlines(), start=1):
        for pattern, regex in zip(FORBIDDEN_PATTERNS, FORBIDDEN_REGEXES):
            if regex.search(line):
                violations.append(
                    Violation(
                        path=path,
                        line_number=idx,
                        pattern=pattern,
                        line_text=line.strip(),
                    )
                )
    return violations


def load_raw_log_allowlist() -> dict[str, int]:
    if not RAW_LOG_ALLOWLIST.exists():
        raise FileNotFoundError(f"Missing allowlist: {as_repo_relative(RAW_LOG_ALLOWLIST)}")

    allowances: dict[str, int] = {}
    for line_number, raw_line in enumerate(
        RAW_LOG_ALLOWLIST.read_text(encoding="utf-8", errors="replace").splitlines(),
        start=1,
    ):
        line = raw_line.strip()
        if not line or line.startswith("#"):
            continue
        parts = [part.strip() for part in line.split("|")]
        if len(parts) < 2:
            raise ValueError(
                f"{as_repo_relative(RAW_LOG_ALLOWLIST)}:{line_number} invalid entry '{raw_line}'"
            )
        rel_path = parts[0]
        try:
            allowed_count = int(parts[1])
        except ValueError as exc:
            raise ValueError(
                f"{as_repo_relative(RAW_LOG_ALLOWLIST)}:{line_number} invalid count '{parts[1]}'"
            ) from exc
        allowances[rel_path] = allowed_count
    return allowances


def count_raw_log_lines(text: str) -> int:
    return sum(1 for line in text.splitlines() if RAW_LOG_PATTERN.search(line))


def scan_repo_kotlin_files(
    kotlin_files: list[Path],
) -> tuple[list[Violation], dict[str, int]]:
    violations: list[Violation] = []
    actual_raw_log_counts: dict[str, int] = {}
    for path in kotlin_files:
        rel = as_repo_relative(path)
        should_scan_time = not is_allowed_relative_path(rel)
        should_count_raw_logs = is_production_kotlin_relative_path(rel)
        if not should_scan_time and not should_count_raw_logs:
            continue

        text = path.read_text(encoding="utf-8", errors="replace")
        if should_scan_time:
            violations.extend(find_violations(path, text))
        if should_count_raw_logs:
            count = count_raw_log_lines(text)
            if count > 0:
                actual_raw_log_counts[rel] = count

    return violations, actual_raw_log_counts


def find_raw_log_drift(
    actual_counts: dict[str, int], allowances: dict[str, int]
) -> list[RawLogDrift]:
    drift: list[RawLogDrift] = []
    for rel, actual_count in sorted(actual_counts.items()):
        allowed_count = allowances.get(rel)
        if allowed_count is None:
            drift.append(
                RawLogDrift(path=rel, actual_count=actual_count, allowed_count=None)
            )
        elif actual_count > allowed_count:
            drift.append(
                RawLogDrift(
                    path=rel, actual_count=actual_count, allowed_count=allowed_count
                )
            )
    return drift


def main() -> int:
    kotlin_files = iter_repo_kotlin_files()
    violations, actual_raw_log_counts = scan_repo_kotlin_files(kotlin_files)

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

    try:
        allowances = load_raw_log_allowlist()
    except (FileNotFoundError, ValueError) as exc:
        print(f"\nARCH GATE FAILED: {exc}\n")
        return 1
    raw_log_drift = find_raw_log_drift(actual_raw_log_counts, allowances)

    if raw_log_drift:
        print("\nARCH GATE FAILED: raw Log.* production logging drift detected\n")
        for drift in raw_log_drift[:MAX_REPORT_LINES]:
            if drift.allowed_count is None:
                print(
                    f"- {drift.path}: raw Log.* count {drift.actual_count} is not allowlisted"
                )
            else:
                print(
                    f"- {drift.path}: raw Log.* count {drift.actual_count} exceeds allowlisted baseline {drift.allowed_count}"
                )
        if len(raw_log_drift) > MAX_REPORT_LINES:
            remaining = len(raw_log_drift) - MAX_REPORT_LINES
            print(f"... and {remaining} more")
        print(f"\nTotal violations: {len(raw_log_drift)}")
        print(
            "Fix: delete low-value logs, migrate retained logs to AppLogger, or update "
            f"{as_repo_relative(RAW_LOG_ALLOWLIST)} as part of an approved logging-plan change."
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
