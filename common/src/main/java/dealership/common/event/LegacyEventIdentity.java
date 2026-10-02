package dealership.common.event;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

public final class LegacyEventIdentity {
    private LegacyEventIdentity() {
    }

    public static UUID from(String topic, String eventType, String orderType, String orderId) {
        String canonical = String.join("\n", topic, eventType, orderType, orderId);
        return UUID.nameUUIDFromBytes(canonical.getBytes(StandardCharsets.UTF_8));
    }
}
