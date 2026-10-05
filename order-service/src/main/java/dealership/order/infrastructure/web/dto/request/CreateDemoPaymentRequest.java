package dealership.order.infrastructure.web.dto.request;

import dealership.order.core.domain.enums.DemoPaymentOutcome;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "Demo-only workflow input; no card, bank, amount or real debit data")
public class CreateDemoPaymentRequest {
    @Schema(description = "Caller-controlled simulated outcome", requiredMode = Schema.RequiredMode.REQUIRED)
    private DemoPaymentOutcome outcome;
}
