package dealership.storage.infrastructure.web.dto.response;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Data
public class ConfigurationResultResponse {
    private String carModelName;
    private BigDecimal totalPrice;
    private Map<String, String> selectedVariants;
    private List<ComponentVariantResponse> components;

    @Data
    public static class ComponentVariantResponse {
        private String id;
        private String name;
        private String categoryId;
        private BigDecimal priceAdjustment;
        private boolean isBase;
    }
}
