package dealership.order.infrastructure.persistence.repository;

import dealership.order.infrastructure.persistence.entity.TestDriveRequestJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import jakarta.persistence.LockModeType;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface TestDriveRequestJpaRepository extends JpaRepository<TestDriveRequestJpaEntity, UUID> {
    List<TestDriveRequestJpaEntity> findByRemovedFalse();

    Optional<TestDriveRequestJpaEntity> findByIdAndRemovedFalse(UUID id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from TestDriveRequestJpaEntity t where t.id = :id and t.removed = false")
    Optional<TestDriveRequestJpaEntity> findByIdForUpdate(@Param("id") UUID id);

    List<TestDriveRequestJpaEntity> findByCarIdAndRemovedFalse(String carId);

    List<TestDriveRequestJpaEntity> findByClientIdAndRemovedFalse(String clientId);
}
