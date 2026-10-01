package dealership.storage.infrastructure.persistence.repository;

import dealership.storage.infrastructure.persistence.entity.CarJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CarJpaRepository extends JpaRepository<CarJpaEntity, UUID>, JpaSpecificationExecutor<CarJpaEntity> {
    List<CarJpaEntity> findByAvailableTrueAndRemovedFalse();

    List<CarJpaEntity> findByRemovedFalse();

    Optional<CarJpaEntity> findByIdAndRemovedFalse(UUID id);

    boolean existsByIdAndRemovedFalse(UUID id);

}
