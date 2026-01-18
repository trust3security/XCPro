import argparse
import math
from dataclasses import dataclass
from datetime import datetime, date, time, timedelta, timezone
from typing import List, Optional, Tuple

EARTH_RADIUS_M = 6_371_000.0
MIDNIGHT_ROLLOVER_THRESHOLD = timedelta(hours=6)

@dataclass
class IgcPoint:
    timestamp_ms: int
    lat: float
    lon: float
    gps_alt: float
    pressure_alt: Optional[float]


def parse_date(value: str) -> Optional[date]:
    try:
        day = int(value[0:2])
        month = int(value[2:4])
        year = 2000 + int(value[4:6])
        return date(year, month, day)
    except Exception:
        return None


def parse_b_time(line: str) -> Optional[time]:
    try:
        return time(int(line[1:3]), int(line[3:5]), int(line[5:7]))
    except Exception:
        return None


def parse_coord(value: str, positive: bool, is_lat: bool) -> float:
    deg_len = 2 if is_lat else 3
    degrees = int(value[0:deg_len])
    minutes = int(value[deg_len:deg_len+2])
    thousandths = int(value[deg_len+2:])
    total_minutes = minutes + thousandths / 1000.0
    coord = degrees + total_minutes / 60.0
    if not positive:
        coord = -coord
    return coord


def parse_igc(path: str) -> List[IgcPoint]:
    points: List[IgcPoint] = []
    current_date = datetime.now(timezone.utc).date()
    last_ts: Optional[int] = None

    with open(path, "r", encoding="utf-8", errors="ignore") as f:
        for raw in f:
            line = raw.strip()
            if line.startswith("HFDTE") and len(line) >= 11:
                parsed = parse_date(line[5:11])
                if parsed:
                    current_date = parsed
                last_ts = None
                continue
            if line.startswith("B") and len(line) >= 35:
                t = parse_b_time(line)
                if t is None:
                    continue
                candidate = datetime.combine(current_date, t, tzinfo=timezone.utc)
                candidate_ms = int(candidate.timestamp() * 1000)
                if last_ts is not None and candidate_ms < last_ts:
                    delta = last_ts - candidate_ms
                    if delta >= int(MIDNIGHT_ROLLOVER_THRESHOLD.total_seconds() * 1000):
                        current_date = current_date + timedelta(days=1)
                        candidate = datetime.combine(current_date, t, tzinfo=timezone.utc)
                        candidate_ms = int(candidate.timestamp() * 1000)
                    else:
                        # skip small backwards jumps
                        continue

                try:
                    lat = parse_coord(line[7:14], line[14] == 'N', True)
                    lon = parse_coord(line[15:23], line[23] == 'E', False)
                    pressure_alt = int(line[25:30]) if line[25:30].isdigit() else None
                    gps_alt = int(line[30:35]) if line[30:35].isdigit() else 0
                except Exception:
                    continue

                points.append(IgcPoint(
                    timestamp_ms=candidate_ms,
                    lat=lat,
                    lon=lon,
                    gps_alt=float(gps_alt),
                    pressure_alt=float(pressure_alt) if pressure_alt is not None else None
                ))
                last_ts = candidate_ms
    return points


def haversine(lat1: float, lon1: float, lat2: float, lon2: float) -> float:
    rlat1 = math.radians(lat1)
    rlat2 = math.radians(lat2)
    dlat = math.radians(lat2 - lat1)
    dlon = math.radians(lon2 - lon1)
    a = math.sin(dlat / 2) ** 2 + math.cos(rlat1) * math.cos(rlat2) * math.sin(dlon / 2) ** 2
    c = 2 * math.atan2(math.sqrt(a), math.sqrt(1 - a))
    return EARTH_RADIUS_M * c


