package dealership.order;

import dealership.order.core.application.port.out.StockOrderRepository;
import dealership.order.core.application.port.out.UserRepository;
import dealership.order.core.application.service.StockOrderService;
import dealership.order.core.application.service.StockReservationRecoveryService;
import dealership.order.core.application.service.CustomOrderService;
import dealership.order.core.application.service.TestDriveService;
import dealership.order.core.application.port.out.CustomOrderRepository;
import dealership.order.core.application.port.out.CustomConfigurationGateway;
import dealership.order.core.application.port.out.TestDriveCarGateway;
import dealership.order.core.application.port.out.TestDriveRequestRepository;
import dealership.order.core.domain.entity.car.CarConfiguration;
import dealership.order.core.domain.entity.order.CustomOrder;
import dealership.order.core.domain.enums.CustomOrderStatus;
import dealership.order.core.domain.entity.order.StockOrder;
import dealership.order.core.domain.entity.testdrive.TestDriveRequest;
import dealership.order.core.domain.entity.user.User;
import dealership.order.core.domain.enums.StockOrderStatus;
import dealership.order.core.domain.enums.TestDriveStatus;
import dealership.order.core.domain.enums.UserRole;
import dealership.order.core.domain.exception.StorageUnavailableException;
import dealership.order.core.domain.exception.DomainValidationException;
import dealership.order.core.domain.exception.EntityNotFoundException;
import dealership.order.core.domain.exception.TestDriveConflictException;
import dealership.order.infrastructure.grpc.CarGrpcClient;
import dealership.order.infrastructure.messaging.OutboxEvent;
import dealership.order.infrastructure.messaging.OutboxRepository;
import dealership.order.infrastructure.messaging.OutboxClaimStore;
import dealership.order.infrastructure.messaging.OrderResponseMessageTransactions;
import dealership.common.event.OrderApprovedEvent;
import dealership.common.event.PermanentMessageException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.time.Instant;
import java.time.Duration;
import java.time.LocalDateTime;
import java.math.BigDecimal;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.doNothing;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.any;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@AutoConfigureMockMvc
@DisplayName("OrderService - Интеграционные тесты")
class OrderServiceIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private StockOrderService stockOrderService;

    @Autowired
    private StockOrderRepository stockOrderRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private OutboxRepository outboxRepository;

    @Autowired
    private StockReservationRecoveryService recoveryService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private OutboxClaimStore outboxClaimStore;

    @Autowired
    private OrderResponseMessageTransactions responseTransactions;

    @Autowired
    private CustomOrderService customOrderService;

    @Autowired
    private CustomOrderRepository customOrderRepository;

    @Autowired
    private TestDriveService testDriveService;

    @Autowired
    private TestDriveRequestRepository testDriveRequestRepository;

    // Boundary test: storage reservation behavior is covered against real PostgreSQL
    // in storage-service; order integration tests isolate the remote gRPC dependency.
    @MockBean
    private CarGrpcClient reservationGateway;

    @BeforeEach
    void stubStorageLease() {
        when(reservationGateway.reserve(anyString(), anyString()))
                .thenReturn(new dealership.order.core.application.port.out.StockCarReservationGateway.ReservationLease(
                        Instant.now().plusSeconds(900)));
        when(reservationGateway.quote(anyString(), any())).thenAnswer(invocation ->
                new CustomConfigurationGateway.ConfigurationQuote(
                        invocation.getArgument(0), invocation.getArgument(1),
                        new BigDecimal("3500000.00")));
        when(reservationGateway.get(anyString())).thenAnswer(invocation ->
                new TestDriveCarGateway.TestDriveCar(
                        UUID.fromString(invocation.getArgument(0)).toString(), true, true));
    }

    @Test
    @DisplayName("Liquibase миграции: таблицы созданы")
    void shouldRunMigrations() {
        assertNotNull(stockOrderRepository);
        assertNotNull(outboxRepository);
    }

    @Test
    @DisplayName("Outbox lease не позволяет устаревшему ACK завершить новый claim")
    void shouldFenceStaleOutboxAcknowledgement() {
        jdbcTemplate.update("UPDATE outbox_events SET next_attempt_at = clock_timestamp() + INTERVAL '1 hour' WHERE sent = FALSE");
        OutboxEvent event = new OutboxEvent();
        event.setAggregateType("STOCK");
        event.setAggregateId(UUID.randomUUID().toString());
        event.setEventType("OrderSentForApproval");
        event.setPayload("{}");
        event.setTraceId(UUID.randomUUID().toString());
        outboxRepository.saveAndFlush(event);

        var first = outboxClaimStore.claim(1, Duration.ofSeconds(30)).getFirst();
        jdbcTemplate.update("UPDATE outbox_events SET claim_until = clock_timestamp() - INTERVAL '1 second' WHERE id = ?",
                event.getId());
        var second = outboxClaimStore.claim(1, Duration.ofSeconds(30)).getFirst();

        assertNotEquals(first.claimToken(), second.claimToken());
        assertFalse(outboxClaimStore.markSent(event.getId(), first.claimToken()));
        assertTrue(outboxClaimStore.markSent(event.getId(), second.claimToken()));
    }

    @Test
    @DisplayName("Inbox rollback позволяет повторить approval после исправления состояния")
    void shouldRollbackInboxWithInvalidStockTransitionAndDeduplicateRetries() {
        User manager = new User("mgr-inbox", "pass", "Manager Inbox", "inbox@test.com", "+7010", UserRole.MANAGER);
        userRepository.save(manager);
        StockOrder order = stockOrderService.createOrder("client-inbox", UUID.randomUUID().toString());
        UUID eventId = UUID.randomUUID();
        OrderApprovedEvent approved = new OrderApprovedEvent(
                order.getId(), "STOCK", UUID.randomUUID().toString(), UUID.randomUUID().toString());

        assertThrows(PermanentMessageException.class, () -> responseTransactions.processApproved(
                eventId, "order.responses", 0, 1, approved));
        assertEquals(0, inboxCount(eventId));

        stockOrderService.advanceOrder(order.getId());
        stockOrderService.advanceOrder(order.getId());
        stockOrderService.advanceOrder(order.getId());
        responseTransactions.processApproved(eventId, "order.responses", 0, 1, approved);
        responseTransactions.processApproved(eventId, "order.responses", 0, 1, approved);
        responseTransactions.processApproved(UUID.randomUUID(), "order.responses", 0, 2, approved);

        assertEquals(StockOrderStatus.READY_FOR_PICKUP,
                stockOrderRepository.findById(order.getId()).getStatus());
    }

    @Test
    @DisplayName("Custom approval использует блокировку и повтор не продвигает заказ дважды")
    void shouldApplyCustomApprovalExactlyOnceByState() {
        String modelId = UUID.randomUUID().toString();
        CustomOrder order = customOrderService.createOrder(
                "custom-client", modelId, Map.of());
        customOrderService.advanceOrder(order.getId());
        customOrderService.advanceOrder(order.getId());
        customOrderService.advanceOrder(order.getId());
        OrderApprovedEvent approved = new OrderApprovedEvent(
                order.getId(), "CUSTOM", UUID.randomUUID().toString(), UUID.randomUUID().toString());

        responseTransactions.processApproved(UUID.randomUUID(), "order.responses", 0, 3, approved);
        responseTransactions.processApproved(UUID.randomUUID(), "order.responses", 0, 4, approved);

        assertEquals(CustomOrderStatus.AWAITING_DELIVERY,
                customOrderRepository.findById(order.getId()).getStatus());
    }

    @Test
    @DisplayName("Создание заказа через сервис с сохранением в БД")
    void shouldCreateAndPersistOrder() {
        User manager = new User("mgr", "pass", "Manager", "mgr@test.com", "+7000", UserRole.MANAGER);
        userRepository.save(manager);

        StockOrder order = stockOrderService.createOrder("client-123", "car-456");

        assertNotNull(order.getId());
        assertEquals(StockOrderStatus.CREATED, order.getStatus());

        StockOrder found = stockOrderRepository.findById(order.getId());
        assertEquals("client-123", found.getClientId());
    }

    @Test
    @DisplayName("Advance до PAID создаёт событие в outbox")
    void shouldCreateOutboxEventOnPaid() {
        User manager = new User("mgr2", "pass", "Manager2", "mgr2@test.com", "+7001", UserRole.MANAGER);
        userRepository.save(manager);

        StockOrder order = stockOrderService.createOrder("client-789", "car-111");
        stockOrderService.advanceOrder(order.getId());
        stockOrderService.advanceOrder(order.getId());
        stockOrderService.advanceOrder(order.getId());

        List<OutboxEvent> events = outboxRepository.findBySentFalseOrderByCreatedAtAsc();
        assertTrue(events.stream().anyMatch(e ->
                e.getAggregateId().equals(order.getId()) && e.getEventType().equals("OrderSentForApproval")));
    }

    @Test
    @DisplayName("Отмена фиксируется до release, а неудачный release можно повторить")
    void shouldCommitCancellationBeforeReleaseAndRetryRelease() {
        User manager = new User("mgr-cancel", "pass", "Manager Cancel", "cancel@test.com", "+7002", UserRole.MANAGER);
        userRepository.save(manager);
        StockOrder order = stockOrderService.createOrder("client-cancel", "car-cancel");
        AtomicInteger releases = new AtomicInteger();
        doAnswer(invocation -> {
            assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
            assertEquals(StockOrderStatus.CANCELLED,
                    stockOrderRepository.findById(order.getId()).getStatus());
            if (releases.getAndIncrement() == 0) {
                throw new StorageUnavailableException("storage down", null);
            }
            return null;
        }).when(reservationGateway).release(order.getCarId(), order.getId());

        assertThrows(StorageUnavailableException.class,
                () -> stockOrderService.cancelOrder(order.getId()));
        assertEquals(StockOrderStatus.CANCELLED,
                stockOrderRepository.findById(order.getId()).getStatus());

        assertEquals(StockOrderStatus.CANCELLED,
                stockOrderService.cancelOrder(order.getId()).getStatus());
        verify(reservationGateway, times(2)).release(order.getCarId(), order.getId());
    }

    @Test
    @DisplayName("CONFIRM_PENDING переживает transport failure и повтор не дублирует outbox")
    void shouldReplayDurableConfirmationIntent() {
        User manager = new User("mgr-confirm", "pass", "Manager Confirm", "confirm@test.com", "+7003", UserRole.MANAGER);
        userRepository.save(manager);
        StockOrder order = stockOrderService.createOrder("client-confirm", "car-confirm");
        stockOrderService.advanceOrder(order.getId());
        stockOrderService.advanceOrder(order.getId());
        doThrow(new StorageUnavailableException("storage down", null))
                .doNothing()
                .when(reservationGateway).confirm(order.getCarId(), order.getId());

        assertThrows(StorageUnavailableException.class,
                () -> stockOrderService.advanceOrder(order.getId()));
        assertEquals(StockOrderStatus.AWAITING_PAYMENT,
                stockOrderRepository.findById(order.getId()).getStatus());
        assertEquals("CONFIRM_PENDING", workflowState(order.getId()));
        assertThrows(DomainValidationException.class,
                () -> stockOrderService.cancelOrder(order.getId()));

        recoveryService.processPending(order.getId());
        recoveryService.processPending(order.getId());

        assertEquals(StockOrderStatus.PAID,
                stockOrderRepository.findById(order.getId()).getStatus());
        assertEquals("CONFIRMED", workflowState(order.getId()));
        long paidEvents = outboxRepository.findBySentFalseOrderByCreatedAtAsc().stream()
                .filter(event -> event.getAggregateId().equals(order.getId()))
                .filter(event -> event.getEventType().equals("OrderSentForApproval"))
                .count();
        assertEquals(1, paidEvents);
    }

    @Test
    @DisplayName("RELEASE_PENDING после рестарта повторно освобождает отменённый заказ")
    void shouldReplayDurableReleaseIntent() {
        User manager = new User("mgr-release", "pass", "Manager Release", "release@test.com", "+7004", UserRole.MANAGER);
        userRepository.save(manager);
        StockOrder order = stockOrderService.createOrder("client-release", "car-release");
        doThrow(new StorageUnavailableException("storage down", null))
                .doNothing()
                .when(reservationGateway).release(order.getCarId(), order.getId());

        assertThrows(StorageUnavailableException.class,
                () -> stockOrderService.cancelOrder(order.getId()));
        assertEquals(StockOrderStatus.CANCELLED,
                stockOrderRepository.findById(order.getId()).getStatus());
        assertEquals("RELEASE_PENDING", workflowState(order.getId()));

        recoveryService.processPending(order.getId());

        assertEquals("RELEASED", workflowState(order.getId()));
        verify(reservationGateway, times(2)).release(order.getCarId(), order.getId());
    }

    @Test
    @DisplayName("LEGACY_UNVERIFIED без складского владельца отменяется вместо оплаты")
    void shouldCancelUnverifiedLegacyOrderWhenStorageHasNoReservation() {
        User manager = new User("mgr-legacy", "pass", "Manager Legacy", "legacy@test.com", "+7005", UserRole.MANAGER);
        userRepository.save(manager);
        StockOrder order = stockOrderService.createOrder("client-legacy", "car-legacy");
        stockOrderService.advanceOrder(order.getId());
        stockOrderService.advanceOrder(order.getId());
        jdbcTemplate.update("UPDATE stock_reservation_workflows SET state = 'LEGACY_UNVERIFIED', storage_expires_at = NULL WHERE order_id = ?",
                java.util.UUID.fromString(order.getId()));
        doThrow(new EntityNotFoundException("missing"))
                .when(reservationGateway).confirm(order.getCarId(), order.getId());

        assertThrows(EntityNotFoundException.class,
                () -> stockOrderService.advanceOrder(order.getId()));

        assertEquals(StockOrderStatus.CANCELLED,
                stockOrderRepository.findById(order.getId()).getStatus());
        assertEquals("EXPIRED", workflowState(order.getId()));
        assertTrue(outboxRepository.findBySentFalseOrderByCreatedAtAsc().stream()
                .noneMatch(event -> event.getAggregateId().equals(order.getId())));
    }

    @Test
    @DisplayName("Локально истёкший неоплаченный hold отменяется и освобождается")
    void shouldCancelAndReleaseExpiredLocalHold() {
        User manager = new User("mgr-expiry", "pass", "Manager Expiry", "expiry@test.com", "+7006", UserRole.MANAGER);
        userRepository.save(manager);
        StockOrder order = stockOrderService.createOrder("client-expiry", "car-expiry");
        jdbcTemplate.update("UPDATE stock_reservation_workflows SET storage_expires_at = clock_timestamp() - INTERVAL '1 second' WHERE order_id = ?",
                java.util.UUID.fromString(order.getId()));

        recoveryService.processExpiredHold(order.getId());

        assertEquals(StockOrderStatus.CANCELLED,
                stockOrderRepository.findById(order.getId()).getStatus());
        assertEquals("RELEASED", workflowState(order.getId()));
        verify(reservationGateway).release(order.getCarId(), order.getId());
    }

    @Test
    @DisplayName("Test-drive exclusion: adjacent and different cars are allowed, overlap is 409")
    void shouldEnforceOneHourHalfOpenTestDriveWindow() {
        String carId = UUID.randomUUID().toString();
        String otherCarId = UUID.randomUUID().toString();
        LocalDateTime start = LocalDateTime.of(2099, 1, 10, 10, 0);

        testDriveService.createRequest("client-a", carId, start);
        testDriveService.createRequest("client-b", carId, start.plusHours(1));
        testDriveService.createRequest("client-c", otherCarId, start.plusMinutes(30));

        assertThrows(TestDriveConflictException.class,
                () -> testDriveService.createRequest("client-d", carId, start.plusMinutes(30)));
    }

    @Test
    @DisplayName("Cancelled, completed and removed test-drives do not block a new booking")
    void shouldIgnoreInactiveTestDrivesForOverlap() {
        String carId = UUID.randomUUID().toString();
        LocalDateTime start = LocalDateTime.of(2099, 2, 10, 10, 0);

        TestDriveRequest cancelled = testDriveRequestRepository.save(
                new TestDriveRequest("cancelled", carId, start));
        jdbcTemplate.update("UPDATE test_drive_requests SET status = 'CANCELLED' WHERE id = ?",
                UUID.fromString(cancelled.getId()));
        TestDriveRequest completed = testDriveRequestRepository.save(
                new TestDriveRequest("completed", carId, start));
        jdbcTemplate.update("UPDATE test_drive_requests SET status = 'COMPLETED' WHERE id = ?",
                UUID.fromString(completed.getId()));
        TestDriveRequest removed = testDriveRequestRepository.save(
                new TestDriveRequest("removed", carId, start));
        jdbcTemplate.update("UPDATE test_drive_requests SET removed = TRUE WHERE id = ?",
                UUID.fromString(removed.getId()));

        assertDoesNotThrow(() -> testDriveService.createRequest("active", carId, start));
    }

    @Test
    @DisplayName("UUID aliases cannot bypass overlap exclusion")
    void shouldTreatUuidAliasesAsSameCar() {
        String canonicalCarId = UUID.randomUUID().toString();
        LocalDateTime start = LocalDateTime.of(2099, 3, 10, 10, 0);
        testDriveRequestRepository.save(
                new TestDriveRequest("client-a", canonicalCarId.toUpperCase(), start));

        assertThrows(TestDriveConflictException.class, () -> testDriveRequestRepository.save(
                new TestDriveRequest("client-b", canonicalCarId, start.plusMinutes(15))));
    }

    @Test
    @DisplayName("Два конкурентных пересекающихся test-drive дают ровно одного победителя")
    void shouldAllowExactlyOneConcurrentTestDrive() throws Exception {
        String carId = UUID.randomUUID().toString();
        LocalDateTime start = LocalDateTime.of(2099, 4, 10, 10, 0);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch release = new CountDownLatch(1);

        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            Future<Boolean> first = executor.submit(() -> tryCreateTestDrive(
                    "client-a", carId, start, ready, release));
            Future<Boolean> second = executor.submit(() -> tryCreateTestDrive(
                    "client-b", carId, start.plusMinutes(30), ready, release));
            try {
                assertTrue(ready.await(10, TimeUnit.SECONDS));
            } finally {
                release.countDown();
            }
            assertEquals(1, (first.get(10, TimeUnit.SECONDS) ? 1 : 0)
                    + (second.get(10, TimeUnit.SECONDS) ? 1 : 0));
        }
    }

    @Test
    @DisplayName("REST custom order ignores forged price and test-drive reports validation statuses")
    void shouldUseServerPriceAndExposeTestDriveStatuses() throws Exception {
        userRepository.save(new User(
                "mgr-http", "pass", "Manager HTTP", "http-manager@test.com", "+7099", UserRole.MANAGER));
        String modelId = UUID.randomUUID().toString();
        String carId = UUID.randomUUID().toString();
        LocalDateTime start = LocalDateTime.of(2099, 5, 10, 10, 0);

        mockMvc.perform(post("/api/orders/custom")
                        .with(userJwt("http-client"))
                        .contentType("application/json")
                        .content("""
                                {"carModelId":"%s","selectedVariants":{},"totalPrice":0.01}
                                """.formatted(modelId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.clientId").value("http-client"))
                .andExpect(jsonPath("$.totalPrice").value(3500000.00));

        mockMvc.perform(post("/api/test-drives")
                        .with(userJwt("http-client"))
                        .contentType("application/json")
                        .content("""
                                {"carId":"%s","scheduledAt":"%s"}
                                """.formatted(carId, start)))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/test-drives")
                        .with(userJwt("http-client"))
                        .contentType("application/json")
                        .content("""
                                {"carId":"%s","scheduledAt":"%s"}
                                """.formatted(carId, start.plusMinutes(30))))
                .andExpect(status().isConflict());
        mockMvc.perform(post("/api/test-drives")
                        .with(userJwt("http-client"))
                        .contentType("application/json")
                        .content("{"))
                .andExpect(status().isBadRequest());

        doThrow(new EntityNotFoundException("missing"))
                .when(reservationGateway).get(anyString());
        mockMvc.perform(post("/api/test-drives")
                        .with(userJwt("http-client"))
                        .contentType("application/json")
                        .content("""
                                {"carId":"%s","scheduledAt":"%s"}
                                """.formatted(UUID.randomUUID(), start.plusDays(1))))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("REST: 401 без токена")
    void shouldReturn401WithoutToken() throws Exception {
        mockMvc.perform(get("/api/orders/stock"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("REST: 200 с ролью USER")
    void shouldReturn200WithUserRole() throws Exception {
        mockMvc.perform(get("/api/orders/stock")
                        .with(jwt()
                                .jwt(token -> token
                                        .subject("client-123")
                                        .claim("realm_access", Map.of("roles", List.of("USER"))))
                                .authorities(new SimpleGrantedAuthority("ROLE_USER"))))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "USER")
    @DisplayName("REST: Swagger доступен")
    void shouldAccessSwagger() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk());
    }

    private String workflowState(String orderId) {
        return jdbcTemplate.queryForObject(
                "SELECT state FROM stock_reservation_workflows WHERE order_id = ?",
                String.class, java.util.UUID.fromString(orderId));
    }

    private int inboxCount(UUID eventId) {
        return jdbcTemplate.queryForObject(
                "SELECT count(*) FROM inbox_events WHERE event_id = ?", Integer.class, eventId);
    }

    private boolean tryCreateTestDrive(String clientId, String carId, LocalDateTime start,
                                       CountDownLatch ready, CountDownLatch release) throws Exception {
        ready.countDown();
        if (!release.await(10, TimeUnit.SECONDS)) {
            throw new IllegalStateException("Concurrent test-drive start timed out");
        }
        try {
            testDriveService.createRequest(clientId, carId, start);
            return true;
        } catch (TestDriveConflictException expected) {
            return false;
        }
    }

    private org.springframework.test.web.servlet.request.RequestPostProcessor userJwt(String subject) {
        return jwt()
                .jwt(token -> token.subject(subject)
                        .claim("realm_access", Map.of("roles", List.of("USER"))))
                .authorities(new SimpleGrantedAuthority("ROLE_USER"));
    }
}
