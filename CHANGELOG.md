# Changelog

## [1.0.0] - 2026-10-05

Первый завершённый учебный релиз двухсервисного backend автосалона.

### Возможности

- JWT-аутентификация и роли через Keycloak; REST API и Swagger для каталога, конфигураций, stock/custom-заказов и тест-драйвов.
- Синхронные запросы между сервисами по gRPC и надёжная асинхронная обработка Kafka через transactional inbox/outbox, retry, lease/fencing и DLT.
- Атомарные складские резервы с TTL, durable confirm/release recovery и защитой от запоздалых операций старого заказа.
- Серверный snapshot цены custom-конфигурации, управляемый demo payment workflow с идемпотентными receipt и полный lifecycle часовых test-drive слотов.
- Liquibase-миграции, seed-данные, Docker Compose, unit/integration/E2E тесты и автоматический Compose smoke test.

### Обновление

- Обновление выполняется с согласованной остановкой обоих сервисов; смешанная работа старых и новых JAR не поддерживается.
- Перед миграцией `005` нужно устранить невалидные UUID и пересечения активных test-drive записей. Перед уникализацией складского business key нужно устранить дубли `assembly_orders.source_order_id`.
- Миграция `006` не создаёт receipt для существующих заказов. Старый `CONFIRM_PENDING` продолжает обрабатываться recovery worker, а новый payment-запрос для такого заказа получает `409`.
- Откат требует совместимого backup обеих PostgreSQL-баз; простой запуск старого JAR поверх обновлённой схемы не поддерживается.

### Известные ограничения

- Demo payment не интегрирован с платёжным провайдером, не списывает деньги и не моделирует возвраты.
- Между двумя PostgreSQL-базами и Kafka нет общей транзакции; доставка событий остаётся at least once и опирается на идемпотентные inbox/ledger операции.
- Проект не включает UI, production TLS/service authentication, метрики/alerts, автоматический replay DLT и retention/архивирование ledger, inbox/outbox и DLT.