def bearing_deg(lat1: float, lon1: float, lat2: float, lon2: float) -> float:
    rlat1 = math.radians(lat1)
    rlat2 = math.radians(lat2)
    dlon = math.radians(lon2 - lon1)
    y = math.sin(dlon) * math.cos(rlat2)
    x = math.cos(rlat1) * math.sin(rlat2) - math.sin(rlat1) * math.cos(rlat2) * math.cos(dlon)
    deg = (math.degrees(math.atan2(y, x)) + 360.0) % 360.0
    return deg


def project(lat_deg: float, lon_deg: float, track_deg: float, distance_m: float) -> Tuple[float, float]:
    lat1 = math.radians(lat_deg)
    lon1 = math.radians(lon_deg)
    bearing = math.radians(track_deg)
    ang_dist = distance_m / EARTH_RADIUS_M

    sin_lat1 = math.sin(lat1)
    cos_lat1 = math.cos(lat1)
    sin_ang = math.sin(ang_dist)
    cos_ang = math.cos(ang_dist)

    lat2 = math.asin(sin_lat1 * cos_ang + cos_lat1 * sin_ang * math.cos(bearing))
    lon2 = lon1 + math.atan2(
        math.sin(bearing) * sin_ang * cos_lat1,
        cos_ang - sin_lat1 * math.sin(lat2)
    )
    lon_norm = (math.degrees(lon2) + 540.0) % 360.0 - 180.0
    return math.degrees(lat2), lon_norm


@dataclass
class RawFix:
    lat: float
    lon: float
    speed_ms: float
    track_deg: float
    accuracy_m: float
    bearing_accuracy_deg: Optional[float]
    speed_accuracy_ms: Optional[float]
    timestamp_ms: int


@dataclass
class SmoothingConfig:
    pos_smooth_ms: float
    heading_smooth_ms: float
    dead_reckon_limit_ms: int
    stale_fix_timeout_ms: int


def adaptive_config(base: SmoothingConfig, speed_ms: float, accuracy_m: float) -> SmoothingConfig:
    # Matches DisplayPoseAdaptiveSmoothing
    def normalize(value: float, min_v: float, max_v: float) -> float:
        if value <= min_v:
            return 0.0
        if value >= max_v:
            return 1.0
        return (value - min_v) / (max_v - min_v)

    def lerp(a: float, b: float, t: float) -> float:
        return a + (b - a) * t

    def clamp(v: float, min_v: float, max_v: float) -> float:
        return max(min_v, min(max_v, v))

    SPEED_SLOW_MS = 8.0
    SPEED_FAST_MS = 60.0
    ACCURACY_GOOD_M = 3.0
    ACCURACY_BAD_M = 15.0
    MIN_SPEED_SCALE = 0.5
    MAX_ACCURACY_SCALE = 1.6
    MIN_SCALE = 0.45
    MAX_SCALE = 1.8
    MIN_DEAD_RECKON_SCALE = 0.5
    MIN_POS_SMOOTH_MS = 80.0
    MIN_HEADING_SMOOTH_MS = 60.0
    MIN_DEAD_RECKON_MS = 120

    if not (math.isfinite(speed_ms) and math.isfinite(accuracy_m)):
        return base

    speed_norm = normalize(speed_ms, SPEED_SLOW_MS, SPEED_FAST_MS)
    accuracy_norm = normalize(accuracy_m, ACCURACY_GOOD_M, ACCURACY_BAD_M)

    speed_scale = lerp(1.0, MIN_SPEED_SCALE, speed_norm)
    accuracy_scale = lerp(1.0, MAX_ACCURACY_SCALE, accuracy_norm)
    combined = clamp(speed_scale * accuracy_scale, MIN_SCALE, MAX_SCALE)

    pos = max(base.pos_smooth_ms * combined, MIN_POS_SMOOTH_MS)
    heading = max(base.heading_smooth_ms * combined, MIN_HEADING_SMOOTH_MS)
    dead_reckon = max(int(round(base.dead_reckon_limit_ms * lerp(1.0, MIN_DEAD_RECKON_SCALE, accuracy_norm))), MIN_DEAD_RECKON_MS)

    return SmoothingConfig(
        pos_smooth_ms=pos,
        heading_smooth_ms=heading,
        dead_reckon_limit_ms=dead_reckon,
        stale_fix_timeout_ms=base.stale_fix_timeout_ms
    )


