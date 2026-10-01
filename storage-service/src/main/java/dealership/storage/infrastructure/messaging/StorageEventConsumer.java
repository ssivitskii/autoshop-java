package dealership.storage.infrastructure.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import dealership.common.event.OrderApprovedEvent;
import dealership.common.event.OrderRejectedEvent;
import dealership.common.event.OrderSentForApprovalEvent;
import dealership.storage.core.application.service.AssemblyOrderService;
import dealership.storage.core.domain.entity.assembly.AssemblyOrder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
public class StorageEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(StorageEventConsumer.class);
    private static final String RESPONSE_TOPIC = "order.responses";

    private final AssemblyOrderService assemblyOrderService;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    public StorageEventConsumer(AssemblyOrderService assemblyOrderService,
                                KafkaTemplate<String, String> kafkaTemplate,
                                ObjectMapper objectMapper) {
        this.assemblyOrderService = assemblyOrderService;
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(topics = "order.events", groupId = "storage-service")
    public void handleOrderEvent(String message) {
        try {
            OrderSentForApprovalEvent event = objectMapper.readValue(message, OrderSentForApprovalEvent.class);
            log.info("Received OrderSentForApproval: orderId={}, type={}, traceId={}",
                    event.getOrderId(), event.getOrderType(), event.getTraceId());

            if (assemblyOrderService.existsBySourceOrderId(event.getOrderId())) {
                log.warn("Duplicate event for orderId={}, skipping", event.getOrderId());
                return;
            }

            AssemblyOrder assemblyOrder = new AssemblyOrder(
                    event.getOrderId(),
                    event.getOrderType(),
                    event.getTraceId()
            );

            try {
                assemblyOrder.markAssembled();
                AssemblyOrder saved = assemblyOrderService.create(assemblyOrder);

                OrderApprovedEvent approved = new OrderApprovedEvent(
                        event.getOrderId(),
                        event.getOrderType(),
                        event.getTraceId(),
                        saved.getId()
                );
                kafkaTemplate.send(RESPONSE_TOPIC, event.getOrderId(),
                        objectMapper.writeValueAsString(approved));
                log.info("Sent OrderApproved: orderId={}, assemblyId={}", event.getOrderId(), saved.getId());

            } catch (Exception e) {
                assemblyOrder.markFailed();
                assemblyOrderService.create(assemblyOrder);

                OrderRejectedEvent rejected = new OrderRejectedEvent(
                        event.getOrderId(),
                        event.getOrderType(),
                        event.getTraceId(),
                        "Сборка не удалась: " + e.getMessage()
                );
                kafkaTemplate.send(RESPONSE_TOPIC, event.getOrderId(),
                        objectMapper.writeValueAsString(rejected));
                log.error("Sent OrderRejected: orderId={}", event.getOrderId(), e);
            }

        } catch (Exception e) {
            log.error("Failed to process order event: {}", message, e);
        }
    }
}
