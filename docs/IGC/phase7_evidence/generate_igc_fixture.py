#!/usr/bin/env python3
"""Generate deterministic IGC fixtures for XCPro and WeGlide upload checks.

The default profile now targets WeGlide/XCSoar compatibility:
- `A` record uses manufacturer `XCS`
- headers follow the XCSoar shape WeGlide accepts
- `G` records are signed with the XCSoar/XCS digest algorithm

`--profile xcpro` keeps a simpler unsigned fixture for local parser work.
"""

from __future__ import annotations

import argparse
import math
from datetime import datetime, timedelta, timezone
from pathlib import Path

DEFAULT_OUTPUT = "docs/IGC/phase7_evidence/fixtures/generated_phase7_basic.igc"

XCSoar_G_KEYS = (
    (0x1C80A301, 0x9EB30B89, 0x39CB2AFE, 0x0D0FEA76),
    (0x48327203, 0x3948EBEA, 0x9A9B9C9E, 0xB3BED89A),
    (0x67452301, 0xEFCDAB89, 0x98BADCFE, 0x10325476),
    (0xC8E899E8, 0x9321C28A, 0x438EBA12, 0x8CBE0AEE),
)

MD5_K = (
    3614090360, 3905402710, 606105819, 3250441966,
    4118548399, 1200080426, 2821735955, 4249261313,
    1770035416, 2336552879, 4294925233, 2304563134,
    1804603682, 4254626195, 2792965006, 1236535329,
    4129170786, 3225465664, 643717713, 3921069994,
    3593408605, 38016083, 3634488961, 3889429448,
    568446438, 3275163606, 4107603335, 1163531501,
    2850285829, 4243563512, 1735328473, 2368359562,
    4294588738, 2272392833, 1839030562, 4259657740,
    2763975236, 1272893353, 4139469664, 3200236656,
    681279174, 3936430074, 3572445317, 76029189,
    3654602809, 3873151461, 530742520, 3299628645,
    4096336452, 1126891415, 2878612391, 4237533241,
    1700485571, 2399980690, 4293915773, 2240044497,
    1873313359, 4264355552, 2734768916, 1309151649,
    4149444226, 3174756917, 718787259, 3951481745,
)

MD5_R = (
    7, 12, 17, 22, 7, 12, 17, 22, 7, 12, 17, 22, 7, 12, 17, 22,
    5, 9, 14, 20, 5, 9, 14, 20, 5, 9, 14, 20, 5, 9, 14, 20,
    4, 11, 16, 23, 4, 11, 16, 23, 4, 11, 16, 23, 4, 11, 16, 23,
    6, 10, 15, 21, 6, 10, 15, 21, 6, 10, 15, 21, 6, 10, 15, 21,
)


