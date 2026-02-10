# ADSB_md_patch_aircraft_metadata_required.md
**v5: enforce “true aircraft identification via ICAO24” implementation**

> Status: Superseded on 2026-02-10 by `ADSB_ICAO_METADATA_IMPLEMENTATION_PLAN.md`.
> Keep this file for historical context only.

## Problem
Current UI “Type” = OpenSky category bucket.
That will never reliably show registration/typecode/model.

## Fix: mandatory tasks for Codex
1) Implement `AircraftMetadataRepository` keyed by ICAO24.
2) Implement Room table `adsb_aircraft_metadata`.
3) Implement WorkManager sync worker:
   - downloads: https://opensky-network.org/datasets/metadata/aircraftDatabase.csv
   - stream-parse quoted CSV with header mapping
   - batch upsert into Room
   - exposes syncState (ready/running/progress/error)
4) Join metadata into ADS‑B UI model every refresh (one IN query).
5) Update details sheet:
   - show Aircraft identification (registration/typecode/model)
   - show Live state (icao24, callsign, altitude, speed, track, vert rate)
   - show Emitter category (label + raw int), not “Type”
6) Add tests (CSV parse + Room import + lookup by ICAO24).

References:
- /states/all index 0 = icao24, extended=1 category: https://openskynetwork.github.io/opensky-api/rest.html
- aircraft database CSV and description: https://opensky-network.org/data
- aircraft database page: https://opensky-network.org/data/aircraft
