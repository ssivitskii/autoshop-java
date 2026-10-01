package dealership.storage.infrastructure.persistence.repository;

import dealership.storage.infrastructure.persistence.entity.ComponentVariantJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ComponentVariantJpaRepository extends JpaRepository<ComponentVariantJpaEntity, UUID> {
    List<ComponentVariantJpaEntity> findByRemovedFalse();

    Optional<ComponentVariantJpaEntity> findByIdAndRemovedFalse(UUID id);

    List<ComponentVariantJpaEntity> findByCategoryIdAndRemovedFalse(String categoryId);
}
