package dealership.storage.infrastructure.persistence.mapper;

import dealership.storage.core.domain.entity.car.CarModel;
import dealership.storage.infrastructure.persistence.entity.CarModelJpaEntity;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class CarModelPersistenceMapper {
    public CarModel toDomain(CarModelJpaEntity entity) {
        CarModel model = new CarModel(entity.getId().toString(), entity.getBrand(), entity.getModelName(), entity.getBasePrice());
        entity.getComponentCategoryIds().forEach(model::addComponentCategory);
        return model;
    }

    public CarModelJpaEntity toJpa(CarModel domain) {
        CarModelJpaEntity entity = new CarModelJpaEntity();
        if (domain.getId() != null) {
            entity.setId(UUID.fromString(domain.getId()));
        }
        entity.setBrand(domain.getBrand());
        entity.setModelName(domain.getModelName());
        entity.setBasePrice(domain.getBasePrice());
        entity.setComponentCategoryIds(new java.util.HashSet<>(domain.getComponentCategoryIds()));
        return entity;
    }
}
