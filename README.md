# AutoShop Java

[![CI](https://github.com/ssivitskii/autoshop-java/actions/workflows/ci.yml/badge.svg)](https://github.com/ssivitskii/autoshop-java/actions/workflows/ci.yml)

Учебный backend автосалона на Java: два Spring Boot сервиса управляют каталогом, складом, тест-драйвами и заказами на автомобили в наличии или в выбранной комплектации.

## Возможности

- каталог автомобилей с фильтрами и отдельным списком для тест-драйва;
- конфигуратор с проверкой совместимости вариантов комплектации;
- заказы на автомобили в наличии и на сборку, оформленные через модели состояний;
- складской учёт автомобилей и запчастей;
- JWT-аутентификация через Keycloak и разграничение доступа по ролям;
- синхронное взаимодействие сервисов по gRPC и обмен событиями через Kafka;
- transactional outbox в `order-service`;
- миграции и демонстрационные данные через Liquibase;
- unit- и integration-тесты на JUnit 5 и Testcontainers.

## Архитектура

| Модуль | Назначение |
| --- | --- |
| `order-service` | Заказы, заявки на тест-драйв, REST-прокси каталога через gRPC, отправка и обработка событий заказов |
| `storage-service` | Каталог, конфигуратор, склад, заказы на сборку, gRPC-сервер и обработка событий |
| `common` | Общие Kafka-события и protobuf/gRPC-контракт |

Внутри сервисов доменная модель и прикладные сервисы отделены от REST, persistence, messaging и security-адаптеров. У каждого сервиса своя PostgreSQL-база. `order-service` записывает событие заказа в outbox, планировщик отправляет его в `order.events`, а `storage-service` создаёт складской заказ и отвечает через `order.responses`.

Основной стек: Java 21, Spring Boot 3.3, Spring Security/OAuth2 Resource Server, Spring Data JPA, PostgreSQL 16, Liquibase, Kafka, gRPC/Protobuf, Keycloak, Gradle 8.12, JUnit 5 и Testcontainers.

## Запуск локально

Понадобятся JDK 21, Docker с Docker Compose и свободные порты из таблицы ниже. Gradle устанавливать отдельно не нужно: wrapper включён в проект.

Поднимите инфраструктуру:

```bash
docker compose up -d
```

Compose запускает только PostgreSQL, Keycloak, ZooKeeper и Kafka. Дождитесь их запуска; на первом старте это может занять время из-за загрузки образов. Состояние можно посмотреть командой `docker compose logs -f`. Затем запустите приложения в отдельных терминалах:

```bash
./gradlew :storage-service:bootRun
```

```bash
./gradlew :order-service:bootRun
```

| Компонент | Адрес/порт |
| --- | --- |
| `order-service` | `http://localhost:8081` |
| `storage-service` | `http://localhost:8082` |
| Storage gRPC | `localhost:9090` |
| Keycloak | `http://localhost:8180` |
| Kafka | `localhost:9092` |
| Order PostgreSQL | `localhost:5433` |
| Storage PostgreSQL | `localhost:5434` |

Swagger UI доступен по адресам:

- `http://localhost:8081/swagger-ui.html`
- `http://localhost:8082/swagger-ui.html`

Файл `docker/Dockerfile` пока содержит только заготовку и не собирает приложения.

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
```

Задача `integrationTest` требует работающий Docker для Testcontainers и не включена автоматически в `build`; её нужно запускать отдельно. GitHub Actions выполняет сборку, unit-тесты и integration-тесты на JDK 21.

## Ограничения

Проект предназначен для обучения и демонстрации архитектурных подходов, а не для production-развёртывания. Локальная конфигурация содержит демонстрационные пароли, включая открытые значения в seed-данных таблицы пользователей. Compose публикует инфраструктурные порты на всех интерфейсах, поэтому его следует запускать только в доверенной локальной среде. gRPC работает без TLS.

Реализация outbox демонстрирует сам паттерн, но помечает событие отправленным сразу после асинхронного вызова `KafkaTemplate.send`, не дожидаясь подтверждения брокера. Поэтому она не гарантирует production-уровень доставки событий и требует доработки подтверждений, повторов и наблюдаемости перед реальным использованием.
