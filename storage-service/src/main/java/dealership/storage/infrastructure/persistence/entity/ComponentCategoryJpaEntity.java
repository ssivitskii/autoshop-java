package dealership.storage.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "component_categories")
@Getter
@Setter
@NoArgsConstructor
public class ComponentCategoryJpaEntity extends BaseJpaEntity {
    @Column(nullable = false)
    private String name;

    private String description;
}