class DisplayPoseSmoother:
    DEFAULT_MIN_SPEED_FOR_HEADING_MS = 2.0
    BEARING_ACCURACY_MIN_DEG = 1.0
    BEARING_ACCURACY_BAD_DEG = 20.0
    PREDICTION_SCALE_MIN = 0.2
    SPEED_ACCURACY_POOR_MS = 2.5
    CLAMP_MIN_METERS = 5.0
    CLAMP_ACCURACY_MULTIPLIER = 3.0
    CLAMP_SPEED_MULTIPLIER = 1.5
    CLAMP_MAX_DT_MS = 2000

    def __init__(self, min_speed_ms: float, config: SmoothingConfig, adaptive: bool):
        self.min_speed_for_heading_ms = min_speed_ms
        self.min_speed_for_prediction_ms = min_speed_ms
        self.config = config
        self.adaptive = adaptive
        self.last_raw: Optional[RawFix] = None
        self.last_display: Optional[Tuple[float, float, float, int, float, Optional[float], Optional[float], float]] = None
        # last_display: lat, lon, track, updated_at_ms, accuracy_m, bearing_accuracy, speed_accuracy, speed_ms

    def push_raw(self, raw: RawFix):
        self.last_raw = raw

    def tick(self, now_ms: int) -> Optional[Tuple[float, float, float]]:
        raw = self.last_raw
        if raw is None:
            return None

        cfg = adaptive_config(self.config, raw.speed_ms, raw.accuracy_m) if self.adaptive else self.config

        raw_age_ms = max(0, now_ms - raw.timestamp_ms)
        if raw_age_ms > cfg.stale_fix_timeout_ms:
            if self.last_display is None:
                return None
            return (self.last_display[0], self.last_display[1], self.last_display[2])

        target_lat, target_lon = self.predict_location(raw, raw_age_ms, cfg.dead_reckon_limit_ms)

        if self.last_display is None:
            dt_ms = int(cfg.pos_smooth_ms)
        else:
            dt_ms = max(1, now_ms - self.last_display[3])

        pos_alpha = self.position_alpha(dt_ms, raw.accuracy_m, cfg.pos_smooth_ms)

        if self.last_display is None:
            clamped_lat, clamped_lon = target_lat, target_lon
        else:
            clamped_lat, clamped_lon = self.clamp_target(
                prev_lat=self.last_display[0],
                prev_lon=self.last_display[1],
                target_lat=target_lat,
                target_lon=target_lon,
                speed_ms=raw.speed_ms,
                speed_accuracy_ms=raw.speed_accuracy_ms,
                accuracy_m=raw.accuracy_m,
                dt_ms=dt_ms
            )

        if self.last_display is None:
            new_lat, new_lon = clamped_lat, clamped_lon
            new_track = raw.track_deg
        else:
            new_lat = self.lerp(self.last_display[0], clamped_lat, pos_alpha)
            new_lon = self.lerp(self.last_display[1], clamped_lon, pos_alpha)
            heading_alpha = self.heading_alpha(
                dt_ms,
                raw.speed_ms,
                raw.accuracy_m,
                raw.bearing_accuracy_deg,
                cfg.heading_smooth_ms
            )
            new_track = self.lerp_angle(self.last_display[2], raw.track_deg, heading_alpha)

        self.last_display = (new_lat, new_lon, new_track, now_ms, raw.accuracy_m, raw.bearing_accuracy_deg, raw.speed_accuracy_ms, raw.speed_ms)
        return new_lat, new_lon, new_track

    def position_alpha(self, dt_ms: int, accuracy_m: float, pos_smooth_ms: float) -> float:
        base = min(max(dt_ms / pos_smooth_ms, 0.0), 1.0)
        if accuracy_m > 20.0:
            scale = 0.25
        elif accuracy_m > 12.0:
            scale = 0.4
        elif accuracy_m > 8.0:
            scale = 0.6
        elif accuracy_m > 5.0:
            scale = 0.8
        else:
            scale = 1.0
        return base * scale

    def heading_alpha(self, dt_ms: int, speed_ms: float, accuracy_m: float, bearing_accuracy_deg: Optional[float], heading_smooth_ms: float) -> float:
        base = min(max(dt_ms / heading_smooth_ms, 0.0), 1.0)
        speed_gate = 0.25 if speed_ms < self.min_speed_for_heading_ms else 1.0
        accuracy_gate = 0.5 if accuracy_m > 15.0 else 1.0
        if bearing_accuracy_deg is not None and math.isfinite(bearing_accuracy_deg) and bearing_accuracy_deg >= 0.0:
            clamped = max(1.0, min(30.0, bearing_accuracy_deg))
            bearing_gate = (1.0 - ((clamped - 1.0) / 29.0)) * 0.8 + 0.2
        else:
            bearing_gate = 1.0
        return base * speed_gate * accuracy_gate * bearing_gate

    def predict_location(self, raw: RawFix, raw_age_ms: int, dead_reckon_limit_ms: int) -> Tuple[float, float]:
        if raw.speed_ms < self.min_speed_for_prediction_ms:
            return raw.lat, raw.lon
        if raw.speed_accuracy_ms is not None and math.isfinite(raw.speed_accuracy_ms) and raw.speed_accuracy_ms >= 0.0:
            if raw.speed_accuracy_ms > self.SPEED_ACCURACY_POOR_MS:
                return raw.lat, raw.lon
        bearing_scale = self.bearing_prediction_scale(raw.bearing_accuracy_deg)
        effective_age_ms = max(0.0, min(raw_age_ms, dead_reckon_limit_ms) * bearing_scale)
        travel_s = effective_age_ms / 1000.0
        if travel_s <= 0.0 or raw.speed_ms <= 0.0:
            return raw.lat, raw.lon
        distance = raw.speed_ms * travel_s
        return project(raw.lat, raw.lon, raw.track_deg, distance)

    def bearing_prediction_scale(self, bearing_accuracy_deg: Optional[float]) -> float:
        if bearing_accuracy_deg is None or not math.isfinite(bearing_accuracy_deg) or bearing_accuracy_deg < 0.0:
            return 1.0
        clamped = max(self.BEARING_ACCURACY_MIN_DEG, min(self.BEARING_ACCURACY_BAD_DEG, bearing_accuracy_deg))
        t = (clamped - self.BEARING_ACCURACY_MIN_DEG) / (self.BEARING_ACCURACY_BAD_DEG - self.BEARING_ACCURACY_MIN_DEG)
        return (1.0 - t) * 1.0 + t * self.PREDICTION_SCALE_MIN

    def clamp_target(self, prev_lat: float, prev_lon: float, target_lat: float, target_lon: float,
                     speed_ms: float, speed_accuracy_ms: Optional[float], accuracy_m: float, dt_ms: int) -> Tuple[float, float]:
        distance = haversine(prev_lat, prev_lon, target_lat, target_lon)
        if not math.isfinite(distance):
            return target_lat, target_lon

        capped_dt_ms = min(dt_ms, self.CLAMP_MAX_DT_MS)
        dt_sec = capped_dt_ms / 1000.0

        safe_accuracy = accuracy_m if math.isfinite(accuracy_m) and accuracy_m >= 0.0 else 0.0
        accuracy_term = safe_accuracy * self.CLAMP_ACCURACY_MULTIPLIER
        speed_term = speed_ms * dt_sec * self.CLAMP_SPEED_MULTIPLIER if math.isfinite(speed_ms) and speed_ms > 0.0 else 0.0

        speed_accuracy_poor = speed_accuracy_ms is not None and math.isfinite(speed_accuracy_ms) and speed_accuracy_ms > self.SPEED_ACCURACY_POOR_MS
        allowed = max(self.CLAMP_MIN_METERS, accuracy_term if speed_accuracy_poor else accuracy_term + speed_term)

        if distance <= allowed:
            return target_lat, target_lon

        bearing = bearing_deg(prev_lat, prev_lon, target_lat, target_lon)
        return project(prev_lat, prev_lon, bearing, allowed)

    @staticmethod
    def lerp(a: float, b: float, alpha: float) -> float:
        return a + (b - a) * alpha

    @staticmethod
    def lerp_angle(from_deg: float, to_deg: float, alpha: float) -> float:
        diff = ((to_deg - from_deg + 540.0) % 360.0) - 180.0
        return (from_deg + diff * alpha + 360.0) % 360.0


