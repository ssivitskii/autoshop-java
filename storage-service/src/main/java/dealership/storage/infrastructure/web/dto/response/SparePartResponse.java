package dealership.storage.infrastructure.web.dto.response;

import lombok.Data;

import java.math.BigDecimal;
import java.util.Set;

@Data
public class SparePartResponse {
    private String id;
    private String name;
    private String manufacturer;
    private String partNumber;
    private BigDecimal price;
    private int quantityInStock;
    private Set<String> compatibleModels;
}
