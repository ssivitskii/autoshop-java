package dealership.order;

import dealership.order.core.domain.entity.testdrive.TestDriveRequest;
import dealership.order.core.domain.exception.TestDriveConflictException;
import dealership.order.infrastructure.persistence.adapter.TestDriveRequestRepositoryAdapter;
import dealership.order.infrastructure.persistence.entity.TestDriveRequestJpaEntity;
import dealership.order.infrastructure.persistence.mapper.TestDriveRequestPersistenceMapper;
import dealership.order.infrastructure.persistence.repository.TestDriveRequestJpaRepository;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.Test;
import org.postgresql.util.PSQLException;
import org.postgresql.util.ServerErrorMessage;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TestDriveRequestRepositoryAdapterTest {

    private final TestDriveRequestJpaRepository repository = mock(TestDriveRequestJpaRepository.class);
    private final TestDriveRequestPersistenceMapper mapper = mock(TestDriveRequestPersistenceMapper.class);
    private final TestDriveRequestRepositoryAdapter adapter =
            new TestDriveRequestRepositoryAdapter(repository, mapper);

    @Test
    void translatesPostgresExclusionWhenHibernateDoesNotExposeConstraintName() {
        TestDriveRequest request = request();
        TestDriveRequestJpaEntity entity = new TestDriveRequestJpaEntity();
        when(mapper.toJpa(request)).thenReturn(entity);
        when(repository.saveAndFlush(entity)).thenThrow(violation("test_drive_no_overlapping_active"));

        assertThrows(TestDriveConflictException.class, () -> adapter.save(request));
    }

    @Test
    void doesNotTranslateAnotherPostgresExclusionConstraint() {
        TestDriveRequest request = request();
        TestDriveRequestJpaEntity entity = new TestDriveRequestJpaEntity();
        DataIntegrityViolationException violation = violation("another_exclusion");
        when(mapper.toJpa(request)).thenReturn(entity);
        when(repository.saveAndFlush(entity)).thenThrow(violation);

        assertSame(violation, assertThrows(
                DataIntegrityViolationException.class, () -> adapter.save(request)));
    }

    private DataIntegrityViolationException violation(String constraintName) {
        ServerErrorMessage serverError = new ServerErrorMessage(
                "SERROR\0C23P01\0Mconflicting key value violates exclusion constraint\0n"
                        + constraintName + "\0\0");
        PSQLException postgres = new PSQLException(serverError);
        ConstraintViolationException hibernate =
                new ConstraintViolationException("constraint violation", postgres, null);
        return new DataIntegrityViolationException("could not execute statement", hibernate);
    }

    private TestDriveRequest request() {
        return new TestDriveRequest("client", "car", LocalDateTime.of(2030, 1, 1, 10, 0));
    }
}
