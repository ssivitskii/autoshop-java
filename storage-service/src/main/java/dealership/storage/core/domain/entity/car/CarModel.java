package dealership.storage.core.domain.entity.car;

import dealership.storage.core.domain.exception.DomainValidationException;
import lombok.*;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Getter
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@ToString
public class CarModel {
    @EqualsAndHashCode.Include
    private final String id;

    private String brand;
    private String modelName;
    private BigDecimal basePrice;

    @Setter(AccessLevel.NONE)
    private final Set<String> componentCategoryIds;

    public CarModel(String brand, String modelName, BigDecimal basePrice) {
        this(null, brand, modelName, basePrice);
    }

    public CarModel(String id, String brand, String modelName, BigDecimal basePrice) {
        if (brand == null) {
            throw new DomainValidationException("brand cannot be null");
        }
        if (modelName == null) {
            throw new DomainValidationException("modelName cannot be null");
        }
        if (basePrice == null) {
            throw new DomainValidationException("basePrice cannot be null");
        }
        this.id = (id != null) ? id : UUID.randomUUID().toString();
        this.brand = brand;
        this.modelName = modelName;
        this.basePrice = basePrice;
        this.componentCategoryIds = new HashSet<>();
    }

    public void addComponentCategory(String categoryId) {
        if (categoryId == null) {
            throw new DomainValidationException("categoryId cannot be null");
        }
        this.componentCategoryIds.add(categoryId);
    }

    public Set<String> getComponentCategoryIds() {
        return Collections.unmodifiableSet(componentCategoryIds);
    }

    public String getFullModelName() {
        return brand + '.' + modelName;
    }
}