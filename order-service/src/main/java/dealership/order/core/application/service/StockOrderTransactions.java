package dealership.order.core.application.service;

import dealership.common.event.OrderSentForApprovalEvent;
import dealership.order.core.application.port.out.StockOrderRepository;
import dealership.order.core.domain.entity.order.StockOrder;
import dealership.order.core.domain.enums.StockOrderStatus;
import dealership.order.infrastructure.messaging.OrderEventProducer;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Component
public class StockOrderTransactions {
    private final StockOrderRepository orderRepository;
    private final OrderEventProducer eventProducer;

    public StockOrderTransactions(StockOrderRepository orderRepository,
                                  OrderEventProducer eventProducer) {
        this.orderRepository = orderRepository;
        this.eventProducer = eventProducer;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public StockOrder saveNew(StockOrder order) {
        return orderRepository.save(order);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public StockOrder cancel(String orderId) {
        StockOrder order = orderRepository.findByIdForUpdate(orderId);
        if (order.getStatus() != StockOrderStatus.CANCELLED) {
            order.cancel();
            return orderRepository.save(order);
        }
        return order;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public StockOrder advance(String orderId) {
        StockOrder order = orderRepository.findByIdForUpdate(orderId);
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
}
