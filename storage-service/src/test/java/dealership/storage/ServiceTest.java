package dealership.storage;

import dealership.storage.core.application.port.out.AssemblyOrderRepository;
import dealership.storage.core.application.port.out.CarRepository;
import dealership.storage.core.application.port.out.SparePartRepository;
import dealership.storage.core.application.service.AssemblyOrderService;
import dealership.storage.core.application.service.CarSearchService;
import dealership.storage.core.application.service.InventoryService;
import dealership.storage.core.domain.entity.assembly.AssemblyOrder;
import dealership.storage.core.domain.entity.car.Car;
import dealership.storage.core.domain.entity.part.SparePart;
import dealership.storage.core.domain.enums.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("Сервисы StorageService")
class ServiceTest {

    @Nested
    @DisplayName("AssemblyOrderService")
    @ExtendWith(MockitoExtension.class)
    class AssemblyOrderServiceTests {

        @Mock
        private AssemblyOrderRepository repository;
        @InjectMocks
        private AssemblyOrderService service;

        @Test
        @DisplayName("создание заказа на сборку")
        void shouldCreate() {
            AssemblyOrder order = new AssemblyOrder("order-1", "STOCK", "trace-1");
            when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            AssemblyOrder saved = service.create(order);
            assertEquals("order-1", saved.getSourceOrderId());
            verify(repository).save(any());
        }

        @Test
        @DisplayName("получение по ID")
        void shouldGetById() {
            AssemblyOrder order = new AssemblyOrder("order-1", "STOCK", "trace-1");
            when(repository.findById(order.getId())).thenReturn(order);
            assertEquals("order-1", service.getById(order.getId()).getSourceOrderId());
        }

        @Test
        @DisplayName("получение всех")
        void shouldGetAll() {
            when(repository.findAll()).thenReturn(List.of());
            assertTrue(service.getAll().isEmpty());
        }

        @Test
        @DisplayName("удаление")
        void shouldDelete() {
            service.delete("id-1");
            verify(repository).deleteById("id-1");
        }

        @Test
        @DisplayName("проверка существования по sourceOrderId")
        void shouldCheckExists() {
            when(repository.existsBySourceOrderId("order-1")).thenReturn(true);
            when(repository.existsBySourceOrderId("order-999")).thenReturn(false);
            assertTrue(service.existsBySourceOrderId("order-1"));
            assertFalse(service.existsBySourceOrderId("order-999"));
        }
    }

    @Nested
    @DisplayName("InventoryService")
    @ExtendWith(MockitoExtension.class)
    class InventoryServiceTests {

        @Mock
        private CarRepository carRepository;
        @Mock
        private SparePartRepository sparePartRepository;
        @InjectMocks
        private InventoryService inventoryService;

        private Car testCar;

        @BeforeEach
        void setUp() {
            testCar = new Car("BMW", "320i", BodyType.SEDAN, FuelType.PETROL,
                    184, 2.0, TransmissionType.AUTOMATIC, DriveType.REAR,
                    Color.BLACK, new BigDecimal("3000000"));
        }

        @Test
        @DisplayName("добавление автомобиля")
        void shouldAddCar() {
            when(carRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            Car result = inventoryService.addCar(testCar);
            assertEquals("BMW", result.getBrand());
        }

        @Test
        @DisplayName("включение/отключение тест-драйва")
        void shouldToggleTestDrive() {
            when(carRepository.findById(testCar.getId())).thenReturn(testCar);
            when(carRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            inventoryService.markForTestDrive(testCar.getId());
            assertTrue(testCar.isAvailableForTestDrive());
            inventoryService.removeFromTestDrive(testCar.getId());
            assertFalse(testCar.isAvailableForTestDrive());
        }

        @Test
        @DisplayName("добавление и получение запчастей")
        void shouldManageParts() {
            SparePart part = new SparePart("Фильтр", "MANN", "HU816x", new BigDecimal("850"), 50, "BMW 320i");
            when(sparePartRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(sparePartRepository.findAll()).thenReturn(List.of(part));
            assertEquals("Фильтр", inventoryService.addPart(part).getName());
            assertEquals(1, inventoryService.getAllParts().size());
        }
    }

    @Nested
    @DisplayName("CarSearchService")
    @ExtendWith(MockitoExtension.class)
    class CarSearchServiceTests {

        @Mock
        private CarRepository carRepository;
        @InjectMocks
        private CarSearchService searchService;

        @Test
        @DisplayName("поиск доступных автомобилей")
        void shouldFindAvailable() {
            Car car = new Car("BMW", "320i", BodyType.SEDAN, FuelType.PETROL,
                    184, 2.0, TransmissionType.AUTOMATIC, DriveType.REAR,
                    Color.BLACK, new BigDecimal("3000000"));
            when(carRepository.findAvailable()).thenReturn(List.of(car));
            assertEquals(1, searchService.findAvailableCars().size());
        }

        @Test
        @DisplayName("тест-драйв: найден с флагом")
        void shouldFindForTestDrive() {
            Car car = new Car("BMW", "320i", BodyType.SEDAN, FuelType.PETROL,
                    184, 2.0, TransmissionType.AUTOMATIC, DriveType.REAR,
                    Color.BLACK, new BigDecimal("3000000"));
            car.markForTestDrive();
            when(carRepository.findAll()).thenReturn(List.of(car));
            assertEquals(1, searchService.findCarsForTestDrive().size());
        }
    }
}
