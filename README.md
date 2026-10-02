# AutoShop Java

[![CI](https://github.com/ssivitskii/autoshop-java/actions/workflows/ci.yml/badge.svg)](https://github.com/ssivitskii/autoshop-java/actions/workflows/ci.yml)

Учебный backend автосалона на Java: два Spring Boot сервиса управляют каталогом, складом, тест-драйвами и заказами на автомобили в наличии или в выбранной комплектации.

## Возможности

- каталог автомобилей с фильтрами и отдельным списком для тест-драйва;
- конфигуратор с проверкой совместимости вариантов комплектации;
- заказы на автомобили в наличии и на сборку, оформленные через модели состояний;
- атомарное резервирование автомобиля в наличии с TTL, подтверждением при оплате и восстановлением после сбоев;
- складской учёт автомобилей и запчастей;
- JWT-аутентификация через Keycloak и разграничение доступа по ролям;
- синхронное взаимодействие сервисов по gRPC и обмен событиями через Kafka;
- transactional inbox/outbox в обоих сервисах, повторная доставка и dead-letter topics;
- миграции и демонстрационные данные через Liquibase;
- unit- и integration-тесты на JUnit 5 и Testcontainers.

## Архитектура

| Модуль | Назначение |
| --- | --- |
| `order-service` | Заказы, заявки на тест-драйв, REST-прокси каталога через gRPC, отправка и обработка событий заказов |
| `storage-service` | Каталог, конфигуратор, склад, заказы на сборку, gRPC-сервер и обработка событий |
| `common` | Общие Kafka-события и protobuf/gRPC-контракт |

Внутри сервисов доменная модель и прикладные сервисы отделены от REST, persistence, messaging и security-адаптеров. У каждого сервиса своя PostgreSQL-база. `order-service` записывает событие заказа в outbox, планировщик отправляет его в `order.events`, а `storage-service` атомарно фиксирует inbox, складской заказ и ответный outbox. Ответ из `order.responses` также применяется вместе с inbox в одной транзакции БД заказов.

Каждое новое Kafka-сообщение имеет envelope версии 1: `eventId`, `eventType`, `version`, `aggregateId`, `traceId` и объект `payload`. ID равен UUID строки outbox и остаётся тем же при каждом повторе. Consumers проверяют версию, тип, обязательные поля и совпадение Kafka key с `aggregateId` и `payload.orderId`. Старые JSON-события без envelope всё ещё принимаются; для них вычисляется стабильный UUID из topic, типа и ID заказа.

Publisher берёт короткую lease в PostgreSQL, освобождает транзакцию и только затем ждёт подтверждение Kafka. Строка помечается отправленной лишь после broker ACK; timeout или ошибка оставляют её для повтора с backoff 1–60 секунд. Более позднее событие того же aggregate ждёт более раннее. Kafka producer использует `acks=all` и idempotence, но общая гарантия остаётся **at least once**: дубликаты отсекает transactional inbox, а не распределённая транзакция с брокером.

Permanent-ошибки контракта сразу, а временные ошибки после трёх попыток, публикуются в `<source-topic>.DLT` с исходными key/value и диагностическими headers. Source offset фиксируется только после успешного ACK от DLT. DLT автоматически не проигрывается: сначала устраните причину, затем повторно отправьте исходное сообщение с тем же key и `eventId`, чтобы сохранить идемпотентность.

Для заказа автомобиля в наличии `order-service` сначала получает временный резерв с TTL и затем в одной локальной транзакции сохраняет заказ и workflow. Ledger в БД склада сериализует операции по ID заказа, а условный `UPDATE` автомобиля гарантирует одного владельца. Повтор того же reserve возвращает первоначальный deadline и не продлевает TTL. При переходе из `AWAITING_PAYMENT` резерв подтверждается и перестаёт истекать; только после этого заказ становится `PAID` и создаёт outbox-событие.

Confirm и release сохраняются как durable intent в `stock_reservation_workflows`. Фоновый worker повторяет незавершённые операции после перезапуска. Отмена сначала фиксирует `CANCELLED` и `RELEASE_PENDING`, затем вызывает склад. Owner-checked release и terminal tombstone защищают новую бронь от запоздалых запросов старого заказа. Отдельный worker склада освобождает неподтверждённые резервы по времени PostgreSQL; локальный worker заказа отменяет соответствующий неоплаченный заказ.

По умолчанию hold живёт `PT15M`, оба recovery worker запускаются каждые `PT10S` и обрабатывают до 100 записей. Неуспешные intents получают backoff от 1 до 60 секунд, чтобы одна проблемная запись не блокировала очередь. Параметры задаются через `reservation.hold-ttl`, `reservation.recovery.interval` и `reservation.recovery.batch-size`.

При создании stock-заказа несуществующий автомобиль возвращает `404`, недоступный или уже зарезервированный — `409`, некорректный UUID — `400`, а недоступность склада — `503`. Для отмены `503` означает, что `CANCELLED` уже зафиксирован, а durable worker продолжит owner-checked release. Отмена временно отклоняется, пока выполняется подтверждение резерва, чтобы concurrent confirm и cancel не расходились.

Основной стек: Java 21, Spring Boot 3.3, Spring Security/OAuth2 Resource Server, Spring Data JPA, PostgreSQL 16, Liquibase, Kafka, gRPC/Protobuf, Keycloak, Gradle 8.12, JUnit 5 и Testcontainers.

## Запуск локально

Понадобятся JDK 21, Docker с Docker Compose и свободные порты из таблицы ниже. Gradle устанавливать отдельно не нужно: wrapper включён в проект.

Поднимите весь проект одной командой:

```bash
docker compose up --build --wait
```

Compose собирает оба приложения, запускает PostgreSQL, Keycloak, ZooKeeper и Kafka, ждёт health checks и затем поднимает сервисы. Состояние можно посмотреть командой `docker compose ps`, логи — `docker compose logs -f`. Остановка с удалением локальных данных:

```bash
docker compose down --volumes
```

Для разработки через `bootRun` можно поднять только инфраструктуру:

```bash
docker compose up -d order-db storage-db keycloak-db keycloak zookeeper kafka
./gradlew :storage-service:bootRun # отдельный терминал
./gradlew :order-service:bootRun   # отдельный терминал
```

| Компонент | Адрес/порт |
| --- | --- |
| `order-service` | `http://localhost:8081` |
| `storage-service` | `http://localhost:8082` |
| Keycloak | `http://localhost:8180` |
| Kafka | `localhost:9092` |
| Order PostgreSQL | `localhost:5433` |
| Storage PostgreSQL | `localhost:5434` |

Swagger UI доступен по адресам:

- `http://localhost:8081/swagger-ui.html`
- `http://localhost:8082/swagger-ui.html`

В Compose внутренний gRPC доступен только `order-service` по адресу `storage-service:9090` и не публикуется на host. HTTP и инфраструктурные порты привязаны к `127.0.0.1`.

При нативном запуске Storage gRPC по умолчанию слушает только `127.0.0.1`. Внутри Compose он слушает контейнерную сеть. Эта сеть и Kafka являются доверенной demo-границей; перед внешним развёртыванием им нужны TLS, аутентификация сервисов и ACL.

Обновление с версии без reservation ledger или reliable messaging нужно выполнять с согласованной остановкой и последующим запуском обоих сервисов. Перед добавлением уникального business key проверьте историю склада запросом `SELECT source_order_id, count(*) FROM assembly_orders GROUP BY source_order_id HAVING count(*) > 1;`: миграция намеренно остановится при дублях и не удаляет данные автоматически. `source_order_id` после обновления считается неизменяемым, повторное использование ID после soft delete не поддерживается. Смешанная работа старых и новых версий небезопасна. Миграция reservation помечает прежние неоплаченные заказы как `LEGACY_UNVERIFIED` и проверяет их резерв при попытке оплаты; оплаченные заказы сохраняются как подтверждённые без ретроактивного TTL.

## Демонстрационная авторизация

Realm `dealership` импортируется при первом запуске Keycloak. Все перечисленные учётные записи и пароль предназначены только для локальной демонстрации.

| Логин | Роль | Пароль |
| --- | --- | --- |
| `client1`, `client2` | `USER` | `password` |
| `manager1` | `MANAGER` | `password` |
| `warehouse1` | `WAREHOUSE_ADMIN` | `password` |
| `admin1` | `ADMIN` | `password` |

Пример получения токена и безопасного чтения каталога (нужны `curl` и `jq`):

```bash
ACCESS_TOKEN="$(curl --fail --silent --show-error \
  -X POST 'http://localhost:8180/realms/dealership/protocol/openid-connect/token' \
  -H 'Content-Type: application/x-www-form-urlencoded' \
  --data-urlencode 'client_id=dealership-app' \
  --data-urlencode 'grant_type=password' \
  --data-urlencode 'username=client1' \
  --data-urlencode 'password=password' | jq -r '.access_token')"

curl --fail --silent --show-error \
  -H "Authorization: Bearer ${ACCESS_TOKEN}" \
  'http://localhost:8082/api/cars/available'
```

## API

Полные схемы запросов и ответов находятся в Swagger. Основные группы маршрутов:

| Сервис | Маршруты | Доступ |
| --- | --- | --- |
| Order | `GET /api/v1/cars`, `GET /api/v1/cars/{id}` | `USER`, `MANAGER`, `ADMIN` |
| Order | `/api/orders/stock/**` | создание и отмена: `USER`/`ADMIN`; просмотр: `USER`/`MANAGER`/`ADMIN`; смена статуса: `MANAGER`/`ADMIN` |
| Order | `/api/orders/custom/**` | создание и отмена: `USER`/`ADMIN`; просмотр: `USER`/`MANAGER`/`ADMIN`; смена статуса: `MANAGER`/`WAREHOUSE_ADMIN`/`ADMIN` |
| Order | `/api/test-drives/**` | создание: `USER`/`ADMIN`; просмотр: `MANAGER`/`ADMIN` |
| Storage | `GET /api/cars/{id}`, `/api/cars/available`, `/api/cars/test-drive`, `/api/cars/search` | любой аутентифицированный пользователь |
| Storage | `/api/configuration/**` | любой аутентифицированный пользователь |
| Storage | `/api/inventory/cars/**` | `MANAGER`, `ADMIN` |
| Storage | `/api/inventory/parts/**` | чтение: `WAREHOUSE_ADMIN`/`MANAGER`/`ADMIN`; изменение: `WAREHOUSE_ADMIN`/`ADMIN` |
| Storage | `/api/assembly-orders/**` | `WAREHOUSE_ADMIN`, `ADMIN` |

## Сборка и тесты

```bash
./gradlew --no-daemon clean assemble
./gradlew --no-daemon test
./gradlew --no-daemon integrationTest
./gradlew --no-daemon :order-service:e2eTest
```

Задачи `integrationTest` и `e2eTest` требуют работающий Docker для Testcontainers и не включены автоматически в `build`. GitHub Actions выполняет обе вместе со сборкой и unit-тестами на JDK 21.

Интеграционные тесты проверяют конкурентность и гонку confirm/expiry на реальной PostgreSQL, lease/fencing outbox, atomic inbox и обработку poison-сообщения через реальный Kafka DLT. E2E-тест поднимает две PostgreSQL, Kafka и Keycloak, запускает `order-service` и `storage-service` отдельными JVM и проходит настоящий JWT HTTP → gRPC сценарий. Отдельный CI smoke запускает готовый Compose stack и проверяет JWT → HTTP → gRPC → Kafka → ответный Kafka-переход, повтор ответа, отмену и повторную бронь.

## Ограничения

Проект предназначен для обучения и демонстрации архитектурных подходов, а не для production-развёртывания. Локальная конфигурация содержит демонстрационные пароли, включая открытые значения в seed-данных таблицы пользователей. Compose привязывает host-порты к loopback, но внутренняя сеть сервисов остаётся доверенной и работает без TLS.

Общей транзакции между двумя PostgreSQL-базами и Kafka нет: API может вернуть временную ошибку, пока durable worker завершает confirm или release, а события могут приходить повторно. Сиротский reserve после аварии освобождается TTL склада. Проект не содержит операторской панели, метрик/alerts и автоматического безопасного replay для DLT; DLT и таблицы inbox/outbox нужно включить в эксплуатационный мониторинг.

Terminal ledger-записи, release tombstone, inbox, отправленные outbox-строки и DLT-сообщения сейчас сохраняются без автоматической очистки; для долгоживущей системы нужна согласованная retention-политика и архивирование. Откат версии также выполняется согласованно для обоих приложений и из совместимого backup обеих БД: простой запуск старого JAR после новых миграций не поддерживается.