@dataclass
class SimResult:
    frame_times: List[int]
    positions: List[Tuple[float, float]]
    tracks: List[float]


def interpolate_point(p1: IgcPoint, p2: IgcPoint, timestamp_ms: int) -> IgcPoint:
    if p2.timestamp_ms == p1.timestamp_ms:
        return p1
    fraction = (timestamp_ms - p1.timestamp_ms) / (p2.timestamp_ms - p1.timestamp_ms)
    fraction = max(0.0, min(1.0, fraction))
    lat = p1.lat + (p2.lat - p1.lat) * fraction
    lon = p1.lon + (p2.lon - p1.lon) * fraction
    gps_alt = p1.gps_alt + (p2.gps_alt - p1.gps_alt) * fraction
    if p1.pressure_alt is not None and p2.pressure_alt is not None:
        pressure_alt = p1.pressure_alt + (p2.pressure_alt - p1.pressure_alt) * fraction
    elif p1.pressure_alt is not None:
        pressure_alt = p1.pressure_alt
    elif p2.pressure_alt is not None:
        pressure_alt = p2.pressure_alt
    else:
        pressure_alt = None
    return IgcPoint(timestamp_ms=timestamp_ms, lat=lat, lon=lon, gps_alt=gps_alt, pressure_alt=pressure_alt)


