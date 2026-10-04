package dealership.order;

import dealership.order.core.application.port.out.StockOrderRepository;
import dealership.order.core.application.port.out.UserRepository;
import dealership.order.core.application.service.StockOrderService;
import dealership.order.core.application.service.StockReservationRecoveryService;
import dealership.order.core.application.service.CustomOrderService;
import dealership.order.core.application.service.DemoPaymentService;
import dealership.order.core.application.service.TestDriveService;
import dealership.order.core.application.port.out.CustomOrderRepository;
import dealership.order.core.application.port.out.CustomConfigurationGateway;
import dealership.order.core.application.port.out.TestDriveCarGateway;
import dealership.order.core.application.port.out.TestDriveRequestRepository;
import dealership.order.core.domain.entity.car.CarConfiguration;
import dealership.order.core.domain.entity.order.CustomOrder;
import dealership.order.core.domain.enums.CustomOrderStatus;
import dealership.order.core.domain.enums.DemoPaymentOutcome;
import dealership.order.core.domain.enums.DemoPaymentStatus;
import dealership.order.core.domain.entity.order.StockOrder;
import dealership.order.core.domain.entity.testdrive.TestDriveRequest;
import dealership.order.core.domain.entity.user.User;
import dealership.order.core.domain.enums.StockOrderStatus;
import dealership.order.core.domain.enums.TestDriveStatus;
import dealership.order.core.domain.enums.UserRole;
import dealership.order.core.domain.exception.StorageUnavailableException;
import dealership.order.core.domain.exception.DomainValidationException;
import dealership.order.core.domain.exception.DemoPaymentConflictException;
import dealership.order.core.domain.exception.EntityNotFoundException;
import dealership.order.core.domain.exception.TestDriveConflictException;
import dealership.order.infrastructure.grpc.CarGrpcClient;
import dealership.order.infrastructure.persistence.entity.DemoPaymentAttemptJpaEntity;
import dealership.order.infrastructure.persistence.repository.DemoPaymentAttemptJpaRepository;
import dealership.order.infrastructure.messaging.OutboxEvent;
import dealership.order.infrastructure.messaging.OutboxRepository;
import dealership.order.infrastructure.messaging.OutboxClaimStore;
import dealership.order.infrastructure.messaging.OrderResponseMessageTransactions;
import dealership.order.infrastructure.messaging.OrderEventProducer;
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
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.time.Instant;
import java.time.Duration;
import java.time.Clock;
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
import static org.mockito.Mockito.doCallRealMethod;
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
    private DemoPaymentService demoPaymentService;

    @Autowired
    private DemoPaymentAttemptJpaRepository demoPaymentRepository;

    @SpyBean
    private OrderEventProducer eventProducer;

    @Autowired
    private TestDriveService testDriveService;

    @Autowired
    private TestDriveRequestRepository testDriveRequestRepository;

    @Autowired
    private Clock businessClock;

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
        demoPaymentService.startStockPayment(
                order.getId(), order.getClientId(), UUID.randomUUID(), DemoPaymentOutcome.SUCCESS);
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
        demoPaymentService.startCustomPayment(
                order.getId(), order.getClientId(), UUID.randomUUID(), DemoPaymentOutcome.SUCCESS);
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
    @DisplayName("Успешный demo payment создаёт событие в outbox")
    void shouldCreateOutboxEventOnPaid() {
        User manager = new User("mgr2", "pass", "Manager2", "mgr2@test.com", "+7001", UserRole.MANAGER);
        userRepository.save(manager);

        StockOrder order = stockOrderService.createOrder("client-789", "car-111");
        stockOrderService.advanceOrder(order.getId());
        stockOrderService.advanceOrder(order.getId());
        demoPaymentService.startStockPayment(
                order.getId(), order.getClientId(), UUID.randomUUID(), DemoPaymentOutcome.SUCCESS);

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

        DemoPaymentAttemptJpaEntity payment = demoPaymentService.startStockPayment(
                order.getId(), order.getClientId(), UUID.randomUUID(), DemoPaymentOutcome.SUCCESS);
        assertEquals(DemoPaymentStatus.PENDING, payment.getStatus());
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
        assertEquals(DemoPaymentStatus.SUCCEEDED,
                demoPaymentRepository.findById(payment.getId()).orElseThrow().getStatus());
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

        DemoPaymentAttemptJpaEntity payment = demoPaymentService.startStockPayment(
                order.getId(), order.getClientId(), UUID.randomUUID(), DemoPaymentOutcome.SUCCESS);

        assertEquals(StockOrderStatus.CANCELLED,
                stockOrderRepository.findById(order.getId()).getStatus());
        assertEquals("EXPIRED", workflowState(order.getId()));
        assertEquals(DemoPaymentStatus.FAILED, payment.getStatus());
        assertTrue(outboxRepository.findBySentFalseOrderByCreatedAtAsc().stream()
                .noneMatch(event -> event.getAggregateId().equals(order.getId())));
    }

    @Test
    @DisplayName("Legacy CONFIRM_PENDING без receipt восстанавливается, но новую payment попытку не принимает")
    void shouldRecoverLegacyPendingConfirmationWithoutSyntheticReceipt() {
        User manager = new User(
                "mgr-legacy-pending", "pass", "Legacy Pending", "legacy-pending@test.com",
                "+7007", UserRole.MANAGER);
        userRepository.save(manager);
        StockOrder order = stockOrderService.createOrder("legacy-pending-client", "legacy-car");
        stockOrderService.advanceOrder(order.getId());
        stockOrderService.advanceOrder(order.getId());
        jdbcTemplate.update("""
                UPDATE stock_reservation_workflows
                   SET state = 'CONFIRM_PENDING', next_attempt_at = clock_timestamp()
                 WHERE order_id = ?
                """, UUID.fromString(order.getId()));

        assertThrows(DemoPaymentConflictException.class, () -> demoPaymentService.startStockPayment(
                order.getId(), order.getClientId(), UUID.randomUUID(), DemoPaymentOutcome.SUCCESS));

        recoveryService.processPending(order.getId());

        assertEquals(StockOrderStatus.PAID,
                stockOrderRepository.findById(order.getId()).getStatus());
        assertEquals(0, jdbcTemplate.queryForObject(
                "SELECT count(*) FROM demo_payment_attempts WHERE stock_order_id = ?",
                Integer.class, UUID.fromString(order.getId())));
    }

    @Test
    @DisplayName("Custom demo decline is replayable and a new key can then succeed")
    void shouldRetryCustomPaymentAfterDecline() {
        String modelId = UUID.randomUUID().toString();
        CustomOrder order = customOrderService.createOrder("custom-payment-client", modelId, Map.of());
        customOrderService.advanceOrder(order.getId());
        customOrderService.advanceOrder(order.getId());
        UUID declinedKey = UUID.randomUUID();

        DemoPaymentAttemptJpaEntity declined = demoPaymentService.startCustomPayment(
                order.getId(), order.getClientId(), declinedKey, DemoPaymentOutcome.DECLINE);
        DemoPaymentAttemptJpaEntity replay = demoPaymentService.startCustomPayment(
                order.getId(), order.getClientId(), declinedKey, DemoPaymentOutcome.DECLINE);

        assertEquals(declined.getId(), replay.getId());
        assertEquals(DemoPaymentStatus.DECLINED, replay.getStatus());
        assertEquals(CustomOrderStatus.AWAITING_PAYMENT,
                customOrderRepository.findById(order.getId()).getStatus());
        assertThrows(DemoPaymentConflictException.class, () -> demoPaymentService.startCustomPayment(
                order.getId(), order.getClientId(), declinedKey, DemoPaymentOutcome.SUCCESS));

        UUID successKey = UUID.randomUUID();
        DemoPaymentAttemptJpaEntity succeeded = demoPaymentService.startCustomPayment(
                order.getId(), order.getClientId(), successKey, DemoPaymentOutcome.SUCCESS);
        assertEquals(DemoPaymentStatus.SUCCEEDED, succeeded.getStatus());
        assertEquals(CustomOrderStatus.PAID,
                customOrderRepository.findById(order.getId()).getStatus());
        assertEquals(1, paidOutboxCount(order.getId()));

        customOrderService.cancelOrder(order.getId());
        DemoPaymentAttemptJpaEntity historicalReplay = demoPaymentService.startCustomPayment(
                order.getId(), order.getClientId(), successKey, DemoPaymentOutcome.SUCCESS);
        assertEquals(succeeded.getId(), historicalReplay.getId());
        assertEquals(DemoPaymentStatus.SUCCEEDED, historicalReplay.getStatus());
        assertEquals(CustomOrderStatus.CANCELLED,
                customOrderRepository.findById(order.getId()).getStatus());
    }

    @Test
    @DisplayName("Custom demo payment rolls back order and receipt when outbox write fails")
    void shouldRollbackCustomPaymentWhenOutboxFails() {
        CustomOrder order = customOrderService.createOrder(
                "custom-outbox-client", UUID.randomUUID().toString(), Map.of());
        customOrderService.advanceOrder(order.getId());
        customOrderService.advanceOrder(order.getId());
        doThrow(new RuntimeException("outbox down"))
                .when(eventProducer).publishOrderPaid(any());

        assertThrows(RuntimeException.class, () -> demoPaymentService.startCustomPayment(
                order.getId(), order.getClientId(), UUID.randomUUID(), DemoPaymentOutcome.SUCCESS));

        assertEquals(CustomOrderStatus.AWAITING_PAYMENT,
                customOrderRepository.findById(order.getId()).getStatus());
        assertEquals(0, paymentCount(order.getId(), false));
        assertEquals(0, paidOutboxCount(order.getId()));
    }

    @Test
    @DisplayName("Stock confirmation rollback leaves PENDING receipt and retry emits one outbox event")
    void shouldRetryStockPaymentAfterOutboxRollback() {
        StockOrder order = stockOrderAwaitingPayment("stock-outbox-client", "stock-outbox-car");
        UUID key = UUID.randomUUID();
        doThrow(new RuntimeException("outbox down"))
                .when(eventProducer).publishOrderPaid(any());

        assertThrows(RuntimeException.class, () -> demoPaymentService.startStockPayment(
                order.getId(), order.getClientId(), key, DemoPaymentOutcome.SUCCESS));

        assertEquals(StockOrderStatus.AWAITING_PAYMENT,
                stockOrderRepository.findById(order.getId()).getStatus());
        assertEquals("CONFIRM_PENDING", workflowState(order.getId()));
        assertEquals("PENDING", paymentStatus(order.getId()));
        assertEquals(0, paidOutboxCount(order.getId()));

        doCallRealMethod().when(eventProducer).publishOrderPaid(any());
        recoveryService.processPending(order.getId());
        DemoPaymentAttemptJpaEntity receipt = demoPaymentService.startStockPayment(
                order.getId(), order.getClientId(), key, DemoPaymentOutcome.SUCCESS);

        assertEquals(DemoPaymentStatus.SUCCEEDED, receipt.getStatus());
        assertEquals(StockOrderStatus.PAID,
                stockOrderRepository.findById(order.getId()).getStatus());
        assertEquals(1, paidOutboxCount(order.getId()));
        verify(reservationGateway, times(2)).confirm(order.getCarId(), order.getId());
    }

    @Test
    @DisplayName("Concurrent different-key custom payments produce one receipt and one outbox event")
    void shouldSerializeDifferentCustomPaymentKeys() throws Exception {
        CustomOrder order = customOrderService.createOrder(
                "custom-race-client", UUID.randomUUID().toString(), Map.of());
        customOrderService.advanceOrder(order.getId());
        customOrderService.advanceOrder(order.getId());
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch release = new CountDownLatch(1);

        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            Future<Boolean> first = executor.submit(() -> tryCustomPayment(order, ready, release));
            Future<Boolean> second = executor.submit(() -> tryCustomPayment(order, ready, release));
            try {
                assertTrue(ready.await(10, TimeUnit.SECONDS));
            } finally {
                release.countDown();
            }
            assertEquals(1, (first.get(10, TimeUnit.SECONDS) ? 1 : 0)
                    + (second.get(10, TimeUnit.SECONDS) ? 1 : 0));
        }

        assertEquals(1, paymentCount(order.getId(), false));
        assertEquals(1, paidOutboxCount(order.getId()));
        assertEquals(CustomOrderStatus.PAID,
                customOrderRepository.findById(order.getId()).getStatus());
    }

    @Test
    @DisplayName("Concurrent same-key stock payment returns one receipt and one outbox event")
    void shouldDeduplicateConcurrentSameKeyPayment() throws Exception {
        StockOrder order = stockOrderAwaitingPayment("same-key-client", "same-key-car");
        UUID key = UUID.randomUUID();
        CountDownLatch confirmationStarted = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        doAnswer(invocation -> {
            confirmationStarted.countDown();
            if (!release.await(10, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Confirmation was not released");
            }
            return null;
        }).when(reservationGateway).confirm(order.getCarId(), order.getId());

        try (ExecutorService executor = Executors.newSingleThreadExecutor()) {
            Future<DemoPaymentAttemptJpaEntity> first = executor.submit(() ->
                    demoPaymentService.startStockPayment(
                            order.getId(), order.getClientId(), key, DemoPaymentOutcome.SUCCESS));
            assertTrue(confirmationStarted.await(10, TimeUnit.SECONDS));
            DemoPaymentAttemptJpaEntity replay;
            try {
                replay = demoPaymentService.startStockPayment(
                        order.getId(), order.getClientId(), key, DemoPaymentOutcome.SUCCESS);
                assertEquals(DemoPaymentStatus.PENDING, replay.getStatus());
            } finally {
                release.countDown();
            }
            DemoPaymentAttemptJpaEntity firstReceipt = first.get(10, TimeUnit.SECONDS);
            assertEquals(firstReceipt.getId(), replay.getId());
            assertEquals(DemoPaymentStatus.SUCCEEDED, firstReceipt.getStatus());
        }

        assertEquals(1, paymentCount(order.getId(), true));
        assertEquals(1, paidOutboxCount(order.getId()));
        verify(reservationGateway, times(1)).confirm(order.getCarId(), order.getId());
    }

    @Test
    @DisplayName("Different payment key loses while the first SUCCESS attempt is pending")
    void shouldRejectDifferentKeyDuringSuccessfulPayment() throws Exception {
        StockOrder order = stockOrderAwaitingPayment("different-key-client", "different-key-car");
        CountDownLatch confirmationStarted = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        doAnswer(invocation -> {
            confirmationStarted.countDown();
            if (!release.await(10, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Confirmation was not released");
            }
            return null;
        }).when(reservationGateway).confirm(order.getCarId(), order.getId());

        try (ExecutorService executor = Executors.newSingleThreadExecutor()) {
            Future<DemoPaymentAttemptJpaEntity> first = executor.submit(() ->
                    demoPaymentService.startStockPayment(
                            order.getId(), order.getClientId(), UUID.randomUUID(),
                            DemoPaymentOutcome.SUCCESS));
            assertTrue(confirmationStarted.await(10, TimeUnit.SECONDS));
            try {
                assertThrows(DemoPaymentConflictException.class, () ->
                        demoPaymentService.startStockPayment(
                                order.getId(), order.getClientId(), UUID.randomUUID(),
                                DemoPaymentOutcome.SUCCESS));
            } finally {
                release.countDown();
            }
            assertEquals(DemoPaymentStatus.SUCCEEDED,
                    first.get(10, TimeUnit.SECONDS).getStatus());
        }

        assertEquals(1, paymentCount(order.getId(), true));
        assertEquals(1, paidOutboxCount(order.getId()));
    }

    @Test
    @DisplayName("Stock cancellation cannot pass a durable PENDING payment intent")
    void shouldSerializePaymentAgainstCancellation() throws Exception {
        StockOrder order = stockOrderAwaitingPayment("payment-cancel-client", "payment-cancel-car");
        CountDownLatch confirmationStarted = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        doAnswer(invocation -> {
            confirmationStarted.countDown();
            if (!release.await(10, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Confirmation was not released");
            }
            throw new StorageUnavailableException("storage down", null);
        }).when(reservationGateway).confirm(order.getCarId(), order.getId());

        try (ExecutorService executor = Executors.newSingleThreadExecutor()) {
            Future<DemoPaymentAttemptJpaEntity> payment = executor.submit(() ->
                    demoPaymentService.startStockPayment(
                            order.getId(), order.getClientId(), UUID.randomUUID(),
                            DemoPaymentOutcome.SUCCESS));
            assertTrue(confirmationStarted.await(10, TimeUnit.SECONDS));
            assertThrows(DomainValidationException.class,
                    () -> stockOrderService.cancelOrder(order.getId()));
            release.countDown();
            assertEquals(DemoPaymentStatus.PENDING,
                    payment.get(10, TimeUnit.SECONDS).getStatus());
        }
        assertEquals(StockOrderStatus.AWAITING_PAYMENT,
                stockOrderRepository.findById(order.getId()).getStatus());
    }

    @Test
    @DisplayName("REST demo payment validates auth, ownership, UUIDs and durable PENDING replay")
    void shouldExposeSafeDemoPaymentHttpContract() throws Exception {
        StockOrder order = stockOrderAwaitingPayment("http-payment-owner", "http-payment-car");
        UUID key = UUID.randomUUID();
        doThrow(new StorageUnavailableException("storage down", null))
                .when(reservationGateway).confirm(order.getCarId(), order.getId());
        String path = "/api/orders/stock/" + order.getId() + "/demo-payments";
        String body = "{\"outcome\":\"SUCCESS\"}";

        mockMvc.perform(post(path).header("Idempotency-Key", key)
                        .contentType("application/json").content(body))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post(path).with(managerJwt("manager"))
                        .header("Idempotency-Key", key)
                        .contentType("application/json").content(body))
                .andExpect(status().isForbidden());
        mockMvc.perform(post(path).with(adminJwt("different-admin"))
                        .header("Idempotency-Key", key)
                        .contentType("application/json").content(body))
                .andExpect(status().isForbidden());
        mockMvc.perform(post(path).with(userJwt(order.getClientId()))
                        .contentType("application/json").content(body))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post(path).with(userJwt(order.getClientId()))
                        .header("Idempotency-Key", "not-a-uuid")
                        .contentType("application/json").content(body))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post(path).with(userJwt(order.getClientId()))
                        .header("Idempotency-Key", key)
                        .contentType("application/json").content("{}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post(path).with(userJwt(order.getClientId()))
                        .header("Idempotency-Key", key)
                        .contentType("application/json").content("{\"outcome\":\"UNKNOWN\"}"))
                .andExpect(status().isBadRequest());

        String receiptId = mockMvc.perform(post(path).with(userJwt(order.getClientId()))
                        .header("Idempotency-Key", key)
                        .contentType("application/json").content(body))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andReturn().getResponse().getContentAsString();
        String paymentId = new com.fasterxml.jackson.databind.ObjectMapper()
                .readTree(receiptId).get("id").asText();

        mockMvc.perform(post(path).with(userJwt(order.getClientId()))
                        .header("Idempotency-Key", key)
                        .contentType("application/json").content("{\"outcome\":\"DECLINE\"}"))
                .andExpect(status().isConflict());
        mockMvc.perform(post(path).with(userJwt(order.getClientId()))
                        .header("Idempotency-Key", UUID.randomUUID())
                        .contentType("application/json").content(body))
                .andExpect(status().isConflict());

        String receiptPath = path + "/" + paymentId;
        mockMvc.perform(get(receiptPath).with(userJwt("other-client")))
                .andExpect(status().isForbidden());
        mockMvc.perform(get(receiptPath).with(managerJwt("audit-manager")))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.failureCode").doesNotExist());
        mockMvc.perform(get(path + "/not-a-uuid").with(userJwt(order.getClientId())))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/orders/stock/not-a-uuid/demo-payments")
                        .with(userJwt(order.getClientId()))
                        .header("Idempotency-Key", UUID.randomUUID())
                        .contentType("application/json").content(body))
                .andExpect(status().isBadRequest());
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
        testDriveService.cancelRequest(cancelled.getId(), "cancelled", false);
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
    @DisplayName("Concurrent terminal test-drive transitions serialize on the row lock")
    void shouldSerializeConcurrentTerminalTestDriveTransitions() throws Exception {
        TestDriveRequest approved = testDriveRequestRepository.save(new TestDriveRequest(
                UUID.randomUUID().toString(), "race-owner", UUID.randomUUID().toString(),
                LocalDateTime.now(businessClock).minusHours(2), TestDriveStatus.APPROVED));
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch release = new CountDownLatch(1);

        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            Future<Boolean> complete = executor.submit(() -> tryTransition(
                    ready, release, () -> testDriveService.completeRequest(approved.getId())));
            Future<Boolean> cancel = executor.submit(() -> tryTransition(
                    ready, release, () -> testDriveService.cancelRequest(
                            approved.getId(), "manager", true)));
            try {
                assertTrue(ready.await(10, TimeUnit.SECONDS));
            } finally {
                release.countDown();
            }
            assertEquals(1, (complete.get(10, TimeUnit.SECONDS) ? 1 : 0)
                    + (cancel.get(10, TimeUnit.SECONDS) ? 1 : 0));
        }

        assertTrue(List.of(TestDriveStatus.COMPLETED, TestDriveStatus.CANCELLED)
                .contains(testDriveRequestRepository.findById(approved.getId()).getStatus()));
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
    @DisplayName("REST test-drive lifecycle enforces subject ownership and staff roles")
    void shouldEnforceTestDriveLifecycleAuthorization() throws Exception {
        TestDriveRequest ownerRequest = testDriveRequestRepository.save(new TestDriveRequest(
                "lifecycle-owner", UUID.randomUUID().toString(),
                LocalDateTime.of(2099, 6, 10, 10, 0)));
        TestDriveRequest otherRequest = testDriveRequestRepository.save(new TestDriveRequest(
                "other-client", UUID.randomUUID().toString(),
                LocalDateTime.of(2099, 6, 10, 10, 0)));

        mockMvc.perform(get("/api/test-drives/mine").with(userJwt("lifecycle-owner")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(ownerRequest.getId()))
                .andExpect(jsonPath("$[0].clientId").value("lifecycle-owner"));

        mockMvc.perform(post("/api/test-drives/{id}/cancel", otherRequest.getId())
                        .with(userJwt("lifecycle-owner")))
                .andExpect(status().isForbidden());
        assertEquals(TestDriveStatus.PENDING,
                testDriveRequestRepository.findById(otherRequest.getId()).getStatus());

        mockMvc.perform(post("/api/test-drives/{id}/approve", ownerRequest.getId())
                        .with(userJwt("lifecycle-owner")))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/test-drives/{id}/approve", ownerRequest.getId())
                        .with(managerJwt("lifecycle-manager")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"));
        mockMvc.perform(post("/api/test-drives/{id}/complete", ownerRequest.getId())
                        .with(managerJwt("lifecycle-manager")))
                .andExpect(status().isConflict());

        mockMvc.perform(post("/api/test-drives/{id}/cancel", ownerRequest.getId())
                        .with(userJwt("lifecycle-owner")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));
        mockMvc.perform(post("/api/test-drives/{id}/cancel", ownerRequest.getId())
                        .with(userJwt("lifecycle-owner")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));
        mockMvc.perform(post("/api/test-drives/{id}/cancel", otherRequest.getId())
                        .with(managerJwt("lifecycle-manager")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));

        mockMvc.perform(post("/api/test-drives/not-a-uuid/cancel")
                        .with(userJwt("lifecycle-owner")))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/test-drives/{id}/cancel", UUID.randomUUID())
                        .with(userJwt("lifecycle-owner")))
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

    private int paidOutboxCount(String orderId) {
        return jdbcTemplate.queryForObject("""
                SELECT count(*) FROM outbox_events
                 WHERE aggregate_id = ? AND event_type = 'OrderSentForApproval'
                """, Integer.class, orderId);
    }

    private int paymentCount(String orderId, boolean stock) {
        String column = stock ? "stock_order_id" : "custom_order_id";
        return jdbcTemplate.queryForObject(
                "SELECT count(*) FROM demo_payment_attempts WHERE " + column + " = ?",
                Integer.class, UUID.fromString(orderId));
    }

    private String paymentStatus(String stockOrderId) {
        return jdbcTemplate.queryForObject(
                "SELECT status FROM demo_payment_attempts WHERE stock_order_id = ?",
                String.class, UUID.fromString(stockOrderId));
    }

    private StockOrder stockOrderAwaitingPayment(String clientId, String carId) {
        String suffix = UUID.randomUUID().toString();
        userRepository.save(new User(
                "manager-" + suffix, "pass", "Payment Manager",
                suffix + "@test.com", "+7" + Math.abs(suffix.hashCode()), UserRole.MANAGER));
        StockOrder order = stockOrderService.createOrder(clientId, carId);
        stockOrderService.advanceOrder(order.getId());
        stockOrderService.advanceOrder(order.getId());
        return stockOrderRepository.findById(order.getId());
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

    private boolean tryTransition(CountDownLatch ready, CountDownLatch release,
                                  Runnable transition) throws Exception {
        ready.countDown();
        if (!release.await(10, TimeUnit.SECONDS)) {
            throw new IllegalStateException("Concurrent lifecycle start timed out");
        }
        try {
            transition.run();
            return true;
        } catch (TestDriveConflictException expected) {
            return false;
        }
    }

    private boolean tryCustomPayment(CustomOrder order, CountDownLatch ready,
                                     CountDownLatch release) throws Exception {
        ready.countDown();
        if (!release.await(10, TimeUnit.SECONDS)) {
            throw new IllegalStateException("Concurrent custom payment start timed out");
        }
        try {
            demoPaymentService.startCustomPayment(
                    order.getId(), order.getClientId(), UUID.randomUUID(),
                    DemoPaymentOutcome.SUCCESS);
            return true;
        } catch (DemoPaymentConflictException expected) {
            return false;
        }
    }

    private org.springframework.test.web.servlet.request.RequestPostProcessor userJwt(String subject) {
        return jwt()
                .jwt(token -> token.subject(subject)
                        .claim("realm_access", Map.of("roles", List.of("USER"))))
                .authorities(new SimpleGrantedAuthority("ROLE_USER"));
    }

    private org.springframework.test.web.servlet.request.RequestPostProcessor managerJwt(String subject) {
        return jwt()
                .jwt(token -> token.subject(subject)
                        .claim("realm_access", Map.of("roles", List.of("MANAGER"))))
                .authorities(new SimpleGrantedAuthority("ROLE_MANAGER"));
    }

    private org.springframework.test.web.servlet.request.RequestPostProcessor adminJwt(String subject) {
        return jwt()
                .jwt(token -> token.subject(subject)
                        .claim("realm_access", Map.of("roles", List.of("ADMIN"))))
                .authorities(new SimpleGrantedAuthority("ROLE_ADMIN"));
    }
}
