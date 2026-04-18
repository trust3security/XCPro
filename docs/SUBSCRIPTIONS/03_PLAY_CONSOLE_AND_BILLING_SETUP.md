# Play Console and Billing Setup

## Do this before wiring real billing

1. Finalize the production package name.
2. Create the Play Console app using that package name.
3. Ship an internal-test build containing the Billing Library.
4. Create subscription products:
   - `xcpro_basic`
   - `xcpro_soaring`
   - `xcpro_xc`
   - `xcpro_pro`
5. Add base plans under each product:
   - `monthly`
   - `annual`
6. Add offers only after the base flow works.

## Product modeling rules

- Free is not a paid product.
- Each paid tier is its own subscription product.
- Monthly and annual are base plans, not separate products.
- Trial / intro / win-back are offers, not new products.
- SkySight premium is **not** a Play subscription product in XCPro unless XCPro is actually reselling it.

## Account and identity rules

- Every app user must already have an XCPro account before normal app use.
- Every Play purchase must be attached to the signed-in XCPro account.
- The Play account is not the XCPro account.
- Use an obfuscated XCPro account identifier in the billing flow.
- Do **not** pass cleartext email to Play identity fields.
- Do **not** silently attach a detected purchase to the wrong XCPro account.

## Price and catalog rules

- Do not hardcode prices in source.
- Pull price display data from `ProductDetails`.
- Keep product IDs stable even if copy or pricing changes.
- Treat existing-subscriber price handling as an operations concern, not app logic.
- If Basic targets about **USD 5.99**, set that in Play Console only.

## Store-copy rules

- Do not claim SkySight premium is included in Soaring / XC / Pro unless that is commercially true.
- Safe wording is:
  - Soaring enables SkySight account linking in XCPro.
  - premium SkySight-backed features require a linked paid SkySight account.
- Keep plan descriptions aligned with the real access model.

## Recommended plan-change setup rules

For v1, implement and test with these assumptions:

- same-tier `monthly <-> annual`:
  - same subscription product
  - base-plan switch
  - default replacement behavior: `WITHOUT_PRORATION`
- cross-tier upgrade:
  - different subscription product
  - replacement behavior: `CHARGE_PRORATED_PRICE`
- cross-tier downgrade:
  - different subscription product
  - replacement behavior: `DEFERRED`

If business rules later change, update the Markdown contract first, then code and tests.

## Testing setup

- Use internal testing first.
- Add license testers early.
- Use Play Billing Lab for edge-case testing where useful.
- Test:
  - sign-in required path
  - Free -> Basic -> Soaring -> XC -> Pro upgrades
  - Pro -> XC -> Soaring -> Basic downgrades
  - same-tier monthly -> annual switch
  - same-tier annual -> monthly switch
  - cancellation
  - grace period
  - account hold
  - restore
  - refund
  - expired states
  - wrong-account restore / account mismatch handling

## What not to do

- do not test only the happy path
- do not build pricing logic into tier enums
- do not create throwaway product IDs
- do not rename base plan IDs after launch planning
- do not represent third-party premium provider status as if it were a Play Billing tier
- do not enable anonymous free mode as a workaround for unfinished auth
- do not trust Play alone as the durable entitlement source of truth

## Authority rule

If any old non-Markdown setup template conflicts with this file, this file wins.
