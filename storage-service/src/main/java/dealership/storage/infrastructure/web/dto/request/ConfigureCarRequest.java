package dealership.storage.infrastructure.web.dto.request;

import lombok.Data;

import java.util.Map;

@Data
public class ConfigureCarRequest {
    private String carModelId;
    private Map<String, String> selectedVariants;
}
