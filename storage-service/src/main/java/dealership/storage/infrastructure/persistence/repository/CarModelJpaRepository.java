package dealership.storage.infrastructure.persistence.repository;

import dealership.storage.infrastructure.persistence.entity.CarModelJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CarModelJpaRepository extends JpaRepository<CarModelJpaEntity, UUID>, JpaSpecificationExecutor<CarModelJpaEntity> {

    List<CarModelJpaEntity> findByRemovedFalse();

    Optional<CarModelJpaEntity> findByIdAndRemovedFalse(UUID id);
}