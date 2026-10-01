package dealership.storage.core.application.dto;

import dealership.storage.core.domain.entity.car.CarConfiguration;
import dealership.storage.core.domain.entity.component.ComponentVariant;

import java.math.BigDecimal;
import java.util.List;

public class ConfigurationResultDto {
    private final CarConfiguration configuration;
    private final BigDecimal totalPrice;
    private final List<ComponentVariant> selectedComponents;
    private final String carModelName;

    public ConfigurationResultDto(CarConfiguration configuration, BigDecimal totalPrice, List<ComponentVariant> selectedComponents, String carModelName) {
        this.configuration = configuration;
        this.totalPrice = totalPrice;
        this.selectedComponents = selectedComponents;
        this.carModelName = carModelName;
    }

    public CarConfiguration getConfiguration() {
        return configuration;
    }

    public BigDecimal getTotalPrice() {
        return totalPrice;
    }

    public List<ComponentVariant> getSelectedComponents() {
        return selectedComponents;
    }

    public String getCarModelName() {
        return carModelName;
    }

    @Override
    public String toString() {
        return "ConfigurationResult{" +
                "model='" + carModelName + '\'' +
                ", totalPrice=" + totalPrice +
                ", components=" + selectedComponents.size() +
                '}';
    }
}
