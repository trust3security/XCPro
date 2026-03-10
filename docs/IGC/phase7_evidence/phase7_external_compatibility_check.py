#!/usr/bin/env python3

from __future__ import annotations

import argparse
import json
from dataclasses import dataclass
from pathlib import Path
from typing import Any, Dict, List


@dataclass
class ExternalCheckResult:
    fixture_id: str
    fixture_path: str
    parser: str
    status: str
    result: Dict[str, Any]


def _safe_name(value: Any) -> str:
    return type(value).__name__


def _error_summary(errors: List[Any]) -> List[Dict[str, str]]:
    return [
        {
            "type": _safe_name(error),
            "message": str(error) if str(error) else "",
        }
        for error in errors
    ]


def _run_aerofiles(path: Path) -> Dict[str, Any]:
    try:
        from aerofiles.igc import Reader
    except Exception as exc:
        return {
            "status": "SKIPPED",
            "error": f"{type(exc).__name__}: {exc}",
            "fix_record_count": None,
            "errors": {},
        }

    try:
        with path.open("r", encoding="utf-8", newline="") as handle:
            parsed = Reader().read(handle)
    except Exception as exc:
        return {
            "status": "FAIL",
            "error": f"{type(exc).__name__}: {exc}",
            "fix_record_count": None,
            "errors": {},
        }

    errors: Dict[str, List[Dict[str, str]]] = {}
    for key, value in parsed.items():
        if (
            isinstance(value, list)
            and len(value) == 2
            and isinstance(value[0], list)
            and value[0]
        ):
            errors[key] = _error_summary(value[0])

    fix_records = parsed.get("fix_records")
    fix_count = len(fix_records[1]) if isinstance(fix_records, list) and len(fix_records) == 2 else None

    return {
        "status": "PASS" if not errors else "WARN",
        "fix_record_count": fix_count,
        "errors": errors,
        "logger_id": parsed.get("logger_id"),
    }


def _run_igc_parser(path: Path) -> Dict[str, Any]:
    try:
        from igc_parser import parse_igc_bytes
    except Exception as exc:
        return {
            "status": "SKIPPED",
            "error": f"{type(exc).__name__}: {exc}",
            "position_log_count": None,
            "errors": [],
        }

    try:
        parsed = parse_igc_bytes(path.read_bytes())
        return {
            "status": "PASS",
            "position_log_count": len(parsed.position_logs),
            "errors": [],
            "task": _safe_name(parsed.task) if hasattr(parsed, "task") else None,
        }
    except Exception as exc:
        return {
            "status": "FAIL",
            "position_log_count": None,
            "error": f"{type(exc).__name__}: {exc}",
            "errors": [{"type": type(exc).__name__, "message": str(exc)}],
        }


def run_all(fixtures: List[Dict[str, str]]) -> List[ExternalCheckResult]:
    results: List[ExternalCheckResult] = []
    for fixture in fixtures:
        fixture_id = fixture["fixture_id"]
        path = Path(fixture["path"]).resolve()
        if not path.exists():
            result = {
                "status": "MISSING_FIXTURE",
                "error": f"Fixture not found: {path}",
            }
            results.append(ExternalCheckResult(fixture_id, str(path), "aerofiles", "FAIL", result))
            results.append(ExternalCheckResult(fixture_id, str(path), "igc_parser", "FAIL", result))
            continue

        aerofiles_result = _run_aerofiles(path)
        results.append(
            ExternalCheckResult(
                fixture_id=fixture_id,
                fixture_path=str(path),
                parser="aerofiles",
                status="PASS" if aerofiles_result["status"] == "PASS" else "FAIL" if aerofiles_result["status"] == "FAIL" else "WARN",
                result=aerofiles_result,
            )
        )

        igc_parser_result = _run_igc_parser(path)
        results.append(
            ExternalCheckResult(
                fixture_id=fixture_id,
                fixture_path=str(path),
                parser="igc_parser",
                status=igc_parser_result["status"],
                result=igc_parser_result,
            )
        )

    return results


def _format_markdown(rows: List[ExternalCheckResult]) -> str:
    lines = [
        "# External Compatibility Report",
        "",
        "| Fixture | Parser | Parser Status | Record Count | Error |",
        "| --- | --- | --- | --- | --- |",
    ]
    for row in rows:
        result = row.result
        count = ""
        if "fix_record_count" in result and result["fix_record_count"] is not None:
            count = str(result["fix_record_count"])
        elif "position_log_count" in result and result["position_log_count"] is not None:
            count = str(result["position_log_count"])
        elif "error" in result:
            count = result["error"]
        else:
            count = ""

        if "errors" in result and isinstance(result["errors"], list) and result["errors"]:
            error = result["errors"][0]["type"]
        elif "errors" in result and isinstance(result["errors"], dict) and result["errors"]:
            first_key = next(iter(result["errors"]))
            if result["errors"][first_key]:
                error = result["errors"][first_key][0]["type"]
            else:
                error = ""
        else:
            error = ""

        lines.append(f"| {row.fixture_id} | {row.parser} | {row.status} | {count} | {error} |")

    return "\n".join(lines) + "\n"


def main() -> None:
    parser = argparse.ArgumentParser(description="Run independent IGC compatibility parsers.")
    parser.add_argument(
        "--fixture",
        action="append",
        default=[
            "production_real::feature/igc/src/test/resources/replay/example-production.igc",
            "invalid_a_not_first::docs/IGC/phase7_evidence/fixtures/phase7_a_not_first.igc",
            "invalid_malformed_b_short::docs/IGC/phase7_evidence/fixtures/phase7_malformed_b_short.igc",
            "invalid_bad_time::docs/IGC/phase7_evidence/fixtures/phase7_bad_time.igc",
        ],
        help="fixture entries as id::path",
    )
    parser.add_argument(
        "--output-json",
        default="",
        help="Optional JSON output path.",
    )
    parser.add_argument(
        "--output-markdown",
        default="",
        help="Optional markdown output path.",
    )
    args = parser.parse_args()

    fixtures = []
    for fixture in args.fixture:
        if "::" not in fixture:
            raise ValueError(f"Invalid fixture format: {fixture}")
        fixture_id, path = fixture.split("::", 1)
        fixtures.append({"fixture_id": fixture_id, "path": path})

    results = run_all(fixtures)
    payload = [r.__dict__ for r in results]

    print("Phase 7 external compatibility check")
    print(json.dumps(payload, indent=2))
    print(_format_markdown(results))

    if args.output_json:
        Path(args.output_json).parent.mkdir(parents=True, exist_ok=True)
        Path(args.output_json).write_text(json.dumps(payload, indent=2), encoding="utf-8")
    if args.output_markdown:
        Path(args.output_markdown).parent.mkdir(parents=True, exist_ok=True)
        Path(args.output_markdown).write_text(_format_markdown(results), encoding="utf-8")


if __name__ == "__main__":
    main()
