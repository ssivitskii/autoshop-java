package dealership.order.core.application.service;

import dealership.common.event.OrderSentForApprovalEvent;
import dealership.order.core.application.port.out.CustomOrderRepository;
import dealership.order.core.application.port.out.CustomConfigurationGateway;
import dealership.order.core.application.port.out.UserRepository;
import dealership.order.core.domain.entity.car.CarConfiguration;
import dealership.order.core.domain.entity.order.CustomOrder;
import dealership.order.core.domain.entity.user.User;
import dealership.order.core.domain.enums.CustomOrderStatus;
import dealership.order.core.domain.enums.DemoPaymentOutcome;
import dealership.order.core.domain.enums.DemoPaymentStatus;
import dealership.order.core.domain.enums.UserRole;
import dealership.order.core.domain.exception.DemoPaymentConflictException;
import dealership.order.core.domain.exception.DomainValidationException;
import dealership.order.infrastructure.messaging.OrderEventProducer;
import dealership.order.infrastructure.persistence.entity.DemoPaymentAttemptJpaEntity;
import dealership.order.infrastructure.persistence.repository.DemoPaymentAttemptJpaRepository;
import org.springframework.security.access.AccessDeniedException;
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
    private final DemoPaymentAttemptJpaRepository paymentRepository;
    private final Random random = new Random();

    public CustomOrderService(CustomOrderRepository orderRepository,
                              UserRepository userRepository,
                              OrderEventProducer eventProducer,
                              CustomConfigurationGateway configurationGateway,
                              DemoPaymentAttemptJpaRepository paymentRepository) {
        this.orderRepository = orderRepository;
        this.userRepository = userRepository;
        this.eventProducer = eventProducer;
        this.configurationGateway = configurationGateway;
        this.paymentRepository = paymentRepository;
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
        if (order.getStatus() == CustomOrderStatus.AWAITING_PAYMENT) {
            throw new DemoPaymentConflictException(
                    "Заказ, ожидающий оплату, продвигается только через demo payment");
        }
        order.advanceStatus(null);
        return orderRepository.save(order);
    }

    @Transactional
    public DemoPaymentAttemptJpaEntity startDemoPayment(
            String orderId, String clientId, UUID idempotencyKey, DemoPaymentOutcome outcome) {
        CustomOrder order = orderRepository.findByIdForUpdate(orderId);
        if (!order.getClientId().equals(clientId)) {
            throw new AccessDeniedException("Заказ принадлежит другому клиенту");
        }
        UUID orderUuid = UUID.fromString(orderId);
        var replay = paymentRepository.findCustomByKeyForUpdate(orderUuid, idempotencyKey);
        if (replay.isPresent()) {
            DemoPaymentAttemptJpaEntity payment = replay.get();
            requireSameOutcome(payment, outcome);
            return payment;
        }
        if (paymentRepository.findFirstByCustomOrderIdAndStatusIn(
                orderUuid, List.of(DemoPaymentStatus.PENDING, DemoPaymentStatus.SUCCEEDED)).isPresent()) {
            throw new DemoPaymentConflictException(
                    "Для заказа уже существует активная или успешная demo payment попытка");
        }
        if (order.getStatus() != CustomOrderStatus.AWAITING_PAYMENT) {
            throw new DemoPaymentConflictException("Заказ не ожидает demo payment");
        }

        DemoPaymentAttemptJpaEntity payment = new DemoPaymentAttemptJpaEntity();
        payment.setCustomOrderId(orderUuid);
        payment.setClientId(clientId);
        payment.setIdempotencyKey(idempotencyKey);
        payment.setRequestedOutcome(outcome);
        if (outcome == DemoPaymentOutcome.DECLINE) {
            payment.setStatus(DemoPaymentStatus.DECLINED);
            return paymentRepository.saveAndFlush(payment);
        }

        order.markPaid();
        CustomOrder saved = orderRepository.save(order);
        payment.setStatus(DemoPaymentStatus.SUCCEEDED);
        DemoPaymentAttemptJpaEntity receipt = paymentRepository.saveAndFlush(payment);
        publishPaid(saved);
        return receipt;
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

    private void requireSameOutcome(DemoPaymentAttemptJpaEntity payment,
                                    DemoPaymentOutcome outcome) {
        if (payment.getRequestedOutcome() != outcome) {
            throw new DemoPaymentConflictException(
                    "Idempotency-Key уже использован с другим demo outcome");
        }
    }

    private void publishPaid(CustomOrder saved) {
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
}