def build_gps_samples(points: List[IgcPoint], step_ms: int) -> List[IgcPoint]:
    if len(points) < 2:
        return points
    samples: List[IgcPoint] = []
    start = points[0].timestamp_ms
    end = points[-1].timestamp_ms
    idx = 0
    t = start
    while t <= end:
        while idx < len(points) - 2 and points[idx + 1].timestamp_ms < t:
            idx += 1
        p1 = points[idx]
        p2 = points[idx + 1]
        samples.append(interpolate_point(p1, p2, t))
        t += step_ms
    return samples


def simulate_display(points: List[IgcPoint], gps_step_ms: int, fps: int, accuracy_m: float,
                     profile: str, adaptive: bool) -> SimResult:
    if len(points) < 2:
        return SimResult([], [], [])

    gps_samples = build_gps_samples(points, gps_step_ms)

    if profile == "responsive":
        config = SmoothingConfig(pos_smooth_ms=150.0, heading_smooth_ms=120.0, dead_reckon_limit_ms=250, stale_fix_timeout_ms=2000)
    else:
        config = SmoothingConfig(pos_smooth_ms=300.0, heading_smooth_ms=250.0, dead_reckon_limit_ms=500, stale_fix_timeout_ms=2000)

    smoother = DisplayPoseSmoother(min_speed_ms=2.0, config=config, adaptive=adaptive)

    frame_times: List[int] = []
    positions: List[Tuple[float, float]] = []
    tracks: List[float] = []

    frame_step_ms = int(round(1000 / fps))
    start = gps_samples[0].timestamp_ms
    end = gps_samples[-1].timestamp_ms

    gps_index = 0
    prev_gps: Optional[IgcPoint] = None

    t = start
    while t <= end:
        while gps_index < len(gps_samples) and gps_samples[gps_index].timestamp_ms <= t:
            current = gps_samples[gps_index]
            if prev_gps is None:
                speed = 0.0
                track = 0.0
            else:
                dist = haversine(prev_gps.lat, prev_gps.lon, current.lat, current.lon)
                dt_sec = max((current.timestamp_ms - prev_gps.timestamp_ms) / 1000.0, 1.0)
                speed = dist / dt_sec
                track = bearing_deg(prev_gps.lat, prev_gps.lon, current.lat, current.lon)
            raw = RawFix(
                lat=current.lat,
                lon=current.lon,
                speed_ms=speed,
                track_deg=track,
                accuracy_m=accuracy_m,
                bearing_accuracy_deg=None,
                speed_accuracy_ms=None,
                timestamp_ms=current.timestamp_ms
            )
            smoother.push_raw(raw)
            prev_gps = current
            gps_index += 1

        pose = smoother.tick(t)
        if pose:
            lat, lon, track = pose
            frame_times.append(t)
            positions.append((lat, lon))
            tracks.append(track)
        t += frame_step_ms

    return SimResult(frame_times, positions, tracks)


