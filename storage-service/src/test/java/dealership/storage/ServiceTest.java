package dealership.storage;

import dealership.storage.core.application.port.out.AssemblyOrderRepository;
import dealership.storage.core.application.port.out.CarRepository;
import dealership.storage.core.application.port.out.CarModelRepository;
import dealership.storage.core.application.port.out.ComponentCategoryRepository;
import dealership.storage.core.application.port.out.ComponentVariantRepository;
import dealership.storage.core.application.port.out.SparePartRepository;
import dealership.storage.core.application.service.AssemblyOrderService;
import dealership.storage.core.application.service.CarConfigurationService;
import dealership.storage.core.application.service.CarSearchService;
import dealership.storage.core.application.service.InventoryService;
import dealership.storage.core.application.dto.CarConfigurationRequestDto;
import dealership.storage.core.domain.entity.assembly.AssemblyOrder;
import dealership.storage.core.domain.entity.car.Car;
import dealership.storage.core.domain.entity.car.CarModel;
import dealership.storage.core.domain.entity.component.ComponentCategory;
import dealership.storage.core.domain.entity.component.ComponentVariant;
import dealership.storage.core.domain.entity.part.SparePart;
import dealership.storage.core.domain.enums.*;
import dealership.storage.core.domain.exception.DomainValidationException;
import dealership.storage.core.domain.exception.EntityNotFoundException;
import dealership.storage.core.domain.exception.IncompatibleComponentException;
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
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@DisplayName("Сервисы StorageService")
class ServiceTest {

    @Nested
    @DisplayName("CarConfigurationService")
    @ExtendWith(MockitoExtension.class)
    class CarConfigurationServiceTests {

        @Mock
        private CarModelRepository modelRepository;
        @Mock
        private ComponentVariantRepository variantRepository;
        @Mock
        private ComponentCategoryRepository categoryRepository;
        @InjectMocks
        private CarConfigurationService service;

        private String modelId;
        private String categoryId;
        private String variantId;
        private CarModel model;
        private ComponentVariant variant;

        @BeforeEach
        void setUp() {
            modelId = UUID.randomUUID().toString();
            categoryId = UUID.randomUUID().toString();
            variantId = UUID.randomUUID().toString();
            model = new CarModel(modelId, "BMW", "320i", new BigDecimal("3000000.10"));
            model.addComponentCategory(categoryId);
            variant = new ComponentVariant(
                    variantId, "Sport", categoryId, new BigDecimal("499999.90"), false);
            variant.addCompatibleCarModel(modelId);
        }

        @Test
        @DisplayName("считает точную цену и возвращает канонический snapshot")
        void shouldQuoteExactDecimalAndCanonicalSelection() {
            stubValidConfiguration();
            CarConfigurationRequestDto request = request(
                    modelId.toUpperCase(), Map.of(categoryId.toUpperCase(), variantId.toUpperCase()));

            var result = service.configure(request);

            assertEquals(new BigDecimal("3500000.00"), result.getTotalPrice());
            assertEquals(modelId, result.getConfiguration().getCarModelId());
            assertEquals(Map.of(categoryId, variantId), result.getConfiguration().getSelectedVariants());
        }

        @Test
        @DisplayName("требует точный набор категорий без пропусков и лишних ключей")
        void shouldRejectMissingAndExtraCategories() {
            when(modelRepository.findById(modelId)).thenReturn(model);
            when(categoryRepository.findById(categoryId))
                    .thenReturn(new ComponentCategory(categoryId, "Wheels", null));

            assertThrows(DomainValidationException.class,
                    () -> service.configure(request(modelId, Map.of())));

            String extraCategory = UUID.randomUUID().toString();
            assertThrows(DomainValidationException.class, () -> service.configure(request(modelId,
                    Map.of(categoryId, variantId, extraCategory, UUID.randomUUID().toString()))));
        }

        @Test
        @DisplayName("отклоняет вариант из другой категории")
        void shouldRejectWrongCategory() {
            stubRequiredCategory();
            ComponentVariant wrong = new ComponentVariant(
                    variantId, "Wrong", UUID.randomUUID().toString(), BigDecimal.ZERO, false);
            wrong.addCompatibleCarModel(modelId);
            when(variantRepository.findById(variantId)).thenReturn(wrong);

            assertThrows(DomainValidationException.class,
                    () -> service.configure(request(modelId, Map.of(categoryId, variantId))));
        }

        @Test
        @DisplayName("отклоняет несовместимый вариант")
        void shouldRejectIncompatibleVariant() {
            stubRequiredCategory();
            when(variantRepository.findById(variantId)).thenReturn(new ComponentVariant(
                    variantId, "Foreign", categoryId, BigDecimal.ZERO, false));

            assertThrows(IncompatibleComponentException.class,
                    () -> service.configure(request(modelId, Map.of(categoryId, variantId))));
        }

        @Test
        @DisplayName("UUID проверяются до обращения к репозиториям")
        void shouldRejectInvalidUuidBeforeRepositories() {
            assertThrows(DomainValidationException.class,
                    () -> service.configure(request("not-a-uuid", Map.of())));
            verifyNoInteractions(modelRepository, variantRepository, categoryRepository);
        }

        @Test
        @DisplayName("неизвестная модель или компонент не маскируются")
        void shouldPropagateUnknownIds() {
            when(modelRepository.findById(modelId)).thenThrow(new EntityNotFoundException("missing"));
            assertThrows(EntityNotFoundException.class,
                    () -> service.configure(request(modelId, Map.of(categoryId, variantId))));
        }

        @Test
        @DisplayName("цена должна быть положительной DECIMAL(15,2) без округления")
        void shouldRejectInvalidMoney() {
            model = new CarModel(modelId, "BMW", "320i", new BigDecimal("1.001"));
            model.addComponentCategory(categoryId);
            variant = new ComponentVariant(variantId, "Base", categoryId, BigDecimal.ZERO, true);
            variant.addCompatibleCarModel(modelId);
            stubValidConfiguration();
            assertThrows(DomainValidationException.class,
                    () -> service.configure(request(modelId, Map.of(categoryId, variantId))));

            model = new CarModel(modelId, "BMW", "320i", BigDecimal.ZERO);
            model.addComponentCategory(categoryId);
            when(modelRepository.findById(modelId)).thenReturn(model);
            assertThrows(DomainValidationException.class,
                    () -> service.configure(request(modelId, Map.of(categoryId, variantId))));
        }

        private void stubValidConfiguration() {
            stubRequiredCategory();
            when(variantRepository.findById(variantId)).thenReturn(variant);
        }

        private void stubRequiredCategory() {
            when(modelRepository.findById(modelId)).thenReturn(model);
            when(categoryRepository.findById(categoryId))
                    .thenReturn(new ComponentCategory(categoryId, "Wheels", null));
        }

        private CarConfigurationRequestDto request(String requestedModelId,
                                                   Map<String, String> selection) {
            CarConfigurationRequestDto request = new CarConfigurationRequestDto(requestedModelId);
            request.setSelectedVariants(selection);
            return request;
        }
    }

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
