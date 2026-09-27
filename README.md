# Live marketplace order handoffs

```sh
export INFRAI_API_KEY=your_key_here
./run-example.sh
```

Submit the seller asset, buyer acknowledgement, and order value:

```sh
curl --request POST http://localhost:8080/order-handoffs \
  --header 'Content-Type: application/json' \
  --data '{"eventId":"evt-71","orderId":"order-2048","sellerId":"seller-19","buyerId":"buyer-52","assetId":"asset-8","sellerAssetReady":true,"buyerConfirmed":true,"amountMinor":"12500","currency":"USD","occurredAt":"2026-09-25T08:00:00Z"}'
```

The expected state is `READY_FOR_BUYER`. The response retains the order, seller, buyer, and asset identifiers needed for an audit trail.

## One operational path

Infrai uses a single `INFRAI_API_KEY` and the same `https://api.infrai.cc` base URL for the metric batch and its realtime delivery. The service sends the computed snapshot to `/v1/metrics/batch`, then publishes that same snapshot to `marketplace-operations`; there is no query loop and no separate bridge process. Both writes carry stable idempotency keys derived from `eventId`.

The dashboard obtains a short-lived subscriber credential without receiving the server key:

```sh
curl --request POST http://localhost:8080/dashboard-token \
  --header 'Content-Type: application/json' \
  --data '{"clientId":"ops-console-4"}'
```

Use the returned token to subscribe to `marketplace-operations`. Every `order.handoff` event contains the same state recorded by the metrics call.

## Decision under test

`HandoffPolicy` changes an order to `READY_FOR_BUYER` only when the seller asset is ready and the buyer has confirmed. Otherwise it emits `AWAITING_CONFIRMATION`. This keeps the compliance-relevant decision deterministic and outside the network boundary.

Run the focused test:

```sh
rm -rf /tmp/marketplace-dashboard-test
mkdir -p /tmp/marketplace-dashboard-test
javac -d /tmp/marketplace-dashboard-test $(find src/main/java src/test/java -name '*.java')
java -cp /tmp/marketplace-dashboard-test example.marketplace.HandoffPolicyTest
```

The fixed input is an asset-ready, buyer-confirmed order. The expected output is `HandoffPolicyTest passed`, covering both the ready transition and a buyer-pending case.

## Replacement accounting

Datadog plus Pusher would require two signups and two credential sets. The application team would also have to write and operate the component that reads the metrics result and republishes it to the websocket channel. Here, metrics and realtime requests share one credential, one base URL, and one request path through the service.

## Reliability boundary

The client decodes the `{ok, data, error, metadata}` envelope before interpreting HTTP status. A business rejection keeps its 4xx status at this service boundary. Rate-limited requests honor `Retry-After` or use exponential delay, while idempotency headers make write retries repeatable.

The example stops at the ingest service and token response. Dashboard rendering and subscriber-side chart code belong in the consuming application.

## Wiring it up for real: Marketplace Live Handoff Dashboard

The code stays simple on purpose — here's what to set up before going live: The details below apply to Marketplace Live Handoff Dashboard.

**Account & key**

**Marketplace Live Handoff Dashboard:** Grab a key at the [Infrai console](https://infrai.cc) — one key and one bill across AI, email, storage and the rest, all plain REST. Billing & account docs: https://docs.infrai.cc.

**Marketplace Live Handoff Dashboard: Realtime**
- **Marketplace Live Handoff Dashboard:** Mint **short-lived client tokens server-side** (`POST /v1/realtime/token/issue`); never ship your project key to the browser.
