package dealership.order.infrastructure.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import dealership.common.event.OrderApprovedEvent;
import dealership.common.event.OrderRejectedEvent;
import dealership.order.core.application.service.CustomOrderService;
import dealership.order.core.application.service.StockOrderService;
import dealership.order.core.domain.exception.StorageUnavailableException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class OrderEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(OrderEventConsumer.class);

    private final StockOrderService stockOrderService;
    private final CustomOrderService customOrderService;
    private final ObjectMapper objectMapper;

    public OrderEventConsumer(StockOrderService stockOrderService,
                              CustomOrderService customOrderService,
                              ObjectMapper objectMapper) {
        this.stockOrderService = stockOrderService;
        this.customOrderService = customOrderService;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(topics = "order.responses", groupId = "order-service")
    public void handleOrderResponse(String message) {
        try {
            if (message.contains("\"assemblyOrderId\"")) {
                OrderApprovedEvent event = objectMapper.readValue(message, OrderApprovedEvent.class);
                log.info("Received OrderApproved: orderId={}, traceId={}", event.getOrderId(), event.getTraceId());
                handleApproved(event);
            } else if (message.contains("\"reason\"")) {
                OrderRejectedEvent event = objectMapper.readValue(message, OrderRejectedEvent.class);
                log.info("Received OrderRejected: orderId={}, reason={}, traceId={}", event.getOrderId(), event.getReason(), event.getTraceId());
                handleRejected(event);
            } else {
                log.warn("Unknown message on order.responses: {}", message);
            }
        } catch (StorageUnavailableException e) {
            log.warn("Storage unavailable while processing order response; message will be retried", e);
            throw e;
        } catch (Exception e) {
            log.error("Failed to process order response: {}", message, e);
        }
    }

    private void handleApproved(OrderApprovedEvent event) {
        if ("STOCK".equals(event.getOrderType())) {
            stockOrderService.advanceOrder(event.getOrderId());
        } else if ("CUSTOM".equals(event.getOrderType())) {
            customOrderService.advanceOrder(event.getOrderId());
        }
    }

    private void handleRejected(OrderRejectedEvent event) {
        if ("STOCK".equals(event.getOrderType())) {
            stockOrderService.cancelOrder(event.getOrderId());
        } else if ("CUSTOM".equals(event.getOrderType())) {
            customOrderService.cancelOrder(event.getOrderId());
        }
    }
}
