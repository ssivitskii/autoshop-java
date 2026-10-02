package dealership.order.core.application.service;

import dealership.common.event.OrderSentForApprovalEvent;
import dealership.order.core.application.port.out.StockOrderRepository;
import dealership.order.core.domain.entity.order.StockOrder;
import dealership.order.core.domain.enums.StockOrderStatus;
import dealership.order.core.domain.enums.StockReservationWorkflowState;
import dealership.order.core.domain.exception.DomainValidationException;
import dealership.order.infrastructure.messaging.OrderEventProducer;
import dealership.order.infrastructure.persistence.entity.StockReservationWorkflowJpaEntity;
import dealership.order.infrastructure.persistence.repository.StockReservationWorkflowJpaRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Component
public class StockOrderTransactions {
    public enum Action { NONE, CONFIRM, RELEASE }

    public record Work(StockOrder order, Action action) { }

    private final StockOrderRepository orderRepository;
    private final StockReservationWorkflowJpaRepository workflowRepository;
    private final OrderEventProducer eventProducer;

    public StockOrderTransactions(StockOrderRepository orderRepository,
                                  StockReservationWorkflowJpaRepository workflowRepository,
                                  OrderEventProducer eventProducer) {
        this.orderRepository = orderRepository;
        this.workflowRepository = workflowRepository;
        this.eventProducer = eventProducer;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public StockOrder saveNew(StockOrder order, Instant storageExpiresAt) {
        StockOrder saved = orderRepository.save(order);
        StockReservationWorkflowJpaEntity workflow = new StockReservationWorkflowJpaEntity();
        workflow.setOrderId(UUID.fromString(saved.getId()));
        workflow.setCarId(saved.getCarId());
        workflow.setState(StockReservationWorkflowState.HELD);
        workflow.setStorageExpiresAt(storageExpiresAt);
        workflowRepository.save(workflow);
        return saved;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Work cancel(String orderId) {
        StockOrder order = orderRepository.findByIdForUpdate(orderId);
        StockReservationWorkflowJpaEntity workflow = lockWorkflow(orderId);
        if (workflow.getState() == StockReservationWorkflowState.CONFIRM_PENDING) {
            throw new DomainValidationException("Нельзя отменить заказ во время подтверждения резерва");
        }
        if (order.getStatus() != StockOrderStatus.CANCELLED) {
            order.cancel();
            order = orderRepository.save(order);
        }
        if (workflow.getState() == StockReservationWorkflowState.RELEASED) {
            return new Work(order, Action.NONE);
        }
        workflow.setState(StockReservationWorkflowState.RELEASE_PENDING);
        workflow.setNextAttemptAt(workflowRepository.databaseNow());
        workflowRepository.save(workflow);
        return new Work(order, Action.RELEASE);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Work advance(String orderId) {
        StockOrder order = orderRepository.findByIdForUpdate(orderId);
        StockReservationWorkflowJpaEntity workflow = lockWorkflow(orderId);
        if (order.getStatus() == StockOrderStatus.AWAITING_PAYMENT) {
            if (workflow.getState() == StockReservationWorkflowState.HELD
                    || workflow.getState() == StockReservationWorkflowState.LEGACY_UNVERIFIED) {
                workflow.setState(StockReservationWorkflowState.CONFIRM_PENDING);
                workflow.setNextAttemptAt(workflowRepository.databaseNow());
                workflowRepository.save(workflow);
                return new Work(order, Action.CONFIRM);
            }
            if (workflow.getState() == StockReservationWorkflowState.CONFIRM_PENDING) {
                return new Work(order, Action.CONFIRM);
            }
            if (workflow.getState() == StockReservationWorkflowState.CONFIRMED) {
                return new Work(finalizePaid(order), Action.NONE);
            }
            throw new DomainValidationException("Резерв автомобиля уже завершён");
        }

        order.advanceStatus();
        return new Work(orderRepository.save(order), Action.NONE);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public StockOrder completeConfirmation(String orderId) {
        StockOrder order = orderRepository.findByIdForUpdate(orderId);
        StockReservationWorkflowJpaEntity workflow = lockWorkflow(orderId);
        if (workflow.getState() != StockReservationWorkflowState.CONFIRM_PENDING
                && workflow.getState() != StockReservationWorkflowState.CONFIRMED) {
            return order;
        }
        workflow.setState(StockReservationWorkflowState.CONFIRMED);
        workflow.setNextAttemptAt(null);
        workflowRepository.save(workflow);
        if (order.getStatus() == StockOrderStatus.AWAITING_PAYMENT) {
            return finalizePaid(order);
        }
        return order;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public StockOrder failConfirmationTerminal(String orderId) {
        StockOrder order = orderRepository.findByIdForUpdate(orderId);
        StockReservationWorkflowJpaEntity workflow = lockWorkflow(orderId);
        if (workflow.getState() != StockReservationWorkflowState.CONFIRM_PENDING) {
            return order;
        }
        if (order.getStatus() != StockOrderStatus.CANCELLED && order.canCancel()) {
            order.cancel();
            order = orderRepository.save(order);
        }
        workflow.setState(StockReservationWorkflowState.EXPIRED);
        workflow.setNextAttemptAt(null);
        workflowRepository.save(workflow);
        return order;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public StockOrder completeRelease(String orderId) {
        StockOrder order = orderRepository.findByIdForUpdate(orderId);
        StockReservationWorkflowJpaEntity workflow = lockWorkflow(orderId);
        if (workflow.getState() == StockReservationWorkflowState.RELEASE_PENDING) {
            workflow.setState(StockReservationWorkflowState.RELEASED);
            workflow.setNextAttemptAt(null);
            workflowRepository.save(workflow);
        }
        return order;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Work claimPendingWork(String orderId) {
        StockOrder order = orderRepository.findByIdForUpdate(orderId);
        StockReservationWorkflowJpaEntity workflow = lockWorkflow(orderId);
        Instant now = workflowRepository.databaseNow();
        if (workflow.getNextAttemptAt() != null && workflow.getNextAttemptAt().isAfter(now)) {
            return new Work(order, Action.NONE);
        }
        Action action = actionFor(workflow.getState());
        if (action == Action.NONE) {
            return new Work(order, Action.NONE);
        }
        workflow.setAttemptCount(workflow.getAttemptCount() + 1);
        workflow.setNextAttemptAt(now.plus(backoff(workflow.getAttemptCount())));
        workflowRepository.save(workflow);
        return new Work(order, action);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Work expireLocalHold(String orderId) {
        StockOrder order = orderRepository.findByIdForUpdate(orderId);
        StockReservationWorkflowJpaEntity workflow = lockWorkflow(orderId);
        if (workflow.getState() != StockReservationWorkflowState.HELD) {
            return new Work(order, Action.NONE);
        }
        Instant now = workflowRepository.databaseNow();
        if (workflow.getStorageExpiresAt() == null || workflow.getStorageExpiresAt().isAfter(now)) {
            return new Work(order, Action.NONE);
        }
        if (order.getStatus() != StockOrderStatus.CREATED
                && order.getStatus() != StockOrderStatus.APPROVED_BY_MANAGER
                && order.getStatus() != StockOrderStatus.AWAITING_PAYMENT) {
            return new Work(order, Action.NONE);
        }
        if (order.getStatus() != StockOrderStatus.CANCELLED && order.canCancel()) {
            order.cancel();
            order = orderRepository.save(order);
        }
        workflow.setState(StockReservationWorkflowState.RELEASE_PENDING);
        workflow.setNextAttemptAt(now);
        workflowRepository.save(workflow);
        return new Work(order, Action.RELEASE);
    }

    public List<UUID> findPendingCandidates(int limit) {
        return workflowRepository.findPendingCandidateIds(limit);
    }

    public List<UUID> findExpiredHoldCandidates(int limit) {
        return workflowRepository.findExpiredHoldCandidateIds(limit);
    }

    private StockOrder finalizePaid(StockOrder order) {
        order.advanceStatus();
        StockOrder saved = orderRepository.save(order);
        OrderSentForApprovalEvent event = new OrderSentForApprovalEvent();
        event.setOrderId(saved.getId());
        event.setOrderType("STOCK");
        event.setTraceId(UUID.randomUUID().toString());
        event.setCarId(saved.getCarId());
        eventProducer.publishOrderPaid(event);
        return saved;
    }

    private StockReservationWorkflowJpaEntity lockWorkflow(String orderId) {
        return workflowRepository.findByOrderIdForUpdate(UUID.fromString(orderId))
                .orElseThrow(() -> new IllegalStateException(
                        "Reservation workflow for order '%s' is missing".formatted(orderId)));
    }

    private Action actionFor(StockReservationWorkflowState state) {
        return switch (state) {
            case CONFIRM_PENDING -> Action.CONFIRM;
            case RELEASE_PENDING -> Action.RELEASE;
            default -> Action.NONE;
        };
    }

    private Duration backoff(int attempt) {
        long seconds = Math.min(60, 1L << Math.min(attempt - 1, 6));
        return Duration.ofSeconds(seconds);
    }
}
