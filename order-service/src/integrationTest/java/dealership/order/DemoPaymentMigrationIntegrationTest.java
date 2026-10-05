package dealership.order;

import liquibase.Contexts;
import liquibase.Liquibase;
import liquibase.database.Database;
import liquibase.database.DatabaseFactory;
import liquibase.database.jvm.JdbcConnection;
import liquibase.resource.ClassLoaderResourceAccessor;
import liquibase.resource.CompositeResourceAccessor;
import liquibase.resource.DirectoryResourceAccessor;
import liquibase.resource.ResourceAccessor;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Savepoint;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("Demo-payment migration 006 on populated data")
class DemoPaymentMigrationIntegrationTest extends BaseIntegrationTest {

    @Test
    @DisplayName("production master adds receipts without rewriting existing orders")
    void shouldUpgradePopulatedSchemaWithoutSyntheticReceipts() throws Exception {
        withIsolatedVersionFiveSchema((connection, database, resources) -> {
            insertRepresentativeRows(connection);
            connection.commit();

            LegacySnapshot before = snapshot(connection);

            applyProductionMaster(database, resources);

            assertEquals(before, snapshot(connection));
            assertEquals(0, scalar(connection, "SELECT count(*) FROM demo_payment_attempts"));
            assertEquals(1, scalar(connection, """
                    SELECT count(*)
                    FROM databasechangelog
                    WHERE filename = 'db/changelog/006-create-demo-payment-attempts.sql'
                    """));
        });
    }

    @Test
    @DisplayName("receipt constraints accept valid scoped rows and reject invalid relations")
    void shouldEnforceReceiptRelationshipsAndBusinessKeys() throws Exception {
        withIsolatedVersionFiveSchema((connection, database, resources) -> {
            insertRepresentativeRows(connection);
            connection.commit();
            applyProductionMaster(database, resources);

            assertEquals(1, update(connection, """
                    INSERT INTO demo_payment_attempts
                        (id, stock_order_id, client_id, idempotency_key,
                         requested_outcome, status)
                    VALUES
                        ('30000000-0000-0000-0000-000000000001',
                         '10000000-0000-0000-0000-000000000001', 'legacy-client',
                         '40000000-0000-0000-0000-000000000001', 'DECLINE', 'DECLINED')
                    """));
            assertEquals(1, update(connection, """
                    INSERT INTO demo_payment_attempts
                        (id, custom_order_id, client_id, idempotency_key,
                         requested_outcome, status)
                    VALUES
                        ('30000000-0000-0000-0000-000000000002',
                         '20000000-0000-0000-0000-000000000001', 'legacy-client',
                         '40000000-0000-0000-0000-000000000001', 'SUCCESS', 'SUCCEEDED')
                    """));

            assertSqlState(connection, "23514", """
                    INSERT INTO demo_payment_attempts
                        (id, stock_order_id, custom_order_id, client_id, idempotency_key,
                         requested_outcome, status)
                    VALUES
                        ('30000000-0000-0000-0000-000000000003',
                         '10000000-0000-0000-0000-000000000001',
                         '20000000-0000-0000-0000-000000000001', 'legacy-client',
                         '40000000-0000-0000-0000-000000000002', 'SUCCESS', 'SUCCEEDED')
                    """);
            assertSqlState(connection, "23514", """
                    INSERT INTO demo_payment_attempts
                        (id, client_id, idempotency_key, requested_outcome, status)
                    VALUES
                        ('30000000-0000-0000-0000-000000000008', 'legacy-client',
                         '40000000-0000-0000-0000-000000000006', 'DECLINE', 'DECLINED')
                    """);
            assertSqlState(connection, "23514", """
                    INSERT INTO demo_payment_attempts
                        (id, stock_order_id, client_id, idempotency_key,
                         requested_outcome, status)
                    VALUES
                        ('30000000-0000-0000-0000-000000000009',
                         '10000000-0000-0000-0000-000000000001', 'legacy-client',
                         '40000000-0000-0000-0000-000000000007', 'RETRY', 'DECLINED')
                    """);
            assertSqlState(connection, "23514", """
                    INSERT INTO demo_payment_attempts
                        (id, stock_order_id, client_id, idempotency_key,
                         requested_outcome, status)
                    VALUES
                        ('30000000-0000-0000-0000-000000000010',
                         '10000000-0000-0000-0000-000000000001', 'legacy-client',
                         '40000000-0000-0000-0000-000000000008', 'DECLINE', 'REFUNDED')
                    """);
            assertSqlState(connection, "23503", """
                    INSERT INTO demo_payment_attempts
                        (id, stock_order_id, client_id, idempotency_key,
                         requested_outcome, status)
                    VALUES
                        ('30000000-0000-0000-0000-000000000004',
                         '10000000-0000-0000-0000-999999999999', 'legacy-client',
                         '40000000-0000-0000-0000-000000000003', 'SUCCESS', 'SUCCEEDED')
                    """);
            assertSqlState(connection, "23505", """
                    INSERT INTO demo_payment_attempts
                        (id, stock_order_id, client_id, idempotency_key,
                         requested_outcome, status)
                    VALUES
                        ('30000000-0000-0000-0000-000000000005',
                         '10000000-0000-0000-0000-000000000001', 'legacy-client',
                         '40000000-0000-0000-0000-000000000001', 'DECLINE', 'DECLINED')
                    """);

            assertEquals(1, update(connection, """
                    INSERT INTO demo_payment_attempts
                        (id, stock_order_id, client_id, idempotency_key,
                         requested_outcome, status)
                    VALUES
                        ('30000000-0000-0000-0000-000000000006',
                         '10000000-0000-0000-0000-000000000001', 'legacy-client',
                         '40000000-0000-0000-0000-000000000004', 'SUCCESS', 'PENDING')
                    """));
            assertSqlState(connection, "23505", """
                    INSERT INTO demo_payment_attempts
                        (id, stock_order_id, client_id, idempotency_key,
                         requested_outcome, status)
                    VALUES
                        ('30000000-0000-0000-0000-000000000007',
                         '10000000-0000-0000-0000-000000000001', 'legacy-client',
                         '40000000-0000-0000-0000-000000000005', 'SUCCESS', 'SUCCEEDED')
                    """);
        });
    }

