# Backend Verification and Entitlements

## Position

For XCPro, the backend should be authoritative. The device is not trusted to decide paid access.

## Account and identity contract

- Every entitlement API call requires an authenticated XCPro account.
- The backend must return a canonical `FREE` entitlement snapshot for a signed-in XCPro user with no active paid subscription.
- Signed-out state is not equivalent to `FREE`.
- Google Play account identity and XCPro account identity must remain separate.
- Purchases, restore behavior, and support flows attach to the XCPro account.

## Required backend responsibilities

1. Accept purchase tokens from the app.
2. Verify purchases against Google Play Developer API.
3. Store the verified subscription state.
4. Acknowledge purchases through the correct flow.
5. Maintain entitlements after renewals, cancellations, expiries, refunds, and plan changes.
6. Process RTDN messages and dedupe them.
7. Expose a clean entitlement API to the app.
8. Expose provider-linked state needed for dual-gated features, such as SkySight account status.
9. Prevent purchases from silently attaching to the wrong XCPro account.
10. Return a canonical free entitlement state for signed-in non-paying users.

## Recommended API contract

Route names may follow repo conventions, but the app needs at least these behaviors.

### `POST /api/v1/subscriptions/googleplay/sync`
Request:
- authenticated XCPro user ID
- package name
- product ID
- base plan ID
- purchase token
- obfuscated account/profile IDs if used
- optional prior product/base-plan context for upgrades or downgrades

Response:
- canonical subscription status
- canonical tier
- canonical billing period
- granted features
- expiry time
- next refresh hint
- whether acknowledgement has completed
- account mismatch / recovery status if relevant

### `GET /api/v1/subscriptions/entitlements`
Response:
- current plan
- billing period
- status
- feature set
- expiry / valid-until
- source
- last verified time
- integration states needed for access policy, for example:
  - `skysight.accountState = UNLINKED | LINKED_FREE | LINKED_PAID | LINK_ERROR | UNKNOWN`

### `POST /api/v1/subscriptions/googleplay/rtdn`
- Pub/Sub push or pull receiver
- updates server-side subscription state
- should be idempotent
- dedupe by message ID before processing

### `POST /api/v1/integrations/skysight/link`
Request:
- authenticated XCPro user ID
- SkySight credentials or delegated auth payload, depending on the final integration design

Response:
- canonical SkySight account state
- whether the linked account is currently premium-capable
- refresh / expiry metadata if available

### `DELETE /api/v1/integrations/skysight/link`
- removes the stored XCPro-side SkySight link state
- should revoke local premium SkySight access immediately

## Suggested storage model

### Tables / collections

- `users`
- `billing_google_purchase`
- `billing_google_event`
- `account_entitlement_snapshot`
- `integration_accounts`
- `integration_account_events`
- `audit_log`

## Required lifecycle handling

- signed-in user with no paid subscription -> `FREE`
- new purchase
- restore purchase
- renewal
- cancellation pending term end
- expiry
- refund / revoke
- grace period
- account hold
- same-tier monthly <-> annual switch
- upgrade / downgrade / replace
- linked purchase token chains where applicable
- SkySight link
- SkySight unlink
- SkySight free -> paid transition
- SkySight paid -> free / expired transition

## Security rules

- never trust the client's claimed tier
- always validate package name
- always validate product ID
- always validate base plan ID
- always validate purchase token ownership and current status
- treat the backend as the only durable XCPro subscription authority
- never treat SkySight linked state as a substitute for XCPro subscription entitlement
- do not store raw third-party credentials in plaintext
- keep an audit trail for support and charge disputes
- reject or quarantine suspicious account-mismatch cases instead of silently granting access

## Offline and stale-cache rule recommendation

- cached entitlements may be used for temporary display continuity
- new premium XCPro access must not be granted solely from local client assumptions
- new premium SkySight-backed access must not be granted solely from stale local linked-account assumptions
- stale cache behavior must be explicit and tested
- fresh install with no verified entitlement must not unlock paid features
- signed-in free users should still receive a server-backed `FREE` entitlement snapshot when online

## Authority rule

If any old YAML, JSON, or sample API contract conflicts with this file, this file wins.
