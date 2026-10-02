package dealership.order.infrastructure.messaging;

import dealership.common.event.OrderApprovedEvent;
import dealership.common.event.OrderRejectedEvent;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class OrderEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(OrderEventConsumer.class);

    private final OrderResponseMessageDecoder decoder;
    private final OrderResponseMessageTransactions transactions;

    public OrderEventConsumer(OrderResponseMessageDecoder decoder,
                              OrderResponseMessageTransactions transactions) {
        this.decoder = decoder;
        this.transactions = transactions;
    }

    @KafkaListener(topics = "order.responses", groupId = "order-service")
    public void handleOrderResponse(ConsumerRecord<String, String> record) {
        OrderResponseMessageDecoder.DecodedResponse decoded = decoder.decode(record);
        if (decoded.body() instanceof OrderApprovedEvent approved) {
            transactions.processApproved(decoded.eventId(), record.topic(), record.partition(), record.offset(), approved);
            log.info("Processed OrderApproved: orderId={}, eventId={}", approved.getOrderId(), decoded.eventId());
        } else {
            OrderRejectedEvent rejected = (OrderRejectedEvent) decoded.body();
            transactions.processRejected(decoded.eventId(), record.topic(), record.partition(), record.offset(), rejected);
            log.info("Processed OrderRejected: orderId={}, eventId={}", rejected.getOrderId(), decoded.eventId());
        }
    }
}
