# ADSB_md_patch_aircraft_identity.md — Patch to enforce ICAO24 + metadata enrichment

Apply this patch to your ADS‑B implementation tasks:

1) Parse ICAO24 from state vector index 0 (string hex).
   - normalize lowercase for storage
   - show uppercase in UI if desired
2) Use ICAO24 as the MapLibre Feature.id (no flicker)
3) Details sheet must always display ICAO24
4) Implement `AircraftMetadataRepository` keyed by ICAO24:
   - local Room table
   - WorkManager sync/import from OpenSky aircraft database CSV
5) Join metadata into ADS‑B UI model:
   - registration, typecode, model, manufacturer, operator, icaoaircrafttype
6) If metadata missing, show “Metadata not available” (but never hide ICAO24)

Authoritative references:
- ICAO24 as 6-char hex identity: https://openskynetwork.github.io/opensky-api/
- ICAO24 in /states/all index 0 + extended param: https://openskynetwork.github.io/opensky-api/rest.html
- Aircraft database CSV + “as is”: https://opensky-network.org/data/aircraft
