#!/usr/bin/env python3
"""Capture and analyze SIM2 trail/overlay lag from adb logcat.

Usage:
  python tools/sim2_log_tool.py capture --seconds 60 --out logs/sim2_logcat.txt --launch
  python tools/sim2_log_tool.py analyze --log logs/sim2_logcat.txt --out logs/sim2_analysis.json
"""
from __future__ import annotations

import argparse
import csv
import datetime as dt
import json
import math
import os
import re
import subprocess
import sys
import time
from dataclasses import dataclass
from pathlib import Path
from typing import List, Optional, Tuple

DEFAULT_FILTER = (
    "SnailTrailRender:D "
    "SnailTrailOverlay:D "
    "SailplaneLocationOverlay:D "
    "MapPositionController:D "
    "MapLocationFilter:D "
    "LocationManager:D "
    "MapCameraManager:D "
    "*:S"
)

THREADTIME_RE = re.compile(
    r"^(\d{2}-\d{2}) (\d{2}:\d{2}:\d{2}\.\d{3})\s+\d+\s+\d+\s+([VDIWEF])\s+([^:]+):\s(.*)$"
)
RENDER_START_RE = re.compile(r"render#(?P<id>\d+) start")
RENDER_END_RE = re.compile(r"render#(?P<id>\d+) end")
LINE_TO_CURRENT_RE = re.compile(
    r"render#(?P<id>\d+) .*kind=line-to-current .*from=(?P<lat1>-?\d+\.\d+),(?P<lon1>-?\d+\.\d+) "
    r"to=(?P<lat2>-?\d+\.\d+),(?P<lon2>-?\d+\.\d+)"
)
LOC_UPDATE_RE = re.compile(
    r"Location updated: lat=(?P<lat>-?\d+\.\d+), lon=(?P<lon>-?\d+\.\d+)"
)
OVERLAY_UPDATE_RE = re.compile(
    r"overlayUpdate loc=(?P<lat>-?\d+\.\d+),(?P<lon>-?\d+\.\d+)"
)


@dataclass
class OverlaySample:
    t: dt.datetime
    lat: float
    lon: float
    source: str


@dataclass
class TrailSegment:
    t: dt.datetime
    render_id: int
    from_lat: float
    from_lon: float
    to_lat: float
    to_lon: float


@dataclass
class LagEvent:
    t: dt.datetime
    render_id: int
    seg_len_m: float
    lag_ms: float
    lag_dist_m: float
    overlay_source: str
    overlay_lat: float
    overlay_lon: float
    tail_lat: float
    tail_lon: float


def run_adb(args: List[str], check: bool = True) -> subprocess.CompletedProcess:
    return subprocess.run(["adb", *args], check=check, capture_output=True, text=True)


def detect_package() -> Optional[str]:
    try:
        out = run_adb(["shell", "pm", "list", "packages", "com.example.xcpro"]).stdout
    except subprocess.CalledProcessError:
        return None
    packages = [line.split(":", 1)[1].strip() for line in out.splitlines() if line.startswith("package:")]
    if "com.example.xcpro.debug" in packages:
        return "com.example.xcpro.debug"
    if "com.example.xcpro" in packages:
        return "com.example.xcpro"
    return packages[0] if packages else None


def resolve_activity(package: str) -> Optional[str]:
    try:
        out = run_adb([
            "shell", "cmd", "package", "resolve-activity",
            "-a", "android.intent.action.MAIN",
            "-c", "android.intent.category.LAUNCHER",
            package,
        ]).stdout
    except subprocess.CalledProcessError:
        return None
    for line in out.splitlines():
        line = line.strip()
        if line.startswith("name="):
            return line.split("=", 1)[1].strip()
    return None


def ensure_parent(path: Path) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)


def capture(args: argparse.Namespace) -> int:
    out_path = Path(args.out)
    ensure_parent(out_path)

    if not args.no_clear:
        run_adb(["logcat", "-c"], check=False)

    package = args.package or detect_package()
    if args.launch:
        if not package:
            print("Could not detect package; use --package", file=sys.stderr)
        else:
            activity = args.activity or resolve_activity(package) or "com.example.xcpro.MainActivity"
            component = f"{package}/{activity}"
            run_adb(["shell", "am", "start", "-n", component], check=False)

    filter_spec = args.filter or DEFAULT_FILTER
    cmd = ["adb", "logcat", "-v", "threadtime", *filter_spec.split()]
    print(f"Capturing logcat for {args.seconds}s -> {out_path}")

    with out_path.open("w", encoding="utf-8") as f:
        proc = subprocess.Popen(cmd, stdout=f, stderr=subprocess.STDOUT)
        try:
            time.sleep(args.seconds)
        finally:
            proc.terminate()
            try:
                proc.wait(timeout=5)
            except subprocess.TimeoutExpired:
                proc.kill()

    print("Capture complete.")
    return 0


