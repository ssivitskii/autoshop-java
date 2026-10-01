package dealership.storage.infrastructure.persistence.repository;

import dealership.storage.infrastructure.persistence.entity.SparePartJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface SparePartJpaRepository extends JpaRepository<SparePartJpaEntity, UUID> {
    List<SparePartJpaEntity> findByRemovedFalse();

    Optional<SparePartJpaEntity> findByIdAndRemovedFalse(UUID id);
}
