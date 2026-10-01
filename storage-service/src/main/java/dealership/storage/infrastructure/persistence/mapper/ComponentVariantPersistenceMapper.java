package dealership.storage.infrastructure.persistence.mapper;

import dealership.storage.core.domain.entity.component.ComponentVariant;
import dealership.storage.infrastructure.persistence.entity.ComponentVariantJpaEntity;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.UUID;

@Component
public class ComponentVariantPersistenceMapper {

    public ComponentVariant toDomain(ComponentVariantJpaEntity entity) {
        ComponentVariant variant = new ComponentVariant(entity.getId().toString(), entity.getName(), entity.getCategoryId(), entity.getPriceAdjustment(), entity.isBase());
        entity.getCompatibleCarModelIds().forEach(variant::addCompatibleCarModel);
        return variant;
    }

    public ComponentVariantJpaEntity toJpa(ComponentVariant domain) {
        ComponentVariantJpaEntity entity = new ComponentVariantJpaEntity();
        if (domain.getId() != null) {
            entity.setId(UUID.fromString(domain.getId()));
        }
        entity.setName(domain.getName());
        entity.setCategoryId(domain.getCategoryId());
        entity.setPriceAdjustment(domain.getPriceAdjustment());
        entity.setBase(domain.isBase());
        entity.setCompatibleCarModelIds(new HashSet<>(domain.getCompatibleCarModelIds()));
        return entity;
    }
}
