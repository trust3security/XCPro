# Rollout and Operations

## Rollout sequence

1. Internal test only
2. Small tester group with license testers
3. Validate purchase, restore, expiry, downgrade, provider link, and provider account-state handling
4. Validate support playbook
5. Release with monitoring
6. Expand offers only after the base lifecycle is proven

## Support scenarios to document

- user paid but app still shows Free
- user changed device and wants restore
- user upgraded from Basic to Soaring
- user upgraded from Soaring to XC
- user upgraded from XC to Pro
- user downgraded from Pro to XC
- renewal failed / grace period / account hold
- refund or revoke
- user linked SkySight but still sees premium SkySight surfaces locked
- user linked a free SkySight account and expected paid features
- user unlinked or re-linked SkySight
- family / account mismatch confusion

## Observability

Track:
- purchase started
- purchase success
- purchase cancelled
- purchase verification failed
- entitlement refresh success / failure
- paywall viewed
- restore attempted
- restore success / failure
- SkySight link started
- SkySight link success / failure
- provider account-state refresh success / failure

Do not log:
- raw secrets
- full purchase tokens in plaintext logs
- raw third-party credentials
- sensitive personal data

## Rollback plan

If monetization rollout causes instability:
- keep app usable in Free mode
- disable or hide paywall entry points only if necessary
- keep non-premium core flight value available
- stop selling broken offers before shipping new code
- preserve existing user data
- do not destroy entitlement history during rollback
- do not silently unlock provider-backed premium surfaces during rollback
