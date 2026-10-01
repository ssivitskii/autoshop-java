package dealership.order.infrastructure.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import dealership.common.event.OrderSentForApprovalEvent;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Component
public class OrderEventProducer {

    private final OutboxRepository outboxRepository;
    private final ObjectMapper objectMapper;

    public OrderEventProducer(OutboxRepository outboxRepository, ObjectMapper objectMapper) {
        this.outboxRepository = outboxRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public void publishOrderPaid(OrderSentForApprovalEvent event) {
        try {
            OutboxEvent outbox = new OutboxEvent();
            outbox.setAggregateType(event.getOrderType());
            outbox.setAggregateId(event.getOrderId());
            outbox.setEventType("OrderSentForApproval");
            outbox.setTraceId(event.getTraceId() != null ? event.getTraceId() : UUID.randomUUID().toString());
            outbox.setPayload(objectMapper.writeValueAsString(event));
            outbox.setSent(false);
            outboxRepository.save(outbox);
        } catch (Exception e) {
            throw new RuntimeException("Failed to write outbox event", e);
        }
    }
}
