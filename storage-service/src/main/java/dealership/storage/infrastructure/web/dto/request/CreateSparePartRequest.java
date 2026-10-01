package dealership.storage.infrastructure.web.dto.request;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class CreateSparePartRequest {
    private String name;
    private String manufacturer;
    private String partNumber;
    private BigDecimal price;
    private int quantityInStock;
    private String compatibleModels;
}
