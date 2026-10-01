package dealership.storage.infrastructure.persistence.repository;

import dealership.storage.infrastructure.persistence.entity.AssemblyOrderJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface AssemblyOrderJpaRepository extends JpaRepository<AssemblyOrderJpaEntity, UUID> {
    Optional<AssemblyOrderJpaEntity> findByIdAndRemovedFalse(UUID id);

    boolean existsBySourceOrderIdAndRemovedFalse(String sourceOrderId);
}
