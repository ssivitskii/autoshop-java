package dealership.storage.infrastructure.messaging;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dealership.common.event.EventEnvelope;
import dealership.common.event.LegacyEventIdentity;
import dealership.common.event.OrderSentForApprovalEvent;
import dealership.common.event.PermanentMessageException;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.UUID;

@Component
public class StorageOrderMessageDecoder {
    static final String EVENT_TYPE = "OrderSentForApproval";
    private static final Set<String> ORDER_TYPES = Set.of("STOCK", "CUSTOM");

    private final ObjectMapper objectMapper;

    public StorageOrderMessageDecoder(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public DecodedOrder decode(ConsumerRecord<String, String> record) {
        try {
            JsonNode root = objectMapper.readTree(record.value());
            if (root != null && root.isTextual()) {
                root = objectMapper.readTree(root.textValue());
            }
            if (root == null || !root.isObject()) {
                throw new PermanentMessageException("Kafka order event must be a JSON object");
            }
            return root.has("eventId") ? decodeEnvelope(record, root) : decodeLegacy(record, root);
        } catch (PermanentMessageException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new PermanentMessageException("Malformed Kafka order event", exception);
        }
    }

    private DecodedOrder decodeEnvelope(ConsumerRecord<String, String> record, JsonNode root) throws Exception {
        UUID eventId = parseUuid(requiredText(root, "eventId"));
        String eventType = requiredText(root, "eventType");
        if (!EVENT_TYPE.equals(eventType)) {
            throw new PermanentMessageException("Unknown order event type: " + eventType);
        }
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
        validatePayloadShape(payload);
        OrderSentForApprovalEvent event = objectMapper.treeToValue(payload, OrderSentForApprovalEvent.class);
        validate(record.key(), aggregateId, traceId, event);
        return new DecodedOrder(eventId, event);
    }

    private DecodedOrder decodeLegacy(ConsumerRecord<String, String> record, JsonNode root) throws Exception {
        if (root.has("assemblyOrderId") || root.has("reason")) {
            throw new PermanentMessageException("Unexpected response shape on order.events");
        }
        validatePayloadShape(root);
        OrderSentForApprovalEvent event = objectMapper.treeToValue(root, OrderSentForApprovalEvent.class);
        validate(record.key(), event.getOrderId(), event.getTraceId(), event);
        UUID eventId = LegacyEventIdentity.from(
                record.topic(), EVENT_TYPE, event.getOrderType(), event.getOrderId());
        return new DecodedOrder(eventId, event);
    }

    private void validate(String key, String aggregateId, String envelopeTraceId,
                          OrderSentForApprovalEvent event) {
        if (event.getOrderId() == null || event.getOrderId().isBlank()
                || !event.getOrderId().equals(aggregateId) || !event.getOrderId().equals(key)) {
            throw new PermanentMessageException("Kafka key, aggregateId and payload orderId must match");
        }
        parseUuid(event.getOrderId());
        if (!ORDER_TYPES.contains(event.getOrderType())) {
            throw new PermanentMessageException("Unsupported order type: " + event.getOrderType());
        }
        if ("STOCK".equals(event.getOrderType())) {
            parseUuid(event.getCarId());
        } else {
            parseUuid(event.getCarModelId());
            if (event.getTotalPrice() == null || event.getTotalPrice().signum() < 0
                    || event.getSelectedVariants() == null) {
                throw new PermanentMessageException("Custom order payload is incomplete");
            }
        }
        if (event.getTraceId() != null && !event.getTraceId().isBlank()
                && !event.getTraceId().equals(envelopeTraceId)) {
            throw new PermanentMessageException("Envelope and payload traceId must match");
        }
    }

    private String requiredText(JsonNode root, String field) {
        JsonNode value = root.get(field);
        if (value == null || !value.isTextual() || value.textValue().isBlank()) {
            throw new PermanentMessageException("Missing envelope field: " + field);
        }
        return value.textValue();
    }

    private void validatePayloadShape(JsonNode payload) {
        requiredText(payload, "orderId");
        String orderType = requiredText(payload, "orderType");
        if (payload.has("traceId") && !payload.get("traceId").isNull() && !payload.get("traceId").isTextual()) {
            throw new PermanentMessageException("payload traceId must be a string");
        }
        if ("STOCK".equals(orderType)) {
            requiredText(payload, "carId");
        } else if ("CUSTOM".equals(orderType)) {
            requiredText(payload, "carModelId");
            if (!payload.has("totalPrice") || !payload.get("totalPrice").isNumber()
                    || !payload.has("selectedVariants") || !payload.get("selectedVariants").isObject()) {
                throw new PermanentMessageException("Custom order payload has invalid field types");
            }
        }
    }

    private UUID parseUuid(String value) {
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException exception) {
            throw new PermanentMessageException("Identifier must be a UUID", exception);
        }
    }

    public record DecodedOrder(UUID eventId, OrderSentForApprovalEvent event) {
    }
}
