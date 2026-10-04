package dealership.e2e;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.MountableFile;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.*;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ReservationLifecycleE2ETest {
    private static final String CAR_ONE = "e0000000-0000-0000-0000-000000000001";
    private static final String CAR_TWO = "e0000000-0000-0000-0000-000000000002";

    private final PostgreSQLContainer<?> orderDb = postgres("order_service", "orders");
    private final PostgreSQLContainer<?> storageDb = postgres("storage_service", "storage");
    private final KafkaContainer kafka = new KafkaContainer(
            DockerImageName.parse("confluentinc/cp-kafka:7.6.0"));
    private final GenericContainer<?> keycloak = new GenericContainer<>(
            DockerImageName.parse("quay.io/keycloak/keycloak:24.0"))
            .withCopyFileToContainer(MountableFile.forHostPath(
                    Path.of(System.getProperty("project.root.dir"), "keycloak/realm-export.json")),
                    "/opt/keycloak/data/import/realm-export.json")
            .withCommand("start-dev", "--import-realm")
            .withEnv("KEYCLOAK_ADMIN", "admin")
            .withEnv("KEYCLOAK_ADMIN_PASSWORD", "admin")
            .withEnv("JAVA_OPTS_APPEND", "-Xms64m -Xmx384m")
            .withExposedPorts(8080)
            .waitingFor(Wait.forHttp("/realms/dealership").forStatusCode(200));

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5)).build();
    private final ObjectMapper json = new ObjectMapper();
    private Path root;
    private Path logs;
    private int orderPort;
    private int storagePort;
    private int grpcPort;
    private Process orderProcess;
    private Process storageProcess;

    @BeforeAll
    void startEnvironment() throws Exception {
        root = Path.of(System.getProperty("project.root.dir"));
        logs = root.resolve("order-service/build/e2e-logs");
        Files.createDirectories(logs);
        orderDb.start();
        storageDb.start();
        kafka.start();
        keycloak.start();
        orderPort = freePort();
        storagePort = freePort();
        grpcPort = freePort();
        storageProcess = startStorage("storage.log");
        awaitHttp(storageProcess, storagePort, "/v3/api-docs", Duration.ofSeconds(90));
        orderProcess = startOrder("order.log");
        awaitHttp(orderProcess, orderPort, "/v3/api-docs", Duration.ofSeconds(90));
    }

    @AfterAll
    void stopEnvironment() {
        stop(orderProcess);
        stop(storageProcess);
        keycloak.stop();
        kafka.stop();
        storageDb.stop();
        orderDb.stop();
    }

    @Test
    void realHttpJwtGrpcAndRecoveryLifecycle() throws Exception {
        String clientOne = token("client1");
        String clientTwo = token("client2");
        String manager = token("manager1");

        verifyRealQuoteAndTestDrive(clientOne, clientTwo, manager);

        JsonNode first = createOrder(clientOne, CAR_ONE, 201);
        createOrder(clientTwo, CAR_ONE, 409);
        request("POST", "/api/orders/stock/" + first.get("id").asText() + "/cancel",
                clientOne, null, 200);

        JsonNode rebooked = createOrder(clientTwo, CAR_ONE, 201);
        request("POST", "/api/orders/stock/" + rebooked.get("id").asText() + "/cancel",
                clientTwo, null, 200);

        JsonNode interrupted = createOrder(clientOne, CAR_TWO, 201);
        String interruptedId = interrupted.get("id").asText();
        stop(storageProcess);
        request("POST", "/api/orders/stock/" + interruptedId + "/cancel",
                clientOne, null, 503);
        assertEquals("CANCELLED", orderValue(
                "SELECT status FROM stock_orders WHERE id = ?::uuid", interruptedId));
        assertEquals("RELEASE_PENDING", orderValue(
                "SELECT state FROM stock_reservation_workflows WHERE order_id = ?::uuid", interruptedId));

        stop(orderProcess);
        storageProcess = startStorage("storage-restarted.log");
        awaitHttp(storageProcess, storagePort, "/v3/api-docs", Duration.ofSeconds(90));
        orderProcess = startOrder("order-restarted.log");
        awaitHttp(orderProcess, orderPort, "/v3/api-docs", Duration.ofSeconds(90));
        await(Duration.ofSeconds(30), () -> "RELEASED".equals(orderValue(
                "SELECT state FROM stock_reservation_workflows WHERE order_id = ?::uuid", interruptedId)));
        assertEquals("true", storageValue("SELECT available::text FROM cars WHERE id = ?::uuid", CAR_TWO));

        JsonNode expiring = createOrder(clientOne, CAR_ONE, 201);
        String expiringId = expiring.get("id").asText();
        storageUpdate("UPDATE car_reservations SET hold_expires_at = clock_timestamp() - INTERVAL '1 second' WHERE order_id = ?::uuid",
                expiringId);
        orderUpdate("UPDATE stock_reservation_workflows SET storage_expires_at = clock_timestamp() - INTERVAL '1 second' WHERE order_id = ?::uuid",
                expiringId);
        await(Duration.ofSeconds(30), () -> "CANCELLED".equals(orderValue(
                "SELECT status FROM stock_orders WHERE id = ?::uuid", expiringId)));
        await(Duration.ofSeconds(30), () -> "RELEASED".equals(orderValue(
                "SELECT state FROM stock_reservation_workflows WHERE order_id = ?::uuid", expiringId)));

        JsonNode paid = createOrder(clientOne, CAR_ONE, 201);
        String paidId = paid.get("id").asText();
        for (int i = 0; i < 2; i++) {
            request("POST", "/api/orders/stock/" + paidId + "/advance", manager, null, 200);
        }
        String paymentKey = java.util.UUID.randomUUID().toString();
        String paymentPath = "/api/orders/stock/" + paidId + "/demo-payments";
        String paymentBody = "{\"outcome\":\"SUCCESS\"}";
        JsonNode receipt = json.readTree(request(
                "POST", paymentPath, clientOne, paymentBody, 200,
                Map.of("Idempotency-Key", paymentKey)).body());
        assertEquals("SUCCEEDED", receipt.get("status").asText());
        JsonNode replay = json.readTree(request(
                "POST", paymentPath, clientOne, paymentBody, 200,
                Map.of("Idempotency-Key", paymentKey)).body());
        assertEquals(receipt.get("id").asText(), replay.get("id").asText());
        request("POST", paymentPath, clientOne, "{\"outcome\":\"DECLINE\"}", 409,
                Map.of("Idempotency-Key", paymentKey));
        request("GET", paymentPath + "/" + receipt.get("id").asText(),
                clientTwo, null, 403);
        request("GET", paymentPath + "/" + receipt.get("id").asText(),
                manager, null, 200);
        assertEquals("PAID", orderValue("SELECT status FROM stock_orders WHERE id = ?::uuid", paidId));
        assertEquals("1", orderValue(
                "SELECT count(*)::text FROM outbox_events WHERE aggregate_id = ? AND event_type = 'OrderSentForApproval'",
                paidId));
        assertEquals("CONFIRMED", orderValue(
                "SELECT state FROM stock_reservation_workflows WHERE order_id = ?::uuid", paidId));
        storageUpdate("UPDATE car_reservations SET hold_expires_at = clock_timestamp() - INTERVAL '1 second' WHERE order_id = ?::uuid",
                paidId);
        orderUpdate("UPDATE stock_reservation_workflows SET storage_expires_at = clock_timestamp() - INTERVAL '1 second' WHERE order_id = ?::uuid",
                paidId);
        assertEquals("CONFIRMED", orderValue(
                "SELECT state FROM stock_reservation_workflows WHERE order_id = ?::uuid", paidId));
        assertEquals("PAID", orderValue("SELECT status FROM stock_orders WHERE id = ?::uuid", paidId));
        assertEquals("false", storageValue("SELECT available::text FROM cars WHERE id = ?::uuid", CAR_ONE));
    }

    private void verifyRealQuoteAndTestDrive(
            String clientOne, String clientTwo, String manager) throws Exception {
        String modelId = "b0000000-0000-0000-0000-000000000001";
        Map<String, String> variants = Map.of(
                "c0000000-0000-0000-0000-000000000001", "d0000000-0000-0000-0000-000000000001",
                "c0000000-0000-0000-0000-000000000002", "d0000000-0000-0000-0000-000000000004",
                "c0000000-0000-0000-0000-000000000003", "d0000000-0000-0000-0000-000000000006",
                "c0000000-0000-0000-0000-000000000004", "d0000000-0000-0000-0000-000000000008");
        String customBody = json.writeValueAsString(Map.of(
                "carModelId", modelId,
                "selectedVariants", variants,
                "totalPrice", "0.01"));
        JsonNode customOrder = json.readTree(request(
                "POST", "/api/orders/custom", clientOne, customBody, 201).body());
        assertEquals("3500000.00", customOrder.get("totalPrice").decimalValue().setScale(2).toPlainString());
        request("POST", "/api/orders/custom", clientOne, json.writeValueAsString(Map.of(
                "carModelId", modelId, "selectedVariants", Map.of(), "totalPrice", "999999999")), 400);
        String customId = customOrder.get("id").asText();
        request("POST", "/api/orders/custom/" + customId + "/advance", manager, null, 200);
        request("POST", "/api/orders/custom/" + customId + "/advance", manager, null, 200);
        String customPaymentPath = "/api/orders/custom/" + customId + "/demo-payments";
        JsonNode customReceipt = json.readTree(request(
                "POST", customPaymentPath, clientOne, "{\"outcome\":\"SUCCESS\"}", 200,
                Map.of("Idempotency-Key", java.util.UUID.randomUUID().toString())).body());
        assertEquals("SUCCEEDED", customReceipt.get("status").asText());
        request("GET", customPaymentPath + "/" + customReceipt.get("id").asText(),
                manager, null, 200);

        LocalDateTime start = LocalDateTime.now().plusDays(2).truncatedTo(ChronoUnit.SECONDS);
        String firstBody = json.writeValueAsString(Map.of(
                "carId", CAR_ONE, "scheduledAt", start.toString()));
        JsonNode first = json.readTree(request(
                "POST", "/api/test-drives", clientOne, firstBody, 201).body());
        String firstId = first.get("id").asText();
        JsonNode mine = json.readTree(request(
                "GET", "/api/test-drives/mine", clientOne, null, 200).body());
        assertTrue(mine.findValuesAsText("id").contains(firstId));
        request("POST", "/api/test-drives/" + firstId + "/cancel", clientTwo, null, 403);
        request("POST", "/api/test-drives/" + firstId + "/approve", manager, null, 200);
        request("POST", "/api/test-drives/" + firstId + "/complete", manager, null, 409);
        request("POST", "/api/test-drives/" + firstId + "/cancel", clientOne, null, 200);
        request("POST", "/api/test-drives/" + firstId + "/cancel", clientOne, null, 200);

        JsonNode replacement = json.readTree(request(
                "POST", "/api/test-drives", clientTwo, firstBody, 201).body());
        request("POST", "/api/test-drives", clientOne, json.writeValueAsString(Map.of(
                "carId", CAR_ONE, "scheduledAt", start.plusMinutes(30).toString())), 409);
        request("POST", "/api/test-drives/" + replacement.get("id").asText() + "/cancel",
                manager, null, 200);
        JsonNode completable = json.readTree(request(
                "POST", "/api/test-drives", clientOne, json.writeValueAsString(Map.of(
                        "carId", CAR_ONE, "scheduledAt", start.plusHours(1).toString())), 201).body());
        String completableId = completable.get("id").asText();
        request("POST", "/api/test-drives/" + completableId + "/approve", manager, null, 200);
        orderUpdate("UPDATE test_drive_requests SET requested_date_time = clock_timestamp() - INTERVAL '2 hours' WHERE id = ?::uuid",
                completableId);
        request("POST", "/api/test-drives/" + completableId + "/complete", manager, null, 200);
        request("POST", "/api/test-drives", clientOne, json.writeValueAsString(Map.of(
                "carId", CAR_TWO, "scheduledAt", start.plusDays(1).toString())), 409);
    }

    private JsonNode createOrder(String token, String carId, int status) throws Exception {
        Response response = request("POST", "/api/orders/stock", token,
                "{\"carId\":\"" + carId + "\"}", status);
        return response.body().isBlank() ? json.createObjectNode() : json.readTree(response.body());
    }

    private Response request(String method, String path, String token, String body, int expected) throws Exception {
        return request(method, path, token, body, expected, Map.of());
    }

    private Response request(String method, String path, String token, String body, int expected,
                             Map<String, String> headers) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + orderPort + path))
                .timeout(Duration.ofSeconds(15))
                .header("Authorization", "Bearer " + token);
        headers.forEach(builder::header);
        if (body == null) {
            builder.method(method, HttpRequest.BodyPublishers.noBody());
        } else {
            builder.header("Content-Type", "application/json")
                    .method(method, HttpRequest.BodyPublishers.ofString(body));
        }
        HttpResponse<String> response = http.send(builder.build(), HttpResponse.BodyHandlers.ofString());
        assertEquals(expected, response.statusCode(), response.body());
        return new Response(response.statusCode(), response.body());
    }

    private String token(String username) throws Exception {
        String form = form(Map.of(
                "client_id", "dealership-app",
                "grant_type", "password",
                "username", username,
                "password", "password"));
        HttpRequest request = HttpRequest.newBuilder(URI.create(keycloakBase()
                        + "/realms/dealership/protocol/openid-connect/token"))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(form)).build();
        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, response.statusCode(), response.body());
        return json.readTree(response.body()).get("access_token").asText();
    }

    private Process startStorage(String logName) throws IOException {
        return startJar(root.resolve("storage-service/build/libs/storage-service-1.0-SNAPSHOT.jar"),
                logs.resolve(logName), List.of(
                        "--server.port=" + storagePort,
                        "--grpc.server.address=127.0.0.1",
                        "--grpc.server.port=" + grpcPort,
                        "--spring.datasource.url=" + storageDb.getJdbcUrl(),
                        "--spring.datasource.username=" + storageDb.getUsername(),
                        "--spring.datasource.password=" + storageDb.getPassword(),
                        "--spring.kafka.bootstrap-servers=" + kafka.getBootstrapServers(),
                        "--spring.security.oauth2.resourceserver.jwt.issuer-uri=" + keycloakBase() + "/realms/dealership",
                        "--spring.security.oauth2.resourceserver.jwt.jwk-set-uri=" + keycloakBase() + "/realms/dealership/protocol/openid-connect/certs",
                        "--reservation.hold-ttl=PT30S",
                        "--reservation.recovery.interval=PT0.2S"));
    }

    private Process startOrder(String logName) throws IOException {
        return startJar(root.resolve("order-service/build/libs/order-service-1.0-SNAPSHOT.jar"),
                logs.resolve(logName), List.of(
                        "--server.port=" + orderPort,
                        "--spring.datasource.url=" + orderDb.getJdbcUrl(),
                        "--spring.datasource.username=" + orderDb.getUsername(),
                        "--spring.datasource.password=" + orderDb.getPassword(),
                        "--spring.kafka.bootstrap-servers=" + kafka.getBootstrapServers(),
                        "--spring.security.oauth2.resourceserver.jwt.issuer-uri=" + keycloakBase() + "/realms/dealership",
                        "--spring.security.oauth2.resourceserver.jwt.jwk-set-uri=" + keycloakBase() + "/realms/dealership/protocol/openid-connect/certs",
                        "--grpc.client.storage-service.address=static://127.0.0.1:" + grpcPort,
                        "--reservation.recovery.interval=PT0.2S",
                        "--outbox.publisher.interval=PT1H"));
    }

    private Process startJar(Path jar, Path log, List<String> args) throws IOException {
        assertTrue(Files.isRegularFile(jar), "Missing bootJar: " + jar);
        List<String> command = new java.util.ArrayList<>();
        command.add(Path.of(System.getProperty("java.home"), "bin/java").toString());
        command.add("-Xms64m");
        command.add("-Xmx384m");
        command.add("-jar");
        command.add(jar.toString());
        command.addAll(args);
        return new ProcessBuilder(command)
                .redirectErrorStream(true)
                .redirectOutput(ProcessBuilder.Redirect.to(log.toFile()))
                .start();
    }

    private void awaitHttp(Process process, int port, String path, Duration timeout) throws Exception {
        await(timeout, () -> {
            if (!process.isAlive()) {
                fail("Service exited early with code " + process.exitValue());
            }
            try {
                HttpResponse<Void> response = http.send(HttpRequest.newBuilder(
                                URI.create("http://127.0.0.1:" + port + path))
                                .timeout(Duration.ofSeconds(2)).GET().build(),
                        HttpResponse.BodyHandlers.discarding());
                return response.statusCode() == 200;
            } catch (Exception ignored) {
                return false;
            }
        });
    }

    private void await(Duration timeout, BooleanSupplier condition) throws Exception {
        long deadline = System.nanoTime() + timeout.toNanos();
        while (System.nanoTime() < deadline) {
            if (condition.getAsBoolean()) {
                return;
            }
            Thread.sleep(200);
        }
        fail("Condition was not met within " + timeout);
    }

    private String orderValue(String sql, String id) {
        return value(orderDb, sql, id);
    }

    private String storageValue(String sql, String id) {
        return value(storageDb, sql, id);
    }

    private String value(PostgreSQLContainer<?> database, String sql, String id) {
        try (Connection connection = DriverManager.getConnection(
                database.getJdbcUrl(), database.getUsername(), database.getPassword());
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, id);
            try (ResultSet result = statement.executeQuery()) {
                return result.next() ? result.getString(1) : null;
            }
        } catch (Exception exception) {
            throw new AssertionError(exception);
        }
    }

    private void orderUpdate(String sql, String id) {
        update(orderDb, sql, id);
    }

    private void storageUpdate(String sql, String id) {
        update(storageDb, sql, id);
    }

    private void update(PostgreSQLContainer<?> database, String sql, String id) {
        try (Connection connection = DriverManager.getConnection(
                database.getJdbcUrl(), database.getUsername(), database.getPassword());
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, id);
            assertEquals(1, statement.executeUpdate());
        } catch (Exception exception) {
            throw new AssertionError(exception);
        }
    }

    private String keycloakBase() {
        return "http://127.0.0.1:" + keycloak.getMappedPort(8080);
    }

    private static PostgreSQLContainer<?> postgres(String database, String username) {
        return new PostgreSQLContainer<>(DockerImageName.parse("postgres:16-alpine"))
                .withDatabaseName(database).withUsername(username).withPassword(username);
    }

    private int freePort() throws IOException {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }

    private String form(Map<String, String> values) {
        return values.entrySet().stream()
                .map(entry -> URLEncoder.encode(entry.getKey(), StandardCharsets.UTF_8)
                        + "=" + URLEncoder.encode(entry.getValue(), StandardCharsets.UTF_8))
                .collect(java.util.stream.Collectors.joining("&"));
    }

    private void stop(Process process) {
        if (process == null || !process.isAlive()) {
            return;
        }
        process.destroy();
        try {
            if (!process.waitFor(10, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                process.waitFor(10, TimeUnit.SECONDS);
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            process.destroyForcibly();
        }
    }

    private record Response(int status, String body) { }
}