def _parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Generate a deterministic IGC fixture for testing parsers and uploads.",
    )
    parser.add_argument(
        "--output",
        default=DEFAULT_OUTPUT,
        help="Output path for the fixture file.",
    )
    parser.add_argument(
        "--profile",
        choices=["weglide", "xcpro"],
        default="weglide",
        help="Generation profile. `weglide` emits a signed XCSoar-style file.",
    )
    parser.add_argument(
        "--manufacturer",
        default=None,
        help="3-char manufacturer code. Defaults to XCS for WeGlide, XCP otherwise.",
    )
    parser.add_argument(
        "--logger-id",
        default=None,
        help="Logger identity suffix. Defaults to AAA for WeGlide, 000001 otherwise.",
    )
    parser.add_argument(
        "--serial-id",
        dest="logger_id",
        help=argparse.SUPPRESS,
    )
    parser.add_argument(
        "--signer",
        choices=["auto", "none", "xcs"],
        default="auto",
        help="Signature mode. `auto` chooses XCS signing for WeGlide profile.",
    )
    parser.add_argument(
        "--points",
        type=int,
        default=240,
        help="Number of B records to write.",
    )
    parser.add_argument(
        "--interval-seconds",
        type=int,
        default=None,
        help="Gap between B-record timestamps in seconds. Defaults to 1 for WeGlide, 5 otherwise.",
    )
    parser.add_argument(
        "--start-time",
        default="09:00:00",
        help="Flight start UTC time in HH:MM:SS for first B record.",
    )
    parser.add_argument(
        "--start-date",
        default=None,
        help="Flight UTC date in YYYY-MM-DD. Defaults to the previous UTC date.",
    )
    parser.add_argument(
        "--pilot",
        default="DOE JOHN",
        help="Pilot name for the header.",
    )
    parser.add_argument(
        "--copilot",
        default="",
        help="Copilot/crew header value.",
    )
    parser.add_argument(
        "--glider",
        default="ASK-21",
        help="Glider type.",
    )
    parser.add_argument(
        "--registration",
        default="D-1234",
        help="Glider registration/ID.",
    )
    parser.add_argument(
        "--competition-id",
        default="X1",
        help="Competition ID header value.",
    )
    parser.add_argument(
        "--gps-driver",
        default="INTERNAL",
        help="GPS/driver descriptor for XCSoar-style header.",
    )
    parser.add_argument(
        "--app-version",
        default="XCPro 3.0.0",
        help="Firmware version string for the XCPro profile.",
    )
    parser.add_argument(
        "--hardware",
        default="XCPro device",
        help="Hardware string for the XCPro profile.",
    )
    parser.add_argument(
        "--latitude",
        type=float,
        default=48.133300,
        help="Starting latitude in decimal degrees.",
    )
    parser.add_argument(
        "--longitude",
        type=float,
        default=11.566700,
        help="Starting longitude in decimal degrees.",
    )
    parser.add_argument(
        "--altitude-base",
        type=int,
        default=640,
        help="Ground/base altitude in meters.",
    )
    parser.add_argument(
        "--include-weglide-warning-note",
        action="store_true",
        help="Append a signed comment noting the file was generated for upload testing.",
    )
    parser.add_argument(
        "--line-endings",
        choices=["crlf", "lf"],
        default="crlf",
        help="Output line ending style.",
    )
    return parser.parse_args()


class _XCSoarMd5:
    def __init__(self, state: tuple[int, int, int, int]) -> None:
        self.buffer = bytearray(64)
        self.a, self.b, self.c, self.d = state
        self.message_length = 0

    @staticmethod
    def _leftrotate(value: int, count: int) -> int:
        value &= 0xFFFFFFFF
        return ((value << count) | (value >> (32 - count))) & 0xFFFFFFFF

    def append_byte(self, value: int) -> None:
        position = self.message_length % 64
        self.buffer[position] = value & 0xFF
        self.message_length += 1
        if position == 63:
            self._process_512()

    def append_bytes(self, values: bytes) -> None:
        for value in values:
            self.append_byte(value)

    def finalize(self) -> None:
        buffer_left_over = self.message_length % 64
        if buffer_left_over < 56:
            self.buffer[buffer_left_over] = 0x80
            for index in range(buffer_left_over + 1, 64):
                self.buffer[index] = 0
        else:
            self.buffer[buffer_left_over] = 0x80
            for index in range(buffer_left_over + 1, 64):
                self.buffer[index] = 0
            self._process_512()
            for index in range(64):
                self.buffer[index] = 0

        bit_length = self.message_length * 8
        self.buffer[56:64] = bit_length.to_bytes(8, byteorder="little", signed=False)
        self._process_512()

    def digest_hex(self) -> str:
        parts = (
            self.a.to_bytes(4, byteorder="little", signed=False).hex(),
            self.b.to_bytes(4, byteorder="little", signed=False).hex(),
            self.c.to_bytes(4, byteorder="little", signed=False).hex(),
            self.d.to_bytes(4, byteorder="little", signed=False).hex(),
        )
        return "".join(parts)

    def _process_512(self) -> None:
        words = [
            int.from_bytes(self.buffer[index:index + 4], byteorder="little", signed=False)
            for index in range(0, 64, 4)
        ]

        a = self.a
        b = self.b
        c = self.c
        d = self.d

        for index in range(64):
            if index <= 15:
                f = (b & c) | ((~b) & d)
                g = index
            elif index <= 31:
                f = (d & b) | ((~d) & c)
                g = (5 * index + 1) % 16
            elif index <= 47:
                f = b ^ c ^ d
                g = (3 * index + 5) % 16
            else:
                f = c ^ (b | (~d))
                g = (7 * index) % 16

            temp = d
            d = c
            c = b
            total = (a + f + MD5_K[index] + words[g]) & 0xFFFFFFFF
            b = (b + self._leftrotate(total, MD5_R[index])) & 0xFFFFFFFF
            a = temp

        self.a = (self.a + a) & 0xFFFFFFFF
        self.b = (self.b + b) & 0xFFFFFFFF
        self.c = (self.c + c) & 0xFFFFFFFF
        self.d = (self.d + d) & 0xFFFFFFFF


