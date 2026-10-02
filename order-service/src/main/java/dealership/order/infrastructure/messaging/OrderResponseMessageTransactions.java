package dealership.order.infrastructure.messaging;

import dealership.common.event.OrderApprovedEvent;
import dealership.common.event.OrderRejectedEvent;
import dealership.common.event.PermanentMessageException;
import dealership.order.core.application.port.out.CustomOrderRepository;
import dealership.order.core.application.port.out.StockOrderRepository;
import dealership.order.core.domain.entity.order.CustomOrder;
import dealership.order.core.domain.entity.order.StockOrder;
import dealership.order.core.domain.enums.CustomOrderStatus;
import dealership.order.core.domain.enums.StockOrderStatus;
import dealership.order.core.domain.enums.StockReservationWorkflowState;
import dealership.order.infrastructure.persistence.entity.StockReservationWorkflowJpaEntity;
import dealership.order.infrastructure.persistence.repository.StockReservationWorkflowJpaRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Component
public class OrderResponseMessageTransactions {
    private final OrderInboxStore inboxStore;
    private final StockOrderRepository stockOrders;
    private final CustomOrderRepository customOrders;
    private final StockReservationWorkflowJpaRepository workflows;

    public OrderResponseMessageTransactions(OrderInboxStore inboxStore,
                                            StockOrderRepository stockOrders,
                                            CustomOrderRepository customOrders,
                                            StockReservationWorkflowJpaRepository workflows) {
        this.inboxStore = inboxStore;
        this.stockOrders = stockOrders;
        this.customOrders = customOrders;
        this.workflows = workflows;
    }

    @Transactional
    public void processApproved(UUID eventId, String topic, int partition, long offset,
                                OrderApprovedEvent event) {
        if (!inboxStore.insert(eventId, topic, partition, offset)) {
            return;
        }
        if ("STOCK".equals(event.getOrderType())) {
            approveStock(event.getOrderId());
        } else if ("CUSTOM".equals(event.getOrderType())) {
            approveCustom(event.getOrderId());
        } else {
            throw new PermanentMessageException("Unsupported order type: " + event.getOrderType());
        }
    }

    @Transactional
    public void processRejected(UUID eventId, String topic, int partition, long offset,
                                OrderRejectedEvent event) {
        if (!inboxStore.insert(eventId, topic, partition, offset)) {
            return;
        }
        if ("STOCK".equals(event.getOrderType())) {
            rejectStock(event.getOrderId());
        } else if ("CUSTOM".equals(event.getOrderType())) {
            rejectCustom(event.getOrderId());
        } else {
            throw new PermanentMessageException("Unsupported order type: " + event.getOrderType());
        }
    }

    private void approveStock(String orderId) {
        StockOrder order = stockOrders.findByIdForUpdate(orderId);
        StockReservationWorkflowJpaEntity workflow = lockWorkflow(orderId);
        if (order.getStatus() == StockOrderStatus.PAID
                && workflow.getState() == StockReservationWorkflowState.CONFIRMED) {
            order.advanceStatus();
            stockOrders.save(order);
            return;
        }
        if (order.getStatus() == StockOrderStatus.READY_FOR_PICKUP
                || order.getStatus() == StockOrderStatus.COMPLETED
                || order.getStatus() == StockOrderStatus.CANCELLED) {
            return;
        }
        throw new PermanentMessageException("Stock order is not ready for approval: " + orderId);
    }

    private void rejectStock(String orderId) {
        StockOrder order = stockOrders.findByIdForUpdate(orderId);
        StockReservationWorkflowJpaEntity workflow = lockWorkflow(orderId);
        if (order.getStatus() == StockOrderStatus.CANCELLED) {
            return;
        }
        if (order.getStatus() == StockOrderStatus.READY_FOR_PICKUP
                || order.getStatus() == StockOrderStatus.COMPLETED) {
            throw new PermanentMessageException("Stock rejection conflicts with order state: " + orderId);
        }
        if (order.getStatus() != StockOrderStatus.PAID
                || workflow.getState() != StockReservationWorkflowState.CONFIRMED) {
            throw new PermanentMessageException("Stock order is not ready for rejection: " + orderId);
        }
        order.cancel();
        stockOrders.save(order);
        workflow.setState(StockReservationWorkflowState.RELEASE_PENDING);
        workflow.setNextAttemptAt(workflows.databaseNow());
        workflows.save(workflow);
    }

    private void approveCustom(String orderId) {
        CustomOrder order = customOrders.findByIdForUpdate(orderId);
        if (order.getStatus() == CustomOrderStatus.PAID) {
            order.advanceStatus(null);
            customOrders.save(order);
            return;
        }
        if (order.getStatus() == CustomOrderStatus.AWAITING_DELIVERY
                || order.getStatus() == CustomOrderStatus.READY_FOR_PICKUP
                || order.getStatus() == CustomOrderStatus.COMPLETED
                || order.getStatus() == CustomOrderStatus.CANCELLED) {
            return;
        }
        throw new PermanentMessageException("Custom order is not ready for approval: " + orderId);
    }

    private void rejectCustom(String orderId) {
        CustomOrder order = customOrders.findByIdForUpdate(orderId);
        if (order.getStatus() == CustomOrderStatus.CANCELLED) {
            return;
        }
        if (order.getStatus() != CustomOrderStatus.PAID) {
            throw new PermanentMessageException("Custom rejection conflicts with order state: " + orderId);
        }
        order.cancel();
        customOrders.save(order);
    }

    private StockReservationWorkflowJpaEntity lockWorkflow(String orderId) {
        return workflows.findByOrderIdForUpdate(UUID.fromString(orderId))
                .orElseThrow(() -> new PermanentMessageException(
                        "Reservation workflow is missing for stock order: " + orderId));
    }
}
