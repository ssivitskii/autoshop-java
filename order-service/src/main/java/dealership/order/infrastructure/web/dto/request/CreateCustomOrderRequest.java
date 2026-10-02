package dealership.order.infrastructure.web.dto.request;

import lombok.Data;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.Map;

@Data
public class CreateCustomOrderRequest {
    private String clientId;
    private String carModelId;
    @Deprecated
    @Schema(deprecated = true, description = "Игнорируется: итоговую цену рассчитывает storage-service")
    private java.math.BigDecimal totalPrice;
    private Map<String, String> selectedVariants;
}
