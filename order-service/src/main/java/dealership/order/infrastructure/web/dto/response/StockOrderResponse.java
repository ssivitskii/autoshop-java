package dealership.order.infrastructure.web.dto.response;

import lombok.Data;

@Data
public class StockOrderResponse {
    private String id;
    private String clientId;
    private String managerId;
    private String carId;
    private String status;
}