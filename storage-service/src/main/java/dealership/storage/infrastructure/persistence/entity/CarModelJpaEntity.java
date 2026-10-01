package dealership.storage.infrastructure.persistence.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.Set;


@Entity
@Table(name = "car_models")
@Getter
@Setter
@NoArgsConstructor
public class CarModelJpaEntity extends BaseJpaEntity {
    @Column(nullable = false)
    private String brand;

    @Column(nullable = false)
    private String modelName;

    @Column(nullable = false)
    private BigDecimal basePrice;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "car_model_categories", joinColumns = @JoinColumn(name = "car_model_id"))
    @Column(name = "category_id")
    private Set<String> componentCategoryIds = new HashSet<>();
}