package dealership.order;

import com.fasterxml.jackson.databind.ObjectMapper;
import dealership.common.event.EventEnvelope;
import dealership.common.event.OrderApprovedEvent;
import dealership.common.event.PermanentMessageException;
import dealership.order.infrastructure.messaging.OrderResponseMessageDecoder;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class OrderEventConsumerTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private final OrderResponseMessageDecoder decoder = new OrderResponseMessageDecoder(mapper);

    @Test
    void decodesVersionedApprovedEnvelope() throws Exception {
        UUID eventId = UUID.randomUUID();
        String orderId = UUID.randomUUID().toString();
        String traceId = UUID.randomUUID().toString();
        OrderApprovedEvent body = new OrderApprovedEvent(
                orderId, "STOCK", traceId, UUID.randomUUID().toString());
        EventEnvelope<OrderApprovedEvent> envelope = new EventEnvelope<>(
                eventId, "OrderApproved", 1, orderId, traceId, body);

        var decoded = decoder.decode(record(orderId, mapper.writeValueAsString(envelope)));

        assertEquals(eventId, decoded.eventId());
        assertEquals(orderId, ((OrderApprovedEvent) decoded.body()).getOrderId());
    }

    @Test
    void legacyResponseHasDeterministicIdentity() throws Exception {
        String orderId = UUID.randomUUID().toString();
        OrderApprovedEvent body = new OrderApprovedEvent(
                orderId, "CUSTOM", null, UUID.randomUUID().toString());
        String message = mapper.writeValueAsString(body);

        assertEquals(decoder.decode(record(orderId, message)).eventId(),
                decoder.decode(record(orderId, message)).eventId());
    }

    @Test
    void rejectsFractionalVersionAndMissingTypedPayloadField() throws Exception {
        String orderId = UUID.randomUUID().toString();
        Map<String, Object> fractional = Map.of(
                "eventId", UUID.randomUUID(), "eventType", "OrderApproved", "version", 1.5,
                "aggregateId", orderId, "traceId", "trace",
                "payload", Map.of("orderId", orderId, "orderType", "STOCK", "traceId", "trace",
                        "assemblyOrderId", UUID.randomUUID().toString()));
        Map<String, Object> missingAssemblyId = Map.of(
                "eventId", UUID.randomUUID(), "eventType", "OrderApproved", "version", 1,
                "aggregateId", orderId, "traceId", "trace",
                "payload", Map.of("orderId", orderId, "orderType", "STOCK", "traceId", "trace"));

        assertThrows(PermanentMessageException.class,
                () -> decoder.decode(record(orderId, mapper.writeValueAsString(fractional))));
        assertThrows(PermanentMessageException.class,
                () -> decoder.decode(record(orderId, mapper.writeValueAsString(missingAssemblyId))));
    }

    @Test
    void rejectsMismatchedKafkaKey() throws Exception {
        String orderId = UUID.randomUUID().toString();
        OrderApprovedEvent body = new OrderApprovedEvent(
                orderId, "STOCK", null, UUID.randomUUID().toString());

        assertThrows(PermanentMessageException.class,
                () -> decoder.decode(record(UUID.randomUUID().toString(), mapper.writeValueAsString(body))));
    }

    private ConsumerRecord<String, String> record(String key, String value) {
        return new ConsumerRecord<>("order.responses", 0, 10, key, value);
    }
}
