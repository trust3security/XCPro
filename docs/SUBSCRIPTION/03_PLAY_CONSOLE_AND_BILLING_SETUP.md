# Play Console and Billing Setup

## Do this before wiring billing

1. Finalize the production package name
2. Create the Play Console app using that package name
3. Ship an internal-test build
4. Create subscription products:
   - `xcpro_soar`
   - `xcpro_xc`
   - `xcpro_pro`
5. Add base plans:
   - `monthly`
   - `annual`
6. Add offers only after the base flow works

## Product modeling rules

- Free is not a paid product
- Each paid plan is its own subscription product
- Monthly and annual are base plans, not separate products
- Trial/intro/win-back are offers, not new products

## Price operations rules

- Do not hardcode prices in source
- Pull price display data from `ProductDetails`
- Keep product IDs stable even if copy or pricing changes
- Treat existing-subscriber price handling as an operations concern, not app logic

## Testing setup

- Use internal testing first
- Add license testers early
- Use Play Billing Lab for edge-case testing where useful
- Test upgrade, downgrade, cancellation, grace period, account hold, restore, refund, and expired states

## What not to do

- do not test only the happy path
- do not build pricing logic into tier enums
- do not create throwaway product IDs
- do not rename base plan IDs after launch planning
