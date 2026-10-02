package dealership.storage.infrastructure.persistence.adapter;

import dealership.storage.core.application.dto.CarFilterDto;
import dealership.storage.core.application.port.out.CarRepository;
import dealership.storage.core.domain.entity.car.Car;
import dealership.storage.core.domain.exception.EntityNotFoundException;
import dealership.storage.infrastructure.persistence.entity.CarJpaEntity;
import dealership.storage.infrastructure.persistence.mapper.CarPersistenceMapper;
import dealership.storage.infrastructure.persistence.repository.CarJpaRepository;
import dealership.storage.infrastructure.persistence.specification.CarSpecification;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class CarRepositoryAdapter implements CarRepository {

    private final CarJpaRepository jpaRepository;
    private final CarPersistenceMapper mapper;

    public CarRepositoryAdapter(CarJpaRepository jpaRepository, CarPersistenceMapper mapper) {
        this.jpaRepository = jpaRepository;
        this.mapper = mapper;
    }

    @Override
    public Car save(Car car) {
        CarJpaEntity entity = mapper.toJpa(car);
        CarJpaEntity saved = jpaRepository.save(entity);
        return mapper.toDomain(saved);
    }

    @Override
    public Car findById(String id) {
        return jpaRepository.findByIdAndRemovedFalse(UUID.fromString(id)).map(mapper::toDomain).orElseThrow(() -> new EntityNotFoundException("Автомобиль с id '%s' не найден".formatted(id)));
    }

    @Override
    public Optional<Car> findByIdOptional(String id) {
        return jpaRepository.findByIdAndRemovedFalse(UUID.fromString(id)).map(mapper::toDomain);
    }

    @Override
    public List<Car> findAll() {
        return jpaRepository.findByRemovedFalse().stream().map(mapper::toDomain).toList();
    }

    @Override
    public List<Car> findAvailable() {
        return jpaRepository.findByAvailableTrueAndRemovedFalse().stream().map(mapper::toDomain).toList();
    }

    @Override
    public List<Car> findByFilter(CarFilterDto filter) {
        return jpaRepository.findAll(CarSpecification.byFilter(filter)).stream().map(mapper::toDomain).toList();
    }

    @Override
    public void deleteById(String id) {
        CarJpaEntity entity = jpaRepository.findByIdAndRemovedFalse(UUID.fromString(id)).orElseThrow(() -> new EntityNotFoundException("Автомобиль с id '%s' не найден".formatted(id)));
        entity.setRemoved(true);
        jpaRepository.save(entity);
    }

    @Override
    public boolean existsById(String id) {
        return jpaRepository.existsByIdAndRemovedFalse(UUID.fromString(id));
    }

    @Override
    @Transactional
    public boolean reserve(String carId, String orderId) {
        return jpaRepository.reserve(UUID.fromString(carId), UUID.fromString(orderId)) == 1;
    }

    @Override
    @Transactional
    public void release(String carId, String orderId) {
        UUID carUuid = UUID.fromString(carId);
        jpaRepository.release(carUuid, UUID.fromString(orderId));
        if (!jpaRepository.existsByIdAndRemovedFalse(carUuid)) {
            throw new EntityNotFoundException("Автомобиль с id '%s' не найден".formatted(carId));
        }
    }
}
