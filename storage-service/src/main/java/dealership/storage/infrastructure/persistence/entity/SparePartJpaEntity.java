package dealership.storage.infrastructure.persistence.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "spare_parts")
@Getter
@Setter
@NoArgsConstructor
public class SparePartJpaEntity extends BaseJpaEntity {

    @Column(nullable = false)
    private String name;

    private String manufacturer;

    @Column(nullable = false)
    private String partNumber;

    @Column(nullable = false)
    private BigDecimal price;

    @Column(nullable = false)
    private int quantityInStock;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "spare_part_compatible_models", joinColumns = @JoinColumn(name = "spare_part_id"))
    @Column(name = "model_name")
    private Set<String> compatibleModels = new HashSet<>();
}
