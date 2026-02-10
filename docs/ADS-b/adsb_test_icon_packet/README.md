# README.md
This packet contains:

- `ic_adsb_aircraft_test.png` — one north-up aircraft icon for all ADS-B markers (test icon)
- `ADSB_TestIcon_Orientation.md` — implementation plan
- `ADSB_TestIcon_FileManifest.md` — file checklist

How to use:
1) Copy PNG into `feature/map/src/main/res/drawable/` (or the module owning the map overlay).
2) Follow the plan; implement rotation from OpenSky `true_track` via `track_deg` property.
3) Run and verify the icon points in the direction of travel.