def parse_time(mmdd: str, hhmmss: str, year: int) -> Optional[dt.datetime]:
    try:
        return dt.datetime.strptime(f"{year}-{mmdd} {hhmmss}", "%Y-%m-%d %H:%M:%S.%f")
    except ValueError:
        return None


def haversine_m(lat1: float, lon1: float, lat2: float, lon2: float) -> float:
    r = 6371000.0
    phi1 = math.radians(lat1)
    phi2 = math.radians(lat2)
    dphi = math.radians(lat2 - lat1)
    dlambda = math.radians(lon2 - lon1)
    a = math.sin(dphi / 2) ** 2 + math.cos(phi1) * math.cos(phi2) * math.sin(dlambda / 2) ** 2
    c = 2 * math.atan2(math.sqrt(a), math.sqrt(1 - a))
    return r * c


def nearest_sample(samples: List[OverlaySample], t: dt.datetime) -> Optional[OverlaySample]:
    if not samples:
        return None
    # binary search by time
    lo, hi = 0, len(samples) - 1
    while lo < hi:
        mid = (lo + hi) // 2
        if samples[mid].t < t:
            lo = mid + 1
        else:
            hi = mid
    candidates = [samples[lo]]
    if lo > 0:
        candidates.append(samples[lo - 1])
    best = min(candidates, key=lambda s: abs((s.t - t).total_seconds()))
    return best


def percentile(values: List[float], p: float) -> Optional[float]:
    if not values:
        return None
    values_sorted = sorted(values)
    k = (len(values_sorted) - 1) * p
    f = math.floor(k)
    c = math.ceil(k)
    if f == c:
        return values_sorted[int(k)]
    return values_sorted[f] * (c - k) + values_sorted[c] * (k - f)


