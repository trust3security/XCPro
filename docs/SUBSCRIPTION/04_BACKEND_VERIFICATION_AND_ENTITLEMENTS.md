# Backend Verification and Entitlements

## Position

For XCPro, the backend should be authoritative. The device is not trusted to decide paid access.

## Required backend responsibilities

1. Accept purchase tokens from the app
2. Verify purchases against Google Play Developer API
3. Store the verified subscription state
4. Acknowledge purchases through the correct flow
5. Maintain entitlements after renewals, cancellations, expiries, refunds, and plan changes
6. Process RTDN messages
7. Expose a clean entitlement API to the app
8. Expose provider-linked state needed for dual-gated features, such as SkySight account status

## Minimal API contract

### `POST /billing/google/verify-subscription`
Request:
- authenticated XCPro user ID
- package name
- product ID
- purchase token
- optional obfuscated account/profile IDs

Response:
- canonical subscription status
- canonical tier
- granted features
- expiry time
- next refresh hint
- whether acknowledgement has completed

### `GET /me/entitlements`
Response:
- tier
- features
- expiry
- source
- last verified time
- integration states needed for access policy, for example:
  - `skysight.accountState = UNLINKED | LINKED_FREE | LINKED_PAID | LINK_ERROR | UNKNOWN`

### `POST /billing/google/rtdn`
- Pub/Sub push or pull receiver
- updates server-side subscription state
- should be idempotent

### `POST /integrations/skysight/link`
Request:
- authenticated XCPro user ID
- SkySight credentials or delegated auth payload, depending on the final integration design

Response:
- canonical SkySight account state
- whether the linked account is currently premium-capable
- refresh / expiry metadata if available

### `DELETE /integrations/skysight/link`
- removes the stored XCPro-side SkySight link state
- should revoke local premium SkySight access immediately

## Suggested storage model

### Tables / collections

- `users`
- `subscription_purchases`
- `subscription_entitlements`
- `subscription_events`
- `subscription_catalog`
- `integration_accounts`
- `integration_account_events`
- `audit_log`

## Required lifecycle handling

- new purchase
- restore purchase
- renewal
- cancellation pending term end
- expiry
- refund / revoke
- grace period
- account hold
- upgrade / downgrade / replace
- linked purchase token chains where applicable
- SkySight link
- SkySight unlink
- SkySight free -> paid transition
- SkySight paid -> free / expired transition

## Security rules

- never trust the client’s claimed tier
- always validate package name
- always validate product ID
- always validate purchase token ownership and current status
- treat the backend as the only durable XCPro subscription authority
- never treat SkySight linked state as a substitute for XCPro subscription entitlement
- do not store raw third-party credentials in plaintext
- keep an audit trail for support and charge disputes

## Offline rule recommendation

- cached entitlements may be used for temporary display continuity
- new premium XCPro access must not be granted solely from local client assumptions
- new premium SkySight-backed access must not be granted solely from stale local linked-account assumptions
- stale cache behavior must be explicit and tested
