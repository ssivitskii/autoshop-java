package dealership.order.core.application.service;

import dealership.order.core.application.port.out.CustomOrderRepository;
import dealership.order.core.application.port.out.StockOrderRepository;
import dealership.order.core.domain.enums.DemoPaymentOutcome;
import dealership.order.core.domain.exception.CarReservationConflictException;
import dealership.order.core.domain.exception.DomainValidationException;
import dealership.order.core.domain.exception.EntityNotFoundException;
import dealership.order.core.domain.exception.StorageUnavailableException;
import dealership.order.infrastructure.persistence.entity.DemoPaymentAttemptJpaEntity;
import dealership.order.infrastructure.persistence.repository.DemoPaymentAttemptJpaRepository;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class DemoPaymentService {
    private final StockOrderTransactions stockTransactions;
    private final StockReservationRecoveryService recoveryService;
    private final CustomOrderService customOrderService;
    private final StockOrderRepository stockOrderRepository;
    private final CustomOrderRepository customOrderRepository;
    private final DemoPaymentAttemptJpaRepository paymentRepository;

    public DemoPaymentService(StockOrderTransactions stockTransactions,
                              StockReservationRecoveryService recoveryService,
                              CustomOrderService customOrderService,
                              StockOrderRepository stockOrderRepository,
                              CustomOrderRepository customOrderRepository,
                              DemoPaymentAttemptJpaRepository paymentRepository) {
        this.stockTransactions = stockTransactions;
        this.recoveryService = recoveryService;
        this.customOrderService = customOrderService;
        this.stockOrderRepository = stockOrderRepository;
        this.customOrderRepository = customOrderRepository;
        this.paymentRepository = paymentRepository;
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public DemoPaymentAttemptJpaEntity startStockPayment(
            String orderId, String clientId, UUID idempotencyKey, DemoPaymentOutcome outcome) {
        requireRequest(idempotencyKey, outcome);
        StockOrderTransactions.PaymentWork paymentWork = stockTransactions.startDemoPayment(
                orderId, clientId, idempotencyKey, outcome);
        try {
            recoveryService.execute(paymentWork.work());
        } catch (StorageUnavailableException | CarReservationConflictException
                 | EntityNotFoundException expected) {
            // The durable receipt is authoritative: transient failures remain PENDING,
            // terminal reservation failures are committed as FAILED before reloading.
        }
        return paymentRepository.findById(paymentWork.payment().getId())
                .orElseThrow(() -> new IllegalStateException("Demo payment receipt disappeared"));
    }

    public DemoPaymentAttemptJpaEntity startCustomPayment(
            String orderId, String clientId, UUID idempotencyKey, DemoPaymentOutcome outcome) {
        requireRequest(idempotencyKey, outcome);
        return customOrderService.startDemoPayment(orderId, clientId, idempotencyKey, outcome);
    }

    public DemoPaymentAttemptJpaEntity getStockPayment(
            String orderId, String paymentId, String actorId, boolean auditRead) {
        var order = stockOrderRepository.findById(orderId);
        requireOwnerOrAudit(order.getClientId(), actorId, auditRead);
        return paymentRepository.findByIdAndStockOrderId(
                        UUID.fromString(paymentId), UUID.fromString(orderId))
                .orElseThrow(() -> missingReceipt(paymentId));
    }

    public DemoPaymentAttemptJpaEntity getCustomPayment(
            String orderId, String paymentId, String actorId, boolean auditRead) {
        var order = customOrderRepository.findById(orderId);
        requireOwnerOrAudit(order.getClientId(), actorId, auditRead);
        return paymentRepository.findByIdAndCustomOrderId(
                        UUID.fromString(paymentId), UUID.fromString(orderId))
                .orElseThrow(() -> missingReceipt(paymentId));
    }

    private void requireRequest(UUID idempotencyKey, DemoPaymentOutcome outcome) {
        if (idempotencyKey == null) {
            throw new DomainValidationException("Idempotency-Key обязателен");
        }
        if (outcome == null) {
            throw new DomainValidationException("Demo payment outcome обязателен");
        }
    }

    private void requireOwnerOrAudit(String clientId, String actorId, boolean auditRead) {
        if (!auditRead && !clientId.equals(actorId)) {
            throw new AccessDeniedException("Заказ принадлежит другому клиенту");
        }
    }

    private EntityNotFoundException missingReceipt(String paymentId) {
        return new EntityNotFoundException(
                "Demo payment receipt с id '%s' не найден".formatted(paymentId));
    }
}
