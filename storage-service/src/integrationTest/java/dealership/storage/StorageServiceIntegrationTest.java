package dealership.storage;

import com.fasterxml.jackson.databind.ObjectMapper;
import dealership.common.event.OrderSentForApprovalEvent;
import dealership.storage.core.application.port.out.AssemblyOrderRepository;
import dealership.storage.core.application.port.out.CarRepository;
import dealership.storage.core.application.service.AssemblyOrderService;
import dealership.storage.core.application.service.CarReservationService;
import dealership.storage.core.domain.enums.CarReservationState;
import dealership.storage.core.domain.entity.assembly.AssemblyOrder;
import dealership.storage.core.domain.entity.car.Car;
import dealership.storage.core.domain.enums.*;
import dealership.storage.core.domain.exception.CarReservationConflictException;
import dealership.storage.core.domain.exception.EntityNotFoundException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@AutoConfigureMockMvc
@DisplayName("StorageService - Интеграционные тесты")
class StorageServiceIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private CarRepository carRepository;

    @Autowired
    private AssemblyOrderService assemblyOrderService;

    @Autowired
    private AssemblyOrderRepository assemblyOrderRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private KafkaTemplate<String, String> kafkaTemplate;

    @Autowired
    private CarReservationService carReservationService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("Liquibase миграции: таблицы и seed data")
    void shouldRunMigrations() {
        assertFalse(carRepository.findAvailable().isEmpty());
    }

    @Test
    @DisplayName("AssemblyOrder: создание и получение из БД")
    void shouldCreateAndFindAssemblyOrder() {
        AssemblyOrder order = new AssemblyOrder("source-1", "STOCK", "trace-1");
        AssemblyOrder saved = assemblyOrderService.create(order);

        assertNotNull(saved.getId());
        assertEquals(AssemblyStatus.CREATED, saved.getStatus());

        AssemblyOrder found = assemblyOrderService.getById(saved.getId());
        assertEquals("source-1", found.getSourceOrderId());
    }

    @Test
    @DisplayName("AssemblyOrder: идемпотентность по sourceOrderId")
    void shouldCheckIdempotency() {
        AssemblyOrder order = new AssemblyOrder("source-idem-1", "STOCK", "trace-1");
        assemblyOrderService.create(order);

        assertTrue(assemblyOrderService.existsBySourceOrderId("source-idem-1"));
        assertFalse(assemblyOrderService.existsBySourceOrderId("source-nonexistent"));
    }

    @Test
    @DisplayName("Kafka: обработка OrderSentForApproval создаёт AssemblyOrder")
    void shouldProcessKafkaEvent() throws Exception {
        String orderId = UUID.randomUUID().toString();
        String traceId = UUID.randomUUID().toString();

        OrderSentForApprovalEvent event = new OrderSentForApprovalEvent();
        event.setOrderId(orderId);
        event.setOrderType("STOCK");
        event.setTraceId(traceId);
        event.setCarId("car-123");

        kafkaTemplate.send("order.events", orderId, objectMapper.writeValueAsString(event)).get();

        Thread.sleep(5000);

        assertTrue(assemblyOrderService.existsBySourceOrderId(orderId),
                "AssemblyOrder должен быть создан после получения Kafka события");
    }

    @Test
    @DisplayName("REST: 401 без токена")
    void shouldReturn401WithoutToken() throws Exception {
        mockMvc.perform(get("/api/cars/available"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(roles = "WAREHOUSE_ADMIN")
    @DisplayName("REST: WAREHOUSE_ADMIN может видеть assembly orders")
    void shouldAllowWarehouseAdminAssemblyOrders() throws Exception {
        mockMvc.perform(get("/api/assembly-orders"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "USER")
    @DisplayName("REST: USER не может видеть assembly orders")
    void shouldDenyUserAssemblyOrders() throws Exception {
        mockMvc.perform(get("/api/assembly-orders"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "USER")
    @DisplayName("REST: USER может видеть доступные авто")
    void shouldAllowUserViewCars() throws Exception {
        mockMvc.perform(get("/api/cars/available"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Резервирование: из двух одновременных заказов побеждает ровно один")
    void shouldAllowExactlyOneConcurrentReservation() throws Exception {
        Car car = saveAvailableCar();
        String firstOrder = UUID.randomUUID().toString();
        String secondOrder = UUID.randomUUID().toString();
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);

        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            Future<Boolean> first = executor.submit(() -> tryReserveTogether(car.getId(), firstOrder, ready, start));
            Future<Boolean> second = executor.submit(() -> tryReserveTogether(car.getId(), secondOrder, ready, start));
            boolean allWorkersReady = ready.await(10, TimeUnit.SECONDS);
            start.countDown();
            assertTrue(allWorkersReady);

            assertEquals(1, (first.get(10, TimeUnit.SECONDS) ? 1 : 0)
                    + (second.get(10, TimeUnit.SECONDS) ? 1 : 0));
        }

        assertFalse(carRepository.findAvailable().stream().anyMatch(item -> item.getId().equals(car.getId())));
        assertTrue(List.of(firstOrder, secondOrder).contains(reservedBy(car.getId())));
    }

    @Test
    @DisplayName("Резервирование: отмена освобождает автомобиль для нового заказа")
    void shouldReleaseAndRebookCar() {
        Car car = saveAvailableCar();
        String firstOrder = UUID.randomUUID().toString();
        String secondOrder = UUID.randomUUID().toString();

        carReservationService.reserve(car.getId(), firstOrder);
        carReservationService.release(car.getId(), firstOrder);
        carReservationService.reserve(car.getId(), secondOrder);
        carReservationService.release(car.getId(), firstOrder);

        assertEquals(secondOrder, reservedBy(car.getId()));
        assertFalse(carRepository.findById(car.getId()).isAvailable());
    }

    @Test
    @DisplayName("Резервирование: повтор владельца идемпотентен, чужое освобождение безопасно")
    void shouldKeepReservationOnRetryAndForeignRelease() {
        Car car = saveAvailableCar();
        String owner = UUID.randomUUID().toString();

        CarReservationService.ReservationLease first = carReservationService.reserve(car.getId(), owner);
        CarReservationService.ReservationLease retry = carReservationService.reserve(car.getId(), owner);
        carReservationService.release(car.getId(), UUID.randomUUID().toString());

        assertEquals(first.expiresAt(), retry.expiresAt());
        assertEquals(owner, reservedBy(car.getId()));
        assertFalse(carRepository.findById(car.getId()).isAvailable());
    }

    @Test
    @DisplayName("Резервирование: устаревшее сохранение каталога не открывает удерживаемый автомобиль")
    void shouldRejectStaleCatalogueSave() {
        Car staleCar = saveAvailableCar();
        String owner = UUID.randomUUID().toString();
        carReservationService.reserve(staleCar.getId(), owner);

        assertThrows(RuntimeException.class, () -> carRepository.save(staleCar));

        assertEquals(owner, reservedBy(staleCar.getId()));
        assertFalse(carRepository.findById(staleCar.getId()).isAvailable());
    }

    @Test
    @DisplayName("Резервирование: отсутствующий и недоступный автомобили различаются")
    void shouldDistinguishMissingAndUnavailableCars() {
        assertThrows(EntityNotFoundException.class,
                () -> carReservationService.reserve(UUID.randomUUID().toString(), UUID.randomUUID().toString()));

        Car unavailable = saveAvailableCar();
        unavailable.markAsUnavailable();
        carRepository.save(unavailable);
        assertThrows(CarReservationConflictException.class,
                () -> carReservationService.reserve(unavailable.getId(), UUID.randomUUID().toString()));
    }

    @Test
    @DisplayName("Резервирование: один заказ не может удерживать два автомобиля")
    void shouldRejectSecondCarForSameOrder() {
        Car firstCar = saveAvailableCar();
        Car secondCar = saveAvailableCar();
        String orderId = UUID.randomUUID().toString();
        carReservationService.reserve(firstCar.getId(), orderId);

        assertThrows(CarReservationConflictException.class,
                () -> carReservationService.reserve(secondCar.getId(), orderId));

        assertTrue(carRepository.findById(secondCar.getId()).isAvailable());
        assertNull(reservedBy(secondCar.getId()));
    }

    @Test
    @DisplayName("Резервирование: release до запоздалого reserve оставляет tombstone")
    void shouldFenceDelayedReserveWithReleaseTombstone() {
        Car car = saveAvailableCar();
        String orderId = UUID.randomUUID().toString();

        carReservationService.release(car.getId(), orderId);

        assertThrows(CarReservationConflictException.class,
                () -> carReservationService.reserve(car.getId(), orderId));
        assertEquals("RELEASED", reservationState(orderId));
        assertTrue(carRepository.findById(car.getId()).isAvailable());
    }

    @Test
    @DisplayName("Резервирование: confirm и expiry сериализуются по ledger")
    void shouldSerializeConfirmAndExpiry() throws Exception {
        Car car = saveAvailableCar();
        String orderId = UUID.randomUUID().toString();
        carReservationService.reserve(car.getId(), orderId);
        jdbcTemplate.update("UPDATE car_reservations SET hold_expires_at = clock_timestamp() - INTERVAL '1 second' WHERE order_id = ?",
                UUID.fromString(orderId));
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);

        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            Future<CarReservationService.ConfirmationResult> confirmation = executor.submit(() -> {
                ready.countDown();
                start.await(10, TimeUnit.SECONDS);
                return carReservationService.confirm(car.getId(), orderId);
            });
            Future<Boolean> expiration = executor.submit(() -> {
                ready.countDown();
                start.await(10, TimeUnit.SECONDS);
                return carReservationService.expireIfDue(UUID.fromString(orderId));
            });
            boolean allReady = ready.await(10, TimeUnit.SECONDS);
            start.countDown();
            assertTrue(allReady);
            assertNotEquals(CarReservationService.ConfirmationResult.CONFIRMED,
                    confirmation.get(10, TimeUnit.SECONDS));
            expiration.get(10, TimeUnit.SECONDS);
        }

        assertEquals(CarReservationState.EXPIRED.name(), reservationState(orderId));
        assertTrue(carRepository.findById(car.getId()).isAvailable());
    }

    @Test
    @DisplayName("Резервирование: подтверждённая бронь не истекает")
    void shouldRetainConfirmedReservationPastOriginalDeadline() {
        Car car = saveAvailableCar();
        String orderId = UUID.randomUUID().toString();
        carReservationService.reserve(car.getId(), orderId);
        assertEquals(CarReservationService.ConfirmationResult.CONFIRMED,
                carReservationService.confirm(car.getId(), orderId));
        jdbcTemplate.update("UPDATE car_reservations SET hold_expires_at = clock_timestamp() - INTERVAL '1 second' WHERE order_id = ?",
                UUID.fromString(orderId));

        assertFalse(carReservationService.expireIfDue(UUID.fromString(orderId)));
        assertEquals(CarReservationState.CONFIRMED.name(), reservationState(orderId));
        assertFalse(carRepository.findById(car.getId()).isAvailable());
    }

    @Test
    @WithMockUser(roles = "WAREHOUSE_ADMIN")
    @DisplayName("REST: WAREHOUSE_ADMIN может видеть запчасти")
    void shouldAllowWarehouseAdminParts() throws Exception {
        mockMvc.perform(get("/api/inventory/parts"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "USER")
    @DisplayName("REST: USER не может управлять inventory")
    void shouldDenyUserInventory() throws Exception {
        mockMvc.perform(get("/api/inventory/parts"))
                .andExpect(status().isForbidden());
    }

    private boolean tryReserveTogether(String carId, String orderId,
                                       CountDownLatch ready, CountDownLatch start) throws InterruptedException {
        ready.countDown();
        if (!start.await(10, TimeUnit.SECONDS)) {
            return false;
        }
        try {
            carReservationService.reserve(carId, orderId);
            return true;
        } catch (CarReservationConflictException exception) {
            return false;
        }
    }

    private Car saveAvailableCar() {
        return carRepository.save(new Car(
                "Test", "Reservation", BodyType.SEDAN, FuelType.PETROL,
                150, 2.0, TransmissionType.AUTOMATIC, DriveType.FRONT,
                Color.BLACK, new BigDecimal("1000000")));
    }

    private String reservedBy(String carId) {
        UUID owner = jdbcTemplate.queryForObject(
                "SELECT reserved_by_order_id FROM cars WHERE id = ?",
                UUID.class, UUID.fromString(carId));
        return owner == null ? null : owner.toString();
    }

    private String reservationState(String orderId) {
        return jdbcTemplate.queryForObject(
                "SELECT state FROM car_reservations WHERE order_id = ?",
                String.class, UUID.fromString(orderId));
    }
}