def analyze(args: argparse.Namespace) -> int:
    log_path = Path(args.log)
    if not log_path.exists():
        print(f"Log not found: {log_path}", file=sys.stderr)
        return 1

    year = dt.datetime.now().year
    overlays: List[OverlaySample] = []
    segments: List[TrailSegment] = []
    render_start: dict[int, dt.datetime] = {}
    render_end: dict[int, dt.datetime] = {}

    with log_path.open("r", encoding="utf-8", errors="ignore") as f:
        for line in f:
            m = THREADTIME_RE.match(line.strip())
            if not m:
                continue
            mmdd, hhmmss, _level, tag, msg = m.groups()
            t = parse_time(mmdd, hhmmss, year)
            if t is None:
                continue

            if tag == "SnailTrailRender":
                if RENDER_START_RE.search(msg):
                    rid = int(RENDER_START_RE.search(msg).group("id"))
                    render_start[rid] = t
                if RENDER_END_RE.search(msg):
                    rid = int(RENDER_END_RE.search(msg).group("id"))
                    render_end[rid] = t
                seg_m = LINE_TO_CURRENT_RE.search(msg)
                if seg_m:
                    segments.append(
                        TrailSegment(
                            t=t,
                            render_id=int(seg_m.group("id")),
                            from_lat=float(seg_m.group("lat1")),
                            from_lon=float(seg_m.group("lon1")),
                            to_lat=float(seg_m.group("lat2")),
                            to_lon=float(seg_m.group("lon2")),
                        )
                    )
            elif tag == "SailplaneLocationOverlay":
                loc = LOC_UPDATE_RE.search(msg)
                if loc:
                    overlays.append(
                        OverlaySample(
                            t=t,
                            lat=float(loc.group("lat")),
                            lon=float(loc.group("lon")),
                            source="SailplaneLocationOverlay",
                        )
                    )
            elif tag == "MapPositionController":
                loc = OVERLAY_UPDATE_RE.search(msg)
                if loc:
                    overlays.append(
                        OverlaySample(
                            t=t,
                            lat=float(loc.group("lat")),
                            lon=float(loc.group("lon")),
                            source="MapPositionController",
                        )
                    )

    overlays.sort(key=lambda s: s.t)
    segments.sort(key=lambda s: s.t)

    lag_events: List[LagEvent] = []
    seg_len_m_list: List[float] = []
    lag_ms_list: List[float] = []
    lag_dist_m_list: List[float] = []
    gap_ms_list: List[float] = []

    last_seg_time: Optional[dt.datetime] = None

    for seg in segments:
        seg_len = haversine_m(seg.from_lat, seg.from_lon, seg.to_lat, seg.to_lon)
        seg_len_m_list.append(seg_len)

        if last_seg_time is not None:
            gap_ms = (seg.t - last_seg_time).total_seconds() * 1000.0
            gap_ms_list.append(gap_ms)
        last_seg_time = seg.t

        nearest = nearest_sample(overlays, seg.t)
        if nearest is None:
            continue
        lag_ms = (nearest.t - seg.t).total_seconds() * 1000.0
        lag_dist = haversine_m(nearest.lat, nearest.lon, seg.to_lat, seg.to_lon)

        lag_events.append(
            LagEvent(
                t=seg.t,
                render_id=seg.render_id,
                seg_len_m=seg_len,
                lag_ms=lag_ms,
                lag_dist_m=lag_dist,
                overlay_source=nearest.source,
                overlay_lat=nearest.lat,
                overlay_lon=nearest.lon,
                tail_lat=seg.to_lat,
                tail_lon=seg.to_lon,
            )
        )
        lag_ms_list.append(lag_ms)
        lag_dist_m_list.append(lag_dist)

    def stats(values: List[float]) -> dict:
        return {
            "count": len(values),
            "mean": sum(values) / len(values) if values else None,
            "p50": percentile(values, 0.50),
            "p95": percentile(values, 0.95),
            "max": max(values) if values else None,
        }

    summary = {
        "overlay_samples": len(overlays),
        "trail_segments": len(segments),
        "render_start": len(render_start),
        "render_end": len(render_end),
        "segment_length_m": stats(seg_len_m_list),
        "lag_ms": stats(lag_ms_list),
        "lag_dist_m": stats(lag_dist_m_list),
        "segment_gap_ms": stats(gap_ms_list),
    }

    out_json = Path(args.out)
    ensure_parent(out_json)
    with out_json.open("w", encoding="utf-8") as f:
        json.dump(
            {
                "summary": summary,
                "events": [e.__dict__ for e in lag_events],
            },
            f,
            indent=2,
            default=str,
        )

    if args.csv:
        out_csv = Path(args.csv)
        ensure_parent(out_csv)
        with out_csv.open("w", encoding="utf-8", newline="") as f:
            writer = csv.writer(f)
            writer.writerow([
                "timestamp",
                "render_id",
                "seg_len_m",
                "lag_ms",
                "lag_dist_m",
                "overlay_source",
                "overlay_lat",
                "overlay_lon",
                "tail_lat",
                "tail_lon",
            ])
            for e in lag_events:
                writer.writerow([
                    e.t.isoformat(),
                    e.render_id,
                    f"{e.seg_len_m:.3f}",
                    f"{e.lag_ms:.1f}",
                    f"{e.lag_dist_m:.3f}",
                    e.overlay_source,
                    f"{e.overlay_lat:.6f}",
                    f"{e.overlay_lon:.6f}",
                    f"{e.tail_lat:.6f}",
                    f"{e.tail_lon:.6f}",
                ])

    print("Analysis complete.")
    print(json.dumps(summary, indent=2))
    return 0


def build_parser() -> argparse.ArgumentParser:
    p = argparse.ArgumentParser(description="SIM2 log capture + analysis tool")
    sub = p.add_subparsers(dest="cmd", required=True)

    c = sub.add_parser("capture", help="Capture adb logcat for SIM2")
    c.add_argument("--seconds", type=int, default=60)
    c.add_argument("--out", default="logs/sim2_logcat.txt")
    c.add_argument("--filter", default=DEFAULT_FILTER)
    c.add_argument("--launch", action="store_true")
    c.add_argument("--package", default=None)
    c.add_argument("--activity", default=None)
    c.add_argument("--no-clear", action="store_true")

    a = sub.add_parser("analyze", help="Analyze captured logcat")
    a.add_argument("--log", required=True)
    a.add_argument("--out", default="logs/sim2_analysis.json")
    a.add_argument("--csv", default="logs/sim2_analysis.csv")

    return p


def main() -> int:
    parser = build_parser()
    args = parser.parse_args()
    if args.cmd == "capture":
        return capture(args)
    if args.cmd == "analyze":
        return analyze(args)
    return 1


if __name__ == "__main__":
    raise SystemExit(main())
