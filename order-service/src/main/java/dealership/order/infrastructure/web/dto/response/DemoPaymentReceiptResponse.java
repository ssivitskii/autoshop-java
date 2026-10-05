package dealership.order.infrastructure.web.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.Instant;
import java.util.UUID;

@Data
@Schema(description = "Durable receipt for the demo-only payment workflow; not proof of a real charge")
public class DemoPaymentReceiptResponse {
    private UUID id;
    private String orderType;
    private UUID orderId;
    private UUID idempotencyKey;
    private String requestedOutcome;
    private String status;
    private String failureCode;
    private Instant createdAt;
    private Instant updatedAt;
}