def _is_reserved_igc_char(ch: str) -> bool:
    return ch in {"$", "*", "!", "\\", "^", "~"}


def _is_valid_igc_char(ch: str) -> bool:
    code = ord(ch)
    return 0x20 <= code <= 0x7E and not _is_reserved_igc_char(ch)


def _include_record_in_xcs_g_calc(line: str) -> bool:
    if not line:
        return False
    first = line[0]
    if first == "L":
        return line[1:].startswith("XCS")
    if first == "G":
        return False
    if first == "H":
        return not line[1:].startswith("OP")
    return True


def _compute_xcs_g_digest(records: list[str]) -> str:
    digests = [_XCSoarMd5(state) for state in XCSoar_G_KEYS]
    ignore_comma = True

    for line in records:
        if not _include_record_in_xcs_g_calc(line):
            continue

        if line.startswith("HFFTYFRTYPE:XCSOAR,XCSOAR ") and " 6.5 " in line[25:]:
            ignore_comma = False

        for ch in line:
            if ignore_comma and ch == ",":
                continue
            if not _is_valid_igc_char(ch):
                continue
            value = ord(ch)
            for digest in digests:
                digest.append_byte(value)

    for digest in digests:
        digest.finalize()

    return "".join(digest.digest_hex() for digest in digests)


def _wrap_xcs_g_records(digest_hex: str) -> list[str]:
    return [f"G{digest_hex[index:index + 16]}" for index in range(0, len(digest_hex), 16)]


def _validate_profile_args(args: argparse.Namespace) -> tuple[str, str, str, int]:
    manufacturer = args.manufacturer or ("XCS" if args.profile == "weglide" else "XCP")
    manufacturer = manufacturer.upper()
    if len(manufacturer) != 3 or not manufacturer.isalnum():
        raise ValueError("manufacturer must be exactly 3 alphanumeric characters")

    if args.profile == "weglide":
        logger_id = (args.logger_id or "AAA").upper()
        if len(logger_id) != 3 or not logger_id.isalnum():
            raise ValueError("weglide profile requires a 3-character alphanumeric logger-id")
    else:
        logger_id = (args.logger_id or "000001").upper()
        if len(logger_id) < 3 or not logger_id.isalnum():
            raise ValueError("xcpro profile requires an alphanumeric logger-id of at least 3 characters")

    signer = args.signer
    if signer == "auto":
        signer = "xcs" if args.profile == "weglide" else "none"
    if signer == "xcs" and manufacturer != "XCS":
        raise ValueError("xcs signer requires manufacturer XCS")

    interval_seconds = args.interval_seconds
    if interval_seconds is None:
        interval_seconds = 1 if args.profile == "weglide" else 5
    if interval_seconds <= 0:
        raise ValueError("interval-seconds must be positive")
    if args.points <= 0:
        raise ValueError("points must be positive")

    return manufacturer, logger_id, signer, interval_seconds


