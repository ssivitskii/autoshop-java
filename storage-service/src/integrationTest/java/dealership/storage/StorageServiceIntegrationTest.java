package dealership.storage;

import com.fasterxml.jackson.databind.ObjectMapper;
import dealership.common.event.OrderSentForApprovalEvent;
import dealership.storage.core.application.port.out.AssemblyOrderRepository;
import dealership.storage.core.application.port.out.CarRepository;
import dealership.storage.core.application.service.AssemblyOrderService;
import dealership.storage.core.domain.entity.assembly.AssemblyOrder;
import dealership.storage.core.domain.entity.car.Car;
import dealership.storage.core.domain.enums.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.UUID;

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
}
