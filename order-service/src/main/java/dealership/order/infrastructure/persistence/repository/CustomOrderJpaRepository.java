package dealership.order.infrastructure.persistence.repository;

import dealership.order.infrastructure.persistence.entity.CustomOrderJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CustomOrderJpaRepository extends JpaRepository<CustomOrderJpaEntity, UUID> {
    List<CustomOrderJpaEntity> findByRemovedFalse();

    Optional<CustomOrderJpaEntity> findByIdAndRemovedFalse(UUID id);

    List<CustomOrderJpaEntity> findByClientIdAndRemovedFalse(String clientId);
}
