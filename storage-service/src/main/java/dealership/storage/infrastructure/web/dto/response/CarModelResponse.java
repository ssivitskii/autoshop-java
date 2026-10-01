package dealership.storage.infrastructure.web.dto.response;

import lombok.Data;

import java.math.BigDecimal;
import java.util.Set;

@Data
public class CarModelResponse {
    private String id;
    private String brand;
    private String modelName;
    private BigDecimal basePrice;
    private Set<String> componentCategoryIds;
}