def _flight_date(start_date: str | None) -> datetime:
    if start_date is None:
        return (datetime.now(timezone.utc) - timedelta(days=1)).replace(
            hour=0,
            minute=0,
            second=0,
            microsecond=0,
        )
    return datetime.strptime(start_date, "%Y-%m-%d").replace(tzinfo=timezone.utc)


def _h_date(date: datetime) -> str:
    return date.strftime("%d%m%y")


def _parse_start_seconds(start_time: str) -> int:
    hhmmss = start_time.split(":")
    if len(hhmmss) != 3:
        raise ValueError("--start-time must be HH:MM:SS")
    hours, minutes, seconds = [int(part) for part in hhmmss]
    if not (0 <= hours <= 23 and 0 <= minutes <= 59 and 0 <= seconds <= 59):
        raise ValueError("--start-time must be a valid UTC time")
    return hours * 3600 + minutes * 60 + seconds


def _as_igc_time(seconds: int) -> str:
    if seconds < 0:
        raise ValueError("seconds must be non-negative")
    hh = (seconds // 3600) % 24
    mm = (seconds % 3600) // 60
    ss = seconds % 60
    return f"{hh:02d}{mm:02d}{ss:02d}"


def _as_lat_ddmmmmm(value: float) -> str:
    hemi = "N" if value >= 0 else "S"
    abs_val = abs(value)
    deg = int(abs_val)
    minutes_total = (abs_val - deg) * 60.0
    minute_int = int(minutes_total)
    minute_frac = int(round((minutes_total - minute_int) * 1000))
    if minute_frac == 1000:
        minute_int += 1
        minute_frac = 0
    if minute_int == 60:
        deg += 1
        minute_int = 0
    return f"{deg:02d}{minute_int:02d}{minute_frac:03d}{hemi}"


def _as_lon_dddmmmmm(value: float) -> str:
    hemi = "E" if value >= 0 else "W"
    abs_val = abs(value)
    deg = int(abs_val)
    minutes_total = (abs_val - deg) * 60.0
    minute_int = int(minutes_total)
    minute_frac = int(round((minutes_total - minute_int) * 1000))
    if minute_frac == 1000:
        minute_int += 1
        minute_frac = 0
    if minute_int == 60:
        deg += 1
        minute_int = 0
    return f"{deg:03d}{minute_int:02d}{minute_frac:03d}{hemi}"


def _normalize_altitude(value: int) -> int:
    if value < -9999:
        return -9999
    if value > 99999:
        return 99999
    return value


def _as_alt(value: int) -> str:
    return f"{_normalize_altitude(value):05d}"


def _build_altitudes(point_count: int, base_alt: int, profile: str) -> list[int]:
    if profile == "xcpro":
        altitudes: list[int] = []
        for idx in range(point_count):
            if idx < 4:
                altitude = base_alt - 20
            elif idx < point_count - 4:
                climb = min(160, (idx - 4) * 8)
                drift = int(12 * math.sin((idx - 4) / 10))
                altitude = base_alt + climb + drift
            else:
                sink = (point_count - 1 - idx) * 4
                altitude = base_alt - 20 + max(0, 40 - sink)
            altitudes.append(max(0, altitude))
        return altitudes

    pre_ground = max(12, point_count // 10)
    post_ground = max(12, point_count // 10)
    climb = max(40, point_count // 4)
    descent = max(40, point_count // 4)
    cruise = max(20, point_count - pre_ground - post_ground - climb - descent)
    total = pre_ground + climb + cruise + descent + post_ground
    if total > point_count:
        cruise = max(20, cruise - (total - point_count))
    elif total < point_count:
        cruise += point_count - total

    altitudes = []
    for idx in range(point_count):
        if idx < pre_ground:
            altitude = base_alt
        elif idx < pre_ground + climb:
            progress = (idx - pre_ground + 1) / climb
            altitude = base_alt + int(progress * 520)
        elif idx < pre_ground + climb + cruise:
            cruise_idx = idx - pre_ground - climb
            altitude = base_alt + 520 + int(18 * math.sin(cruise_idx / 8.0))
        elif idx < pre_ground + climb + cruise + descent:
            descent_idx = idx - pre_ground - climb - cruise
            progress = (descent_idx + 1) / descent
            altitude = base_alt + max(0, int((1.0 - progress) * 520))
        else:
            altitude = base_alt
        altitudes.append(max(0, altitude))
    return altitudes


def _build_path_points(
    start_lat: float,
    start_lon: float,
    point_count: int,
    profile: str,
) -> list[tuple[float, float]]:
    if profile == "xcpro":
        return [
            (start_lat + index * 0.00007, start_lon + index * 0.00009)
            for index in range(point_count)
        ]

    pre_ground = max(12, point_count // 10)
    post_ground = max(12, point_count // 10)
    climb = max(40, point_count // 4)
    descent = max(40, point_count // 4)
    cruise = max(20, point_count - pre_ground - post_ground - climb - descent)
    total = pre_ground + climb + cruise + descent + post_ground
    if total > point_count:
        cruise = max(20, cruise - (total - point_count))
    elif total < point_count:
        cruise += point_count - total

    lat = start_lat
    lon = start_lon
    points: list[tuple[float, float]] = []
    for idx in range(point_count):
        if idx < pre_ground:
            lat_step = 0.0
            lon_step = 0.0
        elif idx < pre_ground + climb:
            lat_step = 0.00010
            lon_step = 0.00017
        elif idx < pre_ground + climb + cruise:
            lat_step = 0.00018
            lon_step = 0.00026
        elif idx < pre_ground + climb + cruise + descent:
            lat_step = 0.00009
            lon_step = 0.00014
        else:
            lat_step = 0.0
            lon_step = 0.0
        lat += lat_step
        lon += lon_step
        points.append((lat, lon))
    return points


def _build_xcsoar_header_lines(
    header_date: datetime,
    manufacturer: str,
    logger_id: str,
    pilot: str,
    copilot: str,
    glider: str,
    registration: str,
    competition_id: str,
    gps_driver: str,
) -> list[str]:
    return [
        f"A{manufacturer}{logger_id}",
        f"HFDTE{_h_date(header_date)}",
        "HFFXA050",
        f"HFPLTPILOTINCHARGE:{pilot}",
        f"HFCM2CREW2:{copilot if copilot else 'NIL'}",
        f"HFGTYGLIDERTYPE:{glider}",
        f"HFGIDGLIDERID:{registration}",
        f"HFCIDCOMPETITIONID:{competition_id}",
        "HFFTYFRTYPE:XCSOAR,XCSOAR Android synthetic",
        f"HFGPS:{gps_driver}",
        "HFDTM100DATUM:WGS-1984",
        "I023638FXA3940SIU",
    ]


def _build_xcpro_header_lines(
    header_date: datetime,
    manufacturer: str,
    logger_id: str,
    pilot: str,
    glider: str,
    app_version: str,
    hardware: str,
) -> list[str]:
    return [
        f"A{manufacturer}{logger_id}",
        f"HFDTEDATE:{_h_date(header_date)},01",
        f"HFPLTPILOTINCHARGE:{pilot}",
        "HFCM2CREW2:NIL",
        f"HFGTYGLIDERTYPE:{glider}",
        "HFGIDGLIDERID:NKN",
        "HFDTMGPSDATUM:WGS84",
        f"HFRFWFIRMWAREVERSION:{app_version}",
        f"HFRHWHARDWAREVERSION:{hardware}",
        "HFFTYFRTYPE:XCPro,Mobile",
        "HFGPSRECEIVER:NKN",
        "HFPRSPRESSALTSENSOR:NKN",
        "HFFRSSECURITY:UNSIGNED",
        "HFALGALTGPS:GEO",
        "HFALPALTPRESSURE:ISA",
    ]


def _build_track_records(
    start_time_seconds: int,
    point_count: int,
    interval_seconds: int,
    start_lat: float,
    start_lon: float,
    base_alt: int,
    profile: str,
) -> list[str]:
    altitudes = _build_altitudes(point_count, base_alt, profile)
    positions = _build_path_points(start_lat, start_lon, point_count, profile)
    records: list[str] = []

    for index in range(point_count):
        time_code = _as_igc_time(start_time_seconds + index * interval_seconds)
        lat_code = _as_lat_ddmmmmm(positions[index][0])
        lon_code = _as_lon_dddmmmmm(positions[index][1])
        altitude = altitudes[index]
        pressure_alt = altitude + (3 if profile == "weglide" else 0)
        gps_alt = altitude

        if profile == "weglide":
            epe = 6 if index >= 6 else 12
            satellites = 10 if index >= 6 else 7
            records.append(
                f"B{time_code}{lat_code}{lon_code}A{_as_alt(pressure_alt)}{_as_alt(gps_alt)}{epe:03d}{satellites:02d}"
            )
        else:
            records.append(
                f"B{time_code}{lat_code}{lon_code}A{_as_alt(pressure_alt)}{_as_alt(gps_alt)}"
            )

    return records


def generate_fixture(args: argparse.Namespace) -> tuple[Path, list[str], str]:
    manufacturer, logger_id, signer, interval_seconds = _validate_profile_args(args)
    output_path = Path(args.output)
    flight_date = _flight_date(args.start_date)
    start_seconds = _parse_start_seconds(args.start_time)

    if args.profile == "weglide":
        records = _build_xcsoar_header_lines(
            header_date=flight_date,
            manufacturer=manufacturer,
            logger_id=logger_id,
            pilot=args.pilot,
            copilot=args.copilot,
            glider=args.glider,
            registration=args.registration,
            competition_id=args.competition_id,
            gps_driver=args.gps_driver,
        )
        records.append(f"F{_as_igc_time(start_seconds)}121701")
    else:
        records = _build_xcpro_header_lines(
            header_date=flight_date,
            manufacturer=manufacturer,
            logger_id=logger_id,
            pilot=args.pilot,
            glider=args.glider,
            app_version=args.app_version,
            hardware=args.hardware,
        )

    records.extend(
        _build_track_records(
            start_time_seconds=start_seconds,
            point_count=args.points,
            interval_seconds=interval_seconds,
            start_lat=args.latitude,
            start_lon=args.longitude,
            base_alt=args.altitude_base,
            profile=args.profile,
        )
    )

    if args.include_weglide_warning_note:
        if signer == "xcs":
            records.append("LXCSFIXTURE:GENERATED FOR WEGLIDE UPLOAD TESTING")
        else:
            records.append("LXCProUploadNote: generated test fixture")

    if signer == "xcs":
        digest = _compute_xcs_g_digest(records)
        records.extend(_wrap_xcs_g_records(digest))
    else:
        digest = ""

    line_sep = "\r\n" if args.line_endings == "crlf" else "\n"
    content = line_sep.join(records) + line_sep
    output_path.parent.mkdir(parents=True, exist_ok=True)
    output_path.write_bytes(content.encode("ascii"))
    return output_path, records, digest


def main() -> None:
    args = _parse_args()
    output_path, records, digest = generate_fixture(args)
    print(f"Wrote {len(records)} lines to {output_path}")
    print(f"Profile: {args.profile}")
    print(f"Signed: {'yes' if digest else 'no'}")
    if digest:
        print(f"G digest: {digest}")
    print(f"First B record: {next((record for record in records if record.startswith('B')), 'N/A')}")


if __name__ == "__main__":
    main()
