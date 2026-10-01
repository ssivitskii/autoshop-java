package dealership.storage.infrastructure.persistence.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "component_variants")
@Getter
@Setter
@NoArgsConstructor
public class ComponentVariantJpaEntity extends BaseJpaEntity {
    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String categoryId;

    @Column(nullable = false)
    private BigDecimal priceAdjustment;

    @Column(nullable = false)
    private boolean isBase;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "variant_compatible_models", joinColumns = @JoinColumn(name = "variant_id"))
    @Column(name = "car_model_id")
    private Set<String> compatibleCarModelIds = new HashSet<>();
}
