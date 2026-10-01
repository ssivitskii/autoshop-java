package dealership.order.infrastructure.web.dto.response;

import lombok.Data;

import java.math.BigDecimal;
import java.util.Map;

@Data
public class CustomOrderResponse {
    private String id;
    private String clientId;
    private String managerId;
    private String carModelId;
    private BigDecimal totalPrice;
    private String status;
    private Map<String, String> selectedVariants;
}