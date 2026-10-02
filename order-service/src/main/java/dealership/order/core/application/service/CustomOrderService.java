package dealership.order.core.application.service;

import dealership.common.event.OrderSentForApprovalEvent;
import dealership.order.core.application.port.out.CustomOrderRepository;
import dealership.order.core.application.port.out.CustomConfigurationGateway;
import dealership.order.core.application.port.out.UserRepository;
import dealership.order.core.domain.entity.car.CarConfiguration;
import dealership.order.core.domain.entity.order.CustomOrder;
import dealership.order.core.domain.entity.user.User;
import dealership.order.core.domain.enums.CustomOrderStatus;
import dealership.order.core.domain.enums.UserRole;
import dealership.order.core.domain.exception.DomainValidationException;
import dealership.order.infrastructure.messaging.OrderEventProducer;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Random;
import java.util.UUID;

@Service
public class CustomOrderService {
    private final CustomOrderRepository orderRepository;
    private final UserRepository userRepository;
    private final OrderEventProducer eventProducer;
    private final CustomConfigurationGateway configurationGateway;
    private final Random random = new Random();

    public CustomOrderService(CustomOrderRepository orderRepository,
                              UserRepository userRepository,
                              OrderEventProducer eventProducer,
                              CustomConfigurationGateway configurationGateway) {
        this.orderRepository = orderRepository;
        this.userRepository = userRepository;
        this.eventProducer = eventProducer;
        this.configurationGateway = configurationGateway;
    }

    public CustomOrder createOrder(String clientId, String carModelId,
                                   java.util.Map<String, String> selectedVariants) {
        CustomConfigurationGateway.ConfigurationQuote quote =
                configurationGateway.quote(carModelId, selectedVariants);
        String managerId = assignRandomManager();
        CarConfiguration configuration = new CarConfiguration(quote.carModelId());
        quote.selectedVariants().forEach(configuration::selectVariant);
        CustomOrder order = new CustomOrder(
                null, managerId, clientId, quote.carModelId(), configuration, quote.totalPrice());
        return orderRepository.save(order);
    }

    public CustomOrder getOrderById(String orderId) {
        return orderRepository.findById(orderId);
    }

    @Transactional
    public CustomOrder cancelOrder(String orderId) {
        CustomOrder order = orderRepository.findByIdForUpdate(orderId);
        order.cancel();
        return orderRepository.save(order);
    }

    @Transactional
    public CustomOrder advanceOrder(String orderId) {
        CustomOrder order = orderRepository.findByIdForUpdate(orderId);
        CustomOrderStatus statusBefore = order.getStatus();
        order.advanceStatus(null);
        CustomOrder saved = orderRepository.save(order);

        if (statusBefore == CustomOrderStatus.AWAITING_PAYMENT && saved.getStatus() == CustomOrderStatus.PAID) {
            OrderSentForApprovalEvent event = new OrderSentForApprovalEvent();
            event.setOrderId(saved.getId());
            event.setOrderType("CUSTOM");
            event.setTraceId(UUID.randomUUID().toString());
            event.setCarModelId(saved.getCarModelId());
            event.setTotalPrice(saved.getTotalPrice());
            if (saved.getConfiguration() != null) {
                event.setSelectedVariants(saved.getConfiguration().getSelectedVariants());
            }
            eventProducer.publishOrderPaid(event);
        }

        return saved;
    }

    public List<CustomOrder> getClientOrders(String clientId) {
        return orderRepository.findByClientId(clientId);
    }

    public List<CustomOrder> getAllOrders() {
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
