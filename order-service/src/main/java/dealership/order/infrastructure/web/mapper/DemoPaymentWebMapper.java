package dealership.order.infrastructure.web.mapper;

import dealership.order.infrastructure.persistence.entity.DemoPaymentAttemptJpaEntity;
import dealership.order.infrastructure.web.dto.response.DemoPaymentReceiptResponse;
import org.springframework.stereotype.Component;

@Component
public class DemoPaymentWebMapper {
    public DemoPaymentReceiptResponse toResponse(DemoPaymentAttemptJpaEntity payment) {
        DemoPaymentReceiptResponse response = new DemoPaymentReceiptResponse();
        response.setId(payment.getId());
        response.setOrderType(payment.getOrderType().name());
        response.setOrderId(payment.getOrderId());
        response.setIdempotencyKey(payment.getIdempotencyKey());
        response.setRequestedOutcome(payment.getRequestedOutcome().name());
        response.setStatus(payment.getStatus().name());
        response.setFailureCode(payment.getFailureCode());
        response.setCreatedAt(payment.getCreatedAt());
        response.setUpdatedAt(payment.getUpdatedAt());
        return response;
    }
}
