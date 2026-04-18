# Rollout and Operations

## Rollout sequence

1. Internal test only
2. Small tester group with license testers
3. Validate sign-in, purchase, restore, expiry, monthly/annual switch, downgrade, provider link, and provider account-state handling
4. Validate support playbook
5. Release with monitoring
6. Expand offers only after the base lifecycle is proven

## Operational prerequisites outside the code patch

- final production package name
- Play Console app created with that package name
- internal test track build published
- service account / Google Play Developer API access configured
- RTDN Pub/Sub wiring configured
- license testers configured
- Play Billing Lab access where needed
- backend secrets and operational dashboards configured

## Support scenarios to document

- user cannot access the app because no XCPro account session exists
- user forgot password / needs account recovery
- signed-in free user expects paid features but has no active subscription
- user paid but app still shows Free
- user changed device and wants restore
- user is signed into the wrong XCPro account
- user upgraded from Basic to Soaring
- user upgraded from Soaring to XC
- user upgraded from XC to Pro
- user switched monthly -> annual
- user switched annual -> monthly
- user downgraded from Pro to XC
- renewal failed / grace period / account hold
- refund or revoke
- user linked SkySight but still sees premium SkySight surfaces locked
- user linked a free SkySight account and expected paid features
- user unlinked or re-linked SkySight
- family / account mismatch confusion

## Observability

Track:
- sign-in-required gate hit
- purchase started
- purchase success
- purchase cancelled
- purchase verification failed
- entitlement refresh success / failure
- paywall viewed
- restore attempted
- restore success / failure
- manage-subscription opened
- SkySight link started
- SkySight link success / failure
- provider account-state refresh success / failure
- account mismatch detected / resolved

Do not log:
- raw secrets
- full purchase tokens in plaintext logs
- raw third-party credentials
- sensitive personal data
- cleartext email in places that do not require it

## Rollback plan

If monetization rollout causes instability:
- keep app usable for signed-in Free users
- do not remove the account-required rule unless product explicitly changes
- disable or hide paywall entry points only if necessary
- keep non-premium core flight value available
- stop selling broken offers before shipping new code
- preserve existing user data
- do not destroy entitlement history during rollback
- do not silently unlock provider-backed premium surfaces during rollback

## Authority rule

If any old operations checklist conflicts with this file, this file wins.
