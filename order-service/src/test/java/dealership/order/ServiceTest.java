package dealership.order;

import dealership.order.core.application.port.out.CustomOrderRepository;
import dealership.order.core.application.port.out.StockOrderRepository;
import dealership.order.core.application.port.out.StockCarReservationGateway;
import dealership.order.core.application.port.out.TestDriveRequestRepository;
import dealership.order.core.application.port.out.UserRepository;
import dealership.order.core.application.service.CustomOrderService;
import dealership.order.core.application.service.StockOrderService;
import dealership.order.core.application.service.StockOrderTransactions;
import dealership.order.core.application.service.TestDriveService;
import dealership.order.core.domain.entity.car.CarConfiguration;
import dealership.order.core.domain.entity.order.CustomOrder;
import dealership.order.core.domain.entity.order.StockOrder;
import dealership.order.core.domain.entity.testdrive.TestDriveRequest;
import dealership.order.core.domain.entity.user.User;
import dealership.order.core.domain.enums.CustomOrderStatus;
import dealership.order.core.domain.enums.StockOrderStatus;
import dealership.order.core.domain.enums.TestDriveStatus;
import dealership.order.core.domain.enums.UserRole;
import dealership.order.core.domain.exception.DomainValidationException;
import dealership.order.infrastructure.messaging.OrderEventProducer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

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
            when(userRepository.findByRole(UserRole.MANAGER)).thenReturn(List.of(testManager));
            when(transactions.saveNew(any(StockOrder.class))).thenAnswer(inv -> inv.getArgument(0));

            StockOrder order = orderService.createOrder("client-1", "car-1");
            assertNotNull(order.getId());
            assertEquals("client-1", order.getClientId());
            assertEquals(StockOrderStatus.CREATED, order.getStatus());
            InOrder inOrder = inOrder(reservationGateway, transactions);
            inOrder.verify(reservationGateway).reserve("car-1", order.getId());
            inOrder.verify(transactions).saveNew(any());
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
            when(transactions.cancel(order.getId())).thenReturn(order);

            StockOrder cancelled = orderService.cancelOrder(order.getId());
            assertEquals(StockOrderStatus.CANCELLED, cancelled.getStatus());
            InOrder inOrder = inOrder(transactions, reservationGateway);
            inOrder.verify(transactions).cancel(order.getId());
            inOrder.verify(reservationGateway).release(order.getCarId(), order.getId());
        }

        @Test
        @DisplayName("advance делегируется локальной транзакции")
        void shouldAdvanceInLocalTransaction() {
            StockOrder order = new StockOrder("cl", "m", "car");
            order.advanceStatus();
            order.advanceStatus();
            order.advanceStatus();

            when(transactions.advance(order.getId())).thenReturn(order);

            StockOrder advanced = orderService.advanceOrder(order.getId());
            assertEquals(StockOrderStatus.PAID, advanced.getStatus());
            verify(transactions).advance(order.getId());
        }

        @Test
        @DisplayName("ошибка локального сохранения компенсирует резерв")
        void shouldReleaseReservationWhenSaveFails() {
            when(userRepository.findByRole(UserRole.MANAGER)).thenReturn(List.of(testManager));
            when(transactions.saveNew(any())).thenThrow(new RuntimeException("db down"));

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
            doThrow(conflict).when(reservationGateway).reserve(eq("car"), anyString());

            assertSame(conflict, assertThrows(RuntimeException.class,
                    () -> orderService.createOrder("cl", "car")));
            verify(transactions, never()).saveNew(any());
        }

        @Test
        @DisplayName("ошибка компенсации не скрывает исходную ошибку")
        void shouldPreserveOriginalFailureWhenCleanupFails() {
            when(userRepository.findByRole(UserRole.MANAGER)).thenReturn(List.of(testManager));
            RuntimeException original = new RuntimeException("reserve uncertain");
            RuntimeException cleanup = new RuntimeException("cleanup failed");
            doThrow(original).when(reservationGateway).reserve(eq("car"), anyString());
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
            when(transactions.cancel(cancelled.getId())).thenReturn(cancelled);

            orderService.cancelOrder(cancelled.getId());
            orderService.cancelOrder(cancelled.getId());

            verify(reservationGateway, times(2)).release(cancelled.getCarId(), cancelled.getId());
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
        @InjectMocks
        private StockOrderTransactions transactions;

        @Test
        @DisplayName("advance блокирует строку и пишет outbox при переходе в PAID")
        void shouldLockAndPublishWhenAdvancingToPaid() {
            StockOrder order = new StockOrder("cl", "m", "car");
            order.advanceStatus();
            order.advanceStatus();
            when(orderRepository.findByIdForUpdate(order.getId())).thenReturn(order);
            when(orderRepository.save(order)).thenReturn(order);

            StockOrder saved = transactions.advance(order.getId());

            assertEquals(StockOrderStatus.PAID, saved.getStatus());
            verify(orderRepository).findByIdForUpdate(order.getId());
            verify(eventProducer).publishOrderPaid(any());
        }

        @Test
        @DisplayName("advance до промежуточного статуса не пишет outbox")
        void shouldNotPublishBeforePaid() {
            StockOrder order = new StockOrder("cl", "m", "car");
            when(orderRepository.findByIdForUpdate(order.getId())).thenReturn(order);
            when(orderRepository.save(order)).thenReturn(order);

            transactions.advance(order.getId());

            verify(eventProducer, never()).publishOrderPaid(any());
        }

        @Test
        @DisplayName("повторная отмена блокирует строку и не перезаписывает состояние")
        void shouldLockButNotRewriteAlreadyCancelledOrder() {
            StockOrder order = new StockOrder("cl", "m", "car");
            order.cancel();
            when(orderRepository.findByIdForUpdate(order.getId())).thenReturn(order);

            StockOrder result = transactions.cancel(order.getId());

            assertSame(order, result);
            verify(orderRepository).findByIdForUpdate(order.getId());
            verify(orderRepository, never()).save(any());
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
            CarConfiguration config = new CarConfiguration("model-1");
            config.selectVariant("cat-1", "var-1");
            when(userRepository.findByRole(UserRole.MANAGER)).thenReturn(List.of(testManager));
            when(orderRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            CustomOrder order = customOrderService.createOrder("cl", "model-1", config, new BigDecimal("3500000"));
            assertNotNull(order.getId());
            assertEquals(CustomOrderStatus.CREATED, order.getStatus());
        }

        @Test
        @DisplayName("advance до PAID публикует событие в outbox")
        void shouldPublishEventOnPaid() {
            CarConfiguration config = new CarConfiguration("model-1");
            config.selectVariant("cat-1", "var-1");
            CustomOrder order = new CustomOrder("mgr", "cl", "model-1", config, new BigDecimal("3500000"));
            order.advanceStatus(null);
            order.advanceStatus(null);

            when(orderRepository.findById(order.getId())).thenReturn(order);
            when(orderRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            CustomOrder advanced = customOrderService.advanceOrder(order.getId());
            assertEquals(CustomOrderStatus.PAID, advanced.getStatus());
            verify(eventProducer).publishOrderPaid(any());
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
        @InjectMocks
        private TestDriveService testDriveService;

        @Test
        @DisplayName("создание заявки на тест-драйв")
        void shouldCreateRequest() {
            when(requestRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            TestDriveRequest req = testDriveService.createRequest("cl", "car", LocalDateTime.now().plusDays(1));
            assertNotNull(req.getId());
            assertEquals(TestDriveStatus.PENDING, req.getStatus());
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
    }
}
