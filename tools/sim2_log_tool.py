#!/usr/bin/env python3
"""Capture and analyze SIM2 trail/overlay lag from adb logcat.

Usage:
  python tools/sim2_log_tool.py capture --seconds 120 --out logs/sim2_logcat.txt --launch
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
    "MapScreenReplayCoord:I "
    "*:S"
)

THREADTIME_RE = re.compile(
    r"^(\d{2}-\d{2}) (\d{2}:\d{2}:\d{2}\.\d{3})\s+\d+\s+\d+\s+([VDIWEF])\s+([^:]+):\s(.*)$"
)
RENDER_START_RE = re.compile(r"render#(?P<id>\d+) start")
RENDER_START_DETAIL_RE = re.compile(
    r"render#(?P<id>\d+) start.*loc=(?P<lat>-?\d+\.\d+),(?P<lon>-?\d+\.\d+).*zoom=(?P<zoom>-?\d+\.\d+)"
)
RENDER_END_RE = re.compile(r"render#(?P<id>\d+) end")
LINE_TO_CURRENT_RE = re.compile(
    r"render#(?P<id>\d+) .*kind=line-to-current.*frame=(?P<frame>-?\d+) .*from=(?P<lat1>-?\d+\.\d+),(?P<lon1>-?\d+\.\d+) "
    r"to=(?P<lat2>-?\d+\.\d+),(?P<lon2>-?\d+\.\d+)"
)
LOC_UPDATE_RE = re.compile(
    r"Location updated: lat=(?P<lat>-?\d+\.\d+), lon=(?P<lon>-?\d+\.\d+)"
)
OVERLAY_UPDATE_RE = re.compile(
    r"overlayUpdate loc=(?P<lat>-?\d+\.\d+),(?P<lon>-?\d+\.\d+).*frame=(?P<frame>-?\d+)"
)
FRAME_POSE_RE = re.compile(
    r"framePose frame=(?P<frame>-?\d+) t=(?P<time>\d+) lat=(?P<lat>-?\d+\.\d+) "
    r"lon=(?P<lon>-?\d+\.\d+).*timeBase=(?P<timebase>\w+)"
)
SIM2_START_RE = re.compile(r"VARIO_DEMO_SIM_LIVE start")

METERS_PER_PIXEL_EQUATOR = 156543.03392
ICON_SIZE_PX = 144.0
TAIL_OFFSET_FRACTION = 0.12


@dataclass
class OverlaySample:
    t: dt.datetime
    lat: float
    lon: float
    source: str
    frame_id: Optional[int] = None


@dataclass
class TrailSegment:
    t: dt.datetime
    render_id: int
    frame_id: Optional[int]
    from_lat: float
    from_lon: float
    to_lat: float
    to_lon: float


@dataclass
class LagEvent:
    t: dt.datetime
    render_id: int
    frame_id: Optional[int]
    seg_len_m: float
    lag_ms: Optional[float]
    lag_dist_m: Optional[float]
    lag_px: Optional[float]
    overlay_source: Optional[str]
    overlay_lat: Optional[float]
    overlay_lon: Optional[float]
    overlay_mode: Optional[str]
    tail_lat: float
    tail_lon: float
    pose_lag_px: Optional[float]


@dataclass
class FramePose:
    t: dt.datetime
    frame_id: int
    timestamp_ms: int
    lat: float
    lon: float
    time_base: str


@dataclass
class RenderMeta:
    t: dt.datetime
    render_id: int
    lat: float
    lon: float
    zoom: float


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


def meters_per_pixel(lat: float, zoom: float, pixel_ratio: float) -> Optional[float]:
    if not math.isfinite(lat) or not math.isfinite(zoom):
        return None
    lat_rad = math.radians(lat)
    mpp = METERS_PER_PIXEL_EQUATOR * math.cos(lat_rad) / (2.0 ** zoom)
    if not math.isfinite(mpp) or mpp <= 0.0:
        return None
    ratio = pixel_ratio if pixel_ratio > 0.0 else 1.0
    return mpp / ratio


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
    frame_poses: List[FramePose] = []
    render_metas: List[RenderMeta] = []
    render_start: dict[int, dt.datetime] = {}
    render_end: dict[int, dt.datetime] = {}
    sim2_start_t: Optional[dt.datetime] = None

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
                detail = RENDER_START_DETAIL_RE.search(msg)
                if detail:
                    render_metas.append(
                        RenderMeta(
                            t=t,
                            render_id=int(detail.group("id")),
                            lat=float(detail.group("lat")),
                            lon=float(detail.group("lon")),
                            zoom=float(detail.group("zoom")),
                        )
                    )
                if RENDER_END_RE.search(msg):
                    rid = int(RENDER_END_RE.search(msg).group("id"))
                    render_end[rid] = t
                seg_m = LINE_TO_CURRENT_RE.search(msg)
                if seg_m:
                    frame_id = int(seg_m.group("frame"))
                    segments.append(
                        TrailSegment(
                            t=t,
                            render_id=int(seg_m.group("id")),
                            frame_id=frame_id if frame_id >= 0 else None,
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
                            frame_id=int(loc.group("frame")),
                        )
                    )
            elif tag == "LocationManager":
                frame_m = FRAME_POSE_RE.search(msg)
                if frame_m:
                    frame_poses.append(
                        FramePose(
                            t=t,
                            frame_id=int(frame_m.group("frame")),
                            timestamp_ms=int(frame_m.group("time")),
                            lat=float(frame_m.group("lat")),
                            lon=float(frame_m.group("lon")),
                            time_base=frame_m.group("timebase"),
                        )
                    )
            elif tag == "MapScreenReplayCoord":
                if SIM2_START_RE.search(msg):
                    sim2_start_t = t

    if not args.no_sim2_filter:
        sim2_end_t: Optional[dt.datetime] = None
        if sim2_start_t is None:
            replay_frames = [fp for fp in frame_poses if fp.time_base == "REPLAY"]
            if replay_frames:
                replay_frames.sort(key=lambda f: f.t)
                sim2_start_t = replay_frames[0].t
                sim2_end_t = replay_frames[-1].t
        if sim2_start_t is not None:
            overlays = [o for o in overlays if o.t >= sim2_start_t]
            segments = [s for s in segments if s.t >= sim2_start_t]
            frame_poses = [f for f in frame_poses if f.t >= sim2_start_t]
            render_metas = [m for m in render_metas if m.t >= sim2_start_t]
            render_start = {k: v for k, v in render_start.items() if v >= sim2_start_t}
            render_end = {k: v for k, v in render_end.items() if v >= sim2_start_t}
            if sim2_end_t is not None:
                overlays = [o for o in overlays if o.t <= sim2_end_t]
                segments = [s for s in segments if s.t <= sim2_end_t]
                frame_poses = [f for f in frame_poses if f.t <= sim2_end_t]
                render_metas = [m for m in render_metas if m.t <= sim2_end_t]
                render_start = {k: v for k, v in render_start.items() if v <= sim2_end_t}
                render_end = {k: v for k, v in render_end.items() if v <= sim2_end_t}
        else:
            print(
                "Warning: SIM2 start marker not found in log and no REPLAY frames detected; "
                "analysis includes all data. Make sure SIM2 was started during capture.",
                file=sys.stderr,
            )

    overlays.sort(key=lambda s: s.t)
    segments.sort(key=lambda s: s.t)
    frame_poses.sort(key=lambda f: f.t)
    render_metas.sort(key=lambda m: m.t)

    frame_pose_by_id = {fp.frame_id: fp for fp in frame_poses}
    overlay_by_frame = {o.frame_id: o for o in overlays if o.frame_id is not None}

    lag_events: List[LagEvent] = []
    pose_lag_ms_list: List[float] = []
    pose_lag_dist_m_list: List[float] = []
    seg_len_m_list: List[float] = []
    lag_ms_list: List[float] = []
    lag_dist_m_list: List[float] = []
    lag_px_list: List[float] = []
    overlay_frame_lag_ms_list: List[float] = []
    overlay_frame_lag_dist_m_list: List[float] = []
    overlay_frame_lag_px_list: List[float] = []
    overlay_time_lag_ms_list: List[float] = []
    overlay_time_lag_dist_m_list: List[float] = []
    overlay_time_lag_px_list: List[float] = []
    gap_ms_list: List[float] = []
    pose_lag_px_list: List[float] = []
    overlay_outlier_count = 0

    last_seg_time: Optional[dt.datetime] = None
    meta_idx = -1
    pixel_ratio = args.pixel_ratio

    for seg in segments:
        while meta_idx + 1 < len(render_metas) and render_metas[meta_idx + 1].t <= seg.t:
            meta_idx += 1
        meta = render_metas[meta_idx] if meta_idx >= 0 else None
        mpp = meters_per_pixel(meta.lat, meta.zoom, pixel_ratio) if meta else None

        seg_len = haversine_m(seg.from_lat, seg.from_lon, seg.to_lat, seg.to_lon)
        seg_len_m_list.append(seg_len)

        if last_seg_time is not None:
            gap_ms = (seg.t - last_seg_time).total_seconds() * 1000.0
            gap_ms_list.append(gap_ms)
        last_seg_time = seg.t

        overlay = None
        overlay_mode: Optional[str] = None
        if seg.frame_id is not None and seg.frame_id in overlay_by_frame:
            overlay = overlay_by_frame.get(seg.frame_id)
            overlay_mode = "frame"
        else:
            nearest = nearest_sample(overlays, seg.t)
            if nearest is not None:
                lag_ms = abs((nearest.t - seg.t).total_seconds() * 1000.0)
                lag_dist = haversine_m(nearest.lat, nearest.lon, seg.to_lat, seg.to_lon)
                if lag_ms <= args.overlay_max_dt_ms and lag_dist <= args.overlay_max_dist_m:
                    overlay = nearest
                    overlay_mode = "time"
                else:
                    overlay_outlier_count += 1

        lag_ms_val: Optional[float] = None
        lag_dist_val: Optional[float] = None
        lag_px_val: Optional[float] = None
        if overlay is not None:
            lag_ms_val = (overlay.t - seg.t).total_seconds() * 1000.0
            lag_dist_val = haversine_m(overlay.lat, overlay.lon, seg.to_lat, seg.to_lon)
            if mpp is not None and mpp > 0.0:
                lag_px_val = lag_dist_val / mpp
            lag_ms_list.append(lag_ms_val)
            lag_dist_m_list.append(lag_dist_val)
            if lag_px_val is not None:
                lag_px_list.append(lag_px_val)
            if overlay_mode == "frame":
                overlay_frame_lag_ms_list.append(lag_ms_val)
                overlay_frame_lag_dist_m_list.append(lag_dist_val)
                if lag_px_val is not None:
                    overlay_frame_lag_px_list.append(lag_px_val)
            elif overlay_mode == "time":
                overlay_time_lag_ms_list.append(lag_ms_val)
                overlay_time_lag_dist_m_list.append(lag_dist_val)
                if lag_px_val is not None:
                    overlay_time_lag_px_list.append(lag_px_val)

        lag_events.append(
            LagEvent(
                t=seg.t,
                render_id=seg.render_id,
                frame_id=seg.frame_id,
                seg_len_m=seg_len,
                lag_ms=lag_ms_val,
                lag_dist_m=lag_dist_val,
                lag_px=lag_px_val,
                overlay_source=overlay.source if overlay is not None else None,
                overlay_lat=overlay.lat if overlay is not None else None,
                overlay_lon=overlay.lon if overlay is not None else None,
                overlay_mode=overlay_mode,
                tail_lat=seg.to_lat,
                tail_lon=seg.to_lon,
                pose_lag_px=None,
            )
        )

        if seg.frame_id is not None:
            fp = frame_pose_by_id.get(seg.frame_id)
            if fp is not None:
                pose_lag_ms = (fp.t - seg.t).total_seconds() * 1000.0
                pose_lag_dist = haversine_m(fp.lat, fp.lon, seg.to_lat, seg.to_lon)
                pose_lag_ms_list.append(pose_lag_ms)
                pose_lag_dist_m_list.append(pose_lag_dist)
                if mpp is not None and mpp > 0.0:
                    pose_lag_px = pose_lag_dist / mpp
                    pose_lag_px_list.append(pose_lag_px)
                    lag_events[-1].pose_lag_px = pose_lag_px

    def stats(values: List[float]) -> dict:
        return {
            "count": len(values),
            "mean": sum(values) / len(values) if values else None,
            "p50": percentile(values, 0.50),
            "p95": percentile(values, 0.95),
            "max": max(values) if values else None,
        }

    pose_lag_px_stats = stats(pose_lag_px_list)
    implied_pixel_ratio = None
    if pose_lag_px_stats.get("p50"):
        p50 = pose_lag_px_stats["p50"]
        if p50 and p50 > 0:
            implied_pixel_ratio = (ICON_SIZE_PX * TAIL_OFFSET_FRACTION) / p50

    summary = {
        "overlay_samples": len(overlays),
        "trail_segments": len(segments),
        "frame_poses": len(frame_poses),
        "render_start": len(render_start),
        "render_end": len(render_end),
        "pixel_ratio": pixel_ratio,
        "expected_tail_px": ICON_SIZE_PX * TAIL_OFFSET_FRACTION,
        "implied_pixel_ratio": implied_pixel_ratio,
        "segment_length_m": stats(seg_len_m_list),
        "overlay_lag_ms": stats(lag_ms_list),
        "overlay_lag_dist_m": stats(lag_dist_m_list),
        "overlay_lag_px": stats(lag_px_list),
        "overlay_frame_lag_ms": stats(overlay_frame_lag_ms_list),
        "overlay_frame_lag_dist_m": stats(overlay_frame_lag_dist_m_list),
        "overlay_frame_lag_px": stats(overlay_frame_lag_px_list),
        "overlay_time_lag_ms": stats(overlay_time_lag_ms_list),
        "overlay_time_lag_dist_m": stats(overlay_time_lag_dist_m_list),
        "overlay_time_lag_px": stats(overlay_time_lag_px_list),
        "overlay_outlier_count": overlay_outlier_count,
        "overlay_max_dt_ms": args.overlay_max_dt_ms,
        "overlay_max_dist_m": args.overlay_max_dist_m,
        "segment_gap_ms": stats(gap_ms_list),
        "pose_lag_ms": stats(pose_lag_ms_list),
        "pose_lag_dist_m": stats(pose_lag_dist_m_list),
        "pose_lag_px": pose_lag_px_stats,
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
                "frame_id",
                "seg_len_m",
                "lag_ms",
                "lag_dist_m",
                "lag_px",
                "overlay_mode",
                "overlay_source",
                "overlay_lat",
                "overlay_lon",
                "pose_lag_px",
                "tail_lat",
                "tail_lon",
            ])
            for e in lag_events:
                writer.writerow([
                    e.t.isoformat(),
                    e.render_id,
                    e.frame_id if e.frame_id is not None else "",
                    f"{e.seg_len_m:.3f}",
                    f"{e.lag_ms:.1f}" if e.lag_ms is not None else "",
                    f"{e.lag_dist_m:.3f}" if e.lag_dist_m is not None else "",
                    f"{e.lag_px:.2f}" if e.lag_px is not None else "",
                    e.overlay_mode or "",
                    e.overlay_source or "",
                    f"{e.overlay_lat:.6f}" if e.overlay_lat is not None else "",
                    f"{e.overlay_lon:.6f}" if e.overlay_lon is not None else "",
                    f"{e.pose_lag_px:.2f}" if e.pose_lag_px is not None else "",
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
    c.add_argument("--seconds", type=int, default=120)
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
    a.add_argument("--no-sim2-filter", action="store_true")
    a.add_argument("--overlay-max-dt-ms", type=float, default=1000.0)
    a.add_argument("--overlay-max-dist-m", type=float, default=2000.0)
    a.add_argument("--pixel-ratio", type=float, default=1.0)

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
