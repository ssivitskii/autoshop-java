package dealership.common.event;

import java.util.UUID;

public record EventEnvelope<T>(
        UUID eventId,
        String eventType,
        int version,
        String aggregateId,
        String traceId,
        T payload
) {
    public static final int CURRENT_VERSION = 1;
}
