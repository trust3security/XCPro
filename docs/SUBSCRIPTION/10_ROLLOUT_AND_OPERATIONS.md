# Rollout and Operations

## Rollout sequence

1. Internal test only
2. Small tester group with license testers
3. Validate purchase, restore, expiry, downgrade, and refund handling
4. Validate support playbook
5. Release with monitoring
6. Expand offers only after the base lifecycle is proven

## Support scenarios to document

- user paid but app still shows Free
- user changed device and wants restore
- user upgraded from Soar to XC
- user downgraded from Pro to XC
- renewal failed / grace period / account hold
- refund or revoke
- family / account mismatch confusion

## Observability

Track:
- purchase started
- purchase success
- purchase cancelled
- purchase verification failed
- entitlement refresh success/failure
- paywall viewed
- restore attempted
- restore success/failure

Do not log:
- raw secrets
- full purchase tokens in plaintext logs
- sensitive personal data

## Rollback plan

If monetization rollout causes instability:
- keep app usable in Free mode
- disable or hide paywall entry points only if necessary
- stop selling broken offers before shipping new code
- preserve existing user data
- do not destroy entitlement history during rollback
