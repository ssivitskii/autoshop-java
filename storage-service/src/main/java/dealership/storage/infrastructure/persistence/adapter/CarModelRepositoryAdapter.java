package dealership.storage.infrastructure.persistence.adapter;

import dealership.storage.core.application.port.out.CarModelRepository;
import dealership.storage.core.domain.entity.car.CarModel;
import dealership.storage.core.domain.exception.EntityNotFoundException;
import dealership.storage.infrastructure.persistence.entity.CarModelJpaEntity;
import dealership.storage.infrastructure.persistence.mapper.CarModelPersistenceMapper;
import dealership.storage.infrastructure.persistence.repository.CarModelJpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public class CarModelRepositoryAdapter implements CarModelRepository {
    private final CarModelJpaRepository jpaRepository;
    private final CarModelPersistenceMapper mapper;

    public CarModelRepositoryAdapter(CarModelJpaRepository jpaRepository, CarModelPersistenceMapper mapper) {
        this.jpaRepository = jpaRepository;
        this.mapper = mapper;
    }

    @Override
    public CarModel save(CarModel model) {
        CarModelJpaEntity entity = mapper.toJpa(model);
        return mapper.toDomain(jpaRepository.save(entity));
    }

    @Override
    public CarModel findById(String id) {
        return jpaRepository.findByIdAndRemovedFalse(UUID.fromString(id)).map(mapper::toDomain).orElseThrow(() -> new EntityNotFoundException("Модель автомобиля с id '%s' не найдена".formatted(id)));
    }

    @Override
    public List<CarModel> findAll() {
        return jpaRepository.findByRemovedFalse().stream().map(mapper::toDomain).toList();
    }

    @Override
    public void deleteById(String id) {
        CarModelJpaEntity entity = jpaRepository.findByIdAndRemovedFalse(UUID.fromString(id)).orElseThrow(() -> new EntityNotFoundException("Модель с id '%s' не найдена".formatted(id)));
        entity.setRemoved(true);
        jpaRepository.save(entity);
    }
}