package dealership.order.infrastructure.web.dto.request;

import lombok.Data;

import java.util.Map;

@Data
public class CreateCustomOrderRequest {
    private String clientId;
    private String carModelId;
    private java.math.BigDecimal totalPrice;
    private Map<String, String> selectedVariants;
}
