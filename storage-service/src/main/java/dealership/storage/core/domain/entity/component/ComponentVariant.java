package dealership.storage.core.domain.entity.component;

import dealership.storage.core.domain.exception.DomainValidationException;
import lombok.*;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.Set;
import java.util.TreeSet;

@Getter
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@ToString
public class ComponentVariant {
    @EqualsAndHashCode.Include
    private final String id;

    private final String name;
    private final String categoryId;
    private final BigDecimal priceAdjustment;
    private final boolean isBase;

    @Setter(AccessLevel.NONE)
    private final Set<String> compatibleCarModelIds;

    public ComponentVariant(String name, String categoryId, BigDecimal priceAdjustment, boolean isBase) {
        this(null, name, categoryId, priceAdjustment, isBase);
    }

    public ComponentVariant(String id, String name, String categoryId, BigDecimal priceAdjustment, boolean isBase) {
        if (name == null || name.isEmpty()) {
            throw new DomainValidationException("Название варианта компонента не может быть пустым");
        }
        if (categoryId == null || categoryId.isEmpty()) {
            throw new DomainValidationException("ID категории не может быть пустым");
        }
        if (priceAdjustment == null) {
            throw new DomainValidationException("Доплата не может быть null");
        }
        this.id = id;
        this.name = name;
        this.categoryId = categoryId;
        this.priceAdjustment = priceAdjustment;
        this.isBase = isBase;
        this.compatibleCarModelIds = new TreeSet<>();
    }

    public void addCompatibleCarModel(String carModelId) {
        if (carModelId == null || carModelId.isBlank()) {
            throw new DomainValidationException("ID модели автомобиля не может быть пустым");
        }
        compatibleCarModelIds.add(carModelId);
    }

    public boolean isCompatibleWith(String carModelId) {
        return compatibleCarModelIds.contains(carModelId);
    }

    public Set<String> getCompatibleCarModelIds() {
        return Collections.unmodifiableSet(compatibleCarModelIds);
    }
}
