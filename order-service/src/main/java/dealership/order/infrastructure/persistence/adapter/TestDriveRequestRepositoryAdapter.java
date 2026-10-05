package dealership.order.infrastructure.persistence.adapter;

import dealership.order.core.application.port.out.TestDriveRequestRepository;
import dealership.order.core.domain.entity.testdrive.TestDriveRequest;
import dealership.order.core.domain.exception.EntityNotFoundException;
import dealership.order.core.domain.exception.TestDriveConflictException;
import dealership.order.infrastructure.persistence.entity.TestDriveRequestJpaEntity;
import dealership.order.infrastructure.persistence.mapper.TestDriveRequestPersistenceMapper;
import dealership.order.infrastructure.persistence.repository.TestDriveRequestJpaRepository;
import org.springframework.stereotype.Repository;
import org.springframework.dao.DataIntegrityViolationException;
import org.hibernate.exception.ConstraintViolationException;
import org.postgresql.util.PSQLException;

import java.sql.SQLException;

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
        try {
            return mapper.toDomain(jpaRepository.saveAndFlush(entity));
        } catch (DataIntegrityViolationException exception) {
            if (isTestDriveExclusion(exception)) {
                throw new TestDriveConflictException(
                        "Автомобиль уже занят на выбранное время", exception);
            }
            throw exception;
        }
    }

    @Override
    public TestDriveRequest findById(String id) {
        return jpaRepository.findByIdAndRemovedFalse(UUID.fromString(id)).map(mapper::toDomain).orElseThrow(() -> new EntityNotFoundException("Заявка на тест-драйв с id '%s' не найдена".formatted(id)));
    }

    @Override
    public TestDriveRequest findByIdForUpdate(String id) {
        return jpaRepository.findByIdForUpdate(UUID.fromString(id)).map(mapper::toDomain)
                .orElseThrow(() -> new EntityNotFoundException(
                        "Заявка на тест-драйв с id '%s' не найдена".formatted(id)));
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
    public List<TestDriveRequest> findByClientId(String clientId) {
        return jpaRepository.findByClientIdAndRemovedFalse(clientId).stream()
                .map(mapper::toDomain)
                .toList();
    }

    @Override
    public void deleteById(String id) {
        TestDriveRequestJpaEntity entity = jpaRepository.findByIdAndRemovedFalse(UUID.fromString(id)).orElseThrow(() -> new EntityNotFoundException("Заявка с id '%s' не найдена".formatted(id)));
        entity.setRemoved(true);
        jpaRepository.save(entity);
    }

    private boolean isTestDriveExclusion(Throwable exception) {
        boolean exclusionState = false;
        boolean namedConstraint = false;
        for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
            if (cause instanceof SQLException sqlException
                    && "23P01".equals(sqlException.getSQLState())) {
                exclusionState = true;
            }
            if (cause instanceof ConstraintViolationException constraintViolation
                    && "test_drive_no_overlapping_active".equals(
                    constraintViolation.getConstraintName())) {
                namedConstraint = true;
            }
            if (cause instanceof PSQLException postgresException
                    && postgresException.getServerErrorMessage() != null
                    && "test_drive_no_overlapping_active".equals(
                    postgresException.getServerErrorMessage().getConstraint())) {
                namedConstraint = true;
            }
        }
        return exclusionState && namedConstraint;
    }
}
