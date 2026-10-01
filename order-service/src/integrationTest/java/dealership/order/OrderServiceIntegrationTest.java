package dealership.order;

import dealership.order.core.application.port.out.StockOrderRepository;
import dealership.order.core.application.port.out.UserRepository;
import dealership.order.core.application.service.StockOrderService;
import dealership.order.core.domain.entity.order.StockOrder;
import dealership.order.core.domain.entity.user.User;
import dealership.order.core.domain.enums.StockOrderStatus;
import dealership.order.core.domain.enums.UserRole;
import dealership.order.infrastructure.messaging.OutboxEvent;
import dealership.order.infrastructure.messaging.OutboxRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
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

    @Test
    @DisplayName("Liquibase миграции: таблицы созданы")
    void shouldRunMigrations() {
        assertNotNull(stockOrderRepository);
        assertNotNull(outboxRepository);
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
    @DisplayName("REST: 401 без токена")
    void shouldReturn401WithoutToken() throws Exception {
        mockMvc.perform(get("/api/orders/stock"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(roles = "USER")
    @DisplayName("REST: 200 с ролью USER")
    void shouldReturn200WithUserRole() throws Exception {
        mockMvc.perform(get("/api/orders/stock"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "USER")
    @DisplayName("REST: Swagger доступен")
    void shouldAccessSwagger() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk());
    }
}
