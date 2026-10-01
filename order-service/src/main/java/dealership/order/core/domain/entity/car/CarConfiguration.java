package dealership.order.core.domain.entity.car;

import dealership.order.core.domain.exception.DomainValidationException;
import lombok.*;

import java.util.HashMap;
import java.util.Map;

@Getter
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@ToString
public class CarConfiguration {
    @EqualsAndHashCode.Include
    private final String id;

    private final String carModelId;

    @Setter(AccessLevel.NONE)
    private final Map<String, String> selectedVariants;

    public CarConfiguration(String carModelId) {
        this(null, carModelId);
    }

    public CarConfiguration(String id, String carModelId) {
        this.id = id;
        this.carModelId = carModelId;
        this.selectedVariants = new HashMap<>();
    }

    public void selectVariant(String categoryId, String variantId) {
        if (categoryId == null || categoryId.isBlank()) {
            throw new DomainValidationException("ID категории не может быть пустым");
        }
        if (variantId == null || variantId.isBlank()) {
            throw new DomainValidationException("ID варианта не может быть пустым");
        }
        this.selectedVariants.put(categoryId, variantId);
    }

    public Map<String, String> getSelectedVariants() {
        return new HashMap<>(selectedVariants);
    }

    public boolean hasCategory(String categoryId) {
        return selectedVariants.containsKey(categoryId);
    }
}