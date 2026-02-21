# Proposed JSON task schema (example)

This is a pragmatic JSON format for representing a Racing Task in the app.

It is *not* an FAI-defined format; it is meant for internal storage, export/import, and interoperability between components.

## JSON schema (informal)

```json
{
  "version": 1,
  "taskType": "RT",
  "name": "Example RT",
  "dateLocal": "2026-02-18",
  "timezone": "Australia/Sydney",
  "contestSite": {
    "name": "Example Airfield",
    "lat": -34.12345,
    "lon": 150.12345,
    "elevationM": 220
  },

  "start": {
    "type": "LINE",                     // LINE | RING | CYLINDER_START
    "point": { "name": "START", "lat": -34.120, "lon": 150.120 },

    "gateOpenLocal": "12:30:00",
    "gateCloseLocal": "18:10:00",

    "preStartAltitude": { "valueM": 1800, "ref": "MSL" },

    "line": {
      "lengthM": 10000,
      "courseBearingDeg": 45.0,         // start -> TP1 bearing
      "crossingDirectionDeg": 45.0      // usually same as courseBearingDeg
    },

    "pevStart": {
      "enabled": false,
      "waitMin": 5,
      "windowMin": 5
    },

    "energyControl": {
      "enabled": false,
      "maxStartAlt": { "valueM": 2400, "ref": "MSL" },
      "maxStartGroundspeedKph": 170
    }
  },

  "turnpoints": [
    { "name": "TP1", "lat": -34.050, "lon": 150.400, "radiusM": 500 },
    { "name": "TP2", "lat": -33.900, "lon": 150.600, "radiusM": 500 }
  ],

  "steeringPoint": null,                // or {name/lat/lon/radiusM}

  "finish": {
    "type": "RING",                      // RING | LINE
    "point": { "name": "FINISH", "lat": -34.12345, "lon": 150.12345 },

    "ring": { "radiusM": 3000 },
    "line": null,

    "minAltitude": { "valueM": 600, "ref": "MSL" },
    "crossingDirectionDeg": 225.0
  }
}
```

## Notes for implementers

- Store all distances in **meters** and bearings in **degrees true**.
- Keep `radiusM` per TP even if fixed at 500 m for RT; it makes your schema reusable for AAT/DHT later.
- `CYLINDER_START` is the waiver-only Cylinder Start described in SC3 Annex A 2025; only enable when needed.
- For start/finish line geometry you can either:
  - store `courseBearingDeg` + `lengthM` and derive endpoints, or
  - store explicit endpoints. (Endpoints are useful for exact line intersection tests.)

