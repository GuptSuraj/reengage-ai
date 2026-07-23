#!/usr/bin/env bash
set -euo pipefail

CORE_URL="${CORE_URL:-http://localhost:8080}"

for _ in $(seq 1 40); do
  if curl -fsS "${CORE_URL}/actuator/health/liveness" >/dev/null; then
    break
  fi
  sleep 2
done
curl -fsS "${CORE_URL}/actuator/health/liveness" >/dev/null

LOGIN_RESPONSE="$(curl -fsS -X POST "${CORE_URL}/api/v1/auth/login" \
  -H "content-type: application/json" \
  -d '{"email":"demo@reengage.ai","password":"Demo@12345"}')"
TOKEN="$(printf '%s' "${LOGIN_RESPONSE}" | python3 -c 'import json,sys; print(json.load(sys.stdin)["accessToken"])')"
AUTH_HEADER="Authorization: Bearer ${TOKEN}"

PRODUCTS="$(curl -fsS "${CORE_URL}/api/v1/products?q=wireless&maxPrice=10000")"
printf '%s' "${PRODUCTS}" | python3 -c 'import json,sys; assert json.load(sys.stdin)["products"], "catalogue is empty"'

EVENTS="$(python3 - <<'PY'
import json
import uuid
from datetime import datetime, timezone

session = str(uuid.uuid4())
kinds = [
    "SEARCH_PERFORMED", "FILTER_APPLIED", "PRODUCT_VIEWED", "PRODUCT_VIEWED",
    "PRODUCT_COMPARED", "TIME_SPENT", "ADD_TO_CART",
]
events = []
for kind in kinds:
    metadata = {"query": "wireless headphones"} if kind == "SEARCH_PERFORMED" else (
        {"seconds": 240} if kind == "TIME_SPENT" else {}
    )
    events.append({
        "eventId": str(uuid.uuid4()),
        "sessionId": session,
        "eventType": kind,
        "productId": "sony-wh-ch720n",
        "timestamp": datetime.now(timezone.utc).isoformat(),
        "sourcePage": "/products/sony-wh-ch720n",
        "device": {"type": "smoke-test"},
        "metadata": metadata,
    })
print(json.dumps({"events": events}))
PY
)"

INGESTED="$(curl -fsS -X POST "${CORE_URL}/api/v1/events/batch" \
  -H "${AUTH_HEADER}" -H "content-type: application/json" -d "${EVENTS}")"
printf '%s' "${INGESTED}" | python3 -c 'import json,sys; value=json.load(sys.stdin); assert value["accepted"] == 7, value'

DUPLICATE="$(curl -fsS -X POST "${CORE_URL}/api/v1/events/batch" \
  -H "${AUTH_HEADER}" -H "content-type: application/json" -d "${EVENTS}")"
printf '%s' "${DUPLICATE}" | python3 -c 'import json,sys; value=json.load(sys.stdin); assert value["duplicates"] == 7, value'

for _ in $(seq 1 20); do
  PROFILE="$(curl -fsS "${CORE_URL}/api/v1/profile" -H "${AUTH_HEADER}")"
  LEVEL="$(printf '%s' "${PROFILE}" | python3 -c 'import json,sys; print(json.load(sys.stdin)["intent_level"])')"
  if [ "${LEVEL}" = "HIGH" ]; then
    break
  fi
  sleep 1
done
test "${LEVEL}" = "HIGH"

curl -fsS -X PUT "${CORE_URL}/api/v1/cart/items/sony-wh-ch720n" \
  -H "${AUTH_HEADER}" -H "content-type: application/json" -d '{"quantity":1}' >/dev/null
IDEMPOTENCY_KEY="$(python3 -c 'import uuid; print(uuid.uuid4())')"
ORDER_ONE="$(curl -fsS -X POST "${CORE_URL}/api/v1/checkout" \
  -H "${AUTH_HEADER}" -H "Idempotency-Key: ${IDEMPOTENCY_KEY}")"
ORDER_TWO="$(curl -fsS -X POST "${CORE_URL}/api/v1/checkout" \
  -H "${AUTH_HEADER}" -H "Idempotency-Key: ${IDEMPOTENCY_KEY}")"
python3 - "${ORDER_ONE}" "${ORDER_TWO}" <<'PY'
import json
import sys

first, replay = map(json.loads, sys.argv[1:])
assert first["id"] == replay["id"], (first, replay)
print(f"Smoke test passed: high intent, duplicate suppression, and idempotent order {first['id']}")
PY
