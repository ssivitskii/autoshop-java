package dealership.order.infrastructure.web.dto.request;

import lombok.Data;

@Data
public class CreateStockOrderRequest {
    private String clientId;
    private String carId;
}