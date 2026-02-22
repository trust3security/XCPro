# Risk Register: SI Migration

Date: 2026-02-22
Status: Active

## Risks

1. Unit contract changes break task UI assumptions.
- Impact: high
- Mitigation: migrate via explicit wrappers, update callers in same PR, add tests.

2. Legacy AAT behavior changes unexpectedly after fixing unit bugs.
- Impact: high
- Mitigation: fixture-based before/after validation tests; targeted manual scenario checks.

3. Regression in replay determinism due to conversion timing changes.
- Impact: high
- Mitigation: do not alter replay clock semantics; isolate conversions at ingress only.

4. Hidden km assumptions in scattered helper code.
- Impact: medium
- Mitigation: repeated code sweeps, naming cleanup, temporary static checks.

5. Incomplete migration leaves mixed contracts and future bugs.
- Impact: medium
- Mitigation: enforce exit criteria and remove deprecated wrappers before sign-off.

## Blocking Criteria
- Any unresolved P0 mismatch blocks compliance sign-off.
- Any untested contract change blocks merge.
