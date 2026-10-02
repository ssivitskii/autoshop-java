package dealership.order;

import liquibase.Contexts;
import liquibase.Liquibase;
import liquibase.database.Database;
import liquibase.database.DatabaseFactory;
import liquibase.database.jvm.JdbcConnection;
import liquibase.exception.LiquibaseException;
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
import java.sql.Statement;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("Test-drive migration 005 on legacy data")
class TestDriveMigrationIntegrationTest extends BaseIntegrationTest {

    @Test
    @DisplayName("adjacent valid legacy rows accept the real exclusion migration")
    void shouldMigrateAdjacentLegacyRows() throws Exception {
        withIsolatedLegacySchema((connection, database, resources) -> {
            String carId = UUID.randomUUID().toString();
            insertLegacy(connection, UUID.randomUUID(), carId.toUpperCase(), "2030-01-01 10:00:00", "PENDING");
            insertLegacy(connection, UUID.randomUUID(), carId, "2030-01-01 11:00:00", "APPROVED");
            connection.commit();

            applyRealMigration(database, resources);

            assertEquals(1, constraintCount(connection));
            SQLException conflict = assertThrows(SQLException.class, () -> insertLegacy(
                    connection, UUID.randomUUID(), carId, "2030-01-01 10:30:00", "PENDING"));
            assertEquals("23P01", conflict.getSQLState());
        });
    }

    @Test
    @DisplayName("invalid active legacy car ID stops migration without deleting data")
    void shouldRejectInvalidLegacyCarIdWithoutDeletingIt() throws Exception {
        withIsolatedLegacySchema((connection, database, resources) -> {
            insertLegacy(connection, UUID.randomUUID(), "not-a-uuid", "2030-01-01 10:00:00", "PENDING");
            connection.commit();

            LiquibaseException failure = assertThrows(LiquibaseException.class,
                    () -> applyRealMigration(database, resources));
            rollbackFailedMigration(connection);

            assertTrue(sqlState(failure).startsWith("22"));
            assertEquals(1, scalar(connection, "SELECT count(*) FROM test_drive_requests"));
            assertEquals(0, constraintCount(connection));
        });
    }

    @Test
    @DisplayName("overlapping active legacy rows stop migration without deleting data")
    void shouldRejectLegacyOverlapWithoutDeletingRows() throws Exception {
        withIsolatedLegacySchema((connection, database, resources) -> {
            String carId = UUID.randomUUID().toString();
            insertLegacy(connection, UUID.randomUUID(), carId, "2030-01-01 10:00:00", "PENDING");
            insertLegacy(connection, UUID.randomUUID(), carId, "2030-01-01 10:30:00", "APPROVED");
            connection.commit();

            LiquibaseException failure = assertThrows(LiquibaseException.class,
                    () -> applyRealMigration(database, resources));
            rollbackFailedMigration(connection);

            assertEquals("23P01", sqlState(failure));
            assertEquals(2, scalar(connection, "SELECT count(*) FROM test_drive_requests"));
            assertEquals(0, constraintCount(connection));
        });
    }

    private void withIsolatedLegacySchema(SqlWork work) throws Exception {
        String schema = "migration_" + UUID.randomUUID().toString().replace("-", "");
        Path changelogDirectory = Files.createTempDirectory("testdrive-migration-");
        Files.writeString(changelogDirectory.resolve("base.yaml"), """
                databaseChangeLog:
                  - include:
                      file: db/changelog/001-create-schema.sql
                  - include:
                      file: db/changelog/002-seed-data.sql
                  - include:
                      file: db/changelog/003-create-stock-reservation-workflows.sql
                  - include:
                      file: db/changelog/004-reliable-messaging.sql
                """);
        Files.writeString(changelogDirectory.resolve("upgrade.yaml"), """
                databaseChangeLog:
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
            new Liquibase(
                    "base.yaml",
                    resources,
                    database
            ).update(new Contexts());
            connection.setAutoCommit(false);
            try {
                work.run(connection, database, resources);
            } finally {
                connection.rollback();
                connection.setAutoCommit(true);
                execute(connection, "DROP SCHEMA \"" + schema + "\" CASCADE");
            }
        } finally {
            Files.deleteIfExists(changelogDirectory.resolve("base.yaml"));
            Files.deleteIfExists(changelogDirectory.resolve("upgrade.yaml"));
            Files.deleteIfExists(changelogDirectory);
        }
    }

    private void applyRealMigration(Database database, ResourceAccessor resources)
            throws LiquibaseException {
        new Liquibase(
                "upgrade.yaml",
                resources,
                database
        ).update(new Contexts());
    }

    private void rollbackFailedMigration(Connection connection) throws SQLException {
        if (!connection.getAutoCommit()) {
            connection.rollback();
        }
    }

    private void insertLegacy(Connection connection, UUID id, String carId,
                              String scheduledAt, String status) throws SQLException {
        try (var statement = connection.prepareStatement("""
                INSERT INTO test_drive_requests
                    (id, client_id, car_id, requested_date_time, status,
                     created_at, updated_at, removed)
                VALUES (?, 'legacy-client', ?, ?::timestamp, ?, clock_timestamp(), clock_timestamp(), false)
                """)) {
            statement.setObject(1, id);
            statement.setString(2, carId);
            statement.setString(3, scheduledAt);
            statement.setString(4, status);
            statement.executeUpdate();
        }
    }

    private int constraintCount(Connection connection) throws SQLException {
        return scalar(connection, """
                SELECT count(*)
                FROM pg_constraint
                WHERE conname = 'test_drive_no_overlapping_active'
                  AND connamespace = current_schema()::regnamespace
                """);
    }

    private int scalar(Connection connection, String sql) throws SQLException {
        try (Statement statement = connection.createStatement();
             var result = statement.executeQuery(sql)) {
            assertTrue(result.next());
            return result.getInt(1);
        }
    }

    private String sqlState(Throwable failure) {
        for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
            if (cause instanceof SQLException sqlException && sqlException.getSQLState() != null) {
                return sqlException.getSQLState();
            }
        }
        assertNotNull(null, "Expected SQLException in Liquibase failure cause chain");
        return "";
    }

    private void execute(Connection connection, String sql) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }

    @FunctionalInterface
    private interface SqlWork {
        void run(Connection connection, Database database, ResourceAccessor resources) throws Exception;
    }
}
