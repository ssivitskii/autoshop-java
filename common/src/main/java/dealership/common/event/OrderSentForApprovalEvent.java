package dealership.common.event;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class OrderSentForApprovalEvent {
    private String orderId;
    private String orderType;
    private String traceId;
    private String carId;
    private String carModelId;
    private Map<String, String> selectedVariants;
    private BigDecimal totalPrice;
}