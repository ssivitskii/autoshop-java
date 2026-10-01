package dealership.storage.infrastructure.persistence.repository;

import dealership.storage.infrastructure.persistence.entity.ComponentCategoryJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ComponentCategoryJpaRepository extends JpaRepository<ComponentCategoryJpaEntity, UUID> {
    List<ComponentCategoryJpaEntity> findByRemovedFalse();

    Optional<ComponentCategoryJpaEntity> findByIdAndRemovedFalse(UUID id);
}
