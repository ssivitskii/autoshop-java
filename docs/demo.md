# Пятиминутная демонстрация AutoShop

Сценарий рассчитан примерно на пять минут после того, как Compose завершил первый запуск. Нужны Docker Compose, `curl` и `jq`; прогрев и сборка контейнеров в эти пять минут не входят.

Из свежего клона поднимите полный стек и дождитесь health checks:

```bash
git clone https://github.com/ssivitskii/autoshop-java.git
cd autoshop-java
docker compose up --build --wait --wait-timeout 300
docker compose ps
```

Интерактивная документация доступна в [Swagger order-service](http://localhost:8081/swagger-ui.html) и [Swagger storage-service](http://localhost:8082/swagger-ui.html). Keycloak работает на `http://localhost:8180`; публичный client ID — `dealership-app`. Seed-пользователи используют пароль `password`: `client1` и `client2` имеют роль `USER`, `manager1` — `MANAGER`, `warehouse1` — `WAREHOUSE_ADMIN`, `admin1` — `ADMIN`.

Ручной сценарий ниже и автоматический smoke используют одни и те же seed-автомобили, поэтому выберите один вариант и запускайте его на чистом seed-состоянии. В качестве автоматической альтернативы ручным шагам выполните `bash scripts/compose-smoke.sh`; этот скрипт дополнительно проверяет custom-заказ, Kafka и повторное бронирование.

Выполните блок ниже в одном shell. Сначала клиент создаёт заявку на будущий часовой слот, менеджер её одобряет, а клиент отменяет и освобождает слот. Затем тот же клиент заказывает seed-автомобиль, менеджер двумя переходами доводит заказ до `AWAITING_PAYMENT`, а demo payment переводит его в оплаченный workflow. Повтор с тем же ключом возвращает тот же receipt.

```bash
set -euo pipefail

ORDER_URL='http://localhost:8081'
KEYCLOAK_URL='http://localhost:8180'
CAR_ID='e0000000-0000-0000-0000-000000000001'

token() {
  curl --fail --silent --show-error \
    -X POST "${KEYCLOAK_URL}/realms/dealership/protocol/openid-connect/token" \
    -H 'Content-Type: application/x-www-form-urlencoded' \
    --data-urlencode 'client_id=dealership-app' \
    --data-urlencode 'grant_type=password' \
    --data-urlencode "username=$1" \
    --data-urlencode 'password=password' | jq -er '.access_token'
}

CLIENT_TOKEN="$(token client1)"
MANAGER_TOKEN="$(token manager1)"
TEST_DRIVE_AT="$(jq -nr '((now / 3600 | floor) * 3600 + 172800) | strftime("%Y-%m-%dT%H:%M:%S")')"

TEST_DRIVE="$(curl --fail --silent --show-error \
  -X POST "${ORDER_URL}/api/test-drives" \
  -H "Authorization: Bearer ${CLIENT_TOKEN}" \
  -H 'Content-Type: application/json' \
  -d "{\"carId\":\"${CAR_ID}\",\"scheduledAt\":\"${TEST_DRIVE_AT}\"}")"
TEST_DRIVE_ID="$(jq -er '.id' <<<"${TEST_DRIVE}")"
curl --fail --silent --show-error \
  -X POST "${ORDER_URL}/api/test-drives/${TEST_DRIVE_ID}/approve" \
  -H "Authorization: Bearer ${MANAGER_TOKEN}" | jq '{id,status,requestedDateTime}'
curl --fail --silent --show-error \
  -X POST "${ORDER_URL}/api/test-drives/${TEST_DRIVE_ID}/cancel" \
  -H "Authorization: Bearer ${CLIENT_TOKEN}" | jq '{id,status}'

ORDER="$(curl --fail --silent --show-error \
  -X POST "${ORDER_URL}/api/orders/stock" \
  -H "Authorization: Bearer ${CLIENT_TOKEN}" \
  -H 'Content-Type: application/json' \
  -d "{\"carId\":\"${CAR_ID}\"}")"
ORDER_ID="$(jq -er '.id' <<<"${ORDER}")"

for step in 1 2; do
  curl --fail --silent --show-error \
    -X POST "${ORDER_URL}/api/orders/stock/${ORDER_ID}/advance" \
    -H "Authorization: Bearer ${MANAGER_TOKEN}" | jq '{id,status}'
done

PAYMENT_KEY='90000000-0000-0000-0000-000000000001'
PAYMENT_URL="${ORDER_URL}/api/orders/stock/${ORDER_ID}/demo-payments"
PAYMENT="$(curl --fail-with-body --silent --show-error \
  -X POST "${PAYMENT_URL}" \
  -H "Authorization: Bearer ${CLIENT_TOKEN}" \
  -H "Idempotency-Key: ${PAYMENT_KEY}" \
  -H 'Content-Type: application/json' \
  -d '{"outcome":"SUCCESS"}')"
REPLAY="$(curl --fail-with-body --silent --show-error \
  -X POST "${PAYMENT_URL}" \
  -H "Authorization: Bearer ${CLIENT_TOKEN}" \
  -H "Idempotency-Key: ${PAYMENT_KEY}" \
  -H 'Content-Type: application/json' \
  -d '{"outcome":"SUCCESS"}')"

jq -n --argjson payment "${PAYMENT}" --argjson replay "${REPLAY}" \
  '{paymentStatus:$payment.status, sameReceipt:($payment.id == $replay.id), receiptId:$payment.id}'
curl --fail --silent --show-error \
  -H "Authorization: Bearer ${CLIENT_TOKEN}" \
  "${ORDER_URL}/api/orders/stock/${ORDER_ID}" | jq '{id,status}'
```

Ожидаемый результат: test-drive проходит `PENDING → APPROVED → CANCELLED`; два manager-перехода заказа показывают `APPROVED_BY_MANAGER`, затем `AWAITING_PAYMENT`; итоговый объект сравнения содержит `paymentStatus: "SUCCEEDED"` и `sameReceipt: true`. Сразу после оплаты заказ обычно имеет статус `PAID`, а после асинхронной обработки Kafka переходит в `READY_FOR_PICKUP`.

Demo payment — симулятор workflow. Он не принимает платёжные реквизиты, не обращается к банку и не подтверждает реальное списание денег. Его receipt показывает только выбранный исход и идемпотентность операции.

Обычная остановка сохраняет данные в Docker volumes:

```bash
docker compose down
```

Повтор ручного сценария без сброса не гарантируется: оплаченный seed-автомобиль уже недоступен для нового заказа. Если нужен чистый повтор, команда `docker compose down --volumes` удалит все локальные demo-данные; после неё снова выполните команду запуска Compose. Используйте этот сброс только осознанно.
