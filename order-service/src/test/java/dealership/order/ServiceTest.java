package dealership.order;

import dealership.order.core.application.port.out.CustomOrderRepository;
import dealership.order.core.application.port.out.CustomConfigurationGateway;
import dealership.order.core.application.port.out.StockOrderRepository;
import dealership.order.core.application.port.out.StockCarReservationGateway;
import dealership.order.core.application.port.out.TestDriveRequestRepository;
import dealership.order.core.application.port.out.TestDriveCarGateway;
import dealership.order.core.application.port.out.UserRepository;
import dealership.order.core.application.service.CustomOrderService;
import dealership.order.core.application.service.StockOrderService;
import dealership.order.core.application.service.StockOrderTransactions;
import dealership.order.core.application.service.StockReservationRecoveryService;
import dealership.order.core.application.service.TestDriveService;
import dealership.order.core.domain.entity.car.CarConfiguration;
import dealership.order.core.domain.entity.order.CustomOrder;
import dealership.order.core.domain.entity.order.StockOrder;
import dealership.order.core.domain.entity.testdrive.TestDriveRequest;
import dealership.order.core.domain.entity.user.User;
import dealership.order.core.domain.enums.CustomOrderStatus;
import dealership.order.core.domain.enums.DemoPaymentOutcome;
import dealership.order.core.domain.enums.DemoPaymentStatus;
import dealership.order.core.domain.enums.StockOrderStatus;
import dealership.order.core.domain.enums.StockReservationWorkflowState;
import dealership.order.core.domain.enums.TestDriveStatus;
import dealership.order.core.domain.enums.UserRole;
import dealership.order.core.domain.exception.DomainValidationException;
import dealership.order.core.domain.exception.DemoPaymentConflictException;
import dealership.order.core.domain.exception.StorageUnavailableException;
import dealership.order.core.domain.exception.TestDriveConflictException;
import dealership.order.infrastructure.messaging.OrderEventProducer;
import dealership.order.infrastructure.persistence.entity.StockReservationWorkflowJpaEntity;
import dealership.order.infrastructure.persistence.entity.DemoPaymentAttemptJpaEntity;
import dealership.order.infrastructure.persistence.repository.DemoPaymentAttemptJpaRepository;
import dealership.order.infrastructure.persistence.repository.StockReservationWorkflowJpaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.Instant;
import java.time.Clock;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@DisplayName("Сервисы OrderService")
class ServiceTest {

    @Nested
    @DisplayName("StockOrderService")
    @ExtendWith(MockitoExtension.class)
    class StockOrderServiceTests {

        @Mock
        private StockOrderRepository orderRepository;
        @Mock
        private UserRepository userRepository;
        @Mock
        private StockCarReservationGateway reservationGateway;
        @Mock
        private StockOrderTransactions transactions;
        @Mock
        private StockReservationRecoveryService recoveryService;
        @InjectMocks
        private StockOrderService orderService;

        private User testManager;

        @BeforeEach
        void setUp() {
            testManager = new User("Менеджер", "manager@test.com", UserRole.MANAGER);
        }

        @Test
        @DisplayName("создание заказа: менеджер назначается")
        void shouldCreateOrder() {
            Instant expiry = Instant.now().plusSeconds(60);
            when(userRepository.findByRole(UserRole.MANAGER)).thenReturn(List.of(testManager));
            when(reservationGateway.reserve(eq("car-1"), anyString()))
                    .thenReturn(new StockCarReservationGateway.ReservationLease(expiry));
            when(transactions.saveNew(any(StockOrder.class), eq(expiry)))
                    .thenAnswer(inv -> inv.getArgument(0));

            StockOrder order = orderService.createOrder("client-1", "car-1");
            assertNotNull(order.getId());
            assertEquals("client-1", order.getClientId());
            assertEquals(StockOrderStatus.CREATED, order.getStatus());
            InOrder inOrder = inOrder(reservationGateway, transactions);
            inOrder.verify(reservationGateway).reserve("car-1", order.getId());
            inOrder.verify(transactions).saveNew(any(), eq(expiry));
        }

