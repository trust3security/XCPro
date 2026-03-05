# Capture Checklist

Tier folder: C:\Users\Asus\AndroidStudioProjects\XCPro\artifacts\mapscreen\phase2\pkg-g1\20260305-170311\tier_b

Scenarios:

- scenario-6-replay-render-sync-overlays
- scenario-8-mixed-load-stress

Required outputs:
- traces/perfetto/*.perfetto-trace
- traces/macrobenchmark/*.json
- traces/profiler/*

After capture, update top-level metrics.json and gate_result.json with measured SLO values.
