# Capture Checklist

Tier folder: C:\Users\Asus\AndroidStudioProjects\XCPro\artifacts\mapscreen\phase3\pkg-e1\20260305-190450\tier_b

Scenarios:

- scenario-8-mixed-load-stress
- scenario-3-panzoom-ogn-adsb-rain
- scenario-4-aat-edit-drag

Required outputs:
- traces/perfetto/*.perfetto-trace
- traces/macrobenchmark/*.json
- traces/profiler/*

After capture, update top-level metrics.json and gate_result.json with measured SLO values.