def compute_metrics(result: SimResult) -> dict:
    if len(result.positions) < 2:
        return {}
    step_dists = []
    heading_deltas = []
    for i in range(1, len(result.positions)):
        lat1, lon1 = result.positions[i - 1]
        lat2, lon2 = result.positions[i]
        step_dists.append(haversine(lat1, lon1, lat2, lon2))
        prev = result.tracks[i - 1]
        curr = result.tracks[i]
        delta = ((curr - prev + 540.0) % 360.0) - 180.0
        heading_deltas.append(abs(delta))

    def percentile(values: List[float], p: float) -> float:
        if not values:
            return 0.0
        values = sorted(values)
        k = int(round((len(values) - 1) * p))
        return values[k]

    return {
        "frames": len(result.positions),
        "mean_step_m": sum(step_dists) / len(step_dists),
        "p95_step_m": percentile(step_dists, 0.95),
        "max_step_m": max(step_dists),
        "mean_heading_delta_deg": sum(heading_deltas) / len(heading_deltas),
        "p95_heading_delta_deg": percentile(heading_deltas, 0.95),
        "max_heading_delta_deg": max(heading_deltas)
    }


def main():
    parser = argparse.ArgumentParser(description="Sim2 arrow movement analysis")
    parser.add_argument("--igc", default="app/src/main/assets/replay/vario-demo-0-10-0-60s.igc")
    parser.add_argument("--gps-step-ms", type=int, default=1000)
    parser.add_argument("--accuracy-m", type=float, default=5.0)
    parser.add_argument("--profile", choices=["smooth", "responsive"], default="responsive")
    parser.add_argument("--fps", type=int, default=60)
    parser.add_argument("--adaptive", action="store_true")
    parser.add_argument("--csv", default=None, help="Optional output CSV path")
    args = parser.parse_args()

    points = parse_igc(args.igc)
    if len(points) < 2:
        print("Not enough IGC points")
        return

    result = simulate_display(points, args.gps_step_ms, args.fps, args.accuracy_m, args.profile, args.adaptive)
    metrics = compute_metrics(result)
    print("Sim2 Arrow Movement Metrics")
    for k, v in metrics.items():
        if isinstance(v, float):
            print(f"- {k}: {v:.4f}")
        else:
            print(f"- {k}: {v}")

    if args.csv:
        with open(args.csv, "w", encoding="utf-8") as f:
            f.write("timestamp_ms,lat,lon,track_deg\n")
            for t, (lat, lon), track in zip(result.frame_times, result.positions, result.tracks):
                f.write(f"{t},{lat},{lon},{track}\n")
        print(f"Wrote {len(result.positions)} frames to {args.csv}")


if __name__ == "__main__":
    main()
