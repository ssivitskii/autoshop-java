package dealership.order.core.application.service;

import dealership.order.core.application.port.out.StockCarReservationGateway;
import dealership.order.core.application.port.out.StockOrderRepository;
import dealership.order.core.application.port.out.UserRepository;
import dealership.order.core.domain.entity.order.StockOrder;
import dealership.order.core.domain.entity.user.User;
import dealership.order.core.domain.enums.UserRole;
import dealership.order.core.domain.exception.DomainValidationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Random;

@Service
public class StockOrderService {
    private static final Logger log = LoggerFactory.getLogger(StockOrderService.class);

    private final StockOrderRepository orderRepository;
    private final UserRepository userRepository;
    private final StockCarReservationGateway reservationGateway;
    private final StockOrderTransactions transactions;
    private final Random random = new Random();

    public StockOrderService(StockOrderRepository orderRepository,
                             UserRepository userRepository,
                             StockCarReservationGateway reservationGateway,
                             StockOrderTransactions transactions) {
        this.orderRepository = orderRepository;
        this.userRepository = userRepository;
        this.reservationGateway = reservationGateway;
        this.transactions = transactions;
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public StockOrder createOrder(String clientId, String carId) {
        String managerId = assignRandomManager();
        StockOrder order = new StockOrder(null, clientId, managerId, carId);
        try {
            reservationGateway.reserve(carId, order.getId());
        } catch (RuntimeException exception) {
            releaseAfterFailure(order, exception);
            throw exception;
        }

        try {
            return transactions.saveNew(order);
        } catch (RuntimeException exception) {
            releaseAfterFailure(order, exception);
            throw exception;
        }
    }

    public StockOrder getOrderById(String orderId) {
        return orderRepository.findById(orderId);
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public StockOrder cancelOrder(String orderId) {
        StockOrder cancelled = transactions.cancel(orderId);
        reservationGateway.release(cancelled.getCarId(), cancelled.getId());
        return cancelled;
    }

    public StockOrder advanceOrder(String orderId) {
        return transactions.advance(orderId);
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

    private void releaseAfterFailure(StockOrder order, RuntimeException originalFailure) {
        try {
            reservationGateway.release(order.getCarId(), order.getId());
        } catch (RuntimeException cleanupFailure) {
            originalFailure.addSuppressed(cleanupFailure);
            log.warn("Failed to release car {} for order {} during compensation",
                    order.getCarId(), order.getId(), cleanupFailure);
        }
    }
}
