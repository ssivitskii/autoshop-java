package dealership.storage.core.application.dto;

import java.util.HashMap;
import java.util.Map;

public class CarConfigurationRequestDto {
    private String carModelId;
    private Map<String, String> selectedVariants;

    public CarConfigurationRequestDto(String carModelId) {
        this.carModelId = carModelId;
        this.selectedVariants = new HashMap<>();
    }

    public void addVariant(String categoryId, String variantId) {
        this.selectedVariants.put(categoryId, variantId);
    }

    public String getCarModelId() {
        return carModelId;
    }

    public Map<String, String> getSelectedVariants() {
        return selectedVariants;
    }

    public void setCarModelId(String carModelId) {
        this.carModelId = carModelId;
    }

    public void setSelectedVariants(Map<String, String> selectedVariants) {
        this.selectedVariants = selectedVariants;
    }
}
