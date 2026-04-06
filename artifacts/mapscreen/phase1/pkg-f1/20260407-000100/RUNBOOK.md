# Package Capture Runbook

Package: pkg-f1
Phase: phase1
RunId: 20260407-000100

1. Connect Tier A device/emulator and capture scenario traces into:
   - tier_a/traces/perfetto/
   - tier_a/traces/macrobenchmark/
   - tier_a/traces/profiler/
2. Repeat for Tier B in tier_b/... folders.
3. Initial metric template seeding and threshold verification already ran sequentially after scaffold creation.
4. Update metrics.json with Tier A/B measured values.
5. Rerun verify_mapscreen_package_evidence.ps1 to refresh threshold_check.json and gate_result.json.

Current status: blocked_pending_perf_evidence
