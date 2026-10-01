package dealership.order.infrastructure.persistence.repository;

import dealership.order.infrastructure.persistence.entity.TestDriveRequestJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface TestDriveRequestJpaRepository extends JpaRepository<TestDriveRequestJpaEntity, UUID> {
    List<TestDriveRequestJpaEntity> findByRemovedFalse();

    Optional<TestDriveRequestJpaEntity> findByIdAndRemovedFalse(UUID id);

    List<TestDriveRequestJpaEntity> findByCarIdAndRemovedFalse(String carId);
}
