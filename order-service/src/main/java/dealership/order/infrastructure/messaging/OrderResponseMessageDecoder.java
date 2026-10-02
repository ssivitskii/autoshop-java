package dealership.order.infrastructure.messaging;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dealership.common.event.EventEnvelope;
import dealership.common.event.LegacyEventIdentity;
import dealership.common.event.OrderApprovedEvent;
import dealership.common.event.OrderRejectedEvent;
import dealership.common.event.PermanentMessageException;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.UUID;

@Component
public class OrderResponseMessageDecoder {
    static final String APPROVED = "OrderApproved";
    static final String REJECTED = "OrderRejected";
    private static final Set<String> ORDER_TYPES = Set.of("STOCK", "CUSTOM");

    private final ObjectMapper objectMapper;

    public OrderResponseMessageDecoder(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public DecodedResponse decode(ConsumerRecord<String, String> record) {
        try {
            JsonNode root = objectMapper.readTree(record.value());
            if (root != null && root.isTextual()) {
                root = objectMapper.readTree(root.textValue());
            }
            if (root == null || !root.isObject()) {
                throw new PermanentMessageException("Kafka response must be a JSON object");
            }
            return root.has("eventId") ? decodeEnvelope(record, root) : decodeLegacy(record, root);
        } catch (PermanentMessageException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new PermanentMessageException("Malformed Kafka response", exception);
        }
    }

    private DecodedResponse decodeEnvelope(ConsumerRecord<String, String> record, JsonNode root) throws Exception {
        UUID eventId = parseUuid(requiredText(root, "eventId"));
        String eventType = requiredText(root, "eventType");
        if (!root.path("version").isIntegralNumber()
                || !root.path("version").canConvertToInt()
                || root.path("version").asInt() != EventEnvelope.CURRENT_VERSION) {
            throw new PermanentMessageException("Unsupported event envelope version");
        }
        String aggregateId = requiredText(root, "aggregateId");
        String traceId = requiredText(root, "traceId");
        JsonNode payload = root.get("payload");
        if (payload == null || !payload.isObject()) {
            throw new PermanentMessageException("Event payload must be a JSON object");
        }
        Object body = readBody(eventType, payload);
        validate(record.key(), aggregateId, traceId, body);
        return new DecodedResponse(eventId, eventType, body);
    }

    private DecodedResponse decodeLegacy(ConsumerRecord<String, String> record, JsonNode root) throws Exception {
        boolean approved = root.hasNonNull("assemblyOrderId");
        boolean rejected = root.hasNonNull("reason");
        if (approved == rejected) {
            throw new PermanentMessageException("Legacy response has an ambiguous shape");
        }
        String eventType = approved ? APPROVED : REJECTED;
        Object body = readBody(eventType, root);
        validate(record.key(), orderId(body), traceId(body), body);
        UUID eventId = LegacyEventIdentity.from(record.topic(), eventType, orderType(body), orderId(body));
        return new DecodedResponse(eventId, eventType, body);
    }

    private Object readBody(String eventType, JsonNode payload) throws Exception {
        requireText(payload, "orderId");
        requireText(payload, "orderType");
        if (payload.has("traceId") && !payload.get("traceId").isNull() && !payload.get("traceId").isTextual()) {
            throw new PermanentMessageException("payload traceId must be a string");
        }
        return switch (eventType) {
            case APPROVED -> {
                requireText(payload, "assemblyOrderId");
                yield objectMapper.treeToValue(payload, OrderApprovedEvent.class);
            }
            case REJECTED -> {
                requireText(payload, "reason");
                yield objectMapper.treeToValue(payload, OrderRejectedEvent.class);
            }
            default -> throw new PermanentMessageException("Unknown order response event type: " + eventType);
        };
    }

    private void validate(String key, String aggregateId, String envelopeTraceId, Object body) {
        String orderId = orderId(body);
        String orderType = orderType(body);
        if (orderId == null || orderId.isBlank() || !orderId.equals(aggregateId) || !orderId.equals(key)) {
            throw new PermanentMessageException("Kafka key, aggregateId and payload orderId must match");
        }
        parseUuid(orderId);
        if (!ORDER_TYPES.contains(orderType)) {
            throw new PermanentMessageException("Unsupported order type: " + orderType);
        }
        String bodyTraceId = traceId(body);
        if (bodyTraceId != null && !bodyTraceId.isBlank() && !bodyTraceId.equals(envelopeTraceId)) {
            throw new PermanentMessageException("Envelope and payload traceId must match");
        }
        if (body instanceof OrderApprovedEvent approved) {
            if (approved.getAssemblyOrderId() == null || approved.getAssemblyOrderId().isBlank()) {
                throw new PermanentMessageException("Approved response requires assemblyOrderId");
            }
            parseUuid(approved.getAssemblyOrderId());
        } else if (((OrderRejectedEvent) body).getReason() == null
                || ((OrderRejectedEvent) body).getReason().isBlank()) {
            throw new PermanentMessageException("Rejected response requires reason");
        }
    }

    private String orderId(Object body) {
        return body instanceof OrderApprovedEvent approved ? approved.getOrderId() : ((OrderRejectedEvent) body).getOrderId();
    }

    private String orderType(Object body) {
        return body instanceof OrderApprovedEvent approved ? approved.getOrderType() : ((OrderRejectedEvent) body).getOrderType();
    }

    private String traceId(Object body) {
        return body instanceof OrderApprovedEvent approved ? approved.getTraceId() : ((OrderRejectedEvent) body).getTraceId();
    }

    private String requiredText(JsonNode root, String field) {
        JsonNode value = root.get(field);
        if (value == null || !value.isTextual() || value.textValue().isBlank()) {
            throw new PermanentMessageException("Missing envelope field: " + field);
        }
        return value.textValue();
    }

    private void requireText(JsonNode root, String field) {
        requiredText(root, field);
    }

    private UUID parseUuid(String value) {
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException exception) {
            throw new PermanentMessageException("eventId must be a UUID", exception);
        }
    }

    public record DecodedResponse(UUID eventId, String eventType, Object body) {
    }
}
