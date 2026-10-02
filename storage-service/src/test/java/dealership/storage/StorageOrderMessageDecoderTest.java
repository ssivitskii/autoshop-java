package dealership.storage;

import com.fasterxml.jackson.databind.ObjectMapper;
import dealership.common.event.EventEnvelope;
import dealership.common.event.OrderSentForApprovalEvent;
import dealership.common.event.PermanentMessageException;
import dealership.storage.infrastructure.messaging.StorageOrderMessageDecoder;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class StorageOrderMessageDecoderTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private final StorageOrderMessageDecoder decoder = new StorageOrderMessageDecoder(mapper);

    @Test
    void decodesEnvelopeAndOneLegacyStringLayer() throws Exception {
        String orderId = UUID.randomUUID().toString();
        String traceId = UUID.randomUUID().toString();
        OrderSentForApprovalEvent body = new OrderSentForApprovalEvent();
        body.setOrderId(orderId);
        body.setOrderType("STOCK");
        body.setTraceId(traceId);
        body.setCarId(UUID.randomUUID().toString());
        UUID eventId = UUID.randomUUID();
        EventEnvelope<OrderSentForApprovalEvent> envelope = new EventEnvelope<>(
                eventId, "OrderSentForApproval", 1, orderId, traceId, body);

        assertEquals(eventId, decoder.decode(record(orderId, mapper.writeValueAsString(envelope))).eventId());
        String legacy = mapper.writeValueAsString(body);
        assertEquals(orderId, decoder.decode(record(orderId, mapper.writeValueAsString(legacy))).event().getOrderId());
    }

    @Test
    void rejectsUnknownVersionAndMissingOrderType() throws Exception {
        String orderId = UUID.randomUUID().toString();
        Map<String, Object> unknownVersion = Map.of(
                "eventId", UUID.randomUUID(), "eventType", "OrderSentForApproval", "version", 2,
                "aggregateId", orderId, "traceId", "trace",
                "payload", Map.of("orderId", orderId, "orderType", "STOCK", "traceId", "trace",
                        "carId", UUID.randomUUID().toString()));
        Map<String, Object> missingType = Map.of("orderId", orderId, "traceId", "trace");

        assertThrows(PermanentMessageException.class,
                () -> decoder.decode(record(orderId, mapper.writeValueAsString(unknownVersion))));
        assertThrows(PermanentMessageException.class,
                () -> decoder.decode(record(orderId, mapper.writeValueAsString(missingType))));
    }

    private ConsumerRecord<String, String> record(String key, String value) {
        return new ConsumerRecord<>("order.events", 0, 4, key, value);
    }
}