        @Test
        @DisplayName("нельзя создать заказ если нет менеджеров")
        void shouldRejectWhenNoManagers() {
            when(userRepository.findByRole(UserRole.MANAGER)).thenReturn(List.of());
            assertThrows(DomainValidationException.class, () -> orderService.createOrder("cl", "car"));
        }

        @Test
        @DisplayName("отмена заказа")
        void shouldCancelOrder() {
            StockOrder order = new StockOrder("cl", "m", "car");
            order.cancel();
            StockOrderTransactions.Work work =
                    new StockOrderTransactions.Work(order, StockOrderTransactions.Action.RELEASE);
            when(transactions.cancel(order.getId())).thenReturn(work);
            when(recoveryService.execute(work)).thenReturn(order);

            StockOrder cancelled = orderService.cancelOrder(order.getId());
            assertEquals(StockOrderStatus.CANCELLED, cancelled.getStatus());
            InOrder inOrder = inOrder(transactions, recoveryService);
            inOrder.verify(transactions).cancel(order.getId());
            inOrder.verify(recoveryService).execute(work);
        }

        @Test
        @DisplayName("advance делегируется локальной транзакции")
        void shouldAdvanceInLocalTransaction() {
            StockOrder order = new StockOrder("cl", "m", "car");
            order.advanceStatus();
            order.advanceStatus();

            StockOrderTransactions.Work work =
                    new StockOrderTransactions.Work(order, StockOrderTransactions.Action.NONE);
            when(transactions.advance(order.getId())).thenReturn(work);
            when(recoveryService.execute(work)).thenReturn(order);

            StockOrder advanced = orderService.advanceOrder(order.getId());
            assertEquals(StockOrderStatus.AWAITING_PAYMENT, advanced.getStatus());
            verify(transactions).advance(order.getId());
        }

        @Test
        @DisplayName("ошибка локального сохранения компенсирует резерв")
        void shouldReleaseReservationWhenSaveFails() {
            Instant expiry = Instant.now().plusSeconds(60);
            when(userRepository.findByRole(UserRole.MANAGER)).thenReturn(List.of(testManager));
            when(reservationGateway.reserve(eq("car"), anyString()))
                    .thenReturn(new StockCarReservationGateway.ReservationLease(expiry));
            when(transactions.saveNew(any(), eq(expiry))).thenThrow(new RuntimeException("db down"));

            assertThrows(RuntimeException.class, () -> orderService.createOrder("cl", "car"));
            var orderId = org.mockito.ArgumentCaptor.forClass(String.class);
            verify(reservationGateway).reserve(eq("car"), orderId.capture());
            verify(reservationGateway).release("car", orderId.getValue());
        }

        @Test
        @DisplayName("конфликт резерва не создаёт локальный заказ")
        void shouldNotSaveWhenReservationFails() {
            when(userRepository.findByRole(UserRole.MANAGER)).thenReturn(List.of(testManager));
            RuntimeException conflict = new RuntimeException("conflict");
            when(reservationGateway.reserve(eq("car"), anyString())).thenThrow(conflict);

            assertSame(conflict, assertThrows(RuntimeException.class,
                    () -> orderService.createOrder("cl", "car")));
            verify(transactions, never()).saveNew(any(), any());
            verify(reservationGateway, never()).release(anyString(), anyString());
        }

        @Test
        @DisplayName("ошибка компенсации не скрывает исходную ошибку")
        void shouldPreserveOriginalFailureWhenCleanupFails() {
            when(userRepository.findByRole(UserRole.MANAGER)).thenReturn(List.of(testManager));
            RuntimeException original = new StorageUnavailableException("reserve uncertain", null);
            RuntimeException cleanup = new RuntimeException("cleanup failed");
            when(reservationGateway.reserve(eq("car"), anyString())).thenThrow(original);
            doThrow(cleanup).when(reservationGateway).release(eq("car"), anyString());

            RuntimeException thrown = assertThrows(RuntimeException.class,
                    () -> orderService.createOrder("cl", "car"));

            assertSame(original, thrown);
            assertArrayEquals(new Throwable[]{cleanup}, thrown.getSuppressed());
        }

