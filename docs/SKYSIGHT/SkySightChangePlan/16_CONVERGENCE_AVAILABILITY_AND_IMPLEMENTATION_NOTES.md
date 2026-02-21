# 16 Convergence Availability and Implementation Notes

Date: 2026-02-17
Owner: XCPro Team
Status: Research complete, implementation can proceed with one point-value caveat

## Purpose

Record what is now verified for SkySight convergence support, what is still
unknown, and the safest way to implement convergence in XCPro.

## What Is Verified

### 1) SkySight exposes Convergence as a product

From authenticated SkySight bootstrap (`tmp_skysight_after_login.html`):

- `id`: `wblmaxmin`
- `name`: `Convergence`
- `type`: `vert`
- `unit`: `m/s`
- `min`: `-0.25`
- `max`: `0.75`
- `step`: `0.1`
- `models`: `ss`, `hrrr`, `cosmo`

### 2) SkySight web app routes Convergence as a first-class overlay

From SkySight secure bundle (`tmp_skysight_main_live_fetch.js`):

- Route exists: `/convergence`
- Convergence root uses parameter `wblmaxmin`
- Convergence screen enables optional wind overlay together with convergence
  (pattern: `enable("wblmaxmin", "sfcwind0")`)

### 3) SkySight point endpoint is live, but convergence field is not yet proven

Live point calls were successful:

- `POST https://cf.skysight.io/point/{lat}/{lon}?region={region}`

Observed fields include `wstar`, `dwcrit`, `accrain`, `sfcwindspd`,
`bltopwindspd`, `potfd`, `HGT`, `hwcrit`, etc.

Current gap:

- No observed `wblmaxmin` field in captured point payloads, so convergence
  point-value mapping is not yet contract-proven.

## XCPro Implementation Guidance

## Overlay support

Add convergence as a normal primary overlay parameter in
`SkySightForecastProviderAdapter` with:

- `id = ForecastParameterId("wblmaxmin")`
- `name = "Convergence"`
- `category = "Lift"` (or existing category convention used in XCPro)
- `unitLabel = "m/s"`
- `supportsLegend = true`
- `supportsTiles = true`

Use standard non-wind tile path:

- `https://edge.skysight.io/{target}/{date}/{time}/wblmaxmin/{z}/{x}/{y}`

This follows the same raster/vector indexed-fill branch as other primary
non-wind products.

## Point-value behavior (temporary contract-safe mode)

Until `wblmaxmin` point field mapping is proven:

- Keep convergence overlay enabled and renderable.
- Mark convergence point-value as unavailable in XCPro callout/status.
- Do not treat missing convergence point value as fatal overlay failure.

## Evidence still needed to remove caveat

Capture and commit redacted proofs:

1) `wblmaxmin` tile success evidence (headers and sample URL pattern).
2) `wblmaxmin` legend success payload.
3) Point payload that includes convergence value field, or official mapping
   from SkySight for convergence point value.

If (3) cannot be obtained, keep convergence point query disabled and documented.

## Risks

- Main risk is only point-value mapping uncertainty.
- Overlay rendering risk is low because parameter id and route presence are
  confirmed in authenticated SkySight app artifacts.

## Recommended next step

Implement convergence overlay now behind the existing parameter catalog flow,
with point-value fallback message until the final point-field proof is captured.
