#!/usr/bin/env bash
set -euo pipefail

readonly ORDER_URL="http://127.0.0.1:8081"
readonly KEYCLOAK_URL="http://127.0.0.1:8180"
readonly CAR_ONE="e0000000-0000-0000-0000-000000000001"
readonly CAR_TWO="e0000000-0000-0000-0000-000000000002"

token() {
  curl --fail --silent --show-error \
    -X POST "${KEYCLOAK_URL}/realms/dealership/protocol/openid-connect/token" \
    -H 'Content-Type: application/x-www-form-urlencoded' \
    --data-urlencode 'client_id=dealership-app' \
    --data-urlencode 'grant_type=password' \
    --data-urlencode "username=$1" \
    --data-urlencode 'password=password' | jq -er '.access_token'
}

request() {
  local method="$1" path="$2" bearer="$3" body="${4:-}"
  if [[ -n "$body" ]]; then
    curl --fail --silent --show-error -X "$method" "${ORDER_URL}${path}" \
      -H "Authorization: Bearer ${bearer}" -H 'Content-Type: application/json' -d "$body"
  else
    curl --fail --silent --show-error -X "$method" "${ORDER_URL}${path}" \
      -H "Authorization: Bearer ${bearer}"
  fi
}

await_status() {
  local order_id="$1" bearer="$2" expected="$3"
  local deadline=$((SECONDS + 60)) status
  while (( SECONDS < deadline )); do
    status="$(request GET "/api/orders/stock/${order_id}" "$bearer" | jq -er '.status')"
    [[ "$status" == "$expected" ]] && return 0
    sleep 1
  done
  echo "Order ${order_id} did not reach ${expected}; last status was ${status:-unknown}" >&2
  return 1
}

await_order_consumer_caught_up() {
  local deadline=$((SECONDS + 30))
  while (( SECONDS < deadline )); do
    if docker compose exec -T kafka kafka-consumer-groups \
      --bootstrap-server kafka:29092 --describe --group order-service 2>/dev/null |
      awk '$1 == "order-service" && $2 == "order.responses" { found=1; lag += $6 }
           END { exit !(found && lag == 0) }'; then
      return 0
    fi
    sleep 1
  done
  echo "order-service consumer did not catch up" >&2
  return 1
}

client_one="$(token client1)"
client_two="$(token client2)"
manager="$(token manager1)"

paid_order="$(request POST '/api/orders/stock' "$client_one" "{\"carId\":\"${CAR_ONE}\"}")"
paid_id="$(jq -er '.id' <<<"$paid_order")"
for _ in 1 2 3; do
  request POST "/api/orders/stock/${paid_id}/advance" "$manager" >/dev/null
done
await_status "$paid_id" "$client_one" READY_FOR_PICKUP

response_envelope="$(docker compose exec -T storage-db psql -U storage -d storage_service -At \
  -v ON_ERROR_STOP=1 -c "
    SELECT json_build_object(
      'eventId', id,
      'eventType', event_type,
      'version', 1,
      'aggregateId', aggregate_id,
      'traceId', trace_id,
      'payload', payload::json
    )::text
      FROM outbox_events
     WHERE aggregate_id = '${paid_id}'
     LIMIT 1")"
test -n "$response_envelope"
printf '%s|%s\n' "$paid_id" "$response_envelope" |
  docker compose exec -T kafka kafka-console-producer \
    --bootstrap-server kafka:29092 --topic order.responses \
    --property parse.key=true --property key.separator='|' >/dev/null
await_order_consumer_caught_up
await_status "$paid_id" "$client_one" READY_FOR_PICKUP

cancelled_order="$(request POST '/api/orders/stock' "$client_one" "{\"carId\":\"${CAR_TWO}\"}")"
cancelled_id="$(jq -er '.id' <<<"$cancelled_order")"
request POST "/api/orders/stock/${cancelled_id}/cancel" "$client_one" >/dev/null
rebooked="$(request POST '/api/orders/stock' "$client_two" "{\"carId\":\"${CAR_TWO}\"}")"
test "$(jq -er '.status' <<<"$rebooked")" = CREATED

echo "Compose smoke passed: Kafka approval is idempotent and cancelled stock can be rebooked."
