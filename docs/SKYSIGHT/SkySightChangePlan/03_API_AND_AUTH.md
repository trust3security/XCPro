# API and Auth (Current Status)

Date: 2026-02-24
Owner: XCPro Team
Status: Updated to match implemented runtime

## Purpose

Document the API and auth assumptions currently used by XCPro SkySight forecast and
satellite overlays, and define the boundaries for future auth hardening work.

## Current Implementation Summary

Forecast adapter:
- Provider adapter: `SkySightForecastProviderAdapter`.
- Contract details and parameter mappings are maintained in:
  - `docs/SKYSIGHT/SkySightChangePlan/09_SKYSIGHT_API_CONTRACT_DETAILS.md`.
- Runtime uses captured endpoint and field contracts for catalog, tiles, legends, and point values.

Satellite overlay:
- Runtime overlay owner: `MapOverlayManager` -> `SkySightSatelliteOverlay`.
- Tile contract is maintained in:
  - `docs/SKYSIGHT/SkySightChangePlan/18_SATELLITE_OVERLAY_IMPLEMENTATION_PLAN.md`.
- Hosts are allowlisted in MapLibre network configuration, including `satellite.skysight.io`.

## Auth Posture (Current)

- No user-entered SkySight credential flow is implemented in XCPro runtime.
- No session-cookie login path is implemented for overlays.
- Runtime behavior depends on captured contract compatibility and configured app key/header policy.

## Security and Compliance Guardrails

- Do not log secrets, tokens, or sensitive headers.
- Keep provider-specific literals and legal attribution handling limited to required integration internals.
- Any move to credentialed auth/session flow must be documented first and reviewed against:
  - `docs/SKYSIGHT/SkySightChangePlan/08_RISKS_LEGAL_AND_COMPLIANCE.md`
  - `docs/SKYSIGHT/SkySightChangePlan/SKYSIGHT_RISK_AND_GATES.md`

## Follow-up Work (If Needed)

1. Add explicit token/session contract evidence if SkySight requires authenticated map tile access.
2. Add failure-mode matrix for auth-expired vs network-failed vs coverage-unavailable.
3. Add tests for auth header presence/absence policy per build variant.
