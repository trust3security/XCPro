# Package Capture Runbook

Package: pkg-w1
Phase: phase2
RunId: 20260305-145351

1. Connect Tier A device/emulator and capture scenario traces into:
   - tier_a/traces/perfetto/
   - tier_a/traces/macrobenchmark/
   - tier_a/traces/profiler/
2. Repeat for Tier B in tier_b/... folders.
3. Update metrics.json with p50/p95/p99 and CI values.
4. Update gate_result.json with SLO pass/fail decision.

Current status: blocked_pending_perf_evidence
