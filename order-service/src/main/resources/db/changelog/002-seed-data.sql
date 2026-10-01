INSERT INTO users (id, username, password, full_name, email, phone, role, created_at, updated_at, removed)
VALUES ('a0000000-0000-0000-0000-000000000001', 'client1', 'password', 'Иван Петров', 'ivan@example.com',
        '+79001234567', 'CLIENT', now(), now(), false);
INSERT INTO users (id, username, password, full_name, email, phone, role, created_at, updated_at, removed)
VALUES ('a0000000-0000-0000-0000-000000000002', 'manager1', 'password', 'Мария Сидорова', 'maria@example.com',
        '+79007654321', 'MANAGER', now(), now(), false);
INSERT INTO users (id, username, password, full_name, email, phone, role, created_at, updated_at, removed)
VALUES ('a0000000-0000-0000-0000-000000000003', 'warehouse1', 'password', 'Алексей Козлов', 'alexey@example.com', NULL,
        'WAREHOUSE_ADMIN', now(), now(), false);
INSERT INTO users (id, username, password, full_name, email, phone, role, created_at, updated_at, removed)
VALUES ('a0000000-0000-0000-0000-000000000004', 'admin1', 'password', 'Системный Администратор', 'admin@example.com',
        NULL, 'SYSTEM_ADMIN', now(), now(), false);
