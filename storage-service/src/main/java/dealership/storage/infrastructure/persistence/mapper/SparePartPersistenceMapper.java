package dealership.storage.infrastructure.persistence.mapper;

import dealership.storage.core.domain.entity.part.SparePart;
import dealership.storage.infrastructure.persistence.entity.SparePartJpaEntity;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.UUID;

@Component
public class SparePartPersistenceMapper {
    public SparePart toDomain(SparePartJpaEntity entity) {
        String compatibleModelsStr = String.join(",", entity.getCompatibleModels());
        return new SparePart(entity.getId().toString(), entity.getName(), entity.getManufacturer(), entity.getPartNumber(), entity.getPrice(), entity.getQuantityInStock(), compatibleModelsStr);
    }

    public SparePartJpaEntity toJpa(SparePart domain) {
        SparePartJpaEntity entity = new SparePartJpaEntity();
        if (domain.getId() != null) {
            entity.setId(UUID.fromString(domain.getId()));
        }
        entity.setName(domain.getName());
        entity.setManufacturer(domain.getManufacturer());
        entity.setPartNumber(domain.getPartNumber());
        entity.setPrice(domain.getPrice());
        entity.setQuantityInStock(domain.getQuantityInStock());
        entity.setCompatibleModels(new HashSet<>(domain.getCompatibleModels()));
        return entity;
    }
}
