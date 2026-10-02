package dealership.order;

import dealership.order.core.domain.entity.car.CarConfiguration;
import dealership.order.core.domain.entity.order.CustomOrder;
import dealership.order.core.domain.entity.order.StockOrder;
import dealership.order.core.domain.entity.testdrive.TestDriveRequest;
import dealership.order.core.domain.enums.CustomOrderStatus;
import dealership.order.core.domain.enums.StockOrderStatus;
import dealership.order.core.domain.enums.TestDriveStatus;
import dealership.order.core.domain.exception.DomainValidationException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Domain Entities - OrderService")
class DomainEntityTest {

    @Nested
    @DisplayName("StockOrder — State-паттерн")
    class StockOrderTests {

        @Test
        @DisplayName("создание и валидация null полей")
        void shouldCreateAndRejectInvalid() {
            StockOrder order = new StockOrder("cl", "m", "c");
            assertNotNull(order.getId());
            assertEquals(StockOrderStatus.CREATED, order.getStatus());

            assertThrows(DomainValidationException.class, () -> new StockOrder(null, "m", "c"));
            assertThrows(DomainValidationException.class, () -> new StockOrder("cl", null, "c"));
            assertThrows(DomainValidationException.class, () -> new StockOrder("cl", "m", null));
        }

        @Test
        @DisplayName("полный жизненный цикл CREATED → COMPLETED")
        void shouldAdvanceThroughLifecycle() {
            StockOrder order = new StockOrder("cl", "m", "c");
            order.advanceStatus();
            assertEquals(StockOrderStatus.APPROVED_BY_MANAGER, order.getStatus());
            order.advanceStatus();
            assertEquals(StockOrderStatus.AWAITING_PAYMENT, order.getStatus());
            order.advanceStatus();
            assertEquals(StockOrderStatus.PAID, order.getStatus());
            order.advanceStatus();
            assertEquals(StockOrderStatus.READY_FOR_PICKUP, order.getStatus());
            order.advanceStatus();
            assertEquals(StockOrderStatus.COMPLETED, order.getStatus());
            assertFalse(order.canAdvance());
        }

        @Test
        @DisplayName("отмена из CREATED и PAID")
        void shouldCancelFromAllowedStates() {
            StockOrder o1 = new StockOrder("cl", "m", "c");
            o1.cancel();
            assertEquals(StockOrderStatus.CANCELLED, o1.getStatus());

            StockOrder o2 = new StockOrder("cl", "m", "c");
            o2.advanceStatus();
            o2.advanceStatus();
            o2.advanceStatus();
            o2.cancel();
            assertEquals(StockOrderStatus.CANCELLED, o2.getStatus());
        }

        @Test
        @DisplayName("запрет отмены из READY_FOR_PICKUP, COMPLETED, CANCELLED")
        void shouldRejectCancelFromTerminalStates() {
            StockOrder o1 = new StockOrder("cl", "m", "c");
            for (int i = 0; i < 4; i++) o1.advanceStatus();
            assertFalse(o1.canCancel());
            assertThrows(DomainValidationException.class, o1::cancel);
        }
    }

    @Nested
    @DisplayName("CustomOrder")
    class CustomOrderTests {

        @Test
        @DisplayName("создание с валидными данными")
        void shouldCreate() {
            CarConfiguration config = new CarConfiguration("model-1");
            config.selectVariant("cat-1", "var-1");
            CustomOrder order = new CustomOrder("cl", "m", "model-1", config, new BigDecimal("5000000"));
            assertNotNull(order.getId());
            assertEquals(CustomOrderStatus.CREATED, order.getStatus());
            assertEquals("cl", order.getClientId());
            assertEquals("m", order.getManagerId());
        }

        @Test
        @DisplayName("legacy zero price rehydrates, but model mismatch is rejected")
        void shouldPreserveLegacyZeroPriceAndModelInvariant() {
            CarConfiguration config = new CarConfiguration("model-1");
            CustomOrder legacy = new CustomOrder(
                    "id", "manager", "client", "model-1", config, BigDecimal.ZERO);
            assertEquals(BigDecimal.ZERO, legacy.getTotalPrice());
            assertEquals("client", legacy.getClientId());
            assertEquals("manager", legacy.getManagerId());

            assertThrows(DomainValidationException.class, () -> new CustomOrder(
                    "client", "manager", "model-2", config, BigDecimal.ONE));
        }

        @Test
        @DisplayName("полный жизненный цикл CREATED → COMPLETED")
        void shouldAdvanceThroughLifecycle() {
            CarConfiguration config = new CarConfiguration("model-1");
            config.selectVariant("cat-1", "var-1");
            CustomOrder order = new CustomOrder("cl", "m", "model-1", config, new BigDecimal("5000000"));

            order.advanceStatus(null);
            assertEquals(CustomOrderStatus.APPROVED_BY_WAREHOUSE, order.getStatus());
            order.advanceStatus(null);
            assertEquals(CustomOrderStatus.AWAITING_PAYMENT, order.getStatus());
            order.advanceStatus(null);
            assertEquals(CustomOrderStatus.PAID, order.getStatus());
            order.advanceStatus(null);
            assertEquals(CustomOrderStatus.AWAITING_DELIVERY, order.getStatus());
            order.advanceStatus(null);
            assertEquals(CustomOrderStatus.READY_FOR_PICKUP, order.getStatus());
            order.advanceStatus(null);
            assertEquals(CustomOrderStatus.COMPLETED, order.getStatus());
            assertFalse(order.canAdvance());
        }

        @Test
        @DisplayName("валидация: null клиент, null конфигурация, отрицательная цена")
        void shouldRejectInvalid() {
            CarConfiguration config = new CarConfiguration("model-1");
            assertThrows(DomainValidationException.class, () ->
                    new CustomOrder(null, "m", "model-1", config, new BigDecimal("5000000")));
            assertThrows(DomainValidationException.class, () ->
                    new CustomOrder("cl", "m", "model-1", null, new BigDecimal("5000000")));
            assertThrows(DomainValidationException.class, () ->
                    new CustomOrder("cl", "m", "model-1", config, new BigDecimal("-1")));
        }
    }

    @Nested
    @DisplayName("TestDriveRequest")
    class TestDriveRequestTests {

        @Test
        @DisplayName("создание и смена статусов")
        void shouldCreateAndChangeStatus() {
            TestDriveRequest req = new TestDriveRequest("client1", "car1", LocalDateTime.now().plusDays(1));
            assertNotNull(req.getId());
            assertEquals(TestDriveStatus.PENDING, req.getStatus());
            req.approve();
            assertEquals(TestDriveStatus.APPROVED, req.getStatus());
            req.complete();
            assertEquals(TestDriveStatus.COMPLETED, req.getStatus());
        }

        @Test
        @DisplayName("отмена заявки")
        void shouldCancel() {
            TestDriveRequest req = new TestDriveRequest("client1", "car1", LocalDateTime.now().plusDays(1));
            req.cancel();
            assertEquals(TestDriveStatus.CANCELLED, req.getStatus());
        }

        @Test
        @DisplayName("валидация: null clientId, carId, dateTime")
        void shouldRejectInvalid() {
            assertThrows(DomainValidationException.class, () -> new TestDriveRequest(null, "car1", LocalDateTime.now()));
            assertThrows(DomainValidationException.class, () -> new TestDriveRequest("client1", null, LocalDateTime.now()));
            assertThrows(DomainValidationException.class, () -> new TestDriveRequest("client1", "car1", null));
        }
    }
}