        @Test
        @DisplayName("ошибка фиксации отмены не освобождает автомобиль")
        void shouldNotReleaseWhenCancelCommitFails() {
            when(transactions.cancel("order")).thenThrow(new RuntimeException("commit failed"));

            assertThrows(RuntimeException.class, () -> orderService.cancelOrder("order"));
            verifyNoInteractions(reservationGateway);
        }

        @Test
        @DisplayName("повтор отмены повторяет освобождение")
        void shouldRetryReleaseForAlreadyCancelledOrder() {
            StockOrder cancelled = new StockOrder("cl", "m", "car");
            cancelled.cancel();
            StockOrderTransactions.Work work =
                    new StockOrderTransactions.Work(cancelled, StockOrderTransactions.Action.RELEASE);
            when(transactions.cancel(cancelled.getId())).thenReturn(work);
            when(recoveryService.execute(work)).thenReturn(cancelled);

            orderService.cancelOrder(cancelled.getId());
            orderService.cancelOrder(cancelled.getId());

            verify(recoveryService, times(2)).execute(work);
        }

        @Test
        @DisplayName("получение заказов клиента и всех заказов")
        void shouldGetOrders() {
            when(orderRepository.findByClientId("cl")).thenReturn(List.of());
            when(orderRepository.findAll()).thenReturn(List.of());
            assertTrue(orderService.getClientOrders("cl").isEmpty());
            assertTrue(orderService.getAllOrders().isEmpty());
        }
    }

    @Nested
    @DisplayName("StockOrderTransactions")
    @ExtendWith(MockitoExtension.class)
    class StockOrderTransactionTests {

        @Mock
        private StockOrderRepository orderRepository;
        @Mock
        private OrderEventProducer eventProducer;
        @Mock
        private StockReservationWorkflowJpaRepository workflowRepository;
        @Mock
        private DemoPaymentAttemptJpaRepository paymentRepository;
        @InjectMocks
        private StockOrderTransactions transactions;

        @Test
        @DisplayName("advance блокирует строку и пишет outbox при переходе в PAID")
        void shouldLockAndPublishWhenAdvancingToPaid() {
            StockOrder order = new StockOrder("cl", "m", "car");
            order.advanceStatus();
            order.advanceStatus();
            StockReservationWorkflowJpaEntity workflow = workflow(order, StockReservationWorkflowState.CONFIRM_PENDING);
            when(orderRepository.findByIdForUpdate(order.getId())).thenReturn(order);
            when(workflowRepository.findByOrderIdForUpdate(UUID.fromString(order.getId())))
                    .thenReturn(Optional.of(workflow));
            when(orderRepository.save(order)).thenReturn(order);

            StockOrder saved = transactions.completeConfirmation(order.getId());

            assertEquals(StockOrderStatus.PAID, saved.getStatus());
            verify(orderRepository).findByIdForUpdate(order.getId());
            verify(eventProducer).publishOrderPaid(any());
        }

        @Test
        @DisplayName("demo payment блокирует order и workflow, сохраняет PENDING intent")
        void shouldStartDurableDemoPayment() {
            StockOrder order = new StockOrder("client", "manager", "car");
            order.advanceStatus();
            order.advanceStatus();
            StockReservationWorkflowJpaEntity workflow = workflow(
                    order, StockReservationWorkflowState.HELD);
            UUID key = UUID.randomUUID();
            when(orderRepository.findByIdForUpdate(order.getId())).thenReturn(order);
            when(workflowRepository.findByOrderIdForUpdate(UUID.fromString(order.getId())))
                    .thenReturn(Optional.of(workflow));
            when(workflowRepository.databaseNow()).thenReturn(Instant.parse("2026-10-04T10:00:00Z"));
            when(paymentRepository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));

            StockOrderTransactions.PaymentWork result = transactions.startDemoPayment(
                    order.getId(), "client", key, DemoPaymentOutcome.SUCCESS);