    private void withIsolatedVersionFiveSchema(SqlWork work) throws Exception {
        String schema = "payment_migration_" + UUID.randomUUID().toString().replace("-", "");
        Path changelogDirectory = Files.createTempDirectory("payment-migration-");
        Files.writeString(changelogDirectory.resolve("version-five.yaml"), """
                databaseChangeLog:
                  - include:
                      file: db/changelog/001-create-schema.sql
                  - include:
                      file: db/changelog/002-seed-data.sql
                  - include:
                      file: db/changelog/003-create-stock-reservation-workflows.sql
                  - include:
                      file: db/changelog/004-reliable-messaging.sql
                  - include:
                      file: db/changelog/005-prevent-test-drive-overlap.sql
                """);
        try (Connection connection = DriverManager.getConnection(
                postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
             ResourceAccessor resources = new CompositeResourceAccessor(
                     new DirectoryResourceAccessor(changelogDirectory),
                     new ClassLoaderResourceAccessor())) {
            execute(connection, "CREATE SCHEMA \"" + schema + "\"");
            execute(connection, "SET search_path TO \"" + schema + "\", public");
            Database database = DatabaseFactory.getInstance()
                    .findCorrectDatabaseImplementation(new JdbcConnection(connection));
            database.setDefaultSchemaName(schema);
            database.setLiquibaseSchemaName(schema);
            new Liquibase("version-five.yaml", resources, database).update(new Contexts());
            connection.setAutoCommit(false);
            try {
                work.run(connection, database, resources);
            } finally {
                connection.rollback();
                connection.setAutoCommit(true);
                execute(connection, "DROP SCHEMA \"" + schema + "\" CASCADE");
            }
        } finally {
            Files.deleteIfExists(changelogDirectory.resolve("version-five.yaml"));
            Files.deleteIfExists(changelogDirectory);
        }
    }

    private void applyProductionMaster(Database database, ResourceAccessor resources) throws Exception {
        new Liquibase(
                "db/changelog/db.changelog-master.yaml",
                resources,
                database
        ).update(new Contexts());
    }

    private void insertRepresentativeRows(Connection connection) throws SQLException {
        execute(connection, """
                INSERT INTO stock_orders
                    (id, client_id, manager_id, car_id, status, created_at, updated_at, removed)
                VALUES
                    ('10000000-0000-0000-0000-000000000001', 'legacy-client', 'legacy-manager',
                     '50000000-0000-0000-0000-000000000001', 'AWAITING_PAYMENT',
                     '2025-01-01T10:00:00Z', '2025-01-02T10:00:00Z', false),
                    ('10000000-0000-0000-0000-000000000002', 'legacy-client', 'legacy-manager',
                     '50000000-0000-0000-0000-000000000002', 'PAID',
                     '2025-01-03T10:00:00Z', '2025-01-04T10:00:00Z', false),
                    ('10000000-0000-0000-0000-000000000003', 'legacy-client', 'legacy-manager',
                     '50000000-0000-0000-0000-000000000003', 'CANCELLED',
                     '2025-01-05T10:00:00Z', '2025-01-06T10:00:00Z', false),
                    ('10000000-0000-0000-0000-000000000004', 'legacy-client', 'legacy-manager',
                     '50000000-0000-0000-0000-000000000004', 'CANCELLED',
                     '2025-01-07T10:00:00Z', '2025-01-08T10:00:00Z', true)
                """);
        execute(connection, """
                INSERT INTO stock_reservation_workflows
                    (order_id, car_id, state, storage_expires_at, next_attempt_at,
                     attempt_count, created_at, updated_at)
                VALUES
                    ('10000000-0000-0000-0000-000000000001',
                     '50000000-0000-0000-0000-000000000001', 'CONFIRM_PENDING', NULL,
                     '2025-01-02T10:01:00Z', 3, '2025-01-01T10:00:00Z', '2025-01-02T10:00:00Z'),
                    ('10000000-0000-0000-0000-000000000002',
                     '50000000-0000-0000-0000-000000000002', 'CONFIRMED', NULL,
                     NULL, 0, '2025-01-03T10:00:00Z', '2025-01-04T10:00:00Z'),
                    ('10000000-0000-0000-0000-000000000003',
                     '50000000-0000-0000-0000-000000000003', 'RELEASED', NULL,
                     NULL, 1, '2025-01-05T10:00:00Z', '2025-01-06T10:00:00Z')
                """);
        execute(connection, """
                INSERT INTO custom_orders
                    (id, manager_id, client_id, car_model_id, total_price, status,
                     created_at, updated_at, removed)
                VALUES
                    ('20000000-0000-0000-0000-000000000001', 'legacy-manager', 'legacy-client',
                     '60000000-0000-0000-0000-000000000001', 4250000.50, 'AWAITING_PAYMENT',
                     '2025-02-01T10:00:00Z', '2025-02-02T10:00:00Z', false),
                    ('20000000-0000-0000-0000-000000000002', 'legacy-manager', 'legacy-client',
                     '60000000-0000-0000-0000-000000000002', 5100000.00, 'PAID',
                     '2025-02-03T10:00:00Z', '2025-02-04T10:00:00Z', false),
                    ('20000000-0000-0000-0000-000000000003', 'legacy-manager', 'legacy-client',
                     '60000000-0000-0000-0000-000000000003', 3900000.00, 'CANCELLED',
                     '2025-02-05T10:00:00Z', '2025-02-06T10:00:00Z', false),
                    ('20000000-0000-0000-0000-000000000004', 'legacy-manager', 'legacy-client',
                     '60000000-0000-0000-0000-000000000004', 3700000.00, 'CANCELLED',
                     '2025-02-07T10:00:00Z', '2025-02-08T10:00:00Z', true)
                """);
        execute(connection, """
                INSERT INTO custom_order_variants (custom_order_id, category_id, variant_id)
                VALUES
                    ('20000000-0000-0000-0000-000000000001', 'legacy-engine', 'legacy-engine-20'),
                    ('20000000-0000-0000-0000-000000000001', 'legacy-color', 'legacy-blue'),
                    ('20000000-0000-0000-0000-000000000002', 'legacy-engine', 'legacy-engine-30')
                """);
    }

    private LegacySnapshot snapshot(Connection connection) throws SQLException {
        return new LegacySnapshot(
                rows(connection, """
                        SELECT row_to_json(snapshot)::text
                        FROM (
                            SELECT id, client_id, manager_id, car_id, status,
                                   created_at, updated_at, removed
                            FROM stock_orders
                            WHERE id IN ('10000000-0000-0000-0000-000000000001',
                                         '10000000-0000-0000-0000-000000000002',
                                         '10000000-0000-0000-0000-000000000003',
                                         '10000000-0000-0000-0000-000000000004')
                            ORDER BY id
                        ) snapshot
                        """),
                rows(connection, """
                        SELECT row_to_json(snapshot)::text
                        FROM (
                            SELECT id, manager_id, client_id, car_model_id, total_price,
                                   status, created_at, updated_at, removed
                            FROM custom_orders
                            WHERE id IN ('20000000-0000-0000-0000-000000000001',
                                         '20000000-0000-0000-0000-000000000002',
                                         '20000000-0000-0000-0000-000000000003',
                                         '20000000-0000-0000-0000-000000000004')
                            ORDER BY id
                        ) snapshot
                        """),
                rows(connection, """
                        SELECT row_to_json(snapshot)::text
                        FROM (
                            SELECT order_id, car_id, state, storage_expires_at, next_attempt_at,
                                   attempt_count, created_at, updated_at
                            FROM stock_reservation_workflows
                            WHERE order_id IN ('10000000-0000-0000-0000-000000000001',
                                               '10000000-0000-0000-0000-000000000002',
                                               '10000000-0000-0000-0000-000000000003')
                            ORDER BY order_id
                        ) snapshot
                        """),
                rows(connection, """
                        SELECT row_to_json(snapshot)::text
                        FROM (
                            SELECT custom_order_id, category_id, variant_id
                            FROM custom_order_variants
                            WHERE custom_order_id IN ('20000000-0000-0000-0000-000000000001',
                                                      '20000000-0000-0000-0000-000000000002')
                            ORDER BY custom_order_id, category_id, variant_id
                        ) snapshot
                        """));
    }

    private void assertSqlState(Connection connection, String expected, String sql) throws SQLException {
        Savepoint savepoint = connection.setSavepoint();
        SQLException failure = assertThrows(SQLException.class, () -> update(connection, sql));
        assertEquals(expected, failure.getSQLState());
        connection.rollback(savepoint);
    }

    private List<String> rows(Connection connection, String sql) throws SQLException {
        List<String> rows = new ArrayList<>();
        try (Statement statement = connection.createStatement();
             var result = statement.executeQuery(sql)) {
            while (result.next()) {
                rows.add(result.getString(1));
            }
        }
        return rows;
    }

    private int scalar(Connection connection, String sql) throws SQLException {
        try (Statement statement = connection.createStatement();
             var result = statement.executeQuery(sql)) {
            assertTrue(result.next());
            return result.getInt(1);
        }
    }

    private int update(Connection connection, String sql) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            return statement.executeUpdate(sql);
        }
    }

    private void execute(Connection connection, String sql) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }

    private record LegacySnapshot(
            List<String> stockOrders,
            List<String> customOrders,
            List<String> reservationWorkflows,
            List<String> customVariants) {
    }

    @FunctionalInterface
    private interface SqlWork {
        void run(Connection connection, Database database, ResourceAccessor resources) throws Exception;
    }
}
