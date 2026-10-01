package dealership.storage;

import dealership.storage.core.domain.entity.assembly.AssemblyOrder;
import dealership.storage.core.domain.entity.car.Car;
import dealership.storage.core.domain.entity.car.CarModel;
import dealership.storage.core.domain.entity.component.ComponentVariant;
import dealership.storage.core.domain.entity.part.SparePart;
import dealership.storage.core.domain.enums.*;
import dealership.storage.core.domain.exception.DomainValidationException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Domain Entities - StorageService")
class DomainEntityTest {

    @Nested
    @DisplayName("Car")
    class CarTests {

        @Test
        @DisplayName("создание с валидными данными и бизнес-методы")
        void shouldCreateAndModify() {
            Car car = new Car("BMW", "320i", BodyType.SEDAN, FuelType.PETROL,
                    184, 2.0, TransmissionType.AUTOMATIC, DriveType.REAR,
                    Color.BLACK, new BigDecimal("3000000"));
            assertNotNull(car.getId());
            assertTrue(car.isAvailable());
            assertFalse(car.isAvailableForTestDrive());
            car.markAsUnavailable();
            assertFalse(car.isAvailable());
            car.markForTestDrive();
            assertTrue(car.isAvailableForTestDrive());
        }

        @Test
        @DisplayName("null/blank бренд и отрицательная цена")
        void shouldRejectInvalid() {
            assertThrows(DomainValidationException.class, () -> new Car(
                    null, "320i", BodyType.SEDAN, FuelType.PETROL, 184, 2.0,
                    TransmissionType.AUTOMATIC, DriveType.REAR, Color.BLACK, new BigDecimal("3000000")));
            assertThrows(DomainValidationException.class, () -> new Car(
                    "BMW", "320i", BodyType.SEDAN, FuelType.PETROL, 184, 2.0,
                    TransmissionType.AUTOMATIC, DriveType.REAR, Color.BLACK, new BigDecimal("-1")));
        }
    }

    @Nested
    @DisplayName("CarModel")
    class CarModelTests {

        @Test
        @DisplayName("создание и категории")
        void shouldCreateAndManageCategories() {
            CarModel model = new CarModel("BMW", "320i", new BigDecimal("3000000"));
            assertEquals("BMW.320i", model.getFullModelName());
            model.addComponentCategory("wheels");
            assertEquals(1, model.getComponentCategoryIds().size());
        }

        @Test
        @DisplayName("валидация null полей")
        void shouldRejectInvalid() {
            assertThrows(DomainValidationException.class, () -> new CarModel(null, "320i", new BigDecimal("1")));
            assertThrows(DomainValidationException.class, () -> new CarModel("BMW", null, new BigDecimal("1")));
            assertThrows(DomainValidationException.class, () -> new CarModel("BMW", "320i", null));
        }
    }

    @Nested
    @DisplayName("ComponentVariant")
    class ComponentVariantTests {

        @Test
        @DisplayName("создание и совместимость")
        void shouldCreateAndCheckCompatibility() {
            ComponentVariant v = new ComponentVariant("v1", "19'' M-Sport", "wheels", new BigDecimal("95000"), false);
            v.addCompatibleCarModel("bmw-320i");
            assertTrue(v.isCompatibleWith("bmw-320i"));
            assertFalse(v.isCompatibleWith("audi-a4"));
        }
    }

    @Nested
    @DisplayName("SparePart")
    class SparePartTests {

        @Test
        @DisplayName("создание и управление количеством")
        void shouldCreateAndManageQuantity() {
            SparePart part = new SparePart("Фильтр", "MANN", "HU816x", new BigDecimal("850"), 50, "BMW 320i,BMW 330i");
            assertEquals(50, part.getQuantityInStock());
            part.decreaseQuantity(10);
            assertEquals(40, part.getQuantityInStock());
            part.increaseQuantity(5);
            assertEquals(45, part.getQuantityInStock());
        }

        @Test
        @DisplayName("нельзя уменьшить ниже нуля")
        void shouldRejectOverDecrease() {
            SparePart part = new SparePart("Фильтр", "MANN", "HU816x", new BigDecimal("850"), 5, null);
            assertThrows(DomainValidationException.class, () -> part.decreaseQuantity(10));
        }
    }

    @Nested
    @DisplayName("AssemblyOrder")
    class AssemblyOrderTests {

        @Test
        @DisplayName("создание заказа на сборку")
        void shouldCreate() {
            AssemblyOrder order = new AssemblyOrder("order-1", "STOCK", "trace-123");
            assertNotNull(order.getId());
            assertEquals("order-1", order.getSourceOrderId());
            assertEquals(AssemblyStatus.CREATED, order.getStatus());
        }

        @Test
        @DisplayName("переход CREATED -> ASSEMBLED")
        void shouldMarkAssembled() {
            AssemblyOrder order = new AssemblyOrder("order-1", "STOCK", "trace-123");
            order.markAssembled();
            assertEquals(AssemblyStatus.ASSEMBLED, order.getStatus());
        }

        @Test
        @DisplayName("переход CREATED -> FAIL")
        void shouldMarkFailed() {
            AssemblyOrder order = new AssemblyOrder("order-1", "STOCK", "trace-123");
            order.markFailed();
            assertEquals(AssemblyStatus.FAIL, order.getStatus());
        }

        @Test
        @DisplayName("валидация: null sourceOrderId и orderType")
        void shouldRejectInvalid() {
            assertThrows(DomainValidationException.class, () -> new AssemblyOrder(null, "STOCK", "trace"));
            assertThrows(DomainValidationException.class, () -> new AssemblyOrder("", "STOCK", "trace"));
            assertThrows(DomainValidationException.class, () -> new AssemblyOrder("order-1", null, "trace"));
        }
    }
}