            assertEquals(DemoPaymentStatus.PENDING, result.payment().getStatus());
            assertEquals(StockOrderTransactions.Action.CONFIRM, result.work().action());
            assertEquals(StockReservationWorkflowState.CONFIRM_PENDING, workflow.getState());
            InOrder locks = inOrder(orderRepository, workflowRepository, paymentRepository);
            locks.verify(orderRepository).findByIdForUpdate(order.getId());
            locks.verify(workflowRepository).findByOrderIdForUpdate(UUID.fromString(order.getId()));
            locks.verify(paymentRepository).findStockByKeyForUpdate(
                    UUID.fromString(order.getId()), key);
        }

        @Test
        @DisplayName("demo payment replay требует тот же outcome")
        void shouldRejectChangedOutcomeForSameKey() {
            StockOrder order = new StockOrder("client", "manager", "car");
            order.advanceStatus();
            order.advanceStatus();
            StockReservationWorkflowJpaEntity workflow = workflow(
                    order, StockReservationWorkflowState.HELD);
            UUID key = UUID.randomUUID();
            DemoPaymentAttemptJpaEntity existing = new DemoPaymentAttemptJpaEntity();
            existing.setStockOrderId(UUID.fromString(order.getId()));
            existing.setIdempotencyKey(key);
            existing.setRequestedOutcome(DemoPaymentOutcome.DECLINE);
            existing.setStatus(DemoPaymentStatus.DECLINED);
            when(orderRepository.findByIdForUpdate(order.getId())).thenReturn(order);
            when(workflowRepository.findByOrderIdForUpdate(UUID.fromString(order.getId())))
                    .thenReturn(Optional.of(workflow));
            when(paymentRepository.findStockByKeyForUpdate(UUID.fromString(order.getId()), key))
                    .thenReturn(Optional.of(existing));

            assertThrows(DemoPaymentConflictException.class, () -> transactions.startDemoPayment(
                    order.getId(), "client", key, DemoPaymentOutcome.SUCCESS));
        }

        @Test
        @DisplayName("advance до промежуточного статуса не пишет outbox")
        void shouldNotPublishBeforePaid() {
            StockOrder order = new StockOrder("cl", "m", "car");
            StockReservationWorkflowJpaEntity workflow = workflow(order, StockReservationWorkflowState.HELD);
            when(orderRepository.findByIdForUpdate(order.getId())).thenReturn(order);
            when(workflowRepository.findByOrderIdForUpdate(UUID.fromString(order.getId())))
                    .thenReturn(Optional.of(workflow));
            when(orderRepository.save(order)).thenReturn(order);

            transactions.advance(order.getId());

            verify(eventProducer, never()).publishOrderPaid(any());
        }

        @Test
        @DisplayName("повторная отмена блокирует строку и не перезаписывает состояние")
        void shouldLockButNotRewriteAlreadyCancelledOrder() {
            StockOrder order = new StockOrder("cl", "m", "car");
            order.cancel();
            StockReservationWorkflowJpaEntity workflow = workflow(order, StockReservationWorkflowState.RELEASE_PENDING);
            when(orderRepository.findByIdForUpdate(order.getId())).thenReturn(order);
            when(workflowRepository.findByOrderIdForUpdate(UUID.fromString(order.getId())))
                    .thenReturn(Optional.of(workflow));
            when(workflowRepository.databaseNow()).thenReturn(Instant.now());

            StockOrderTransactions.Work result = transactions.cancel(order.getId());

            assertSame(order, result.order());
            verify(orderRepository).findByIdForUpdate(order.getId());
            verify(orderRepository, never()).save(any());
        }

