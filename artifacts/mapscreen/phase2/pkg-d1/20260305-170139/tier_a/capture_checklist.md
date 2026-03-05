# Capture Checklist

Tier folder: C:\Users\Asus\AndroidStudioProjects\XCPro\artifacts\mapscreen\phase2\pkg-d1\20260305-170139\tier_a

Scenarios:

- scenario-3-panzoom-ogn-adsb-rain
- scenario-7-dense-traffic-scia
- scenario-8-mixed-load-stress

Required outputs:
- traces/perfetto/*.perfetto-trace
- traces/macrobenchmark/*.json
- traces/profiler/*

After capture, update top-level metrics.json and gate_result.json with measured SLO values.
