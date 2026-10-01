package dealership.order.core.application.service;

import dealership.common.event.OrderSentForApprovalEvent;
import dealership.order.core.application.port.out.StockOrderRepository;
import dealership.order.core.application.port.out.UserRepository;
import dealership.order.core.domain.entity.order.StockOrder;
import dealership.order.core.domain.entity.user.User;
import dealership.order.core.domain.enums.StockOrderStatus;
import dealership.order.core.domain.enums.UserRole;
import dealership.order.core.domain.exception.DomainValidationException;
import dealership.order.infrastructure.messaging.OrderEventProducer;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Random;
import java.util.UUID;

@Service
public class StockOrderService {
    private final StockOrderRepository orderRepository;
    private final UserRepository userRepository;
    private final OrderEventProducer eventProducer;
    private final Random random = new Random();

    public StockOrderService(StockOrderRepository orderRepository,
                             UserRepository userRepository,
                             OrderEventProducer eventProducer) {
        this.orderRepository = orderRepository;
        this.userRepository = userRepository;
        this.eventProducer = eventProducer;
    }

    @Transactional
    public StockOrder createOrder(String clientId, String carId) {
        String managerId = assignRandomManager();
        StockOrder order = new StockOrder(null, clientId, managerId, carId);
        return orderRepository.save(order);
    }

    public StockOrder getOrderById(String orderId) {
        return orderRepository.findById(orderId);
    }

    @Transactional
    public StockOrder cancelOrder(String orderId) {
        StockOrder order = orderRepository.findById(orderId);
        order.cancel();
        return orderRepository.save(order);
    }

    @Transactional
    public StockOrder advanceOrder(String orderId) {
        StockOrder order = orderRepository.findById(orderId);
        StockOrderStatus statusBefore = order.getStatus();
        order.advanceStatus();
        StockOrder saved = orderRepository.save(order);

        if (statusBefore == StockOrderStatus.AWAITING_PAYMENT && saved.getStatus() == StockOrderStatus.PAID) {
            OrderSentForApprovalEvent event = new OrderSentForApprovalEvent();
            event.setOrderId(saved.getId());
            event.setOrderType("STOCK");
            event.setTraceId(UUID.randomUUID().toString());
            event.setCarId(saved.getCarId());
            eventProducer.publishOrderPaid(event);
        }

        return saved;
    }

    public List<StockOrder> getClientOrders(String clientId) {
        return orderRepository.findByClientId(clientId);
    }

    public List<StockOrder> getAllOrders() {
        return orderRepository.findAll();
    }

    private String assignRandomManager() {
        List<User> managers = userRepository.findByRole(UserRole.MANAGER);
        if (managers.isEmpty()) {
            throw new DomainValidationException("Нет доступных менеджеров");
        }
        return managers.get(random.nextInt(managers.size())).getId();
    }
}