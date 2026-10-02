package dealership.storage.infrastructure.messaging;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class StorageEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(StorageEventConsumer.class);
    private final StorageOrderMessageDecoder decoder;
    private final StorageOrderMessageTransactions transactions;

    public StorageEventConsumer(StorageOrderMessageDecoder decoder,
                                StorageOrderMessageTransactions transactions) {
        this.decoder = decoder;
        this.transactions = transactions;
    }

    @KafkaListener(topics = "order.events", groupId = "storage-service")
    public void handleOrderEvent(ConsumerRecord<String, String> record) {
        StorageOrderMessageDecoder.DecodedOrder decoded = decoder.decode(record);
        transactions.process(decoded.eventId(), record.topic(), record.partition(), record.offset(), decoded.event());
        log.info("Processed OrderSentForApproval: orderId={}, eventId={}",
                decoded.event().getOrderId(), decoded.eventId());
    }
}