        private StockReservationWorkflowJpaEntity workflow(
                StockOrder order, StockReservationWorkflowState state) {
            StockReservationWorkflowJpaEntity workflow = new StockReservationWorkflowJpaEntity();
            workflow.setOrderId(UUID.fromString(order.getId()));
            workflow.setCarId(order.getCarId());
            workflow.setState(state);
            return workflow;
        }
    }

    @Nested
    @DisplayName("CustomOrderService")
    @ExtendWith(MockitoExtension.class)
    class CustomOrderServiceTests {

        @Mock
        private CustomOrderRepository orderRepository;
        @Mock
        private UserRepository userRepository;
        @Mock
        private OrderEventProducer eventProducer;
        @Mock
        private CustomConfigurationGateway configurationGateway;
        @Mock
        private DemoPaymentAttemptJpaRepository paymentRepository;
        @InjectMocks
        private CustomOrderService customOrderService;

        private User testManager;

        @BeforeEach
        void setUp() {
            testManager = new User("Менеджер", "m@test.com", UserRole.MANAGER);
        }

        @Test
        @DisplayName("создание кастомного заказа")
        void shouldCreateCustomOrder() {
            String modelId = UUID.randomUUID().toString();
            Map<String, String> selection = Map.of(
                    UUID.randomUUID().toString(), UUID.randomUUID().toString());
            when(userRepository.findByRole(UserRole.MANAGER)).thenReturn(List.of(testManager));
            when(orderRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(configurationGateway.quote(modelId, selection)).thenReturn(
                    new CustomConfigurationGateway.ConfigurationQuote(
                            modelId, selection, new BigDecimal("3500000.00")));

            CustomOrder order = customOrderService.createOrder("cl", modelId, selection);
            assertNotNull(order.getId());
            assertEquals(CustomOrderStatus.CREATED, order.getStatus());
            assertEquals("cl", order.getClientId());
            assertEquals(testManager.getId(), order.getManagerId());
            assertEquals(new BigDecimal("3500000.00"), order.getTotalPrice());
        }

        @Test
        @DisplayName("ошибка quote не создаёт заказ")
        void shouldNotCreateOrderWhenQuoteFails() {
            String modelId = UUID.randomUUID().toString();
            when(configurationGateway.quote(eq(modelId), any()))
                    .thenThrow(new StorageUnavailableException("down", null));

            assertThrows(StorageUnavailableException.class,
                    () -> customOrderService.createOrder("cl", modelId, Map.of()));

            verifyNoInteractions(orderRepository);
            verify(userRepository, never()).findByRole(any());
        }

        @Test
        @DisplayName("успешный demo payment переводит заказ в PAID и публикует outbox")
        void shouldPublishEventOnPaid() {
            CarConfiguration config = new CarConfiguration("model-1");
            config.selectVariant("cat-1", "var-1");
            CustomOrder order = new CustomOrder("cl", "mgr", "model-1", config, new BigDecimal("3500000"));
            order.advanceStatus(null);
            order.advanceStatus(null);

            when(orderRepository.findByIdForUpdate(order.getId())).thenReturn(order);
            when(orderRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(paymentRepository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));

            DemoPaymentAttemptJpaEntity payment = customOrderService.startDemoPayment(
                    order.getId(), "cl", UUID.randomUUID(), DemoPaymentOutcome.SUCCESS);
            assertEquals(CustomOrderStatus.PAID, order.getStatus());
            assertEquals(DemoPaymentStatus.SUCCEEDED, payment.getStatus());
            verify(eventProducer).publishOrderPaid(any());
        }

        @Test
        @DisplayName("DECLINE сохраняет receipt и позволяет новую попытку")
        void shouldRecordDeclineWithoutAdvancingOrder() {
            CarConfiguration config = new CarConfiguration("model-1");
            CustomOrder order = new CustomOrder(
                    "client", "manager", "model-1", config, new BigDecimal("3500000"));
            order.advanceStatus(null);
            order.advanceStatus(null);
            when(orderRepository.findByIdForUpdate(order.getId())).thenReturn(order);
            when(paymentRepository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));

            DemoPaymentAttemptJpaEntity payment = customOrderService.startDemoPayment(
                    order.getId(), "client", UUID.randomUUID(), DemoPaymentOutcome.DECLINE);

            assertEquals(DemoPaymentStatus.DECLINED, payment.getStatus());
            assertEquals(CustomOrderStatus.AWAITING_PAYMENT, order.getStatus());
            verify(eventProducer, never()).publishOrderPaid(any());
        }
    }

    @Nested
    @DisplayName("TestDriveService")
    @ExtendWith(MockitoExtension.class)
    class TestDriveServiceTests {

        @Mock
        private TestDriveRequestRepository requestRepository;
        @Mock
        private UserRepository userRepository;
        @Mock
        private TestDriveCarGateway carGateway;
        private TestDriveService testDriveService;
        private MutableClock clock;

        @BeforeEach
        void setUp() {
            clock = new MutableClock(Instant.parse("2026-10-02T09:00:00Z"),
                    ZoneId.of("Europe/Moscow"));
            testDriveService = new TestDriveService(requestRepository, userRepository, carGateway, clock);
        }

        @Test
        @DisplayName("создание заявки на тест-драйв")
        void shouldCreateRequest() {
            String carId = UUID.randomUUID().toString();
            LocalDateTime scheduledAt = LocalDateTime.now(clock).plusHours(1);
            when(requestRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(carGateway.get(carId)).thenReturn(new TestDriveCarGateway.TestDriveCar(carId, true, true));
            TestDriveRequest req = testDriveService.createRequest("cl", carId, scheduledAt);
            assertNotNull(req.getId());
            assertEquals(TestDriveStatus.PENDING, req.getStatus());
        }

        @Test
        @DisplayName("прошедшее и текущее время отклоняются без RPC")
        void shouldRejectPastAndCurrentTimeBeforeRpc() {
            assertThrows(DomainValidationException.class, () -> testDriveService.createRequest(
                    "cl", UUID.randomUUID().toString(), LocalDateTime.now(clock).minusSeconds(1)));
            assertThrows(DomainValidationException.class, () -> testDriveService.createRequest(
                    "cl", UUID.randomUUID().toString(), LocalDateTime.now(clock)));
            verifyNoInteractions(carGateway, requestRepository);
        }

        @Test
        @DisplayName("время повторно проверяется после RPC")
        void shouldRecheckTimeAfterRpc() {
            String carId = UUID.randomUUID().toString();
            LocalDateTime scheduledAt = LocalDateTime.now(clock).plusMinutes(1);
            when(carGateway.get(carId)).thenAnswer(invocation -> {
                clock.advance(java.time.Duration.ofMinutes(2));
                return new TestDriveCarGateway.TestDriveCar(carId, true, true);
            });

            assertThrows(DomainValidationException.class,
                    () -> testDriveService.createRequest("cl", carId, scheduledAt));
            verify(requestRepository, never()).save(any());
        }

        @Test
        @DisplayName("недоступный или неразрешённый для тест-драйва автомобиль даёт конфликт")
        void shouldRejectUnavailableCar() {
            String carId = UUID.randomUUID().toString();
            LocalDateTime scheduledAt = LocalDateTime.now(clock).plusHours(1);
            when(carGateway.get(carId)).thenReturn(
                    new TestDriveCarGateway.TestDriveCar(carId, true, false),
                    new TestDriveCarGateway.TestDriveCar(carId, false, true));

            assertThrows(dealership.order.core.domain.exception.TestDriveConflictException.class,
                    () -> testDriveService.createRequest("cl", carId, scheduledAt));
            assertThrows(dealership.order.core.domain.exception.TestDriveConflictException.class,
                    () -> testDriveService.createRequest("cl", carId, scheduledAt));
            verify(requestRepository, never()).save(any());
        }

        @Test
        @DisplayName("получение всех заявок")
        void shouldGetAll() {
            when(requestRepository.findAll()).thenReturn(List.of());
            assertTrue(testDriveService.getAllRequests().isEmpty());
        }

        @Test
        @DisplayName("получение заявок по ID авто")
        void shouldGetByCarId() {
            when(requestRepository.findByCarId("car1")).thenReturn(List.of());
            assertTrue(testDriveService.getRequestsByCarId("car1").isEmpty());
        }

        @Test
        @DisplayName("личный список фильтруется по subject")
        void shouldGetRequestsForClient() {
            when(requestRepository.findByClientId("client-1")).thenReturn(List.of());

            assertTrue(testDriveService.getClientRequests("client-1").isEmpty());

            verify(requestRepository).findByClientId("client-1");
            verify(requestRepository, never()).findAll();
        }

        @Test
        @DisplayName("PENDING можно одобрить только до начала")
        void shouldApprovePendingRequestBeforeStart() {
            TestDriveRequest request = requestAt(
                    LocalDateTime.now(clock).plusMinutes(1), TestDriveStatus.PENDING);
            when(requestRepository.findByIdForUpdate(request.getId())).thenReturn(request);
            when(requestRepository.save(request)).thenReturn(request);

            assertEquals(TestDriveStatus.APPROVED,
                    testDriveService.approveRequest(request.getId()).getStatus());

            verify(requestRepository).findByIdForUpdate(request.getId());
            verify(requestRepository).save(request);
        }

        @Test
        @DisplayName("одобрение на старте отклоняется, повтор APPROVED идемпотентен")
        void shouldRejectLateApprovalAndKeepApprovalIdempotent() {
            TestDriveRequest pending = requestAt(LocalDateTime.now(clock), TestDriveStatus.PENDING);
            when(requestRepository.findByIdForUpdate(pending.getId())).thenReturn(pending);

            assertThrows(TestDriveConflictException.class,
                    () -> testDriveService.approveRequest(pending.getId()));

            TestDriveRequest approved = requestAt(
                    LocalDateTime.now(clock).minusHours(1), TestDriveStatus.APPROVED);
            when(requestRepository.findByIdForUpdate(approved.getId())).thenReturn(approved);
            assertSame(approved, testDriveService.approveRequest(approved.getId()));
            verify(requestRepository, never()).save(any());
        }

        @Test
        @DisplayName("APPROVED завершается с конца часового слота")
        void shouldCompleteApprovedRequestAtSlotEnd() {
            TestDriveRequest request = requestAt(
                    LocalDateTime.now(clock).minusHours(1), TestDriveStatus.APPROVED);
            when(requestRepository.findByIdForUpdate(request.getId())).thenReturn(request);
            when(requestRepository.save(request)).thenReturn(request);

            assertEquals(TestDriveStatus.COMPLETED,
                    testDriveService.completeRequest(request.getId()).getStatus());
        }

        @Test
        @DisplayName("завершение до конца слота отклоняется")
        void shouldRejectEarlyCompletion() {
            TestDriveRequest request = requestAt(
                    LocalDateTime.now(clock).minusMinutes(59), TestDriveStatus.APPROVED);
            when(requestRepository.findByIdForUpdate(request.getId())).thenReturn(request);

            assertThrows(TestDriveConflictException.class,
                    () -> testDriveService.completeRequest(request.getId()));
            verify(requestRepository, never()).save(any());
        }

        @Test
        @DisplayName("отмена проверяет владельца после блокировки; сотрудник может отменить чужую")
        void shouldAuthorizeCancellationOnLockedRequest() {
            TestDriveRequest foreign = requestAt(
                    LocalDateTime.now(clock).minusDays(1), TestDriveStatus.PENDING);
            when(requestRepository.findByIdForUpdate(foreign.getId())).thenReturn(foreign);

            assertThrows(AccessDeniedException.class,
                    () -> testDriveService.cancelRequest(foreign.getId(), "other-client", false));
            assertEquals(TestDriveStatus.PENDING, foreign.getStatus());
            verify(requestRepository, never()).save(any());

            when(requestRepository.save(foreign)).thenReturn(foreign);
            assertEquals(TestDriveStatus.CANCELLED,
                    testDriveService.cancelRequest(foreign.getId(), "manager", true).getStatus());
        }

        private TestDriveRequest requestAt(LocalDateTime scheduledAt, TestDriveStatus status) {
            return new TestDriveRequest(
                    UUID.randomUUID().toString(), "owner", UUID.randomUUID().toString(),
                    scheduledAt, status);
        }

        private static final class MutableClock extends Clock {
            private final AtomicReference<Instant> instant;
            private final ZoneId zone;

            private MutableClock(Instant instant, ZoneId zone) {
                this.instant = new AtomicReference<>(instant);
                this.zone = zone;
            }

            void advance(java.time.Duration duration) {
                instant.updateAndGet(value -> value.plus(duration));
            }

            @Override
            public ZoneId getZone() {
                return zone;
            }

            @Override
            public Clock withZone(ZoneId zone) {
                return new MutableClock(instant.get(), zone);
            }

            @Override
            public Instant instant() {
                return instant.get();
            }
        }
    }
}
