package dealership.storage.infrastructure.persistence.mapper;

import dealership.storage.core.domain.entity.component.ComponentCategory;
import dealership.storage.infrastructure.persistence.entity.ComponentCategoryJpaEntity;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class ComponentCategoryPersistenceMapper {
    public ComponentCategory toDomain(ComponentCategoryJpaEntity entity) {
        return new ComponentCategory(entity.getId().toString(), entity.getName(), entity.getDescription());
    }

    public ComponentCategoryJpaEntity toJpa(ComponentCategory domain) {
        ComponentCategoryJpaEntity entity = new ComponentCategoryJpaEntity();
        if (domain.getId() != null) {
            entity.setId(UUID.fromString(domain.getId()));
        }
        entity.setName(domain.getName());
        entity.setDescription(domain.getDescription());
        return entity;
    }
}
