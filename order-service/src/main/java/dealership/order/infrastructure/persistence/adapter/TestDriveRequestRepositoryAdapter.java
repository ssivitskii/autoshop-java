package dealership.order.infrastructure.persistence.adapter;

import dealership.order.core.application.port.out.TestDriveRequestRepository;
import dealership.order.core.domain.entity.testdrive.TestDriveRequest;
import dealership.order.core.domain.exception.EntityNotFoundException;
import dealership.order.infrastructure.persistence.entity.TestDriveRequestJpaEntity;
import dealership.order.infrastructure.persistence.mapper.TestDriveRequestPersistenceMapper;
import dealership.order.infrastructure.persistence.repository.TestDriveRequestJpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public class TestDriveRequestRepositoryAdapter implements TestDriveRequestRepository {
    private final TestDriveRequestJpaRepository jpaRepository;
    private final TestDriveRequestPersistenceMapper mapper;

    public TestDriveRequestRepositoryAdapter(TestDriveRequestJpaRepository jpaRepository, TestDriveRequestPersistenceMapper mapper) {
        this.jpaRepository = jpaRepository;
        this.mapper = mapper;
    }

    @Override
    public TestDriveRequest save(TestDriveRequest request) {
        TestDriveRequestJpaEntity entity = mapper.toJpa(request);
        return mapper.toDomain(jpaRepository.save(entity));
    }

    @Override
    public TestDriveRequest findById(String id) {
        return jpaRepository.findByIdAndRemovedFalse(UUID.fromString(id)).map(mapper::toDomain).orElseThrow(() -> new EntityNotFoundException("Заявка на тест-драйв с id '%s' не найдена".formatted(id)));
    }

    @Override
    public List<TestDriveRequest> findAll() {
        return jpaRepository.findByRemovedFalse().stream().map(mapper::toDomain).toList();
    }

    @Override
    public List<TestDriveRequest> findByCarId(String carId) {
        return jpaRepository.findByCarIdAndRemovedFalse(carId).stream().map(mapper::toDomain).toList();
    }

    @Override
    public void deleteById(String id) {
        TestDriveRequestJpaEntity entity = jpaRepository.findByIdAndRemovedFalse(UUID.fromString(id)).orElseThrow(() -> new EntityNotFoundException("Заявка с id '%s' не найдена".formatted(id)));
        entity.setRemoved(true);
        jpaRepository.save(entity);
    }
}
